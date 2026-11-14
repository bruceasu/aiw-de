(() => {
  'use strict';
  const host=document.querySelector('[data-team-members]');if(!host)return;
  const $=s=>host.querySelector(s),form=$('[data-team-form]'),factory=host.dataset.teamMode==='factory';
  const root='/api/r1/team-members',csrf=document.querySelector('meta[name="csrf-token"]').content;
  const skillHistories=new Map();
  const openSkillPanels=new Set();
  const words={
    'Team members':['团队成员','チームメンバー'],'Create in Agent factory':['到 Agent 工厂创建','Agent ファクトリーで作成'],'Create member':['创建成员','メンバーを作成'],'Refresh members':['刷新成员','メンバーを更新'],
    'Open team':['查看团队','チームを表示'],
    'Skill versions':['Skill 版本','Skill バージョン'],'View versions':['查看版本','バージョンを見る'],'Version':['版本','バージョン'],'Reason':['修改理由','変更理由'],'Source task':['来源任务','元のタスク'],'Source Plan':['来源 Plan','元の Plan'],'Current version':['当前版本','現在のバージョン'],'Restore this version':['恢复此版本','このバージョンに戻す'],'Skill restored as a new version':['已恢复为新的 Skill 版本','新しい Skill バージョンとして復元しました'],'Built-in baseline':['内置初始版本','組み込み初期版'],'Select a Skill version':['选择 Skill 版本','Skill バージョンを選択'],'No Skills configured':['没有配置 Skill','Skill が設定されていません'],
    'Member identities survive process restarts. Processes are shared; member settings do not grant tools or permissions.':['成员身份在进程重启后仍保留。执行进程可共享；成员设定不增加工具或权限。','メンバーの識別情報はプロセス再起動後も保持されます。プロセスは共有でき、設定によって権限は増えません。'],
    'Member work currently uses the read-only text runtime. Skills do not enable file edits.':['当前成员工作仅支持只读文本执行；选择 Skills 不会启用文件修改。','現在は読み取り専用のテキスト実行です。Skills を選んでもファイル編集は有効になりません。'],
    'Member list':['成员列表','メンバー一覧'],'Current members':['当前成员','現在のメンバー'],'Dissolved members (read-only)':['已解散成员（只读）','解散済みメンバー（読み取り専用）'],
    'Member name':['成员名称','メンバー名'],'Responsibilities and strengths':['职责与专长','役割と得意分野'],'Built-in Skills (up to 8)':['内置 Skills（最多 8 项）','組み込み Skills（最大8件）'],
    'Human-maintained experience':['人工维护的经验','人が管理する経験メモ'],'Save member':['保存成员','メンバーを保存'],'Cancel edit':['取消编辑','編集をキャンセル'],
    'Notes are unverified context sent with assigned work. Do not enter passwords or API keys. Changes affect future assignments; frozen assignments stay unchanged.':['经验未经自动核实，会随成员任务发送给模型。请勿填写密码或 API Key。修改影响之后的分配；已冻结的成员设定保持不变。','メモは未検証の参考情報として送信されます。パスワードや API キーを入力しないでください。変更は今後の割り当てに適用され、確定済みの設定は変わりません。'],
    'Up to 100 members and 200 linked records are shown. Export includes settings, attached references and confirmed experience; external knowledge services and model credentials are excluded.':['最多显示 100 个成员及 200 条关联记录。导出包含设定、显式参考资料和确认经验，不含外部知识库或模型凭据。','最大100名と関連記録200件を表示します。設定、添付した参考資料、採用済みの経験を出力し、外部知識サービスやモデル認証情報は含みません。'],
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
    'Skill is not configured for this member':['此 Skill 未配置给该成员','この Skill はこのメンバーに設定されていません'],'Skill changed; refresh before restoring':['Skill 已变化，请刷新后再恢复','Skill が変更されています。更新してから復元してください'],'Skill version not found':['找不到此 Skill 版本','Skill バージョンが見つかりません'],'Skill restore request required':['缺少 Skill 恢复参数','Skill の復元情報がありません'],
    'Finish or stop assigned workflows before dissolving this member':['请先完成或停止该成员关联的流程，再解散','解散する前に関連ワークフローを完了または停止してください'],
    'Archived members are read-only':['已解散成员只能查看或导出','解散済みメンバーは読み取り専用です'],
    'Member already exists; refresh before changing it':['成员已存在，请刷新核对后再修改','メンバーは既に存在します。更新して確認してください'],
    'Member not found':['成员不存在或不属于当前账号','メンバーが存在しないか、このアカウントのものではありません'],
    'Member text is empty or too long':['成员名称或职责为空，或资料超过长度限制','名前・役割が空か、文字数の上限を超えています'],
    'Operator login required':['请使用具有操作权限的账号登录','操作権限のあるアカウントでログインしてください'],
    'At most 100 current members per account':['每个账号最多保留 100 个未解散成员','1アカウントで保持できる未解散メンバーは最大100名です'],
    'Ready':['等待领取','取得待ち'],'Running':['执行中','実行中'],'Waiting':['待处理','対応待ち'],'Linked workflow':['关联流程','関連ワークフロー'],
    'Default model (blank inherits)':['默认模型（留空继承）','既定モデル（空欄で継承）'],
    'Default effort (explicit override)':['默认强度（手动覆盖）','既定の推論強度（手動指定）'],
    'API default':['API 默认值','APIの既定値'],
    'Invalid member model name':['成员模型名称格式无效','メンバーのモデル名が無効です'],
    'Invalid member effort':['成员推理强度无效','メンバーの推論強度が無効です'],
    'Unavailable profile; explicitly reselect':['方案已失效，请明确重新选择','設定は無効です。明示的に選び直してください'],
    'Registered profiles changed; explicitly reselect before saving':['已注册方案已变化，请明确重新选择后再保存','登録済み設定が変更されました。明示的に選び直してから保存してください'],
    'Select up to eight text sources totaling 512 KiB':['任务与成员资料合计最多 8 项、512 KiB','タスクとメンバーの資料は合計8件・512 KiBまでです'],
    'Select up to eight model profiles, six sources and eight experiences':['最多选择 8 个模型方案、6 份资料和 8 条经验','モデル設定8件、資料6件、経験メモ8件まで選択してください'],
    'Member references must be knowledge, not executable Skills':['成员参考资料不能注册为可执行 Skills','メンバーの参考資料を実行可能なSkillsとして登録することはできません'],
    'Inherit task or Worker default':['继承任务或 Worker 默认值','タスクまたはWorkerの既定値を継承'],
    'Allowed registered model profiles (optional)':['允许的已注册模型方案（可选）','許可する登録済みモデル設定（任意）'],
    'Defaults do not change accounts or tools. Explicit task preferences take priority within approved limits. Model switching requires a selected allowed range.':['默认值不改变账户或工具权限；任务明确指定的值在许可范围内优先。切换模型必须有明确的允许范围。','既定値はアカウントやツール権限を変更しません。許可範囲内ではタスクの明示設定が優先します。モデルの切り替えには許可範囲の指定が必要です。'],
    'Reference text (up to 6 UTF-8 files)':['参考资料（最多 6 个 UTF-8 文本）','参考資料（UTF-8テキスト6件まで）'],
    'Saving explicitly makes these references available to this member\'s future work. They grant no tools or file permissions. Task and member references share the 8-source, 512-KiB limit.':['保存后，这些资料将供分配给此成员的后续工作按需读取，不授予工具或文件权限。任务与成员资料合计最多 8 项、512 KiB。','保存すると、このメンバーの今後の作業で必要に応じて参照できます。ツールやファイルの権限は付与しません。タスク資料との合計は8件・512 KiBまでです。'],
    'Quote an assigned execution':['引用参与过的执行','担当した実行を引用'],
    'Quote result as an experience draft':['引用结果，作为经验草稿','結果を経験メモの下書きとして引用'],
    'Quoted results are drafts. Correct them and save to confirm adoption; deleting here affects future assignments only. Past execution records remain unchanged.':['引用结果只是草稿，请纠正后保存，明确采用为经验。这里的修改或删除只影响后续分配，不改历史执行。','引用結果は下書きです。修正して保存すると経験として採用します。変更や削除は今後の割り当てにのみ反映し、過去の実行記録は変えません。'],
    'Remove reference':['移除参考资料','参考資料を取り除く'],'Remove experience':['移除这条经验','経験メモを取り除く'],
    'Review and correct this experience':['核对并纠正经验','経験メモを確認・修正'],
    'Select an assigned execution':['请选择参与过的执行','担当した実行を選択'],
    'Source result shortened; review before saving':['来源结果已截短，请核对后保存','出典の結果を省略しています。確認して保存してください'],
    'Review the quoted result before saving':['请核对引用结果，再保存为经验','引用結果を確認してから経験メモとして保存してください'],
    'Select up to six UTF-8 reference files, each at most 128 KiB':['最多选择 6 个 UTF-8 文本，每个不超过 128 KiB','UTF-8テキストを6件まで、各128 KiB以下で選択してください'],
    'Select up to eight experiences':['最多保存 8 条有来源的经验','出典のある経験メモは8件までです'],
    'Select only approved workflow model profiles':['只能选择已获准的工作流模型方案','許可されたワークフロー用モデル設定を選択してください'],
    'Member defaults must match an approved workflow model profile':['默认值必须符合允许的工作流模型方案','既定値は許可されたワークフロー用モデル設定と一致させてください'],
    'Select an execution assigned to this member':['请选择此成员实际参与过的执行','このメンバーが担当した実行を選択してください'],
    'This member has no recorded result in that execution yet':['这次执行中尚无此成员的结果记录','その実行には、このメンバーの結果がまだ記録されていません'],
  };
  const t=x=>words[x]?.[window.aiwI18n?.locale==='zh'?0:window.aiwI18n?.locale==='ja'?1:-1]||x;
  const node=(tag,value,cls)=>{const n=document.createElement(tag);if(value!=null)n.textContent=value;if(cls)n.className=cls;return n;};
  const raw=(tag,value,cls)=>{const n=node(tag,value,cls);n.dataset.i18nSkip='';return n;};
  let data=null,editing=null,busy=false,loading=false,references=[],experiences=[];const histories=new Map();
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
    if(!data)return;const list=$('[data-team-list]');list.replaceChildren();
    for(const member of data.members){
      const card=node('article',null,'team-card'),heading=node('div',null,'workers-heading');
      let mark=0;for(const char of member.id)mark=(mark*31+char.charCodeAt(0))>>>0;
      const avatar=raw('span',[...member.name].slice(0,2).join(''),'team-avatar');avatar.dataset.shape=String(mark%4);heading.append(avatar,raw('h3',member.name),node('span',t(member.state),'pill neutral'));card.append(heading,raw('p',member.purpose.length>300?member.purpose.slice(0,300)+'…':member.purpose),raw('p',member.skills.join(' · '),'help'));
      card.append(raw('p',member.model?[member.model,member.effort].filter(Boolean).join(' · '):t('Inherit task or Worker default'),'help'));
      const skillPanel=node('details',null,'team-skill-panel');skillPanel.append(node('summary',t('Skill versions')));
      if(!member.skills.length)skillPanel.append(node('p',t('No Skills configured'),'help'));
      for(const skill of member.skills){const row=node('div',null,'record-actions');row.append(raw('span',skill),button('View versions',()=>openSkillHistory(member.id,skill)));skillPanel.append(row);}
      const selectedHistory=skillHistories.get(member.id);if(selectedHistory&&member.skills.includes(selectedHistory.ref))renderSkillHistory(skillPanel,member,selectedHistory);else if(selectedHistory)skillHistories.delete(member.id);
      skillPanel.open=openSkillPanels.has(member.id);skillPanel.ontoggle=()=>{if(skillPanel.open)openSkillPanels.add(member.id);else openSkillPanels.delete(member.id);};
      card.append(skillPanel);
      const work=data.work.filter(w=>w.member_id===member.id),runs=data.runs.filter(r=>r.member_id===member.id),seen=new Set();
      if(!work.length&&!runs.length)card.append(node('p',t('No assigned work in this snapshot'),'help'));
      for(const item of work.slice(0,3)){
        const state=['CLAIMED','RUNNING','YIELDING'].includes(item.state)?'Running':item.state==='WAITING_INPUT'?'Waiting':'Ready';
        const link=node('a',t(state)+' · '+item.title);link.href=item.goal_id?'/ui/goals?goal='+encodeURIComponent(item.goal_id):'/ui/workflows?execution='+encodeURIComponent(item.run_id);link.dataset.i18nSkip='';card.append(link);if(item.run_id)seen.add(item.run_id);
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
  async function openSkillHistory(member,ref,version){
    const base='/'+encodeURIComponent(member)+'/skills/'+encodeURIComponent(ref)+'/versions',history=await request(base),selected=version??history.currentVersion;
    const content=await request(base+'/'+selected),previous=selected>0?await request(base+'/'+(selected-1)):null;
    skillHistories.set(member,{ref,history,selected,content,previous});openSkillPanels.add(member);render();
  }
  function skillDiff(before,after){
    const oldLines=before?before.split(/\r?\n/):[],newLines=after.split(/\r?\n/);
    if(oldLines.length>250||newLines.length>250)return '+ '+newLines.join('\n+ ');
    const rows=oldLines.length+1,cols=newLines.length+1,table=Array.from({length:rows},()=>new Uint16Array(cols));
    for(let i=oldLines.length-1;i>=0;i--)for(let j=newLines.length-1;j>=0;j--)table[i][j]=oldLines[i]===newLines[j]?table[i+1][j+1]+1:Math.max(table[i+1][j],table[i][j+1]);
    const result=[];let i=0,j=0;
    while(i<oldLines.length||j<newLines.length){
      if(i<oldLines.length&&j<newLines.length&&oldLines[i]===newLines[j]){result.push('  '+oldLines[i]);i++;j++;}
      else if(i<oldLines.length&&(j===newLines.length||table[i+1][j]>=table[i][j+1])){result.push('- '+oldLines[i]);i++;}
      else {result.push('+ '+newLines[j]);j++;}
    }
    return result.join('\n');
  }
  function renderSkillHistory(target,member,state){
    const details=node('details',null,'team-skill-history');details.open=true;details.append(node('summary',state.ref));
    const selector=raw('select');selector.setAttribute('aria-label',t('Select a Skill version'));
    for(const item of state.history.versions){const label='v'+item.version+(item.version===state.history.currentVersion?' · '+t('Current version'):'');selector.add(new Option(label,String(item.version)));}
    selector.value=String(state.selected);selector.onchange=()=>act(()=>openSkillHistory(member.id,state.ref,Number(selector.value)));
    const selected=state.history.versions.find(item=>item.version===state.selected);
    const reason=state.selected===0?t('Built-in baseline'):(selected?.reason||state.content.reason);
    details.append(selector,node('p',t('Version')+' '+state.selected+' · '+t('Reason')+': '+reason,'help'));
    if(selected?.sourceTaskId)details.append(node('p',t('Source task')+': '+selected.sourceTaskId,'help'));
    if(selected?.sourceRunId){const link=raw('a',t('Source Plan')+': '+selected.sourceRunId);link.href='/ui/workflows?execution='+encodeURIComponent(selected.sourceRunId);link.target='_blank';link.rel='noopener';details.append(link);}
    details.append(raw('pre',skillDiff(state.previous?.content||null,state.content.content),'team-skill-diff'));
    if(member.state!=='ARCHIVED'&&state.selected!==state.history.currentVersion)details.append(button('Restore this version',async()=>{
      await request('/'+encodeURIComponent(member.id)+'/skills/'+encodeURIComponent(state.ref)+'/restore',{expectedVersion:state.history.currentVersion,targetVersion:state.selected});
      status('Skill restored as a new version');await openSkillHistory(member.id,state.ref);
    }));
    target.append(details);
  }
  async function load(){
    if(loading)return;loading=true;controls();
    try{data=await request('?archived='+$('[data-team-archived]').value);if(editing)modelOptions(selectedModels());render();}
    finally{loading=false;controls();}
  }
  function show(profile){
    editing=profile;form.hidden=false;form.elements.name.value=profile.name;form.elements.purpose.value=profile.purpose;form.elements.notes.value=profile.notes;
    const settings=profile.settings||{};form.elements.model.value=settings.model||'';form.elements.effort.value=settings.effort||'';
    modelOptions(settings.allowedProfiles||[]);
    references=[...(settings.knowledge||[])];experiences=(settings.experience||[]).map(item=>({...item}));renderReferences();renderExperience();$('[data-team-files]').value='';
    const source=$('[data-team-experience-source]');source.replaceChildren(new Option(t('Select an assigned execution'),''));
    if(profile.revision>0)request('/'+encodeURIComponent(profile.id)+'/history').then(runs=>{if(editing!==profile)return;for(const run of runs)source.add(new Option(run.title||run.id,run.id));}).catch(error=>status(error.message));
    const choice=$('[data-team-skills]');choice.replaceChildren();for(const id of data.skills){const option=new Option(id,id);option.selected=profile.skills.includes(id);choice.add(option);}requestAnimationFrame(()=>form.elements.name.focus());
  }
  async function edit(id){if(editing&&!window.confirm(t('Discard unsaved edits?')))return;show(await request('/'+encodeURIComponent(id)));}
  const sameModel=(a,b)=>a?.id===b?.id&&['provider','runtime','model','effort','fast','level'].every(key=>a?.profile?.[key]===b?.profile?.[key]);
  const selectedModels=()=>[...form.elements.allowedProfiles.selectedOptions].map(option=>option.memberModel);
  function modelOptions(saved){
    const current=data?.modelProfiles||[],choices=[...current,...saved.filter(item=>!current.some(value=>sameModel(item,value)))];form.elements.allowedProfiles.replaceChildren();
    choices.forEach((item,index)=>{const available=current.some(value=>sameModel(item,value));const option=new Option(item.profile.provider+' / '+item.profile.runtime+' · '+item.profile.model+' · '+item.profile.effort+(available?'':' · '+t('Unavailable profile; explicitly reselect')),String(index));option.memberModel=item;option.selected=saved.some(value=>sameModel(item,value));form.elements.allowedProfiles.add(option);});
  }
  function renderReferences(){const target=$('[data-team-references]');target.replaceChildren();references.forEach((item,index)=>{const row=node('div',null,'record-actions');row.append(raw('span',item.title),button('Remove reference',()=>{references.splice(index,1);renderReferences();}));target.append(row);});}
  function renderExperience(){const target=$('[data-team-experience]');target.replaceChildren();experiences.forEach((item,index)=>{const row=node('div',null,'field'),input=raw('textarea');input.value=item.text;input.maxLength=2000;input.required=true;input.setAttribute('aria-label',t('Review and correct this experience'));input.oninput=()=>item.text=input.value;const link=raw('a',item.sourceRun);link.href='/ui/workflows?execution='+encodeURIComponent(item.sourceRun);link.target='_blank';link.rel='noopener';row.append(link,input,button('Remove experience',()=>{experiences.splice(index,1);renderExperience();}));target.append(row);});}
  $('[data-team-files]').onchange=()=>act(async()=>{
    const files=[...$('[data-team-files]').files];if(files.length+references.length>6||files.some(file=>file.size>131072))throw new Error('Select up to six UTF-8 reference files, each at most 128 KiB');
    const added=[];for(const file of files){const content=new TextDecoder('utf-8',{fatal:true}).decode(await file.arrayBuffer());if(!content.trim()||content.includes('\0'))throw new Error('Select up to six UTF-8 reference files, each at most 128 KiB');added.push({kind:'knowledge',title:file.name.slice(0,160),content,digest:null});}
    references.push(...added);renderReferences();$('[data-team-files]').value='';
  });
  $('[data-team-quote]').onclick=()=>act(async()=>{
    const runId=$('[data-team-experience-source]').value;if(!editing?.revision||!runId)throw new Error('Select an assigned execution');if(experiences.length>=8)throw new Error('Select up to eight experiences');
    const result=await request('/'+encodeURIComponent(editing.id)+'/experience-source?runId='+encodeURIComponent(runId));experiences.push({sourceRun:result.sourceRun,text:result.content});renderExperience();status(result.truncated?'Source result shortened; review before saving':'Review the quoted result before saving');
  });
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
  form.onsubmit=event=>{event.preventDefault();act(async()=>{const selected=[...$('[data-team-skills]').selectedOptions].map(o=>o.value),allowed=selectedModels();if(selected.length>8)throw new Error('Select up to eight registered platform Skills');if(allowed.some(item=>!(data.modelProfiles||[]).some(current=>sameModel(item,current))))throw new Error('Registered profiles changed; explicitly reselect before saving');await request('',{id:editing.id,revision:editing.revision,name:form.elements.name.value,purpose:form.elements.purpose.value,skills:selected,notes:form.elements.notes.value,settings:{model:form.elements.model.value,effort:form.elements.effort.value,allowedProfiles:allowed,knowledge:references,experience:experiences}});editing=null;form.hidden=true;await load();status('Member saved');});};
  window.addEventListener('aiw:locale-changed',()=>{translate();render();if(editing)modelOptions(selectedModels());renderReferences();renderExperience();const source=$('[data-team-experience-source]');if(source.options[0])source.options[0].textContent=t('Select an assigned execution');});translate();act(load);
  const timer=setInterval(()=>{if(!document.hidden&&!editing&&!busy&&!loading)act(load);},8000);window.addEventListener('pagehide',()=>clearInterval(timer));
})();
