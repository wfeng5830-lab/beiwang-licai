'use strict';
const C = LedgerCore, $ = id => document.getElementById(id);
const channels = {wechat:'微信支付',alipay:'支付宝',other:'其他'};
const native = typeof AndroidLedger !== 'undefined';
let page='calendar', month=C.dateKey(new Date()).slice(0,7), chartMode='week', selectedDate=C.dateKey(new Date());
let state={entries:[],pending:[],memos:[]}, storageError='', confirmAction=null, candidateEditing=null, editingSourceKey;
const escapeHTML = s => String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt = cents => C.money(cents);
function refresh(){
  try {
    const data = JSON.parse(native?AndroidLedger.read():localStorage.getItem('daily-ledger-v1')||'{"entries":[],"pending":[]}');
    state={entries:data.entries.map(C.validateEntry),pending:(data.pending||[]).filter(p=>p.source==='scan'),memos:(data.memos||[]).map(C.validateMemo)};
    storageError='';
  }catch(e){storageError='读取账本失败，为保护已有记录，已暂停写入。请重启应用后重试。';}
}
function transact(action, payload) {
  if(storageError) throw new Error(storageError);
  if(native){const result=JSON.parse(AndroidLedger.mutate(action,JSON.stringify(payload)));if(!result.ok){refresh();const error=new Error(result.error||'保存失败');error.code=result.code;throw error;}refresh();return result;}
  const next=JSON.parse(JSON.stringify(state));
  if(action==='upsert') {const entry=C.validateEntry(payload);const index=next.entries.findIndex(e=>e.id===entry.id);if(index<0)next.entries.push(entry);else next.entries[index]=entry;}
  if(action==='delete') next.entries=next.entries.filter(e=>e.id!==payload.id);
  if(action==='import'||action==='importBundle') {for(const e of (action==='importBundle'?payload.entries:payload)){C.validateEntry(e);if(!next.entries.some(old=>old.id===e.id||(e.sourceKey&&old.sourceKey===e.sourceKey)))next.entries.push(e);}}
  if(action==='editCandidate'){const i=next.pending.findIndex(p=>p.id===payload.id);if(i>=0)next.pending[i]={...next.pending[i],...C.validateEntry(payload.entry)};}
  if(action==='confirm') {next.entries.push(C.validateEntry(payload.entry));next.pending=next.pending.filter(e=>e.id!==payload.id);}
  if(action==='dismiss') next.pending=next.pending.filter(e=>e.id!==payload.id);
  if(action==='importBundle'){for(const m of payload.memos){if(!next.memos.some(x=>x.id===m.id)){const incoming=C.validateMemo(m);if(next.memos.some(x=>x.slot>=0&&x.slot===incoming.slot&&!x.done))incoming.slot=-1;next.memos.push(incoming);}}}
  if(action==='saveMemo'){const memo=C.validateMemo(payload),i=next.memos.findIndex(m=>m.id===memo.id);if(memo.slot>=0&&next.memos.some(m=>m.id!==memo.id&&!m.done&&m.slot===memo.slot))throw new Error('该格已有事项，请选择空格');if(i<0)next.memos.push(memo);else next.memos[i]=memo;}
  if(action==='moveMemo')next.memos=C.moveMemo(next.memos,payload.id,payload.slot);
  if(action==='deleteMemo')next.memos=next.memos.filter(m=>m.id!==payload.id);
  if(next.memos.length>1000)throw new Error('最多保存 1000 条备忘录');
  localStorage.setItem('daily-ledger-v1',JSON.stringify(next));state=next;return {ok:true};
}
function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').hidden=true,3500);}
function ask(title,copy,action){$('confirm-title').textContent=title;$('confirm-copy').textContent=copy;confirmAction=action;$('confirm-dialog').showModal();}
const periodPicker=()=>`<div class="period-picker"><button class="icon-button" data-shift="-1" aria-label="上个月">‹</button><input aria-label="选择月份" id="month-picker" type="month" min="2000-01" max="2099-12" value="${month}"><button class="icon-button" data-shift="1" aria-label="下个月">›</button></div>`;
function summaryCards(){
  const entries=C.monthEntries(state.entries,month),today=C.dateKey(new Date());
  const activeDays=new Set(entries.map(e=>e.date)).size;
  const average=C.sum(entries)/((month===today.slice(0,7))?new Date().getDate():C.monthDays(month));
  return `<div class="overview"><section class="summary main"><span class="summary-label">${Number(month.slice(5))} 月总花销</span><div class="summary-value"><span class="currency">¥</span>${fmt(C.sum(entries))}</div><div class="summary-average">日均 ¥${fmt(Math.round(average))} · ${month===today.slice(0,7)?'按已过天数':'按整月天数'}计算</div><div class="summary-foot">${entries.length} 笔消费 · ${activeDays} 天有支出</div></section></div>`;
}
function channelStrip(entries){return `<div class="channel-strip">${['wechat','alipay','other'].map(c=>`<span><i class="dot ${c==='alipay'?'blue':''}"></i>${channels[c]}<strong>¥${fmt(C.sum(entries.filter(e=>e.channel===c)))}</strong></span>`).join('')}</div>`;}
function transactionHTML(entry,editable=false){return `<div class="transaction"><span class="payment-icon ${entry.channel}" aria-hidden="true">${entry.channel==='wechat'?'微':entry.channel==='alipay'?'支':'¥'}</span><div class="transaction-info"><strong>${escapeHTML(entry.note||entry.category)}</strong><small>${editable?entry.time:entry.date.slice(5)+' '+entry.time} · ${channels[entry.channel]} · ${escapeHTML(entry.category)}</small></div><div class="transaction-amount">−${fmt(entry.cents)}</div></div>${editable?`<div class="transaction-actions"><button class="text-button" data-edit="${entry.id}">编辑</button><button class="text-button delete" data-delete="${entry.id}">删除</button></div>`:''}`;}
function emptyHTML(text,button=true){return `<div class="empty"><div class="empty-symbol" aria-hidden="true">▤</div><p>${text}</p>${button?'<button class="secondary" data-add>＋ 记第一笔</button>':''}</div>`;}
function calendarPage(){
  const entries=C.monthEntries(state.entries,month), first=(C.parseDate(month+'-01').getDay()+6)%7, today=C.dateKey(new Date());
  let cells=['一','二','三','四','五','六','日'].map(d=>`<div class="weekday">${d}</div>`).join('')+'<div></div>'.repeat(first);
  for(let day=1;day<=C.monthDays(month);day++){
    const date=month+'-'+C.pad(day), total=C.sum(entries.filter(e=>e.date===date));
    cells+=`<button class="day ${total?'has':''} ${date===today?'today':''}" data-date="${date}" aria-label="${date}，支出 ${fmt(total)} 元，查看明细"><span class="number">${day}</span><span class="spent">${total?fmt(total):'—'}</span></button>`;
  }
  return `<div class="page-heading"><h2>账单花销</h2>${periodPicker()}</div>${summaryCards()}${state.pending.length?`<button class="notice wide" data-page="settings">${state.pending.length} 笔扫描账单待核对，点此查看 →</button>`:''}<section class="card"><div class="section-heading"><h2>每日花销</h2><span class="subtle">点击日期查看明细</span></div><div class="calendar">${cells}</div><p class="calendar-note">金额单位：元 · 绿色边框为今天</p>${channelStrip(entries)}</section>${statsPage()}`;
}

