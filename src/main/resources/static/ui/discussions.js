(() => {
  const byId = id => document.getElementById(id);
  const search = byId('discussion-search'), create = byId('discussion-create');
  const detail = byId('discussion-detail'), reply = byId('discussion-reply');
  const delegate = byId('discussion-delegate'), withdraw = byId('discussion-withdraw');
  const wish = new URLSearchParams(location.search).get('wish') || '';
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  const t = value => window.aiwI18n?.t(value) || value;
  const status = (root, value) => root.querySelector('[role=status]').textContent = t(value);
  let page = 0, selected = null, clarification = null, listRequest = 0, currentRequest = 0;
  async function api(path = '', body) {
    const response = await fetch('/api/r1/discussions' + path, {
      method: body === undefined ? 'GET' : 'POST', credentials: 'same-origin', cache: 'no-store',
      headers: {'Content-Type':'application/json', 'X-CSRF-Token':csrf},
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) throw new Error(data?.error || 'Could not load or save the discussion.');
    return data;
  }
  function formatTime(ms) {
    return new Date(ms).toLocaleString(window.aiwI18n?.locale || undefined);
  }
  async function load(targetPage = page) {
    const request = ++listRequest;
    const result = await api('?' + new URLSearchParams({q:search.elements.q.value.trim(),wish,page:targetPage}));
    if (request !== listRequest) return;
    page = targetPage;
    const list = byId('discussion-list');
    list.replaceChildren();
    for (const topic of result.items) {
      const row = list.insertRow();
      const title = document.createElement('button');
      title.type = 'button';
      title.className = 'btn ghost';
      title.textContent = topic.title;
      title.dataset.i18nSkip = '';
      title.onclick = () => runList(() => open(topic.id));
      row.insertCell().append(title);
      row.insertCell().textContent = t(topic.closed ? 'Closed' : 'Open');
      row.insertCell().textContent = formatTime(topic.updatedMs);
    }
    if (!result.items.length) {
      const cell = list.insertRow().insertCell();
      cell.colSpan = 3;
      cell.className = 'empty';
      cell.textContent = t('No matching discussions.');
    }
    byId('discussion-previous').disabled = page === 0;
    byId('discussion-next').disabled = !result.hasMore;
  }
  async function runList(action) {
    status(document, '');
    try { await action(); }
    catch (error) { status(document, error.message); }
  }
  function show(panel, options) {
    panel.hidden = false;
    window.aiwDesktopWindow.open(panel, {...options,icon:'questions'});
  }
  async function open(id) {
    const request = ++currentRequest;
    const result = await api('/' + encodeURIComponent(id));
    if (request !== currentRequest) return;
    const {topic, posts} = result;
    if(selected!==topic.id)reply.reset();
    selected = topic.id;
    clarification = topic.closed ? null : result.clarification;
    const items = byId('discussion-posts'), source = byId('discussion-wish');
    source.hidden = !topic.wishId;
    if (topic.wishId) source.href = '/ui/goals?goal=' + encodeURIComponent(topic.wishId);
    items.replaceChildren();
    for (const post of posts) {
      const article = document.createElement('article'), heading = document.createElement('h3');
      article.className = 'card';
      heading.textContent = t({USER:'User',MANAGER:'Manager',EMPLOYEE:'Employee',SECRETARY:'Secretary'}[post.role]) + ' · ' + formatTime(post.createdMs);
      const body = document.createElement('p');
      body.className = 'mission-fulltext';
      body.dataset.i18nSkip = '';
      body.textContent = post.body;
      article.append(heading,body);
      items.append(article);
    }
    reply.hidden = topic.closed;
    byId('discussion-clarification').hidden = !clarification;
    const pending = clarification && !clarification.answerId;
    delegate.hidden = !pending || Boolean(clarification?.delegationId);
    withdraw.hidden = !pending || !clarification?.delegationId;
    byId('discussion-clarification-state').textContent = !clarification ? '' : t(clarification.answerId
      ? 'Clarification answered. The manager will read it the next time you submit the wish.'
      : clarification.delegationId ? 'Delegated to your secretary.' : 'The manager needs your clarification.');
    reply.elements.body.maxLength=pending?4000:16000;
    reply.querySelector('[type=submit]').textContent=t(pending?'Send clarification answer':'Post reply');
    status(detail, topic.closed ? 'This discussion is closed.' : '');
    const idle = () => !reply.querySelector('[type=submit]').disabled && !delegate.disabled && !withdraw.disabled;
    show(detail,{title:topic.title,recordTitle:true,canClose:idle,canMinimize:idle,
      onClose:()=>{++currentRequest;detail.hidden=true;selected=null;reply.reset();}});
  }
  byId('discussion-all').hidden = !wish;
  byId('discussion-new').hidden = Boolean(wish);
  search.onsubmit = event => {event.preventDefault();runList(() => load(0));};
  byId('discussion-previous').onclick = () => runList(() => load(page - 1));
  byId('discussion-next').onclick = () => runList(() => load(page + 1));
  byId('discussion-new').onclick = () => show(create,{title:t('New discussion'),focus:create.elements.title,
    canClose:()=>!create.querySelector('[type=submit]').disabled,onClose:()=>{create.hidden=true;create.reset();status(create,'');}});
  create.onsubmit = async event => {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    try {
      const topic = await api('',{title:create.elements.title.value,body:create.elements.body.value});
      window.aiwDesktopWindow.close(create);
      await open(topic.id);
      await runList(() => load(0));
    } catch (error) {status(create.hidden ? document : create,error.message);}
    finally {button.disabled = false;}
  };
  reply.onsubmit = async event => {
    event.preventDefault();
    const button = event.submitter, id = selected;
    button.disabled = true;
    try {
      const pending=clarification&&!clarification.answerId;
      const body=reply.elements.body.value;
      await api('/' + encodeURIComponent(id) + (pending?'/answer':'/reply'),
        pending?{questionId:clarification.questionId,body}:{body});
      reply.reset();
      await open(id);
      await runList(() => load());
    } catch (error) {status(detail,error.message);}
    finally {button.disabled = false;}
  };
  async function changeDelegation(button, action) {
    if(!clarification||clarification.answerId||delegate.disabled||withdraw.disabled)return;
    const id=selected, questionId=clarification.questionId;
    button.disabled = true;
    try {
      await api('/' + encodeURIComponent(id) + '/' + action,{questionId});
      await open(id);
    } catch (error) {status(detail,error.message);}
    finally {button.disabled = false;}
  }
  delegate.onclick=()=>changeDelegation(delegate,'delegate');
  withdraw.onclick=()=>changeDelegation(withdraw,'withdraw');
  byId('discussion-close').onclick = async () => {
    const button = byId('discussion-close');
    if (button.disabled || !await window.aiwConfirm(t('Close this discussion to new replies?'))) return;
    const id = selected;
    button.disabled = true;
    try {
      await api('/' + encodeURIComponent(id) + '/close',{});
      if (selected === id) await open(id);
      await runList(() => load());
    } catch (error) {status(detail,error.message);}
    finally {button.disabled = false;}
  };
  runList(() => load());
})();
