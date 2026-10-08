// Independent public-DOM/API black-box checks; no implementation imports.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const [base,out,role,db,port]=process.argv.slice(2);
if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base)||!/^\d+$/.test(port)||!['ADMIN','VIEWER','OPERATOR','APPROVER'].includes(role))throw Error('explicit loopback fixture required');
const here=path.dirname(fileURLToPath(import.meta.url));
const fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json'),'utf8'));
const inventory=JSON.parse(fs.readFileSync(path.join(here,'scenarios.json'),'utf8'));
const results=[],diagnostics=[],external=[],errors=[];
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const assert=(ok,message)=>{if(!ok)throw Error(message)};
let ws,operation='CDP setup',rememberSections=async()=>{};
try{
 const targets=await(await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(5000)})).json();
 const blankPages=targets.filter(t=>t.type==='page'&&t.url==='about:blank');
 diagnostics.push({target_selection:{strategy:'reuse unique initial blank page',candidates:blankPages.map(t=>({id:t.id,type:t.type,url:t.url}))}});
 assert(blankPages.length===1,'unique owned initial blank page unavailable: '+blankPages.length);
 const target=blankPages[0];assert(typeof target.id==='string'&&typeof target.webSocketDebuggerUrl==='string','initial page CDP identity unavailable');
 ws=new WebSocket(target.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('CDP connect timeout')),10000);ws.addEventListener('open',()=>{clearTimeout(timer);resolve()},{once:true});ws.addEventListener('error',()=>{clearTimeout(timer);reject(Error('CDP connect failed'))},{once:true})});
 let seq=0;const pending=new Map();
 ws.addEventListener('message',e=>{
   const m=JSON.parse(e.data);
   if(m.id){const p=pending.get(m.id);if(!p)return;pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result)}
   else if(m.method==='Fetch.requestPaused'){
     const u=m.params.request.url;
     if(u.startsWith(base+'/')||/^(data:|about:)/.test(u))call('Fetch.continueRequest',{requestId:m.params.requestId}).catch(e=>errors.push(e.message));
     else{external.push(u);call('Fetch.failRequest',{requestId:m.params.requestId,errorReason:'BlockedByClient'}).catch(e=>errors.push(e.message))}
   }else if(m.method==='Runtime.exceptionThrown')errors.push(JSON.stringify(m.params.exceptionDetails));
 });
 const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq,step=operation,timer=setTimeout(()=>{pending.delete(id);diagnostics.push({cdp_timeout:{method,operation:step}});reject(Error('CDP timeout '+method+' [operation='+step+']'))},12000);pending.set(id,{resolve:x=>{clearTimeout(timer);resolve(x)},reject:e=>{clearTimeout(timer);reject(e)}});ws.send(JSON.stringify({id,method,params}))});
 const ev=async expression=>{const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value};
 const loaded=async route=>{let last;for(let i=0;i<70;i++){try{last=await ev('({url:location.href,ready:document.readyState})');if(last.url===base+route&&last.ready==='complete')return}catch(e){if(!/Execution context was destroyed|Cannot find context with specified id/i.test(e.message))throw e}await wait(100)}throw Error('navigation timeout '+route+' '+JSON.stringify(last))};
 rememberSections=async()=>{try{const records=await ev('({sections:window.__fd2SectionDiagnostics?.splice(0)||[],categories:window.__fd2CategoryDiagnostics?.splice(0)||[]})');if(records.sections.length)diagnostics.push({section_associations:records.sections});if(records.categories.length)diagnostics.push({category_controls:records.categories})}catch(e){diagnostics.push({section_diagnostics_unavailable:e.message})}};
 const nav=async route=>{operation='navigate '+route;await rememberSections();const r=await call('Page.navigate',{url:base+route});if(r.errorText)throw Error(r.errorText);operation='wait for '+route;await loaded(route);operation='install public observers '+route;await installDOM();diagnostics.push({route,url:await ev('location.href')})};
 const installDOM=()=>ev(`window.__fd2SectionDiagnostics=window.__fd2SectionDiagnostics||[];window.__fd2CategoryDiagnostics=window.__fd2CategoryDiagnostics||[];window.__fd2={
   sections(pattern){
     const re=new RegExp(pattern,'i'),headingSelector='h1,h2,h3,h4,h5,h6,[role="heading"]';
     const headings=[...document.querySelectorAll(headingSelector)].filter(h=>re.test(h.textContent));
     const runMatch=location.pathname.match(new RegExp('^/ui/runs/([^/]+)/?$'));let runId=null;
     if(runMatch){try{runId=decodeURIComponent(runMatch[1])}catch{runId=runMatch[1]}}
     const trace={pattern,url:location.href,run_id:runId,candidates:[]};window.__fd2SectionDiagnostics.push(trace);
     if(!headings.length)throw Error('public heading unavailable '+pattern);
     // A section needs actual public content; a refresh marker or heading alone is insufficient.
     const structure=/timeline/i.test(pattern)?'ol,ul,li,table,time,details':/attempt|execution history/i.test(pattern)?'table,ol,ul,details,dl':'table,svg,ol,ul,details,dl,pre,p';
     const identities=runId?[...document.querySelectorAll(headingSelector)].filter(h=>h.textContent.trim()===runId):[];
     const meaningful=e=>e&&!e.matches('html,body,main,'+headingSelector)&&!identities.some(h=>e.contains(h))&&(e.matches(structure)||e.querySelector(structure));
     const describe=e=>({tag:e.tagName,id:e.id||null,structures:[...e.querySelectorAll(structure)].map(n=>n.tagName).filter((n,i,a)=>a.indexOf(n)===i)});
     const content=h=>{
       if(h.id){for(const labelled of document.querySelectorAll('[aria-labelledby]'))if(labelled.getAttribute('aria-labelledby').split(/\\s+/).includes(h.id)&&meaningful(labelled))return {element:labelled,basis:'aria-labelledby'}}
       const enclosing=h.closest('[data-refresh-region],section,article');if(meaningful(enclosing))return {element:enclosing,basis:'enclosing public section'};
       // A public heading may precede its section rather than live inside it.
       for(let level=h;level&&level!==document.body&&level!==document.documentElement;level=level.parentElement){
         if(level===enclosing||level.matches('main'))break;
         for(let sibling=level.nextElementSibling;sibling;sibling=sibling.nextElementSibling){
           if(sibling.matches(headingSelector))break;
           const marked=[...(sibling.matches('[data-refresh-region]')?[sibling]:[]),...sibling.querySelectorAll('[data-refresh-region]')];
           for(const candidate of marked)if(meaningful(candidate))return {element:candidate,basis:'following public refresh region'};
           if(meaningful(sibling))return {element:sibling,basis:'following public content'};
         }
       }
       return null;
     };
     const found=[];
     for(const heading of headings){
       const candidate={tag:heading.tagName,title:heading.textContent.trim(),id:heading.id||null};trace.candidates.push(candidate);
       if(runId&&candidate.title===runId){candidate.skipped='current URL Run identity';continue}
       try{const association=content(heading);if(!association){candidate.skipped='no associated public content matching '+structure;continue}candidate.selected={...describe(association.element),basis:association.basis};found.push(association.element)}
       catch(e){candidate.skipped='association unavailable: '+e.message}
     }
     if(!found.length)throw Error('associated public section unavailable '+pattern+' '+JSON.stringify(trace.candidates));
     return [...new Set(found)]
   },
   section(pattern){return this.sections(pattern).sort((a,b)=>a.textContent.length-b.textContent.length)[0]},
   text(){return document.body.textContent},
   button(pattern){return [...document.querySelectorAll('button,a,input[type=submit]')].find(e=>new RegExp(pattern,'i').test(e.textContent||e.value))},
   timeline(){return this.section('timeline')},
   rows(){
     const s=this.timeline(),selector='details,li,tr,article',stamp=new RegExp('[0-9]{4}-[0-9]{2}-[0-9]{2}T');
     const candidates=[...s.querySelectorAll(selector)].map(element=>{
       const summary=element.matches('details')?element.querySelector(':scope > summary'):null;
       if(summary&&stamp.test(summary.textContent))return {element,rank:3};
       if([...element.querySelectorAll('time')].some(t=>t.closest(selector)===element&&stamp.test(t.getAttribute('datetime')||t.textContent)))return {element,rank:2};
       const own=[];const walker=document.createTreeWalker(element,NodeFilter.SHOW_TEXT);let node;
       while(node=walker.nextNode())if(node.parentElement.closest(selector)===element)own.push(node.nodeValue);
       return {element,rank:stamp.test(own.join(' '))?1:0};
     }).filter(candidate=>candidate.rank);
     // Prefer one semantic event over its wrappers or nested detail bodies.
     return candidates.filter(a=>!candidates.some(b=>a!==b&&((b.element.contains(a.element)&&b.rank>a.rank)||(a.element.contains(b.element)&&b.rank>=a.rank)))).map(a=>a.element);
   },
   visibleElement(e){
     if(!e?.getClientRects().length)return false;
     for(let p=e;p;p=p.parentElement){
       const style=getComputedStyle(p);if(p.hidden||style.display==='none'||style.visibility==='hidden'||style.visibility==='collapse'||style.contentVisibility==='hidden')return false;
       if(p!==e&&p.matches('details')&&!p.open&&!p.querySelector(':scope > summary')?.contains(e))return false;
     }
     return true;
   },
   rowFacts(visible=false){return this.rows().map((e,index)=>({key:e.id||[...e.attributes].filter(a=>/key/i.test(a.name)).map(a=>a.value).join('|')||'position:'+index,text:e.textContent,visible:this.visibleElement(e),category_attributes:[...e.attributes].filter(a=>/category|kind/i.test(a.name)).map(a=>({name:a.name,value:a.value}))})).filter(row=>!visible||row.visible)},
   labelFacts(e){
     const related=[...(e.labels||[])].map(node=>({source:'native label',node}));
     for(const id of (e.getAttribute('aria-labelledby')||'').split(' ').filter(Boolean)){const node=document.getElementById(id);if(node)related.push({source:'aria-labelledby',node})}
     const preceding=e.previousElementSibling;if(preceding?.matches('label')&&(!preceding.htmlFor||preceding.htmlFor===e.id))related.push({source:'preceding label',node:preceding});
     const labels=related.map(({source,node})=>{const clone=node.cloneNode(true);clone.querySelectorAll('input,select,textarea,button,option,optgroup').forEach(control=>control.remove());return {source,id:node.id||null,for:node.getAttribute('for'),raw_text:node.textContent,text:clone.matches('input,select,textarea,button,option,optgroup')?'':clone.textContent.trim()}});
     return {aria_label:e.getAttribute('aria-label')||'',aria_labelledby:e.getAttribute('aria-labelledby')||'',labels};
   },
   labelText(e){const facts=this.labelFacts(e);return [facts.aria_label,...facts.labels.map(label=>label.text)].join(' ').trim()},
   controlFacts(e){return {id:e.id||null,name:e.name||null,label:this.labelText(e),label_facts:this.labelFacts(e),visible:this.visibleElement(e),value:e.value,options:[...(e.options||[])].map(o=>({value:o.value,label:o.textContent,disabled:o.disabled}))}},
   attemptIds(){
     const s=this.section('attempt|execution history'),matcher=new RegExp('^fd2-[A-Za-z0-9_-]+-a[1-9][0-9]*$'),found=new Map();
     const add=(value,node)=>{const id=value.trim();if(!matcher.test(id)||node.closest('script,style,template'))return;const row=node.closest('tr,[role="row"],article,details');if(!found.has(id))found.set(id,{id,tag:node.tagName,row_tag:row?.tagName||null,row_id:row?.id||null})};
     for(const node of s.querySelectorAll('code,a,td,th,span,p,[id]'))add(node.textContent,node);
     const walker=document.createTreeWalker(s,NodeFilter.SHOW_TEXT);let node;
     while(node=walker.nextNode())add(node.nodeValue,node.parentElement);
     return [...found.values()].sort((a,b)=>a.id.localeCompare(b.id));
   },
   graph(){return [...document.querySelectorAll('svg')].find(s=>/fd2-/.test(s.textContent))},
   anchors(){return [...document.querySelectorAll('a[href^="#"]')].map(a=>({href:a.getAttribute('href'),text:a.textContent,exists:!!document.getElementById(decodeURIComponent(a.getAttribute('href').slice(1)))}))},
   form(action){return [...document.forms].find(f=>new URL(f.action).pathname.endsWith('/'+action))},
   filterText(){return [...document.querySelectorAll('input')].find(e=>e.type==='search'||/filter|search/i.test((e.placeholder||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.id||'')))},
   filterCategory(){
     const controls=[...document.querySelectorAll('select')].map((element,index)=>{const facts=this.controlFacts(element),semantic=/(^|[^A-Za-z])category([^A-Za-z]|$)|类别/i.test(facts.label);return {element,facts:{index,...facts,semantic,exclusion:!facts.visible?'hidden ancestor or no actual layout':!semantic?'label does not identify Category':null}}});
     const candidates=controls.filter(control=>!control.facts.exclusion),trace={url:location.href,controls:controls.map(control=>control.facts),candidate_count:candidates.length};window.__fd2CategoryDiagnostics.push(trace);
     if(candidates.length!==1)throw Error('visible semantic Category control count '+candidates.length+' '+JSON.stringify(trace.controls));
     trace.selected_index=candidates[0].facts.index;trace.selected_id=candidates[0].facts.id;return candidates[0].element;
   },
   visibleRows(){return this.rowFacts(true).map(row=>row.text)},
   open(){document.querySelectorAll('details').forEach(d=>d.open=true)}
 };true`);
 const text=()=>ev('document.body.textContent');
 const sectionText=pattern=>ev(`window.__fd2.sections(${JSON.stringify(pattern)}).map(s=>s.textContent).join('\\n')`);
 const observe=async(label,fn)=>{try{return await fn()}catch(e){diagnostics.push({observation:label,error:e.message});return null}};
 const saveDiagnostics=()=>fs.writeFileSync(path.join(out,'diagnostics.json'),JSON.stringify({diagnostics,external_requests:external,runtime_errors:errors},null,2));
 const timelineFacts=()=>ev('({rows:window.__fd2.rowFacts(),visible:window.__fd2.rowFacts(true)})');
 const categoryFacts=()=>ev(`(()=>{const e=window.__fd2.filterCategory();return {control:window.__fd2.controlFacts(e),text:window.__fd2.filterText()?.value,rows:window.__fd2.rowFacts(),visible:window.__fd2.rowFacts(true)}})()`);
 const test=async(id,fn)=>{operation='scenario '+id;const item=inventory.find(s=>s.id===id);if(!item)throw Error('unknown scenario '+id);try{const detail=await fn();results.push({id,behavior:item.behavior,passed:true,detail});console.log('PASS',id)}catch(e){results.push({id,behavior:item.behavior,passed:false,detail:e.message});console.log('FAIL',id,e.message);try{await shot('failed-'+id)}catch{}}finally{await rememberSections();fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(results,null,2));fs.writeFileSync(path.join(out,'diagnostics.json'),JSON.stringify({diagnostics,external_requests:external,runtime_errors:errors},null,2))}};
 const api=async route=>ev(`fetch(${JSON.stringify(route)}).then(async r=>{if(!r.ok)throw Error('public API '+r.status);return r.json()})`);
 const refresh=async()=>{
   await ev(`(()=>{window.__fd2RefreshBefore=[...document.querySelectorAll('[data-refresh-region][id]')].map(e=>({id:e.id,node:e}));if(!window.__fd2RefreshBefore.length)throw Error('public refresh regions unavailable');const b=window.__fd2.button('^Refresh facts$')||document.querySelector('[data-refresh-now]');if(!b)throw Error('Refresh facts unavailable');b.click()})()`);
   const deadline=Date.now()+9000;let replaced=false;
   while(Date.now()<deadline){replaced=await ev(`window.__fd2RefreshBefore.some(before=>{const current=document.getElementById(before.id);return current&&current!==before.node})`);if(replaced)break;await wait(100)}
   assert(replaced,'no unprotected region actually replaced before refresh deadline');await installDOM();
 };
 const mutate=mode=>{const r=spawnSync('C:/green/python312/python.exe',[path.join(here,'fixture.py'),db,mode],{windowsHide:true,timeout:5000,encoding:'utf8'});assert(r.status===0,'disposable mutation failed '+r.stderr)};
 const shot=async name=>{
   operation='screenshot '+name;
   const metrics=await call('Page.getLayoutMetrics'),size=metrics.cssContentSize||metrics.contentSize;
   const original=await ev('({x:scrollX,y:scrollY,width:innerWidth,height:innerHeight,dpr:devicePixelRatio})');
   const width=Math.ceil(size.width),height=Math.ceil(size.height),pixelBudget=4000000;
   const trace={name,width:original.width,document_width:width,total_height:height,device_pixel_ratio:original.dpr,max_segment_height:2000,pixel_budget:pixelBudget,captureBeyondViewport:false,clip_policy:'no clip; capture actual viewport',original_scroll:{x:original.x,y:original.y},segments:[]};
   diagnostics.push({screenshot:trace});saveDiagnostics();
   try{
     assert(Number.isFinite(width)&&Number.isFinite(height)&&width>0&&height>0,'public document layout unavailable');
     let coveredBottom=0;
     for(let part=0;coveredBottom<height;part++){
       const filename=name+(part?'-part-'+String(part+1).padStart(3,'0'):'')+'.png';
       operation='screenshot scroll '+name+' part '+(part+1);
       const view=await ev(`new Promise(resolve=>{window.scrollTo({left:0,top:${coveredBottom},behavior:'instant'});requestAnimationFrame(()=>requestAnimationFrame(()=>resolve({x:scrollX,y:scrollY,width:innerWidth,height:innerHeight,dpr:devicePixelRatio})))})`);
       assert(view.width===original.width&&view.height===original.height&&view.dpr===original.dpr,'viewport changed during screenshot');
       assert(view.height>0&&view.height<=2000&&view.dpr>0&&Number.isFinite(view.dpr)&&Math.ceil(view.width*view.dpr)*Math.ceil(view.height*view.dpr)<=pixelBudget,'actual viewport exceeds screenshot bounds');
       assert(view.x===0&&width<=view.width,'document width exceeds actual viewport coverage');
       const bottom=Math.min(height,view.y+view.height);
       assert(view.y>=0&&view.y<=coveredBottom&&bottom>coveredBottom,'viewport capture leaves a vertical gap or cannot advance');
       const segment={filename,requested_scroll_y:coveredBottom,actual_viewport:view,document_coverage:{x:0,y:view.y,width,height:bottom-view.y},covered_before:coveredBottom,covered_after:bottom,captureBeyondViewport:false,clip:null,status:'requested'};trace.segments.push(segment);saveDiagnostics();
       operation='screenshot capture '+name+' part '+(part+1);
       const r=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false}),png=Buffer.from(r.data,'base64');
       segment.pixel_width=png.readUInt32BE(16);segment.pixel_height=png.readUInt32BE(20);
       assert(segment.pixel_width*segment.pixel_height<=pixelBudget,'screenshot bitmap exceeds segment pixel budget');
       assert(Math.abs(segment.pixel_width-Math.round(view.width*view.dpr))<=1&&Math.abs(segment.pixel_height-Math.round(view.height*view.dpr))<=1,'screenshot dimensions differ from actual viewport');
       fs.writeFileSync(path.join(out,filename),png);segment.status='saved';coveredBottom=bottom;trace.covered_through=coveredBottom;saveDiagnostics();
     }
     const finalMetrics=await call('Page.getLayoutMetrics');trace.final_document_height=Math.ceil((finalMetrics.cssContentSize||finalMetrics.contentSize).height);
     assert(coveredBottom>=height&&coveredBottom>=trace.final_document_height,'viewport captures did not cover the document bottom');trace.status='complete';return {name,segments:trace.segments.length};
   }catch(e){trace.error=e.message;throw e}finally{
     try{operation='screenshot restore scroll '+name;const restored=await ev(`new Promise(resolve=>{window.scrollTo({left:${original.x},top:${original.y},behavior:'instant'});requestAnimationFrame(()=>requestAnimationFrame(()=>resolve({x:scrollX,y:scrollY})))})`);trace.restored_scroll=restored;assert(Math.abs(restored.x-original.x)<1&&Math.abs(restored.y-original.y)<1,'screenshot did not restore original scroll')}
     catch(e){trace.scroll_restore_error=e.message;if(!trace.error){trace.error=e.message;trace.status='restore failed';throw e}}
     finally{saveDiagnostics()}
   }
 };
 const iso=n=>new Date(n).toISOString();
 const hasTime=(value,n)=>{const stamp=iso(n);return value.includes(stamp)||value.includes(stamp.replace('.000Z','Z'))||value.includes(stamp.slice(0,19))};
 const snapshot=()=>ev(`({time:document.querySelector('[data-snapshot-time]')?.textContent,regions:[...document.querySelectorAll('[data-refresh-region]')].map(e=>e.id)})`);
 const graphData=async ids=>ev(`(()=>{const svg=window.__fd2.graph();if(!svg)throw Error('task SVG unavailable');const rootMatrix=svg.getCTM();if(!rootMatrix)throw Error('SVG coordinate matrix unavailable');const rootInverse=rootMatrix.inverse();const point=(element,x,y)=>{const m=element.getCTM();if(!m)throw Error('element coordinate matrix unavailable');const p=new DOMPoint(x,y).matrixTransform(m).matrixTransform(rootInverse);return [p.x,p.y]};const ids=${JSON.stringify(ids)};const nodes={};for(const id of ids){let rect=[...svg.querySelectorAll('rect')].find(r=>{let a=r.closest('a')||r.parentElement;return a.textContent.includes(id)});if(rect){const b=rect.getBBox();const corners=[[b.x,b.y],[b.x+b.width,b.y],[b.x,b.y+b.height],[b.x+b.width,b.y+b.height]].map(([x,y])=>point(rect,x,y));const xs=corners.map(p=>p[0]),ys=corners.map(p=>p[1]);nodes[id]={x:Math.min(...xs),y:Math.min(...ys),w:Math.max(...xs)-Math.min(...xs),h:Math.max(...ys)-Math.min(...ys)}}}const edges=[...svg.querySelectorAll('line,path,polyline')].filter(e=>e.hasAttribute('marker-end')).map(e=>{let nums;if(e.tagName==='line')nums=['x1','y1','x2','y2'].map(a=>Number(e.getAttribute(a)));else nums=(e.getAttribute('d')||e.getAttribute('points')||'').match(/-?\\d+(?:\\.\\d+)?/g)?.map(Number)||[];if(nums.length<4)throw Error('edge geometry unavailable');const start=point(e,...nums.slice(0,2)),end=point(e,...nums.slice(-2));const nearest=(p,side)=>Object.entries(nodes).sort((a,b)=>Math.hypot(p[0]-(a[1].x+(side?a[1].w:0)),p[1]-(a[1].y+a[1].h/2))-Math.hypot(p[0]-(b[1].x+(side?b[1].w:0)),p[1]-(b[1].y+b[1].h/2)))[0]?.[0];return [nearest(start,true),nearest(end,false)]});return {nodes,edges,raw:svg.outerHTML}})()`);
 const savedEdges=g=>g.edges.map(e=>e.join(' -> ')).sort();
 const dependencyCounts=value=>{
   const t=value.replace(/\s+/g,' ');
   const run=t.match(/(\d+)\s*(?:\/|of)\s*(\d+)\s+Run\s+dependenc(?:y|ies)/i)
     ||t.match(/Run\s+dependenc(?:y|ies)[^\d]{0,40}(\d+)\s*(?:\/|of)\s*(\d+)/i);
   const window=t.match(/(\d+)\s+(?:belong|apply|relate)[^.;]{0,20}(?:displayed|shown|window)\s+tasks/i)
     ||t.match(/(?:displayed|shown|window)\s+tasks[^\d.;]{0,40}(\d+)/i)
     ||t.match(/(?:candidate|window)\s+(?:edges|dependencies)[^\d]{0,20}(\d+)/i);
   assert(run&&window,'Run dependency denominator and displayed-task candidate count are not identifiable');
   return {returned:Number(run[1]),run_total:Number(run[2]),candidates:Number(window[1])};
 };
 const controls=()=>ev(`[...document.forms].map(f=>new URL(f.action).pathname).filter(p=>/\\/(pause|resume|recover|cancel)$/.test(p))`);
 const token=()=>ev(`document.querySelector('input[name="_csrf"]')?.value||''`);
 const post=async(action,rid,includeCSRF=true)=>{const csrf=includeCSRF?await token():'';return ev(`(()=>{const form=window.__fd2.form(${JSON.stringify(action)});const values=form?new URLSearchParams(new FormData(form)):new URLSearchParams();for(const name of ['controlledBy','recoveredBy','actor'])if(values.has(name))values.set(name,'operator:fixture');if(values.has('reason'))values.set('reason','independent fixture action');${includeCSRF?`values.set('_csrf',${JSON.stringify(csrf)});`:`values.delete('_csrf');`}return fetch('/ui/runs/'+${JSON.stringify(rid)}+'/'+${JSON.stringify(action)},{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:values}).then(async r=>({status:r.status,url:r.url,text:await r.text()}))})()`)};
 await call('Page.enable');await call('Runtime.enable');await call('Fetch.enable',{patterns:[{urlPattern:'*',requestStage:'Request'}]});
 await call('Target.activateTarget',{targetId:target.id});await call('Emulation.setFocusEmulationEnabled',{enabled:true});
 await nav('/ui/login');operation='submit login form';await ev(`document.querySelector('[name="username"]').value='tester';document.querySelector('[name="password"]').value='local-fixture-secret';document.querySelector('form').requestSubmit()`);operation='wait for login redirect /ui';await loaded('/ui');
 await nav('/ui/runs/fd2-action');
 await test(role+'-permission',async()=>{const visible=await controls();const allowed=['ADMIN','OPERATOR'].includes(role);assert((visible.length>0)===allowed,'wrong visible action authority');const response=await post('pause','fd2-action');const state=await api('/api/r8/runs/fd2-action');if(allowed){assert(state.controlState==='PAUSED','permitted pause failed '+response.status)}else{assert(response.status===403||/forbidden|not allowed|permission/i.test(response.text),'forbidden action feedback absent');assert(state.controlState==='ACTIVE','unauthorized action mutated Run')}return {visible,status:response.status,state}});
 if(role==='ADMIN'){
 await nav('/ui/runs/fd2-single');await ev('window.__fd2.open()');
 await test('requirement-original',async()=>{assert((await sectionText('requirement')).includes(fixture.long_requirement),'original requirement differs');return 'saved requirementTaskId input matched'});
 await test('requirement-long',async()=>assert((await text()).includes(fixture.long_requirement),'requirement truncated'));
 await test('requirement-escaped',async()=>{assert(await ev(`window.__injected!==1&&![...document.querySelectorAll('script')].some(s=>s.textContent.includes('window.__injected=1'))`),'untrusted script executed or injected');assert((await text()).includes('<script>window.__injected=1</script>'),'literal script text lost')});
 const state=await api('/api/r8/runs/fd2-single'),dashboard=await api('/api/r8/runs/fd2-single/dashboard');diagnostics.push({state,dashboard});
 await test('status-lifecycle',async()=>assert((await text()).includes(state.status),'lifecycle state absent'));
 await test('status-control',async()=>assert((await text()).includes(state.controlState),'control state absent'));
 await test('status-effective',async()=>{const effective=dashboard.effectiveStatus;assert(typeof effective==='string'&&(await text()).includes(effective),'public Dashboard effective state absent');return {effective}});
 await test('dashboard-phase',async()=>{const phases=[];const visit=o=>{for(const [k,v] of Object.entries(o||{})){if(/^(current)?phase$/i.test(k)&&typeof v==='string')phases.push(v);if(v&&typeof v==='object')visit(v)}};visit(dashboard);const t=await text();assert(phases.length>0&&phases.some(phase=>t.includes(phase)),'public dashboard phase absent');return {phases}});
 await test('task-counts',async()=>{const t=await sectionText('task|run authority|mission summary');assert(/(?:total|tasks|completed)[^\n\d]{0,40}4\b|4\s*(?:\/|of)\s*4|4\s+tasks/i.test(t),'4 fixture tasks count label absent');const values=[];const visit=(o,base='')=>{for(const [key,value] of Object.entries(o||{})){const field=base?base+'.'+key:key;if((field==='tasks.total'||field.endsWith('.tasks.total')||/total.*task|task.*total/i.test(key))&&typeof value==='number')values.push({field,value});if(value&&typeof value==='object')visit(value,field)}};visit(dashboard);assert(values.some(v=>v.value===4),'public dashboard full task total differs or unavailable');return {expected:4,publicTotals:values}});
 await test('no-eta',async()=>assert(!/\bETA\s*[:=]\s*\d|(?:complete|completion|progress)[^\n]{0,30}\d+\s*%/i.test(await sectionText('requirement|mission summary|run authority')),'invented numerical ETA/engineering percentage'));
 const single=await observe('single graph',()=>graphData(fixture.runs['fd2-single'].tasks));diagnostics.push({single});
 await test('single-edges',async()=>assert(JSON.stringify(savedEdges(single))===JSON.stringify(savedEdges(fixture.runs['fd2-single'])),'graph edges differ '+JSON.stringify(single.edges)));
 await test('single-direction',async()=>assert(single.edges.length===3&&single.edges.every(([a,b])=>single.nodes[a]?.x<single.nodes[b]?.x),'edge arrow direction differs'));
 await test('graph-node-link',async()=>{const anchors=await ev('window.__fd2.anchors()');assert(anchors.some(a=>a.text.includes('fd2-s-impl')&&a.exists),'real SVG task anchor unavailable');return anchors});
 await test('graph-hide',async()=>{await ev(`window.__fd2.button('Hide graph').click()`);assert(await ev(`getComputedStyle(window.__fd2.graph()).display==='none'||!window.__fd2.graph().getClientRects().length`),'graph remained visible');assert((await text()).includes('Fixture instruction for fd2-s-impl'),'task table fallback lost')});
 await nav('/ui/runs/fd2-parallel');const parallel=await observe('parallel graph',()=>graphData(fixture.runs['fd2-parallel'].tasks));diagnostics.push({parallel});
 await test('parallel-edges',async()=>assert(JSON.stringify(savedEdges(parallel))===JSON.stringify(savedEdges(fixture.runs['fd2-parallel'])),'parallel saved edges differ'));
 await test('parallel-levels',async()=>assert(parallel.nodes['fd2-p-a'].x===parallel.nodes['fd2-p-b'].x&&parallel.nodes['fd2-p-a'].x<parallel.nodes['fd2-p-integrate'].x,'parallel/integration layering differs'));
 await test('final-review-level',async()=>assert(parallel.nodes['fd2-p-integrate'].x<parallel.nodes['fd2-p-review'].x,'review precedes integration'));
 await test('verification-level',async()=>assert(parallel.nodes['fd2-p-integrate'].x<parallel.nodes['fd2-p-verify'].x,'verification precedes integration'));
 mutate('repair');await refresh();const repaired=await observe('repaired graph',()=>graphData([...fixture.runs['fd2-parallel'].tasks,'fd2-p-repair']));diagnostics.push({repaired});
 await test('repair-added',async()=>assert(repaired.nodes['fd2-p-repair']&&repaired.edges.some(e=>e[0]==='fd2-p-review'&&e[1]==='fd2-p-repair'),'repair task/edge not refreshed'));
 await test('repair-history',async()=>assert(fixture.runs['fd2-parallel'].tasks.every(id=>repaired.nodes[id])&&savedEdges(parallel).every(e=>savedEdges(repaired).includes(e)),'repair erased graph history'));
 await nav('/ui/runs/fd2-disconnected');const disconnected=await observe('disconnected graph',()=>graphData(['fd2-disconnected-req','fd2-d-impl','fd2-d-review']));
 await test('no-inferred-edge',async()=>assert(disconnected.edges.length===0,'task types synthesized dependency'));
 await nav('/ui/runs/fd2-cycle');
 await test('cycle-warning',async()=>assert(/cycl|unlayer|invalid|循环|异常/i.test(await text()),'cycle warning absent'));
 await test('cycle-table',async()=>{const rows=await ev(`[...document.querySelectorAll('tr,li')].filter(r=>r.textContent.includes('fd2-c-a')&&r.textContent.includes('fd2-c-b')).map(r=>r.textContent)`);assert(rows.length>=2,'cycle saved edge table absent');return rows});
 await nav('/ui/runs/fd2-boundary');const boundary=await text();
 await test('tasks-cap',async()=>{const rows=await ev(`[...document.querySelectorAll('tr')].filter(r=>/fd2-b-\\d{3}/.test(r.textContent)&&r.textContent.includes('IMPLEMENTATION')).map(r=>r.textContent.match(/fd2-b-\\d{3}/)[0])`);assert(new Set(rows).size===200,'displayed task count '+new Set(rows).size);assert(boundary.includes('201'),'full task total absent');diagnostics.push({taskRows:rows});return rows});
 await test('tasks-order',async()=>{const rows=await ev(`[...document.querySelectorAll('tr')].filter(r=>/fd2-b-\\d{3}/.test(r.textContent)&&r.textContent.includes('IMPLEMENTATION')).map(r=>r.textContent.match(/fd2-b-\\d{3}/)[0])`);assert(rows.length===200&&rows[0]==='fd2-b-000'&&rows.at(-1)==='fd2-b-199'&&rows.every((v,i)=>v===`fd2-b-${String(i).padStart(3,'0')}`),'task stable chronological order differs');return rows});
 await test('requirement-window-outside',async()=>assert((await sectionText('requirement')).includes('Original requirement for fd2-boundary'),'outside requirement lost'));
 await test('outside-prerequisite',async()=>assert(boundary.includes('fd2-boundary-req')&&/outside|window|partial/i.test(boundary),'outside prerequisite ID/range absent'));
 await test('outside-no-anchor',async()=>assert(!(await ev('window.__fd2.anchors()')).some(a=>a.text.includes('fd2-boundary-req')&&!a.exists),'outside task broken anchor'));
 await test('cross-run-edge',async()=>assert(boundary.includes('fd2-foreign-req')&&/cross.run|other run|outside.*run|different.*run/i.test(boundary),'cross Run relation scope absent'));
 await test('cross-subject-edge',async()=>assert(boundary.includes('fd2-other-subject')&&/subject|outside|cross/i.test(boundary),'cross subject scope absent'));
 await nav('/ui/runs/fd2-dense');const denseText=await text();
 await test('edges-cap',async()=>{const count=await ev(`window.__fd2.graph().querySelectorAll('[marker-end]').length`);assert(count===1200&&denseText.includes('1200')&&denseText.includes('1201')&&/truncat|partial/i.test(denseText),'dependency cap metadata absent '+count);return {count}});
 await test('edges-total',async()=>{
   const dense=dependencyCounts(denseText),limited=dependencyCounts(boundary),expected=fixture.runs['fd2-boundary'];
   assert(dense.returned===1200&&dense.run_total===fixture.runs['fd2-dense'].edges.length&&dense.candidates===1201,'dense Run/window dependency counts differ');
   assert(/truncat|partial/i.test(denseText),'capped dependency output lacks truncation scope');
   assert(limited.returned===expected.returned_edges&&limited.run_total===expected.run_edges&&limited.candidates===expected.candidate_edges,
     'excluded-task edge did not separate full Run total4 from window candidates3');
   assert(limited.run_total>limited.candidates&&/partial|outside|window/i.test(boundary),'outside-task dependency range is not explicit');
   return {dense,boundary:limited};
 });
 await test('partial-graph',async()=>assert(/partial|truncat/i.test(denseText),'truncated graph presented as complete'));
 await nav('/ui/runs/fd2-attempt-window');const attempts=await sectionText('attempt|execution history');
 await test('attempt-cap',async()=>assert(attempts.includes('300')&&attempts.includes('301')&&/truncat/i.test(attempts),'attempt cap metadata absent'));
 await test('attempt-run-filter',async()=>{
   const nodes=await ev('window.__fd2.attemptIds()'),actual=nodes.map(node=>node.id).sort();
   const expected=Array.from({length:300},(_,i)=>'fd2-aw-task-a'+(i+2)).sort();
   const missing=expected.filter(id=>!actual.includes(id)),extra=actual.filter(id=>!expected.includes(id));
   const observation={actual_ids:actual,actual_count:actual.length,expected_count:300,expected_attempt_numbers:[2,301],missing_ids:missing,extra_ids:extra,nodes};
   diagnostics.push({observation:'attempt window exact identities',...observation});
   assert(!actual.some(id=>id.startsWith('fd2-foreign-req-a')),'other Run attempt leaked');
   assert(actual.includes('fd2-aw-task-a301')&&!actual.includes('fd2-aw-task-a1'),'wrong newest attempt window');
   assert(actual.length===300&&JSON.stringify(actual)===JSON.stringify(expected),'returned attempt IDs differ from newest300; actual='+actual.length+' missing='+missing.length+' extra='+extra.length);
   return observation;
 });
 await nav('/ui/runs/fd2-history');await ev('window.__fd2.open()');const history=await sectionText('attempt|execution history'), failure=await sectionText('failure|recovery');
 await test('attempt-task-link',async()=>assert(history.includes('fd2-h-task')&&(await ev('window.__fd2.anchors()')).some(a=>a.text.includes('fd2-h-task')&&a.exists),'attempt task association missing'));
 await test('attempt-number',async()=>assert([1,2,3,4,5].every(n=>history.includes('fd2-h-task-a'+n)),'actual attempt number/ID missing'));
 await test('attempt-worker',async()=>{assert(history.includes('fd2-worker-historical'),'saved historical worker absent');assert(await ev(`[...document.querySelectorAll('a[href*="/ui/workers"]')].some(a=>a.textContent.includes('fd2-worker-historical'))`),'Fleet association absent')});
 await test('attempt-state',async()=>assert(['FAILED','EXPIRED','COMPLETED'].every(s=>history.includes(s)),'attempt state lost'));
 await test('attempt-times',async()=>{for(const n of [1,4,5])for(const offset of [0,10,20])assert(hasTime(history,fixture.time+100*n+offset),'actual attempt time absent '+iso(fixture.time+100*n+offset));return 'created/started/finished timestamps matched'});
 await test('attempt-failure-category',async()=>assert(history.includes('UNKNOWN'),'stored failure category absent'));
 await test('attempt-reason-paused',async()=>assert(history.includes('OPERATOR_PAUSED'),'paused reason absent'));
 await test('attempt-reason-cancelled',async()=>assert(history.includes('OPERATOR_CANCELLED'),'cancelled reason absent'));
 await test('attempt-reason-missing',async()=>{const row=await ev(`[...document.querySelectorAll('tr,article')].filter(e=>e.textContent.includes('fd2-h-task-a1')).sort((a,b)=>a.textContent.length-b.textContent.length)[0]?.textContent`);assert(row&&/not recorded|unavailable|未记录/i.test(row),'missing reason not explicit');assert(!/automatically recovered|automatic recovery at/i.test(row),'EXPIRED inferred recovery');return row});
 await test('attempt-summary',async()=>assert(history.includes('REPORT SUMMARY ONLY')&&/report summary/i.test(history),'report summary label missing'));
 await test('failure-latest-outside',async()=>assert(failure.includes(fixture.runs['fd2-history'].latest_failure),'latest FAILED lost outside timeline'));
 await test('failure-event-time',async()=>assert(hasTime(failure,fixture.runs['fd2-history'].failure_time),'FAILED timestamp differs'));
 await test('failure-event-actor',async()=>assert(failure.includes('operator:fixture')&&failure.includes('fd2-h-task')&&failure.includes('FAILED OUTSIDE TIMELINE'),'FAILED provenance lost'));
 await test('recovery-event-time',async()=>assert(failure.includes('human:history')&&hasTime(failure,fixture.time-86399000+23),'RECOVERED event actor/time absent'));
 await test('recovery-event-no-attempt',async()=>{assert(/not recorded|unavailable|do(?:es)? not (?:record|retain|store)|no[^.\n]{0,60}(?:association|attempt|worker)/i.test(failure),'missing Run event association not explicit');assert(!await ev(`window.__fd2.sections('failure|recovery').some(s=>s.querySelector('a[href*="/ui/workers"]'))`),'event fabricated worker link')});
 await test('recovery-history-cap',async()=>assert(failure.includes('20')&&failure.includes('23')&&/truncat/i.test(failure),'failure/recovery window metadata absent'));
 await test('auto-recovery-explanation',async()=>assert(/lease|租约/i.test(failure)&&/requeue|queue|排队/i.test(failure)&&/manual|recover|人工/i.test(failure),'recovery mechanisms not distinct'));
 await test('retry-not-recovery-time',async()=>{assert(!hasTime(failure,fixture.time+500),'retry timestamp masquerades as recovery');return 'last retry creation timestamp excluded from recovery section'});
 const timeline=await api('/api/r8/runs/fd2-history/timeline?limit=300');diagnostics.push({timeline});
 const entries=timeline.entries||timeline.timelineEntries;assert(Array.isArray(entries),'public timeline entries shape unavailable');
 await test('timeline-cap',async()=>{const t=await sectionText('timeline');assert(entries.length===300&&t.includes('300')&&t.includes(String(timeline.totalEntries))&&/truncat/i.test(t),'timeline metadata differs from API');return {total:timeline.totalEntries,returned:entries.length}});
 await test('timeline-boundaries',async()=>{const t=await sectionText('timeline');assert(t.includes(entries[0].occurredAt)&&t.includes(entries.at(-1).occurredAt),'timeline first/last timestamp differs');return [entries[0].occurredAt,entries.at(-1).occurredAt]});
 await test('timeline-filter-scope',async()=>assert(/window|returned|recent|300/i.test(await sectionText('timeline')),'filter scope absent'));
 await nav('/ui/runs/fd2-timeline');await ev('window.__fd2.open()');const small=await api('/api/r8/runs/fd2-timeline/timeline?limit=300');diagnostics.push({small});
 await test('timeline-chronology',async()=>{const t=await sectionText('timeline');const labels=['BEFORE MIDNIGHT','AFTER MIDNIGHT','SAME TIME SECOND','Long event full detail'];const offsets=labels.map(x=>t.indexOf(x));assert(offsets.every(x=>x>=0)&&offsets.every((x,i)=>!i||x>offsets[i-1]),'time/tie order differs from fixture/public API');return offsets});
 await test('timeline-utc-groups',async()=>{const t=await sectionText('timeline');assert(t.includes('2025-10-01')&&t.includes('2025-10-02')&&/UTC/.test(t),'UTC midnight groups absent')});
 await test('timeline-full-detail',async()=>assert((await sectionText('timeline')).includes('Long event full detail '+'detail words '.repeat(100)),'long event detail truncated'));
 await test('timeline-missing-metadata',async()=>assert(/not recorded|unavailable/i.test(await sectionText('timeline')),'missing timeline provenance not explicit'));
 await test('timeline-text-filter',async()=>{
   const before=await timelineFacts();diagnostics.push({observation:'timeline text filter before',...before});
   await ev(`(()=>{let e=window.__fd2.filterText();if(!e)throw Error('text filter unavailable');e.value='BEFORE MIDNIGHT';e.dispatchEvent(new Event('input',{bubbles:true}))})()`);
   const after=await timelineFacts();diagnostics.push({observation:'timeline text filter after',...after});
   assert(after.visible.length>0&&after.visible.length<before.rows.length&&after.visible.every(row=>row.text.includes('BEFORE MIDNIGHT')),'text filter did not limit entries');return after.visible;
 });
 await test('timeline-reset',async()=>{await ev(`window.__fd2.button('^Reset').click()`);assert(await ev(`window.__fd2.filterText().value===''`),'Reset left filter text');assert((await ev('window.__fd2.visibleRows()')).some(t=>t.includes('AFTER MIDNIGHT')),'Reset omitted returned events')});
 await test('timeline-category-filter',async()=>{
   const before=await categoryFacts();diagnostics.push({observation:'timeline category filter before',...before});
   const options=before.control.options.filter(option=>option.value.trim()&&!option.disabled&&option.value!==before.control.value);
   const chosen=options.find(option=>/run/i.test(option.value))||options[0];assert(chosen,'category options absent; control='+JSON.stringify(before.control));
   await ev(`(()=>{const e=window.__fd2.filterCategory();e.value=${JSON.stringify(chosen.value)};e.dispatchEvent(new Event('change',{bubbles:true}))})()`);
   const after=await categoryFacts();diagnostics.push({observation:'timeline category filter after',chosen,...after});
   assert(after.control.value===chosen.value&&!!after.control.value.trim(),'nonempty category selection lost');
   assert(after.visible.length>0&&after.visible.length<before.rows.length,'category filter did not narrow real categories');return {chosen,...after};
 });
 await test('refresh-filter',async()=>{
   const before=await categoryFacts();diagnostics.push({observation:'refresh filters before',...before});
   assert(before.visible.length>0&&before.visible.length<before.rows.length,'no narrowed category result before refresh');
   await refresh();const after=await categoryFacts();diagnostics.push({observation:'refresh filters after',...after});
   assert(before.text===after.text&&before.control.value===after.control.value,'filter controls lost');
   assert(after.visible.length>0&&after.visible.length<after.rows.length,'filter not reapplied');
   const facts=state=>state.visible.map(row=>({key:row.key,text:row.text})).sort((a,b)=>a.key.localeCompare(b.key));
   assert(JSON.stringify(facts(before))===JSON.stringify(facts(after)),'filtered entry identities/body changed after refresh');
 });
 await ev(`window.__fd2.button('^Reset').click()`);
 await test('timeline-detail-key',async()=>{await ev(`(()=>{const d=[...window.__fd2.timeline().querySelectorAll('details')].find(d=>d.textContent.includes('Long event full detail'));if(!d)throw Error('event detail unavailable');d.open=true;window.__detail=d;window.__detailKey=d.id||[...d.attributes].filter(a=>/key/.test(a.name)).map(a=>a.value).join('|')})()`);await refresh();assert(await ev(`(()=>{const d=[...window.__fd2.timeline().querySelectorAll('details')].find(d=>d.textContent.includes('Long event full detail'));return d?.open&&!!window.__detailKey&&(d.id||[...d.attributes].filter(a=>/key/.test(a.name)).map(a=>a.value).join('|'))===window.__detailKey})()`),'event stable key/open state lost')});
 await test('refresh-details',async()=>{await ev(`(()=>{const d=document.querySelector('details');if(!d)throw Error('task detail unavailable');d.open=true;window.__detailText=d.textContent})()`);await refresh();assert(await ev(`[...document.querySelectorAll('details')].some(d=>d.open&&d.textContent===window.__detailText)`),'expanded detail collapsed')});
 await test('refresh-selection',async()=>{await ev(`(()=>{let node=[...window.__fd2.timeline().querySelectorAll('summary,p,span')].find(e=>e.firstChild?.nodeType===3&&e.firstChild.length>10)?.firstChild;if(!node)throw Error('selectable timeline text unavailable');let r=document.createRange();r.setStart(node,0);r.setEnd(node,10);let s=getSelection();s.removeAllRanges();s.addRange(r);window.__selection={node,text:s.toString(),anchor:s.anchorOffset,focus:s.focusOffset}})()`);await refresh();assert(await ev(`(()=>{let s=getSelection(),b=window.__selection;return s.anchorNode===b.node&&s.focusNode===b.node&&s.toString()===b.text&&s.anchorOffset===b.anchor&&s.focusOffset===b.focus})()`),'selection node/text/offset replaced');await ev('getSelection().removeAllRanges()')});
 await test('refresh-scroll',async()=>{await ev('window.scrollTo(0,150);window.__scroll=scrollY');await refresh();assert(await ev('Math.abs(scrollY-window.__scroll)<2'),'page scroll moved')});
 await test('refresh-auto',async()=>{mutate('tick');await wait(5800);await installDOM();assert((await sectionText('timeline')).includes('NEW AFTER REFRESH'),'5s live refresh failed')});
 await nav('/ui/runs/fd2-history');
 await test('publication-text-only',async()=>{assert((await text()).includes('https://example.invalid/not-to-be-read'),'publication ref text absent');assert(!await ev(`[...document.querySelectorAll('a')].some(a=>a.href==='https://example.invalid/not-to-be-read')`),'publication ref became external navigation');assert(external.length===0,'external request attempted '+JSON.stringify(external))});
 await test('no-broken-links',async()=>{const links=await ev('window.__fd2.anchors()');assert(links.every(a=>a.href==='#'||a.exists),'broken task/attempt anchors '+JSON.stringify(links.filter(a=>!a.exists)));return links});
 await nav('/ui/runs/fd2-bad-link');
 await test('requirement-cross-run',async()=>{const t=await sectionText('requirement');assert(/unavailable|not recorded|归属/i.test(t)&&!t.includes('FOREIGN REQUIREMENT MUST NOT BE USED'),'cross Run requirement substituted')});
 await nav('/ui/runs/fd2-missing-link');
 await test('requirement-missing',async()=>assert(/unavailable|not recorded/i.test(await sectionText('requirement')),'missing requirement fallback absent'));
 await test('empty-graph',async()=>assert(/no tasks|no .*attempt|empty|not recorded|unavailable/i.test(await text()),'empty graph/attempt state absent'));
 await nav('/ui/runs/'+fixture.long_run);await ev('window.__fd2.open()');
 await test('long-id',async()=>assert((await text()).includes(fixture.long_run)&&(await text()).includes(fixture.long_task),'full long ID truncated'));
 await nav('/ui/runs/fd2-dense');
 for(const width of [390,768,1280,1440])await test('width-'+width,async()=>{await nav('/ui/runs/fd2-dense');await call('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});await wait(100);assert(await ev('document.documentElement.scrollWidth<=innerWidth'),'dense graph page horizontal overflow');if(width===390)assert(await ev(`(()=>{const g=window.__fd2.graph();let p=g?.parentElement;while(p&&p!==document.body){if(p.scrollWidth>p.clientWidth&&/auto|scroll/.test(getComputedStyle(p).overflowX))return true;p=p.parentElement}return false})()`),'dense graph lacks internal scroll');await shot('dense-'+width);await nav('/ui/runs/'+fixture.long_run);await ev('window.__fd2.open()');assert((await text()).includes(fixture.long_run)&&(await text()).includes('INTEGRATION_VERIFICATION'),'long ID/type fixture absent');assert(await ev('document.documentElement.scrollWidth<=innerWidth'),'long Run ID/phase page horizontal overflow');await shot('long-id-'+width)});
 await nav('/ui/runs/fd2-dense');
 await test('refresh-graph-state',async()=>{await ev(`(()=>{let g=window.__fd2.graph(),p=g.parentElement;while(p&&p!==document.body){if(p.scrollWidth>p.clientWidth){window.__graphScroll=p;p.scrollLeft=100;break}p=p.parentElement}window.__fd2.button('Hide graph').click()})()`);await refresh();assert(await ev(`!window.__fd2.graph().getClientRects().length`),'graph visibility reset');await ev(`window.__fd2.button('Show graph').click()`);assert(await ev(`(()=>{let p=window.__fd2.graph().parentElement;while(p&&p!==document.body){if(p.scrollWidth>p.clientWidth)return Math.abs(p.scrollLeft-100)<2;p=p.parentElement}return false})()`),'graph internal scroll reset')});
 await test('motion-toggle',async()=>{await ev(`(()=>{let b=document.querySelector('[data-motion-toggle]')||window.__fd2.button('Motion');if(!b)throw Error('Motion toggle unavailable');if(!/off/i.test(b.textContent))b.click()})()`);await nav('/ui/runs/fd2-single');assert(await ev(`/off/i.test((document.querySelector('[data-motion-toggle]')||window.__fd2.button('Motion')).textContent)`),'Motion preference lost');assert(await ev(`[...document.querySelectorAll('svg *')].every(e=>getComputedStyle(e).animationName==='none'||getComputedStyle(e).animationDuration==='0s')`),'Motion off left SVG animation')});
 await test('reduced-motion',async()=>{await ev(`(()=>{let b=document.querySelector('[data-motion-toggle]')||window.__fd2.button('Motion');if(/off/i.test(b.textContent))b.click()})()`);await call('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});assert(await ev(`[...document.querySelectorAll('svg *, .status-pulse')].every(e=>getComputedStyle(e).animationName==='none'||getComputedStyle(e).animationDuration==='0s')`),'decorative animation remains')});
 await nav('/ui/runs/fd2-action');
 await test('csrf-required',async()=>{const before=await api('/api/r8/runs/fd2-action');const r=await post('resume','fd2-action',false);const after=await api('/api/r8/runs/fd2-action');assert(r.status===403||/csrf/i.test(r.text),'missing CSRF accepted');assert(before.controlState===after.controlState,'CSRF failure mutated run')});
 await test('resume-submit',async()=>{const r=await post('resume','fd2-action');assert((await api('/api/r8/runs/fd2-action')).controlState==='ACTIVE'&&/success|resum|ACTIVE/i.test(r.text),'valid resume feedback/state absent')});
 await nav('/ui/runs/fd2-action');
 await test('pause-submit',async()=>{const r=await post('pause','fd2-action');assert((await api('/api/r8/runs/fd2-action')).controlState==='PAUSED'&&/success|paus|PAUSED/i.test(r.text),'valid pause feedback/state absent')});
 await nav('/ui/runs/fd2-failed');
 await test('recover-submit',async()=>{const r=await post('recover','fd2-failed');assert((await api('/api/r8/runs/fd2-failed')).status==='RUNNING'&&/recover|success|RUNNING/i.test(r.text),'valid recover feedback/state absent')});
 await nav('/ui/runs/fd2-action');
 await test('cancel-warning',async()=>{const f=await ev(`(()=>{const f=window.__fd2.form('cancel');return f?{text:f.textContent,html:f.outerHTML}:null})()`);assert(f&&/irrevers|cannot.*undo|confirm|不可逆/i.test(f.text+' '+f.html),'cancel irreversible prompt absent')});
 await test('cancel-submit',async()=>{const r=await post('cancel','fd2-action');assert((await api('/api/r8/runs/fd2-action')).controlState==='CANCELLED'&&/cancel|success|CANCELLED/i.test(r.text),'cancel feedback/state absent')});
 await test('cancel-irreversible',async()=>{const r=await post('resume','fd2-action');assert((await api('/api/r8/runs/fd2-action')).controlState==='CANCELLED'&&(r.status===409||/error|cannot|cancelled|conflict/i.test(r.text)),'cancelled Run resumed')});
 await nav('/ui/runs/fd2-paused');
 await test('stale-action-conflict',async()=>{await post('resume','fd2-paused');await post('pause','fd2-paused');const r=await post('pause','fd2-paused');assert(r.status===409||/error|already|conflict|cannot/i.test(r.text),'stale duplicate pause missing state competition feedback');return {status:r.status,excerpt:r.text.slice(-1500)}});
 await test('action-snapshot-reload',async()=>assert(/Reload action availability/.test(await text())&&/snapshot|reload|loaded/i.test(await text()),'action snapshot/reload guidance absent'));
 await nav('/ui/runs/fd2-failed');
 await test('refresh-form-focus',async()=>{await ev(`(()=>{const e=[...document.querySelectorAll('form input,form textarea')].find(e=>e.type==='text'||e.tagName==='TEXTAREA')||document.querySelector('form input[type="number"]');const stamp=document.querySelector('[data-snapshot-time]');if(!e||!stamp)throw Error('editable Run action form/snapshot unavailable');window.__editingValue=e.type==='number'?'17':'INDEPENDENT EDIT';e.value=window.__editingValue;e.dispatchEvent(new Event('input',{bubbles:true}));e.focus();window.__editing=e;window.__snapshot=stamp.textContent})()`);await wait(5800);assert(await ev(`document.activeElement===window.__editing&&window.__editing.isConnected&&window.__editing.value===window.__editingValue`),'input/focus lost');assert(await ev(`document.querySelector('[data-snapshot-time]').textContent===window.__snapshot`),'facts refreshed during edit');await ev('window.__editing.form.reset();window.__editing.blur()')});
 await test('keyboard-focus',async()=>{await ev(`document.querySelector('input:not([type="hidden"]),textarea,button,a').focus()`);await call('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});await call('Input.dispatchKeyEvent',{type:'keyUp',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});assert(await ev(`document.hasFocus()&&document.activeElement.matches(':focus-visible')&&getComputedStyle(document.activeElement).outlineStyle!=='none'`),'real Tab focus invisible')});
 await shot('final-admin');
 }
 await shot('role-'+role);
}catch(e){diagnostics.push({infrastructure_error:e.stack});console.log('INFRASTRUCTURE',e.stack);process.exitCode=1}
finally{
 await rememberSections();
 fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(results,null,2));
 fs.writeFileSync(path.join(out,'diagnostics.json'),JSON.stringify({diagnostics,external_requests:external,runtime_errors:errors},null,2));
 ws?.close();
}
if(results.some(r=>!r.passed)||external.length||!results.length)process.exitCode=1;
