(() => {
  'use strict';
  const host=document.querySelector('[data-team-members]');if(!host)return;
  const $=s=>host.querySelector(s),form=$('[data-team-form]'),factory=host.dataset.teamMode==='factory';
  const root='/api/r1/team-members',csrf=document.querySelector('meta[name="csrf-token"]').content;
  const words={
    'Team members':['团队成员','チームメンバー'],'Create in Agent factory':['到 Agent 工厂创建','Agent ファクトリーで作成'],'Create member':['创建成员','メンバーを作成'],'Refresh members':['刷新成员','メンバーを更新'],
    'Open team':['查看团队','チームを表示'],
    'Member identities survive process restarts. Processes are shared; member settings do not grant tools or permissions.':['成员身份在进程重启后仍保留。执行进程可共享；成员设定不增加工具或权限。','メンバーの識別情報はプロセス再起動後も保持されます。プロセスは共有でき、設定によって権限は増えません。'],
    'Member work currently uses the read-only text runtime. Skills do not enable file edits.':['当前成员工作仅支持只读文本执行；选择 Skills 不会启用文件修改。','現在は読み取り専用のテキスト実行です。Skills を選んでもファイル編集は有効になりません。'],
    'Member list':['成员列表','メンバー一覧'],'Current members':['当前成员','現在のメンバー'],'Dissolved members (read-only)':['已解散成员（只读）','解散済みメンバー（読み取り専用）'],
    'Member name':['成员名称','メンバー名'],'Responsibilities and strengths':['职责与专长','役割と得意分野'],'Built-in Skills (up to 8)':['内置 Skills（最多 8 项）','組み込み Skills（最大8件）'],
    'Human-maintained experience':['人工维护的经验','人が管理する経験メモ'],'Save member':['保存成员','メンバーを保存'],'Cancel edit':['取消编辑','編集をキャンセル'],
    'Notes are unverified context sent with assigned work. Do not enter passwords or API keys. Changes affect future assignments; frozen assignments stay unchanged.':['经验未经自动核实，会随成员任务发送给模型。请勿填写密码或 API Key。修改影响之后的分配；已冻结的成员设定保持不变。','メモは未検証の参考情報として送信されます。パスワードや API キーを入力しないでください。変更は今後の割り当てに適用され、確定済みの設定は変わりません。'],
    'Up to 100 members and 200 linked work or workflow records are shown. Export includes profile and human notes; shared knowledge and model credentials are not included.':['最多显示 100 个成员和 200 条关联工作／流程记录。导出只含成员资料与人工经验，不含共享知识库或模型凭据。','最大100名と関連する作業・ワークフロー200件を表示します。エクスポートは設定と経験メモのみで、共有知識や認証情報は含みません。'],
    'ACTIVE':['可接单','受付可能'],'DRAINING':['已停止接单','受付停止'],'ARCHIVED':['已解散（只读）','解散済み（読み取り専用）'],
    'Edit':['修改设定与经验','設定・経験を編集'],'Export profile and notes':['导出资料与经验','設定・メモをエクスポート'],'Stop accepting work':['停止接单','受付を停止'],'Resume accepting work':['恢复接单','受付を再開'],'Dissolve member':['解散成员','メンバーを解散'],
    'Current work continues; unclaimed work waits until this member resumes.':['当前工作继续；尚未领取的工作等待该成员恢复接单。','現在の作業は継続し、未取得の作業は受付再開まで待機します。'],
    'Dissolve this member? Finish or stop assigned workflows first. Profile and history stay read-only; shared processes and knowledge are retained.':['解散该成员？须先完成或停止关联流程。成员资料和历史将保留为只读，不终止共享进程、不删除共享知识。','このメンバーを解散しますか？関連フローを完了または停止してください。設定と履歴は読み取り専用で保持し、共有プロセスや知識は削除しません。'],
    'Open execution':['查看执行','実行を表示'],'No assigned work in this snapshot':['当前快照没有关联工作','このスナップショットには関連作業がありません'],'No members in this list':['此列表暂无成员','メンバーはいません'],
    'Execution history (latest 100)':['执行历史（最近 100 条）','実行履歴（直近100件）'],'No execution history':['暂无关联的执行历史','関連する実行履歴はありません'],'Current work shown':['当前快照中已显示的工作','このスナップショットの表示中の作業'],
    'RUNNING':['执行中','実行中'],'PAUSED':['后续步骤已暂停','後続ステップ停止中'],'WAITING_USER':['待你回答','回答待ち'],'STOPPING':['等待停止确认','停止確認待ち'],'STOPPED':['已停止','停止済み'],'COMPLETED':['已完成','完了'],'FAILED':['需要处理','対応が必要'],
    'Member saved':['成员已保存','保存しました'],'State updated':['成员状态已更新','状態を更新しました'],'Connection issue; process state is unconfirmed':['连接异常；进程状态尚未确认','接続に問題があります。プロセスの状態は未確認です'],
    'Could not load members':['无法读取成员，请检查登录与连接','メンバーを読み込めません。ログインと接続を確認してください'],
    'Request outcome is unknown. Refresh to check the original member before retrying.':['请求结果尚未确认。请先刷新核对原成员，再决定是否重试。','結果は未確認です。再試行する前に更新して元のメンバーを確認してください。'],
    'Discard unsaved edits?':['放弃未保存的修改？','未保存の変更を破棄しますか？'],'Select up to eight registered platform Skills':['请选择最多 8 项已注册的平台 Skills','登録済み Skills を最大8件選択してください'],
    'Member changed; refresh before applying this action':['成员已变化，请刷新后操作','メンバーが変更されています。更新してください'],
    'Member changed or is archived; refresh before saving':['成员已变化或已解散，请刷新后保存','メンバーが変更または解散されています。更新してください'],
    'Finish or stop assigned workflows before dissolving this member':['请先完成或停止该成员关联的流程，再解散','解散する前に関連ワークフローを完了または停止してください'],
    'Archived members are read-only':['已解散成员只能查看或导出','解散済みメンバーは読み取り専用です'],
    'Member already exists; refresh before changing it':['成员已存在，请刷新核对后再修改','メンバーは既に存在します。更新して確認してください'],
    'Member not found':['成员不存在或不属于当前账号','メンバーが存在しないか、このアカウントのものではありません'],
    'Member text is empty or too long':['成员名称或职责为空，或资料超过长度限制','名前・役割が空か、文字数の上限を超えています'],
    'Operator login required':['请使用具有操作权限的账号登录','操作権限のあるアカウントでログインしてください'],
    'At most 100 current members per account':['每个账号最多保留 100 个未解散成员','1アカウントで保持できる未解散メンバーは最大100名です'],
    'Ready':['等待领取','取得待ち'],'Running':['执行中','実行中'],'Waiting':['待处理','対応待ち'],'Linked workflow':['关联流程','関連ワークフロー'],
  };
  const t=x=>words[x]?.[window.aiwI18n?.locale==='zh'?0:window.aiwI18n?.locale==='ja'?1:-1]||x;
  const node=(tag,value,cls)=>{const n=document.createElement(tag);if(value!=null)n.textContent=value;if(cls)n.className=cls;return n;};
  const raw=(tag,value,cls)=>{const n=node(tag,value,cls);n.dataset.i18nSkip='';return n;};
  let data=null,editing=null,busy=false,loading=false;const histories=new Map();
  function translate(){host.querySelectorAll('[data-team-label]').forEach(n=>n.textContent=t(n.dataset.teamLabel));}
  function status(value){$('[data-team-status]').textContent=t(value);}
  function controls(){host.querySelectorAll('button').forEach(b=>b.disabled=busy||loading);$('[data-team-fields]').disabled=busy||loading;}
  async function request(path='',body){
    let response;
    try{response=await fetch(root+path,{method:body===undefined?'GET':'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:body===undefined?undefined:JSON.stringify(body)});}
    catch(_){throw new Error(body===undefined?'Could not load members':'Request outcome is unknown. Refresh to check the original member before retrying.');}
    if(response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw new Error(body===undefined?'Could not load members':'Request outcome is unknown. Refresh to check the original member before retrying.');
    let result;try{result=await response.json();}catch(_){throw new Error(body===undefined?'Could not load members':'Request outcome is unknown. Refresh to check the original member before retrying.');}
    if(!response.ok)throw new Error(response.status>=500&&body!==undefined?'Request outcome is unknown. Refresh to check the original member before retrying.':result.error||'Could not load members');return result;
  }
  async function act(work){if(busy||loading)return;busy=true;controls();try{await work();}catch(error){status(error.message);}finally{busy=false;controls();}}
  function button(label,callback){const b=node('button',t(label),'btn ghost');b.type='button';b.onclick=()=>act(callback);return b;}
  function render(){
    if(!data)return;const list=$('[data-team-list]');list.replaceChildren();if(factory)return;
    for(const member of data.members){
      const card=node('article',null,'team-card'),heading=node('div',null,'workers-heading');
      let mark=0;for(const char of member.id)mark=(mark*31+char.charCodeAt(0))>>>0;
      const avatar=raw('span',[...member.name].slice(0,2).join(''),'team-avatar');avatar.dataset.shape=String(mark%4);heading.append(avatar,raw('h3',member.name),node('span',t(member.state),'pill neutral'));card.append(heading,raw('p',member.purpose.length>300?member.purpose.slice(0,300)+'…':member.purpose),raw('p',member.skills.join(' · '),'help'));
      const work=data.work.filter(w=>w.member_id===member.id),runs=data.runs.filter(r=>r.member_id===member.id),seen=new Set();
      if(!work.length&&!runs.length)card.append(node('p',t('No assigned work in this snapshot'),'help'));
      for(const item of work.slice(0,3)){
        const state=['CLAIMED','RUNNING','YIELDING'].includes(item.state)?'Running':item.state==='WAITING_INPUT'?'Waiting':'Ready';
        const link=node('a',t(state)+' · '+item.title);link.href='/ui/workflows?execution='+encodeURIComponent(item.run_id);link.dataset.i18nSkip='';card.append(link);seen.add(item.run_id);
        if(item.last_heartbeat_at_epoch_ms&&data.observedAt-item.last_heartbeat_at_epoch_ms>90000)card.append(node('p',t('Connection issue; process state is unconfirmed'),'help'));
      }
      for(const run of runs.filter(r=>!seen.has(r.run_id)).slice(0,3)){const link=node('a',t('Linked workflow')+' · '+run.run_id);link.href='/ui/workflows?execution='+encodeURIComponent(run.run_id);card.append(link);}
      if(work.length>3)card.append(node('p',t('Current work shown')+': 3 / '+work.length,'help'));
      const actions=node('div',null,'workers-heading');
      if(member.state!=='ARCHIVED'){
        actions.append(button('Edit',()=>edit(member.id)),button(member.state==='ACTIVE'?'Stop accepting work':'Resume accepting work',()=>change(member,member.state==='ACTIVE'?'stop':'resume')),
          button('Dissolve member',()=>change(member,'archive')));
      }
      actions.append(button('Execution history (latest 100)',async()=>{histories.set(member.id,{rows:await request('/'+encodeURIComponent(member.id)+'/history'),open:true});render();}),button('Export profile and notes',()=>exportMember(member.id)));card.append(actions);
      if(histories.has(member.id)){const history=histories.get(member.id),details=node('details');details.open=history.open;details.ontoggle=()=>history.open=details.open;details.append(node('summary',t('Execution history (latest 100)')));for(const run of history.rows){const link=raw('a',t(run.state)+' · '+(run.title||run.id));link.href='/ui/workflows?execution='+encodeURIComponent(run.id);details.append(link,node('br'));}if(!history.rows.length)details.append(node('p',t('No execution history'),'help'));card.append(details);}list.append(card);
    }
    if(!data.members.length)list.append(node('p',t('No members in this list'),'empty'));controls();
  }
  async function load(){
    if(loading)return;loading=true;controls();
    try{data=await request('?archived='+$('[data-team-archived]').value);render();}
    finally{loading=false;controls();}
  }
  function show(profile){
    editing=profile;form.hidden=false;form.elements.name.value=profile.name;form.elements.purpose.value=profile.purpose;form.elements.notes.value=profile.notes;
    const choice=$('[data-team-skills]');choice.replaceChildren();for(const id of data.skills){const option=new Option(id,id);option.selected=profile.skills.includes(id);choice.add(option);}requestAnimationFrame(()=>form.elements.name.focus());
  }
  async function edit(id){if(editing&&!window.confirm(t('Discard unsaved edits?')))return;show(await request('/'+encodeURIComponent(id)));}
  async function change(member,action){
    if(action==='archive'&&!window.confirm(t('Dissolve this member? Finish or stop assigned workflows first. Profile and history stay read-only; shared processes and knowledge are retained.')))return;
    if(action==='stop'&&!window.confirm(t('Current work continues; unclaimed work waits until this member resumes.')))return;
    const changed=await request('/'+encodeURIComponent(member.id)+'/state',{revision:member.revision,action});
    if(editing?.id===changed.id&&editing.revision===member.revision)editing={...editing,revision:changed.revision,state:changed.state};
    if(changed.state==='ARCHIVED'&&editing?.id===changed.id){editing=null;form.hidden=true;}await load();status('State updated');
  }
  async function exportMember(id){const exported=await request('/'+encodeURIComponent(id)+'/export'),url=URL.createObjectURL(new Blob([JSON.stringify(exported,null,2)],{type:'application/json'})),link=document.createElement('a');link.href=url;link.download=id+'.json';document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}
  $('[data-team-new]').hidden=!factory;$('[data-team-archive-filter]').hidden=factory;
  if(factory){const link=host.querySelector('a[href="/ui/agent-factory"]');link.href='/ui/workers';link.dataset.teamLabel='Open team';}
  $('[data-team-new]').onclick=()=>act(async()=>{if(editing&&!window.confirm(t('Discard unsaved edits?')))return;if(!data)await load();show({id:'member-'+crypto.randomUUID(),revision:0,name:'',purpose:'',skills:[],notes:''});});
  $('[data-team-cancel]').onclick=()=>{editing=null;form.hidden=true;};
  $('[data-team-refresh]').onclick=()=>act(load);$('[data-team-archived]').onchange=()=>act(load);
  form.onsubmit=event=>{event.preventDefault();act(async()=>{const selected=[...$('[data-team-skills]').selectedOptions].map(o=>o.value);if(selected.length>8)throw new Error('Select up to eight registered platform Skills');await request('',{id:editing.id,revision:editing.revision,name:form.elements.name.value,purpose:form.elements.purpose.value,skills:selected,notes:form.elements.notes.value});editing=null;form.hidden=true;await load();status('Member saved');});};
  window.addEventListener('aiw:locale-changed',()=>{translate();render();});translate();act(load);
  const timer=setInterval(()=>{if(!document.hidden&&!editing&&!busy&&!loading)act(load);},8000);window.addEventListener('pagehide',()=>clearInterval(timer));
})();
