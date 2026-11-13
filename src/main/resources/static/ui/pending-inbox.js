(() => {
  'use strict';
  const host=document.querySelector('[data-pending-inbox]');if(!host)return;
  const $=selector=>host.querySelector(selector),csrf=document.querySelector('meta[name="csrf-token"]').content;
  const t=text=>window.aiwI18n?.t(text)||text;
  const element=(tag,text,cls)=>{const item=document.createElement(tag);if(text!==undefined)item.textContent=text;if(cls)item.className=cls;return item;};
  const kinds={WORKFLOW:'Workflow question or decision',QUESTION:'Worker question',AUTHORIZATION:'Authorization question',TOOL_APPROVAL:'Fixed-operation confirmation'};
  const actions={REPLACE_FILE:'Replace a file',DELETE_FILE:'Delete a file',EXECUTE_COMMAND:'Run a fixed command'};
  let records=[],busy=false,observedAt=0,focusDetail=Boolean(new URLSearchParams(location.search).get('planId'));
  function render(){
    const list=$('[data-pending-records]'),filter=$('[data-pending-filter]').value,query=$('[data-pending-search]').value.trim().toLocaleLowerCase();list.replaceChildren();
    const rows=records.filter(row=>(filter==='all'||filter==='action'&&row.needsHuman&&row.canAct||filter==='waiting'&&!row.needsHuman)
      &&(!query||(row.summary+' '+t(kinds[row.kind])).toLocaleLowerCase().includes(query)));
    if(!rows.length)list.append(element('p',t('No matching records in this list.'),'help'));
    for(const row of rows){
      const card=element('article',undefined,'card'),state=row.needsHuman?(row.canAct?'Waiting for your decision':'A different role is required')
        :row.state==='ANSWERED'?'Answered, not yet applied':row.state==='APPROVED'?'Allowed, not yet executed':'Waiting for Worker acknowledgement';
      card.append(element('h3',t(kinds[row.kind])),element('p',row.kind==='TOOL_APPROVAL'?t(actions[row.summary]||row.summary):row.summary),element('p',t(state),'help'));
      const link=element('a',t(row.needsHuman&&row.canAct?'Open and respond':'View the record'),'btn ghost');link.href=row.href;card.append(link);list.append(card);
    }
    if(observedAt)$('[data-pending-status]').textContent=t('Last read')+': '+new Date(observedAt).toLocaleString(window.aiwI18n?.locale||undefined)+' · '+rows.length+' / '+records.length;
  }
  async function refresh(){
    if(busy)return;busy=true;$('[data-pending-refresh]').disabled=true;
    try{
      const response=await fetch('/api/r1/authority/questions/inbox',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:'{}'});
      if(!response.ok||response.redirected)throw new Error(response.status===401||response.status===403?'Sign in with operation or approval rights.':'Pending records could not be read. Refresh to try again.');
      const data=await response.json();if(!Array.isArray(data.items))throw new Error();records=data.items;observedAt=data.observedAt;render();
      if(focusDetail){document.getElementById('question-workbench').scrollIntoView({block:'start'});focusDetail=false;}
    }catch(error){$('[data-pending-status]').textContent=t(error.message==='Sign in with operation or approval rights.'?error.message:'Pending records could not be read. Refresh to try again.');}
    finally{busy=false;$('[data-pending-refresh]').disabled=false;}
  }
  if(new URLSearchParams(location.search).get('planId'))document.getElementById('question-workbench').open=true;
  $('[data-pending-filter]').onchange=render;$('[data-pending-search]').oninput=render;$('[data-pending-refresh]').onclick=refresh;
  window.addEventListener('aiw:locale-changed',render);refresh();
  const timer=setInterval(()=>{if(!document.hidden)refresh();},15000);window.addEventListener('pagehide',()=>clearInterval(timer));
})();
