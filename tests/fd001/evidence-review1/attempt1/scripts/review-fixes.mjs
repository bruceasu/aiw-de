// Independent tests for three public review findings; no implementation imports.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn,spawnSync} from 'node:child_process';
const [url,out,role,db]=process.argv.slice(2);
if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(url)||role!=='ADMIN')throw Error('ADMIN loopback fixture required');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'fd001-tester-chrome-'));
const browser=spawn('C:/Program Files/Google/Chrome/Application/chrome.exe',['--headless=new','--remote-debugging-port=0',`--user-data-dir=${profile}`,'--no-first-run','--disable-background-networking','--disable-component-update','--disable-sync','--no-default-browser-check','about:blank'],{windowsHide:true,stdio:'ignore'});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let ws;const results=[],diagnostics=[];
try{
 let port;for(let i=0;i<100;i++){try{port=fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0];break}catch{}await sleep(100)}
 if(!port)throw Error('Chrome startup timeout');
 const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT',signal:AbortSignal.timeout(5000)})).json();
 ws=new WebSocket(target.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{let timer=setTimeout(()=>reject(Error('CDP open timeout')),5000);ws.addEventListener('open',()=>{clearTimeout(timer);resolve()},{once:true});ws.addEventListener('error',()=>{clearTimeout(timer);reject(Error('CDP open error'))},{once:true})});
 let id=0;const pending=new Map();ws.addEventListener('message',e=>{let m=JSON.parse(e.data);if(m.id){let p=pending.get(m.id);if(!p)return;pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result)}});
 const call=(method,params={})=>new Promise((resolve,reject)=>{let seq=++id,timer=setTimeout(()=>{pending.delete(seq);reject(Error('CDP timeout '+method))},9000);pending.set(seq,{resolve:r=>{clearTimeout(timer);resolve(r)},reject:e=>{clearTimeout(timer);reject(e)}});ws.send(JSON.stringify({id:seq,method,params}))});
 const ev=async expression=>{let r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value};
 const loaded=async route=>{let last;for(let i=0;i<50;i++){try{last=await ev('({url:location.href,ready:document.readyState})');if(last.url===url+route&&last.ready==='complete')return}catch(e){if(!/Execution context was destroyed|Cannot find context with specified id/i.test(e.message))throw e}await sleep(100)}throw Error('Navigation timeout '+JSON.stringify(last))};
 const test=async(id,behavior,fn)=>{try{let detail=await fn();results.push({id,behavior,passed:true,detail});console.log('PASS',id)}catch(e){results.push({id,behavior,passed:false,detail:e.message});console.log('FAIL',id,e.message)}};
 const assert=(condition,detail)=>{if(!condition)throw Error(detail)};
 await call('Page.enable');await call('Runtime.enable');await call('Target.activateTarget',{targetId:target.id});await call('Emulation.setFocusEmulationEnabled',{enabled:true});
 const nav=await call('Page.navigate',{url:url+'/ui/login'});if(nav.errorText)throw Error(nav.errorText);await loaded('/ui/login');
 await ev(`document.querySelector('[name="username"]').value='tester';document.querySelector('[name="password"]').value='local-fixture-secret';document.querySelector('form').requestSubmit()`);await loaded('/ui');
 await ev(`document.querySelectorAll('#overview-waiting details').forEach(e=>e.open=true)`);
 await test('waiting-count-samples','Every mutually exclusive waiting group total agrees with its bounded sample',async()=>{
   const groups=await ev(`Array.from(document.querySelectorAll('#overview-waiting details')).map(e=>({label:e.querySelector('summary').innerText,total:Number(e.querySelector('summary').innerText.match(/(\\d+)\\s*$/)?.[1]),samples:Array.from(e.querySelectorAll('a[href^="/ui/runs/"]')).map(a=>a.textContent.trim()),text:e.innerText}))`);
   diagnostics.push({groups});assert(groups.length===5,'expected five waiting groups');
   for(const group of groups){assert(Number.isFinite(group.total),'missing total '+group.label);assert(group.samples.length===Math.min(5,group.total),'sample count differs from total '+JSON.stringify(group))}
   return groups;
 });
 await test('waiting-overlap','READY task lacking capability and policy admission appears once in the primary Policy group',async()=>{
   const groups=await ev(`Array.from(document.querySelectorAll('#overview-waiting details')).map(e=>({label:e.querySelector('summary').innerText,text:e.innerText}))`);
   const matches=groups.filter(g=>g.text.includes('tester-run-06-ready'));assert(matches.length===1,'overlap task appears '+matches.length+' times');assert(/policy/i.test(matches[0].label),'primary group is not Policy');return matches;
 });
 await test('waiting-open-count-change','Open waiting group survives count change and refresh after focus moved away',async()=>{
   await ev(`window.__group=Array.from(document.querySelectorAll('#overview-waiting details')).find(e=>/matching/i.test(e.querySelector('summary').textContent));window.__group.open=true;window.__oldLabel=window.__group.querySelector('summary').textContent;document.querySelector('[data-motion-toggle]').focus()`);
   const fixture=spawnSync('C:/green/python312/python.exe',[path.join(path.dirname(fileURLToPath(import.meta.url)),'fixture_edit.py'),db],{windowsHide:true,encoding:'utf8',timeout:5000});
   assert(fixture.status===0,'fixture edit failed '+fixture.stderr);
   await ev(`document.querySelector('[data-refresh-now]').click()`);await sleep(1200);
   const state=await ev(`(()=>{let e=Array.from(document.querySelectorAll('#overview-waiting details')).find(e=>/matching/i.test(e.querySelector('summary').textContent));return {open:e.open,before:window.__oldLabel,after:e.querySelector('summary').textContent,focus:document.activeElement.matches('[data-motion-toggle]')}})()`);
   diagnostics.push({open_count_change:state});assert(state.before!==state.after,'count did not actually update');assert(state.open,'group collapsed');assert(state.focus,'other focus changed');return state;
 });
 await test('selection-multi-region','Selection crossing endpoint regions and intermediate regions preserves text and node identity',async()=>{
   await ev(`(()=>{let a=document.querySelector('#overview-runs h2').firstChild,b=document.querySelector('#overview-activity h2').firstChild;let range=document.createRange();range.setStart(a,0);range.setEnd(b,Math.min(8,b.length));let s=getSelection();s.removeAllRanges();s.addRange(range);window.__selection={anchor:s.anchorNode,focus:s.focusNode,anchorOffset:s.anchorOffset,focusOffset:s.focusOffset,text:s.toString(),middle:document.querySelector('#overview-workers'),unselected:document.querySelector('#overview-metrics')};})()`);
   const before=await ev(`({length:window.__selection.text.length,middleSelected:getSelection().containsNode(window.__selection.middle,true),regions:Array.from(document.querySelectorAll('[data-refresh-region]')).filter(e=>getSelection().containsNode(e,true)).map(e=>e.id)})`);
   assert(before.length>20&&before.regions.length>=3&&before.middleSelected,'fixture selection must span intermediate region');
   await ev(`document.querySelector('[data-refresh-now]').click()`);await sleep(1200);
   const after=await ev(`(()=>{let s=getSelection(),b=window.__selection;return {sameAnchor:s.anchorNode===b.anchor,sameFocus:s.focusNode===b.focus,sameOffsets:s.anchorOffset===b.anchorOffset&&s.focusOffset===b.focusOffset,sameText:s.toString()===b.text,middleSame:document.querySelector('#overview-workers')===b.middle,unselectedUpdated:document.querySelector('#overview-metrics')!==b.unselected,rangeRegions:Array.from(document.querySelectorAll('[data-refresh-region]')).filter(e=>s.containsNode(e,true)).map(e=>e.id)}})()`);
   diagnostics.push({selection:{before,after}});assert(after.unselectedUpdated,'unselected region did not actually refresh');assert(after.sameAnchor&&after.sameFocus&&after.sameOffsets&&after.sameText&&after.middleSame,'selected text or endpoints/intermediate node replaced '+JSON.stringify(after));return {before,after};
 });
 const shot=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});fs.writeFileSync(path.join(out,'review-fixes.png'),Buffer.from(shot.data,'base64'));
}catch(e){results.push({id:'review-infrastructure',behavior:'Targeted browser infrastructure completes',passed:false,detail:e.stack})}
finally{
 fs.writeFileSync(path.join(out,role+'-results.json'),JSON.stringify(results,null,2));fs.writeFileSync(path.join(out,'review-diagnostics.json'),JSON.stringify(diagnostics,null,2));ws?.close();browser.kill();await sleep(300);
 const resolved=path.resolve(profile);if(path.dirname(resolved)!==path.resolve(os.tmpdir())||!path.basename(resolved).startsWith('fd001-tester-chrome-'))throw Error('unsafe cleanup target');try{fs.rmSync(resolved,{recursive:true,force:true})}catch(e){fs.writeFileSync(path.join(out,'cleanup.log'),String(e))}
}
process.exitCode=results.some(r=>!r.passed)?1:0;