function chartHTML(items,annual=false){
  const max=Math.max(...items.map(e=>e.cents),1);
  return `<div class="chart-scroller"><div class="chart ${annual?'annual':''}" role="img" aria-label="${items.map(e=>`${e.label} ${fmt(e.cents)} 元`).join('；')}">${items.map(e=>`<button class="chart-column ${e.month===month||(!annual&&e.from<=C.dateKey(new Date())&&e.to>=C.dateKey(new Date()))?'active':''}" ${e.month?`data-month="${e.month}"`:`data-week="${e.from}"`} aria-label="${e.label}，${fmt(e.cents)} 元"><span class="bar-value">${e.cents?fmt(e.cents):'0'}</span><span class="chart-bar" style="height:${e.cents?Math.max(2,e.cents/max*155):0}px"></span></button>`).join('')}</div><div class="chart-labels ${annual?'annual':''}">${items.map(e=>`<span>${e.label}${!annual?`<small>${e.range}</small>`:''}</span>`).join('')}</div></div>`;
}
function statsPage(){
  const entries=C.monthEntries(state.entries,month), items=chartMode==='week'?C.weeks(entries,month):C.annual(state.entries,month.slice(0,4));
  return `<section class="card" id="spending-chart"><div class="section-heading"><div><h2>${chartMode==='week'?'每周花销':month.slice(0,4)+' 年每月花销'}</h2><p>金额单位：元</p></div><div class="segmented" aria-label="统计维度"><button data-mode="week" class="${chartMode==='week'?'active':''}">周总</button><button data-mode="month" class="${chartMode==='month'?'active':''}">月总</button></div></div>${chartHTML(items,chartMode==='month')}<p class="calendar-note">${chartMode==='week'?'每周一开始；跨月周只计所选月份内的支出。点击柱子查看明细。':'点击月份柱子，查看该月的周统计。'}</p></section>${chartMode==='month'?`<section class="card"><div class="section-heading"><h2>月支出汇总</h2><span class="subtle">${month.slice(0,4)} 年</span></div>${items.map(e=>`<button class="row wide" data-month="${e.month}"><span>${e.label}</span><strong>¥${fmt(e.cents)} ›</strong></button>`).join('')}</section>`:''}`;
}
let memoView='list',pendingSlot=-1;
const quadrantNames=['重要且紧急','重要不紧急','紧急不重要','不紧急不重要'];
const memoMark=m=>m.mark||Array.from(C.memoText(m).trim())[0]||'事';
function memoRow(m){return `<div class="todo-row ${m.done?'completed':''}"><button class="todo-check" data-toggle-memo="${escapeHTML(m.id)}" aria-label="${m.done?'恢复':'完成'}：${escapeHTML(C.memoText(m))}" aria-pressed="${m.done}">${m.done?'✓':''}</button><button class="todo-text" data-edit-memo="${escapeHTML(m.id)}">${escapeHTML(C.memoText(m))}</button>${m.slot>=0?`<span class="todo-tag q${Math.floor(m.slot/9)}">${escapeHTML(memoMark(m))}</span>`:''}${!m.done&&memoView==='matrix'?`<button class="drag-handle" data-drag-memo="${escapeHTML(m.id)}" aria-label="拖动事项">⠿</button>`:''}</div>`;}
function memosPage(){
 const active=state.memos.filter(m=>!m.done),done=state.memos.filter(m=>m.done).sort((a,b)=>a.completedAt-b.completedAt);
 const board=`<div class="quadrant-board">${quadrantNames.map((name,q)=>`<section class="quadrant q${q}"><h3>${name}<small>${active.filter(m=>Math.floor(m.slot/9)===q).length}/9</small></h3><div class="quadrant-grid">${Array.from({length:9},(_,i)=>{const slot=q*9+i,m=active.find(m=>m.slot===slot);return `<button class="quadrant-cell ${m?'filled':''}" data-slot="${slot}" ${m?`data-drag-memo="${escapeHTML(m.id)}"`:''} aria-label="${name} 第${i+1}格：${m?escapeHTML(C.memoText(m)):'空格，添加事项'}">${m?`<strong>${escapeHTML(memoMark(m))}</strong><small>${escapeHTML(C.memoText(m).replace(/\s/g,'').slice(0,4))}</small>`:'＋'}</button>`;}).join('')}</div></section>`).join('')}</div>`;
 return `<div class="page-heading"><h2>待办事项</h2><button class="primary compact" data-new-memo>＋ 添加</button></div><div class="memo-toolbar"><span class="subtle">${active.length} 件待办 · ${done.length} 件完成</span><div class="segmented"><button data-memo-view="list" class="${memoView==='list'?'active':''}">清单</button><button data-memo-view="matrix" class="${memoView==='matrix'?'active':''}">四象限</button></div></div>${memoView==='matrix'?board+'<p class="subtle matrix-help">拖动 ⠿ 或方块放入空格；点空格也能选择事项。</p>':''}<section class="todo-paper ${memoView==='matrix'?'task-tray':''}"><h3>${memoView==='matrix'?'事项清单':'未完成'}</h3>${active.length?active.map(memoRow).join(''):emptyHTML('暂时没有待办，添加一句话就好',false)}${done.length?`<h3 class="done-heading">已完成 · ${done.length}</h3>${done.map(memoRow).join('')}`:''}</section>`;
}
function openMemo(id,slot=-1){const m=state.memos.find(m=>m.id===id);pendingSlot=m?.slot??slot;$('memo-id').value=m?.id||'m_'+(crypto.randomUUID?crypto.randomUUID():Date.now()+'_'+Math.random().toString(36).slice(2));$('memo-body').value=m?C.memoText(m):'';$('memo-mark').value=m?.mark||'';$('memo-options').hidden=!m;$('memo-position').textContent=m?.slot>=0?quadrantNames[Math.floor(m.slot/9)]+' · 第 '+(m.slot%9+1)+' 格':'未放入象限';$('memo-unassign').hidden=!(m?.slot>=0);$('memo-remove').hidden=!m;$('memo-error').textContent='';$('memo-dialog').showModal();}
function chooseMemoSlot(slot){pendingSlot=slot;const m=state.memos.find(m=>!m.done&&m.slot===slot);if(m){openMemo(m.id);return;}$('memo-picker-content').innerHTML=state.memos.filter(m=>!m.done).map(m=>`<button class="pick-task" data-pick-memo="${escapeHTML(m.id)}">${escapeHTML(C.memoText(m))}${m.slot>=0?'<small>已在 '+quadrantNames[Math.floor(m.slot/9)]+'</small>':''}</button>`).join('')||'<p class="subtle">还没有待办事项</p>';$('memo-picker').showModal();}
function moveTask(id,slot){transact('moveMemo',{id,slot});render();toast(slot<0?'已移出象限':'已放入'+quadrantNames[Math.floor(slot/9)]);}

