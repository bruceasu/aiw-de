(() => {
  'use strict';
  const host=document.querySelector('[data-worker-access]');if(!host)return;
  const $=s=>host.querySelector(s),form=$('[data-access-form]'),status=$('[data-access-status]');
  const words={
    'External Worker access':['接入外部 Worker','外部 Worker の接続'],
    'Keep the same Worker ID when reconnecting. Each credential belongs to that ID. Remote Workers manage their own model credentials.':['重连时使用相同 Worker ID。每份凭据绑定该 ID。远程 Worker 自行管理模型凭据。','再接続時は同じ Worker ID を使います。認証情報はその ID に紐づき、遠隔 Worker のモデル認証情報は自身で管理します。'],
    'Worker ID':['Worker ID','Worker ID'],'Label':['用途说明','用途'],'Create access credential':['创建接入凭据','接続用認証情報を作成'],
    'Shown once. Store this as AIW_WORKER_TOKEN on the Worker device. Use AIW_WORKER_ID above and an HTTPS AIW_CONTROL_PLANE_URL. Loopback HTTP is allowed.':['令牌仅显示这一次。请在 Worker 设备保存为 AIW_WORKER_TOKEN，AIW_WORKER_ID 使用上方 ID，AIW_CONTROL_PLANE_URL 使用 HTTPS 地址。本机回环地址可用 HTTP。','トークンは今回のみ表示します。Worker 端末で AIW_WORKER_TOKEN として保存し、上記 ID を AIW_WORKER_ID に設定します。AIW_CONTROL_PLANE_URL は HTTPS（ループバックのみ HTTP 可）を使います。'],
    'Show or hide token':['显示或隐藏令牌','トークンを表示・非表示'],'Refresh credentials':['刷新凭据列表','認証情報を更新'],
    'Stopping new work permits current work to finish. Revoking blocks further protocol requests; it does not stop a remote process. Resolve any interrupted work separately.':['停止接单后当前工作可以继续。撤销会拒绝后续协议请求，但不会终止远端进程；中断的工作需要另行核对。','新規受付を停止しても進行中の作業は継続できます。失効は以後の要求を拒否しますが遠隔プロセスを終了しません。中断した作業は別途確認してください。'],
    'ACTIVE':['可接单','受付可能'],'DRAINING':['已停止接单','受付停止'],'REVOKED':['已撤销','失効済み'],
    'Stop new work':['停止领取新任务','新規受付を停止'],'Resume new work':['恢复接单','受付を再開'],'Revoke':['撤销凭据','失効させる'],
    'Revoke this credential? The remote process may still be running.':['撤销这份凭据？远端进程可能仍在运行。','この認証情報を失効させますか？遠隔プロセスは動作し続ける可能性があります。'],
    'No credentials yet':['尚无接入凭据','認証情報はまだありません'],'Credential created':['凭据已创建','認証情報を作成しました'],
    'This request was already accepted. Its token cannot be displayed again. Revoke it and create a new credential if the first reply was lost.':['此请求已受理，令牌不能再次显示。如果首次响应丢失，请撤销该凭据后重新创建。','この要求は受付済みで、トークンの再表示はできません。最初の応答を紛失した場合は失効後に新規作成してください。'],
    'Request failed':['请求失败','要求に失敗しました'],'Unrecorded':['未记录','未記録']
  };
  const t=x=>words[x]?.[window.aiwI18n?.locale==='zh'?0:window.aiwI18n?.locale==='ja'?1:-1]||x;
  const node=(tag,text)=>{const n=document.createElement(tag);n.textContent=text;return n;};
  let rows=[],pending=null,busy=false;
  function translate(){host.querySelectorAll('[data-w-text]').forEach(n=>n.textContent=t(n.dataset.wText));render();}
  async function request(path='',body){
    const response=await fetch(host.dataset.endpoint+path,{method:body?'POST':'GET',credentials:'same-origin',redirect:'error',headers:body?{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content}:{},body:body?JSON.stringify(body):undefined});
    const value=await response.json().catch(()=>null);if(!response.ok)throw Error(value?.error||`${t('Request failed')} (${response.status})`);return value;
  }
  async function load(){rows=await request();render();}
  async function act(work){if(busy)return;busy=true;host.querySelectorAll('button').forEach(b=>b.disabled=true);try{await work();}catch(e){status.textContent=e.message;}finally{busy=false;host.querySelectorAll('button').forEach(b=>b.disabled=false);}}
  function render(){
    const list=$('[data-access-list]');list.replaceChildren();if(!rows.length)list.append(node('p',t('No credentials yet')));
    for(const row of rows){const card=node('div','');card.className='callout';card.append(node('strong',`${row.label} · ${row.workerId}`),node('p',`${t(row.state)} · ${row.id} · ${row.lastUsedAt?new Date(row.lastUsedAt).toLocaleString():t('Unrecorded')}`));
      for(const [state,label] of row.state==='REVOKED'?[]:[[row.state==='ACTIVE'?'DRAINING':'ACTIVE',row.state==='ACTIVE'?'Stop new work':'Resume new work'],['REVOKED','Revoke']]){const button=node('button',t(label));button.type='button';button.className='btn ghost';button.disabled=busy;button.onclick=()=>{if(state==='REVOKED'&&!confirm(t('Revoke this credential? The remote process may still be running.')))return;act(async()=>{await request('/'+encodeURIComponent(row.id),{revision:row.revision,state});await load();});};card.append(button);}list.append(card);}
  }
  form.addEventListener('submit',event=>{event.preventDefault();act(async()=>{const workerId=form.elements.workerId.value.trim(),label=form.elements.label.value.trim();if(!pending||pending.workerId!==workerId||pending.label!==label)pending={requestId:crypto.randomUUID(),workerId,label};const result=await request('',pending);pending=null;$('[data-access-token]').value=result.token||'';$('[data-access-token]').type='password';$('[data-access-secret]').hidden=!result.token;status.textContent=t(result.token?'Credential created':'This request was already accepted. Its token cannot be displayed again. Revoke it and create a new credential if the first reply was lost.');await load();});});
  $('[data-access-reveal]').onclick=()=>{const input=$('[data-access-token]');input.type=input.type==='password'?'text':'password';};
  $('[data-access-refresh]').onclick=()=>act(load);window.addEventListener('aiw:locale-changed',translate);translate();act(load);
})();
