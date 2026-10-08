// Infrastructure diagnosis only; no login submission or behavior matrix.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn} from 'node:child_process';
const [url,out,role]=process.argv.slice(2);
if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(url)||role!=='ADMIN')throw Error('ADMIN loopback fixture required');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'fd001-tester-chrome-'));
const browser=spawn('C:/Program Files/Google/Chrome/Application/chrome.exe',['--headless=new','--remote-debugging-port=0',`--user-data-dir=${profile}`,'--no-first-run','--disable-background-networking','--disable-component-update','--disable-sync','--no-default-browser-check','about:blank'],{windowsHide:true,stdio:'ignore'});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let ws;let failure;const evidence={events:[]};
try {
 let port;for(let i=0;i<100;i++){try{port=fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0];break}catch{}await sleep(100)}
 if(!port)throw Error('Chrome debugger startup timeout');
 const target=await(await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,{method:'PUT',signal:AbortSignal.timeout(5000)})).json();
 evidence.target={id:target.id,type:target.type,url:target.url};
 ws=new WebSocket(target.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{let timer=setTimeout(()=>reject(Error('CDP open timeout')),5000);ws.addEventListener('open',()=>{clearTimeout(timer);resolve()},{once:true});ws.addEventListener('error',()=>{clearTimeout(timer);reject(Error('CDP open error'))},{once:true})});
 let id=0;const pending=new Map();
 ws.addEventListener('message',e=>{let m=JSON.parse(e.data);if(m.id){let p=pending.get(m.id);if(!p)return;pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result)}else if(m.method==='Network.responseReceived'){let r=m.params.response;evidence.events.push({method:m.method,url:r.url,status:r.status,mime_type:r.mimeType,content_type:r.headers['Content-Type']||r.headers['content-type']})}else if(m.method==='Network.loadingFailed'||m.method==='Page.frameNavigated'||m.method==='Page.frameStoppedLoading')evidence.events.push({method:m.method,params:m.params})});
 const call=(method,params={})=>new Promise((resolve,reject)=>{let seq=++id,timer=setTimeout(()=>{pending.delete(seq);reject(Error('CDP timeout '+method))},7000);pending.set(seq,{resolve:r=>{clearTimeout(timer);resolve(r)},reject:e=>{clearTimeout(timer);reject(e)}});ws.send(JSON.stringify({id:seq,method,params}))});
 await call('Page.enable');await call('Runtime.enable');await call('Network.enable');await call('Target.activateTarget',{targetId:target.id});await call('Emulation.setFocusEmulationEnabled',{enabled:true});
 evidence.navigate=await call('Page.navigate',{url:url+'/ui/login'});
 await sleep(2000);
 const state=await call('Runtime.evaluate',{expression:'({url:location.href,ready:document.readyState,title:document.title,bodyExcerpt:document.body?.innerText.slice(0,200)})',returnByValue:true});
 evidence.final_state=state.result.value;
 console.log(JSON.stringify(evidence,null,2));
 if(evidence.navigate.errorText||evidence.final_state.url!==url+'/ui/login')failure='Navigation not successful: '+JSON.stringify(evidence.navigate);
}catch(e){failure=e.stack;evidence.error=failure}
finally {
 fs.writeFileSync(path.join(out,'navigation-diagnosis.json'),JSON.stringify(evidence,null,2));
 fs.writeFileSync(path.join(out,role+'-results.json'),JSON.stringify([{id:'navigation-diagnostic',behavior:'Infrastructure navigation diagnostic, not product behavior',passed:!failure,detail:failure||'new page reached login'}],null,2));
 ws?.close();browser.kill();await sleep(300);
 const resolved=path.resolve(profile);if(path.dirname(resolved)!==path.resolve(os.tmpdir())||!path.basename(resolved).startsWith('fd001-tester-chrome-'))throw Error('unsafe cleanup target');
 try{fs.rmSync(resolved,{recursive:true,force:true})}catch(e){fs.writeFileSync(path.join(out,'cleanup.log'),String(e))}
}
process.exitCode=failure?1:0;
