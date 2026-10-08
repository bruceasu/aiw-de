import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn} from 'node:child_process';
const [url,out,role]=process.argv.slice(2);
if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(url))throw Error('loopback required');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'fd001-tester-chrome-'));
const chrome=spawn('C:/Program Files/Google/Chrome/Application/chrome.exe',['--headless=new','--remote-debugging-port=0',`--user-data-dir=${profile}`,'--no-first-run','--disable-background-networking','--disable-component-update','--disable-sync','--no-default-browser-check','about:blank'],{windowsHide:true,stdio:'ignore'});
const delay=ms=>new Promise(r=>setTimeout(r,ms));
let ws;const results=[];
try{
 let port;for(let i=0;i<100;i++){try{port=fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0];break}catch{}await delay(100)}
 const tabs=await(await fetch(`http://127.0.0.1:${port}/json`)).json();
 ws=new WebSocket(tabs[0].webSocketDebuggerUrl);await new Promise((resolve,reject)=>{let timer=setTimeout(()=>reject(Error('CDP open timeout')),10000);ws.addEventListener('open',()=>{clearTimeout(timer);resolve()},{once:true});ws.addEventListener('error',()=>{clearTimeout(timer);reject(Error('CDP open error'))},{once:true})});
 let seq=0;const pending=new Map();ws.addEventListener('message',e=>{let m=JSON.parse(e.data);if(m.id){let p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result)}});
 const call=(method,params={})=>new Promise((resolve,reject)=>{let id=++seq;let timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout '+method))},12000);pending.set(id,{resolve:r=>{clearTimeout(timer);resolve(r)},reject:e=>{clearTimeout(timer);reject(e)}});ws.send(JSON.stringify({id,method,params}))});
 const ev=async expression=>{let r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.text+' '+JSON.stringify(r.exceptionDetails.exception));return r.result.value};
 const test=async(id,behavior,fn)=>{try{let detail=await fn();results.push({id,behavior,passed:true,detail});console.log('PASS',id)}catch(e){results.push({id,behavior,passed:false,detail:e.message});console.log('FAIL',id,e.message)}};
 const assert=(condition,msg)=>{if(!condition)throw Error(msg)};
 const nav=async route=>{await call('Page.navigate',{url:url+route});await delay(600);await ev(`new Promise(r=>document.readyState==='complete'?r():window.addEventListener('load',r,{once:true}))`)};
 const text=()=>ev('document.body.innerText');
 await call('Page.enable');await call('Runtime.enable');await call('Target.activateTarget',{targetId:tabs[0].id});await call('Emulation.setFocusEmulationEnabled',{enabled:true});
 await nav('/ui/login');await ev(`fetch('/ui/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'username=tester&password=local-fixture-secret&next=%2Fui'})`);await nav('/ui');
 await test(role+'-role','Authenticated role displayed',async()=>assert((await text()).includes(role),'role absent'));
 await test(role+'-new','New Run visibility follows role',async()=>assert(await ev(`!!document.querySelector('a[href="/ui/runs/new"]')`)===(role==='ADMIN'||role==='OPERATOR'),'wrong New Run permission'));
 await nav('/ui/workers');
 await test(role+'-worker-actions','Worker operations visibility follows role',async()=>assert(await ev(`!!document.querySelector('form[action*="/drain"],form[action*="/offline"]')`)===(role==='ADMIN'||role==='OPERATOR'),'wrong Worker operation permission'));
 if(role==='ADMIN'){
 await test('states','Four management/lease states remain readable',async()=>{let t=await text();for(let word of ['Idle','Executing','DRAINING','OFFLINE'])assert(t.includes(word),word+' missing')});
 let identity=await ev(`Array.from(document.querySelectorAll('.worker-avatar')).map(e=>e.outerHTML)`);
 await test('fleet-interval','Fleet automatically updates read-only snapshot after 8 seconds',async()=>{let before=await ev(`document.querySelector('[data-snapshot-time]').textContent`);await delay(8500);assert(await ev(`document.querySelector('[data-snapshot-time]').textContent`)!==before,'snapshot unchanged')});
 await nav('/ui');
 await test('identity','Worker SVG identity consistent between pages',async()=>{let current=await ev(`Array.from(document.querySelectorAll('.worker-avatar')).map(e=>e.outerHTML)`);assert(current.length===4&&current.every(s=>identity.includes(s)),'identity mismatch')});
 await test('run-window','14 nonterminal runs display a 12-card window',async()=>{let t=await text();assert(t.includes('12 of 14'),'wrong run total/window')});
 await test('pending-old','16 older pending approvals survive 110 newer decided rows',async()=>{let t=await text();assert(t.includes('8 of 16'),'wrong pending total/window');assert(t.includes('tester-pending-'),'older pending omitted')});
 await test('failure-missing','Failure reason absence is explicitly unavailable',async()=>assert((await text()).toLowerCase().includes('unavailable'),'missing unavailable fallback'));
 await test('paused','Paused control state is distinguishable',async()=>assert((await text()).includes('PAUSED'),'PAUSED absent'));
 await test('waiting-labels','Waiting category labels remain readable',async()=>{let t=await text();for(let w of ['No matching','capacity','admission','policy'])assert(t.toLowerCase().includes(w.toLowerCase()),'waiting '+w+' absent')});
 await test('activity-bound','Activity feed contains at most 20 real entries',async()=>{let n=await ev(`document.querySelectorAll('#overview-activity li').length`);assert(n>0&&n<=20,'activity count '+n)});
 await test('activity-stable','Identical fixture activity order stable across two reads',async()=>{let a=await ev(`document.querySelector('#overview-activity').textContent.replace(/\\s+/g,' ').trim()`);let b=await ev(`fetch('/ui').then(r=>r.text()).then(s=>new DOMParser().parseFromString(s,'text/html').querySelector('#overview-activity').textContent.replace(/\\s+/g,' ').trim())`);assert(a===b,'activity reordered')});
 await test('safe-text','Untrusted requirement text stays escaped',async()=>assert(await ev(`!document.querySelector('#overview-runs script')`),'injected script'));
 await test('no-eta','Task count does not invent engineering completion percentage or ETA',async()=>assert(!/\bETA\b|\d+%/.test(await ev(`document.querySelector('#overview-runs').innerText`)),'invented progress'));
 await test('overview-interval','Overview automatically updates after 5 seconds',async()=>{let b=await ev(`document.querySelector('[data-snapshot-time]').textContent`);await delay(5500);assert(await ev(`document.querySelector('[data-snapshot-time]').textContent`)!==b,'snapshot unchanged')});
 await test('reading-protection','Expanded details, keyboard focus and scroll survive partial refresh',async()=>{await ev(`document.querySelector('#overview-runs details').open=true; document.querySelector('#overview-runs summary').focus();window.scrollTo(0,250);window.__before={y:scrollY,focused:document.activeElement.textContent};document.querySelector('[data-refresh-now]').click()`);await delay(700);assert(await ev(`document.querySelector('#overview-runs details').open&&document.activeElement.textContent===window.__before.focused&&Math.abs(scrollY-window.__before.y)<2`),'reading position changed')});
 for(let width of [390,768,1280,1440])await test('width-'+width,'Primary content accessible at '+width+' pixels',async()=>{await call('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});await delay(150);assert(await ev('document.documentElement.scrollWidth<=innerWidth'),'page overflow');let shot=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});fs.writeFileSync(path.join(out,'tester-'+width+'.png'),Buffer.from(shot.data,'base64'))});
 await test('motion','Motion toggle persists across navigation',async()=>{await ev(`document.querySelector('[data-motion-toggle]').click()`);let before=await ev(`document.querySelector('[data-motion-toggle]').textContent`);await nav('/ui/workers');assert(await ev(`document.querySelector('[data-motion-toggle]').textContent`)===before,'motion preference reset')});
 await test('reduced-motion','System reduced motion suppresses decorative pulse with local motion on',async()=>{await ev(`if(document.querySelector('[data-motion-toggle]').textContent.toLowerCase().includes('off'))document.querySelector('[data-motion-toggle]').click()`);await call('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});assert(await ev(`Array.from(document.querySelectorAll('.worker-avatar *')).every(e=>getComputedStyle(e).animationName==='none'||getComputedStyle(e).animationDuration==='0s')`),'animation remains')});
 await nav('/ui/system');
 await test('edit-pause','Existing whole-page refresh preserves edited form and pauses',async()=>{await ev(`let e=document.querySelector('input:not([type="hidden"])');e.value='17';e.dispatchEvent(new Event('input',{bubbles:true}));e.focus();window.__edit=e`);await delay(5500);assert(await ev(`window.__edit&&window.__edit.value==='17'&&document.activeElement===window.__edit`),'edited input lost')});
 await test('reset-resume','Reset and finishing input focus allow subsequent refresh',async()=>{await ev(`window.__edit.form.reset();window.__edit.blur();window.__marker=42`);await delay(6000);assert(await ev('window.__marker!==42'),'refresh did not resume after reset')});
 await nav('/ui/runs/new');
 await test('keyboard','Real Tab keyboard focus visibly outlined',async()=>{await ev(`document.querySelector('input:not([type="hidden"]),textarea').focus()`);await call('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});await call('Input.dispatchKeyEvent',{type:'keyUp',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});assert(await ev(`document.hasFocus()&&document.activeElement.matches(':focus-visible')&&getComputedStyle(document.activeElement).outlineStyle!=='none'`),'keyboard focus invisible')});
 await nav('/ui');
 await test('login-expiry','Actual cookie loss stops polling and preserves snapshot',async()=>{let before=await ev(`document.querySelector('#overview-runs').innerText`);await call('Network.clearBrowserCookies');await ev(`document.querySelector('[data-refresh-now]').click()`);await delay(1000);assert(await ev(`document.querySelector('#overview-runs').innerText`)===before,'snapshot lost');assert(/reload|sign|login|session/i.test(await ev(`document.querySelector('[data-refresh-label]').textContent`)),'auth expiry feedback absent')});
 }
}catch(e){results.push({id:role+'-infrastructure',behavior:'Independent browser infrastructure completes',passed:false,detail:e.stack})}
finally{fs.writeFileSync(path.join(out,role+'-results.json'),JSON.stringify(results,null,2));ws?.close();chrome.kill();await delay(300);let resolved=path.resolve(profile);if(path.dirname(resolved)!==path.resolve(os.tmpdir())||!path.basename(resolved).startsWith('fd001-tester-chrome-'))throw Error('unsafe cleanup target');try{fs.rmSync(resolved,{recursive:true,force:true})}catch{}}
process.exitCode=results.some(r=>!r.passed)?1:0;