function settingsPage(){
  const scanner=native?JSON.parse(AndroidLedger.scannerStatus()):{};
  const fresh=state.pending.filter(p=>p.duplicateStatus!=='possible');
  return `<div class="page-heading"><h2>设置</h2><span class="pill">仅存本机</span></div>
  <section class="card"><div class="section-heading"><h2>悬浮扫描</h2><span class="pill">${scanner.connected?'扫描已开启':'未开启'}</span></div>
  <p class="settings-copy">无需无障碍服务。允许悬浮窗后，开启扫描并确认系统屏幕共享授权，再切到微信或支付宝账单页，点悬浮窗“扫描”，自动识别微信或支付宝，无需切换。</p>
  ${native?`<div class="form-grid"><label>账单月份<input type="month" id="scan-month" min="2000-01" max="2099-12" value="${escapeHTML(scanner.month||month)}"></label></div><p class="subtle">月份用于没有年份的历史账单。“今天 / 昨天”按手机当前日期识别。</p>
  <button id="overlay-settings" class="secondary wide">${scanner.enabled?'悬浮窗权限已开启 · 管理':'① 允许显示悬浮窗'}</button><div class="button-row"><button id="start-scan" class="primary">开启扫描</button><button id="stop-scan" class="secondary">停止扫描</button></div><p class="settings-copy">开启期间系统会显示屏幕共享提示。仅点击扫描时识别一帧，不保存截图或录屏，不上传。点悬浮窗 × 或系统“停止共享”即可结束。请勿在输入密码等敏感页面点击扫描。</p>`:'<p class="settings-copy">电脑预览不支持屏幕扫描，请安装安卓应用。</p>'}</section>
  <section class="card" id="scan-queue"><div class="section-heading"><h2>扫描结果</h2><span class="pill">${state.pending.length} 笔待核对</span></div>
  ${fresh.length&&native?`<button id="confirm-scans" class="primary wide">核对全部 ${fresh.length} 笔新记录 · ¥${fmt(C.sum(fresh))}</button>`:''}
  ${state.pending.length?state.pending.map(p=>`<article class="pending-card"><div class="row"><span>${channels[p.channel]}<small>${escapeHTML(p.date)} ${escapeHTML(p.time)}</small></span><strong>¥${fmt(p.cents)}</strong></div><p>${escapeHTML(p.note)}</p>${p.duplicateStatus==='possible'?`<div class="notice warning">疑似重复，请先比较已有记录。<label class="checkbox-label"><input type="checkbox" data-force-id="${escapeHTML(p.id)}">这是另一笔消费，仍然入账</label></div>`:''}<div class="button-row"><button class="secondary" data-dismiss="${escapeHTML(p.id)}">忽略</button><button class="secondary" data-edit-scan="${escapeHTML(p.id)}">编辑</button><button class="primary" data-accept="${escapeHTML(p.id)}">核对</button></div></article>`).join(''):emptyHTML('暂无待核对记录',false)}</section>
  <section class="card"><div class="section-heading"><h2>账本备份</h2><span class="subtle">${state.entries.length} 笔记录</span></div><p class="settings-copy">升级前先备份，直接覆盖安装即可保留账本。备份包含账单和备忘录，导入会核对已有编号和扫描来源。</p><div class="button-row"><button class="secondary" id="export">导出备份</button><button class="secondary" id="import">导入备份</button></div><input type="file" class="import-input" id="import-file" accept="application/json,.json"></section><p class="subtle" style="text-align:center">日常账本 1.5 · 本机保存</p>`;
}
function fitDayAmounts(){
  const canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');if(!ctx)return;
  document.querySelectorAll('.day .spent').forEach(el=>{const style=getComputedStyle(el);ctx.font=`${style.fontWeight} 12px ${style.fontFamily}`;const natural=ctx.measureText(el.textContent).width,available=Math.max(1,el.parentElement.clientWidth-4);el.style.fontSize=Math.min(12,12*available/Math.max(1,natural))+'px';});
}
window.addEventListener('resize',fitDayAmounts);
function render(){
  $('main').innerHTML=(storageError?`<div class="notice warning" role="alert">${escapeHTML(storageError)}</div>`:'')+(page==='calendar'?calendarPage():page==='memos'?memosPage():settingsPage());
  document.querySelectorAll('.bottom-nav button').forEach(b=>{b.classList.toggle('active',b.dataset.page===page);b.setAttribute('aria-current',b.dataset.page===page?'page':'false');});
  $('add-top').hidden=page!=='calendar';
  requestAnimationFrame(fitDayAmounts);
  $('month-picker')?.addEventListener('change',e=>{if(/^20\d\d-(0[1-9]|1[0-2])$/.test(e.target.value)){month=e.target.value;render();}});
  $('overlay-settings')?.addEventListener('click',()=>AndroidLedger.openOverlaySettings());
  $('start-scan')?.addEventListener('click',()=>{try{const r=JSON.parse(AndroidLedger.startScanner($('scan-month').value));if(!r.ok)throw new Error(r.error||'无法开启扫描');toast('请确认系统屏幕共享授权，再切到对应账单页');}catch(e){toast(e.message);}});
  $('stop-scan')?.addEventListener('click',()=>{AndroidLedger.stopScanner();toast('已关闭悬浮窗');});
  $('confirm-scans')?.addEventListener('click',()=>{try{const r=transact('confirmBatch',{ids:state.pending.filter(p=>p.duplicateStatus!=='possible').map(p=>p.id)});render();toast(`已入账 ${r.confirmed||0} 笔${r.conflicts?'，疑似重复请单独核对':''}`);}catch(e){toast(e.message);}});
  $('export')?.addEventListener('click',exportBackup);
  $('import')?.addEventListener('click',()=>native?AndroidLedger.importBackup():$('import-file').click());
  $('import-file')?.addEventListener('change',async e=>{const file=e.target.files[0];if(!file)return;if(file.size>20000000){toast('备份文件不能超过 20 MB');return;}try{prepareImport(await file.text());}catch(error){toast(error.message);}e.target.value='';});
}
function openEntry(entry=null,date=selectedDate,candidate=null){
  candidateEditing=candidate;editingSourceKey=entry?.sourceKey;$('entry-form').reset();$('form-error').textContent='';$('duplicate-review').hidden=true;
  const now=new Date();$('entry-title').textContent=candidate?'编辑扫描记录':entry?'编辑支出':'记一笔支出';
  $('entry-id').value=entry?.id||'e_'+(crypto.randomUUID?crypto.randomUUID():Date.now()+'_'+Math.random().toString(36).slice(2));
  $('amount').value=entry?(entry.cents/100).toFixed(2):'';$('date').value=entry?.date||date;$('time').value=entry?.time||C.pad(now.getHours())+':'+C.pad(now.getMinutes());
  $('channel').value=entry?.channel||'wechat';$('category').value=entry?.category||'餐饮';$('note').value=entry?.note||'';$('entry-form').querySelector('[type=submit]').textContent=candidate?'保存修改':'保存支出';$('entry-dialog').showModal();
}
function updateDuplicateReview(force=false){
  $('force-duplicate').checked=false;$('duplicate-review').hidden=true;
}
['amount','date','time','channel','note'].forEach(id=>$(id).addEventListener('input',()=>updateDuplicateReview()));
function openDay(date){selectedDate=date;const entries=state.entries.filter(e=>e.date===date).sort((a,b)=>b.time.localeCompare(a.time));$('day-title').textContent=Number(date.slice(5,7))+'月'+Number(date.slice(8))+'日 · 消费明细';$('day-summary').innerHTML=`<span class="subtle">当日总花销 · ${entries.length} 笔</span><div class="day-total">¥${fmt(C.sum(entries))}</div>${channelStrip(entries)}`;$('day-content').innerHTML=`${entries.length?entries.map(e=>transactionHTML(e,true)).join(''):emptyHTML('这一天还没有消费记录',false)}<button class="primary wide" style="margin-top:20px" data-add-day="${date}">＋ 为这一天记一笔</button>`;$('day-content').scrollTop=0;if(!$('day-dialog').open)$('day-dialog').showModal();}
function openWeek(from){const w=C.weeks(C.monthEntries(state.entries,month),month).find(e=>e.from===from);if(!w)return;const entries=state.entries.filter(e=>e.date>=w.from&&e.date<=w.to).sort((a,b)=>(b.date+b.time).localeCompare(a.date+a.time));$('day-title').textContent=`${w.label} · ${w.range}`;$('day-summary').innerHTML=`<span class="subtle">本周在所选月内的支出 · ${entries.length} 笔</span><div class="day-total">¥${fmt(w.cents)}</div>`;$('day-content').innerHTML=`${entries.length?entries.map(e=>transactionHTML(e)).join(''):emptyHTML('本周没有消费记录',false)}`;$('day-content').scrollTop=0;$('day-dialog').showModal();}
function exportBackup(){if(storageError){toast(storageError);return;}if(native){AndroidLedger.exportBackup();return;}const blob=new Blob([JSON.stringify({version:1,entries:state.entries,memos:state.memos},null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='日常账本-'+C.dateKey(new Date())+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),3000);}
function prepareImport(text){try{const entries=C.parseBackup(text),memos=C.parseBackupMemos(text),added=entries.filter(e=>!state.entries.some(old=>old.id===e.id)).length;ask('导入账本备份',`备份含 ${entries.length} 笔，其中 ${added} 笔为新记录。另有 ${memos.length} 条备忘录。已有记录不会被覆盖。`,()=>{transact('importBundle',{entries,memos});render();toast(`已导入 ${added} 笔记录`);});}catch(e){toast('导入失败：'+e.message);}}
window.receiveBackup=prepareImport;
window.nativeNotice=toast;
window.refreshLedger=()=>{refresh();render();if($('day-dialog').open){$('day-dialog').close();}};
window.openScanQueue=()=>{page='settings';refresh();render();$('scan-queue')?.scrollIntoView({behavior:'smooth'});return true;};
document.addEventListener('click',e=>{
  const b=e.target.closest('button');if(!b)return;if(Date.now()<suppressMemoClick)return;
  try{
    if(b.dataset.page){page=b.dataset.page;refresh();render();window.scrollTo(0,0);}
    if(b.dataset.shift){const d=C.parseDate(month+'-01');d.setMonth(d.getMonth()+Number(b.dataset.shift));const next=C.dateKey(d).slice(0,7);if(next>='2000-01'&&next<='2099-12'){month=next;render();}}
    if(b.dataset.mode){chartMode=b.dataset.mode;render();$('spending-chart').scrollIntoView({block:'start'});}
    if(b.dataset.month){month=b.dataset.month;chartMode='week';render();$('spending-chart').scrollIntoView({block:'start'});}
    if(b.dataset.memoView){memoView=b.dataset.memoView;render();}
    if(b.hasAttribute('data-new-memo'))openMemo();
    if(b.hasAttribute('data-slot'))chooseMemoSlot(Number(b.dataset.slot));
    if(b.dataset.pickMemo){moveTask(b.dataset.pickMemo,pendingSlot);$('memo-picker').close();}
    if(b.dataset.toggleMemo){const m=state.memos.find(m=>m.id===b.dataset.toggleMemo);if(m){transact('saveMemo',{...m,done:!m.done,completedAt:m.done?0:Date.now(),slot:-1});render();}}

    if(b.dataset.editMemo)openMemo(b.dataset.editMemo);
    if(b.dataset.deleteMemo)ask('删除这条备忘录？','删除后无法恢复。',()=>{transact('deleteMemo',{id:b.dataset.deleteMemo});render();toast('已删除备忘录');});
    if(b.dataset.week)openWeek(b.dataset.week);
    if(b.dataset.date)openDay(b.dataset.date);
    if(b.hasAttribute('data-add')||b.id==='add-top')openEntry(null,C.dateKey(new Date()));
    if(b.dataset.addDay)openEntry(null,b.dataset.addDay);
    if(b.dataset.close)$(b.dataset.close).close();
    if(b.dataset.edit)openEntry(state.entries.find(en=>en.id===b.dataset.edit));
    if(b.dataset.delete)ask('删除这笔支出？','删除后，该笔金额会从日、周、月统计中扣除。',()=>{transact('delete',{id:b.dataset.delete});render();openDay(selectedDate);toast('已删除支出');});
    if(b.dataset.dismiss){transact('dismiss',{id:b.dataset.dismiss});render();toast('已忽略');}
    if(b.dataset.editScan){const p=state.pending.find(p=>p.id===b.dataset.editScan);if(p)openEntry(p,p.date,p.id);}
    if(b.dataset.accept){const p=state.pending.find(p=>p.id===b.dataset.accept);if(p){const force=b.closest('.pending-card').querySelector('[data-force-id]')?.checked||false;try{transact('confirm',{id:p.id,entry:C.validateEntry(p),forceDuplicate:force});render();toast('已记录');}catch(error){render();toast(error.message);}}}
  }catch(error){toast(error.message);}
});
$('entry-form').addEventListener('submit',e=>{e.preventDefault();try{
  const previous=state.entries.find(en=>en.id===$('entry-id').value);
  const entry=C.validateEntry({id:$('entry-id').value,cents:C.toCents($('amount').value),date:$('date').value,time:$('time').value,channel:$('channel').value,category:$('category').value,note:$('note').value.trim(),source:candidateEditing?'scan':previous?.source||'manual',...(editingSourceKey?{sourceKey:editingSourceKey}:{})});
  if(candidateEditing)transact('editCandidate',{id:candidateEditing,entry});else transact('upsert',entry);
  $('entry-dialog').close();month=entry.date.slice(0,7);render();if($('day-dialog').open)openDay(selectedDate);toast(candidateEditing?'已保存修改，请核对入账':'已保存支出');
}catch(error){$('form-error').textContent=error.message;if(candidateEditing&&(error.code==='possible_duplicate'||error.message.includes('疑似重复')))updateDuplicateReview(true);}});
$('confirm-cancel').addEventListener('click',()=>$('confirm-dialog').close());
$('confirm-ok').addEventListener('click',()=>{const action=confirmAction;confirmAction=null;$('confirm-dialog').close();try{action?.();}catch(error){toast(error.message);}});
document.addEventListener('visibilitychange',()=>{if(!document.hidden){refresh();render();}});
refresh();render();

$('memo-form').addEventListener('submit',e=>{e.preventDefault();try{const old=state.memos.find(m=>m.id===$('memo-id').value);transact('saveMemo',{id:$('memo-id').value,title:'',body:$('memo-body').value.trim(),done:old?.done||false,completedAt:old?.completedAt||0,slot:pendingSlot,mark:$('memo-mark').value.trim()});$('memo-dialog').close();render();toast('事项已保存');}catch(error){$('memo-error').textContent=error.message;}});
$('memo-unassign').addEventListener('click',()=>{pendingSlot=-1;$('memo-position').textContent='保存后移出象限';$('memo-unassign').hidden=true;});
$('memo-remove').addEventListener('click',()=>{const id=$('memo-id').value;ask('删除这件事项？','删除后无法恢复。',()=>{transact('deleteMemo',{id});$('memo-dialog').close();render();toast('已删除事项');});});
$('picker-new').addEventListener('click',()=>{$('memo-picker').close();openMemo(undefined,pendingSlot);});
let dayBackdrop=false;
const outsideDay=e=>{const r=$('day-dialog').getBoundingClientRect();return e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom;};
$('day-dialog').addEventListener('pointerdown',e=>{dayBackdrop=e.target===$('day-dialog')&&outsideDay(e);});
$('day-dialog').addEventListener('click',e=>{if(dayBackdrop&&e.target===$('day-dialog')&&outsideDay(e))$('day-dialog').close();dayBackdrop=false;});

let memoDrag=null,suppressMemoClick=0;
document.addEventListener('pointerdown',e=>{const h=e.target.closest('[data-drag-memo]');if(!h||e.button!==0)return;memoDrag={id:h.dataset.dragMemo,x:e.clientX,y:e.clientY,handle:h,pointer:e.pointerId,moved:false};h.setPointerCapture(e.pointerId);});
document.addEventListener('pointermove',e=>{if(!memoDrag||memoDrag.pointer!==e.pointerId)return;const d=memoDrag;if(!d.moved&&Math.hypot(e.clientX-d.x,e.clientY-d.y)<7)return;d.moved=true;e.preventDefault();if(!d.ghost){d.ghost=document.createElement('div');d.ghost.className='memo-drag-ghost';d.ghost.textContent=memoMark(state.memos.find(m=>m.id===d.id));document.body.appendChild(d.ghost);}d.ghost.style.left=e.clientX+'px';d.ghost.style.top=e.clientY+'px';document.querySelectorAll('.drop-target').forEach(x=>x.classList.remove('drop-target'));const target=document.elementFromPoint(e.clientX,e.clientY)?.closest('[data-slot]');target?.classList.add('drop-target');});
function endMemoDrag(e,cancel=false){if(!memoDrag||memoDrag.pointer!==e.pointerId)return;const d=memoDrag;memoDrag=null;d.ghost?.remove();document.querySelectorAll('.drop-target').forEach(x=>x.classList.remove('drop-target'));if(d.handle.hasPointerCapture(e.pointerId))d.handle.releasePointerCapture(e.pointerId);if(!d.moved)return;suppressMemoClick=Date.now()+400;if(cancel)return;const target=document.elementFromPoint(e.clientX,e.clientY)?.closest('[data-slot]');if(target)try{moveTask(d.id,Number(target.dataset.slot));}catch(error){toast(error.message);}}
document.addEventListener('pointerup',e=>endMemoDrag(e));document.addEventListener('pointercancel',e=>endMemoDrag(e,true));
