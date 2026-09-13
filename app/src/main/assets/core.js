(function (root) {
  'use strict';
  const pad = n => String(n).padStart(2, '0');
  const dateKey = d => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
  const parseDate = value => new Date(`${value}T12:00:00`);
  const money = cents => (cents / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  function toCents(value) {
    const text = String(value).trim();
    if (!/^(0|[1-9]\d{0,7})(\.\d{1,2})?$/.test(text)) throw new Error('请输入正确的金额，最多两位小数');
    const [yuan, fen=''] = text.split('.');
    const cents = Number(yuan)*100 + Number(fen.padEnd(2,'0'));
    if (cents <= 0 || cents > 999999999) throw new Error('金额须大于 0 且不超过 9,999,999.99 元');
    return cents;
  }
  function validateEntry(e) {
    if (!e || typeof e !== 'object') throw new Error('账单格式不正确');
    if (typeof e.id !== 'string' || !/^[a-zA-Z0-9_-]{1,100}$/.test(e.id)) throw new Error('账单编号不正确');
    if (!Number.isSafeInteger(e.cents) || e.cents <= 0 || e.cents > 999999999) throw new Error('账单金额不正确');
    if (!/^20\d\d-\d{2}-\d{2}$/.test(e.date) || dateKey(parseDate(e.date)) !== e.date) throw new Error('日期不正确');
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(e.time)) throw new Error('时间不正确');
    if (!['wechat','alipay','other'].includes(e.channel)) throw new Error('支付方式不正确');
    if (!['餐饮','购物','交通','生活缴费','娱乐','医疗','其他'].includes(e.category)) throw new Error('分类不正确');
    if (typeof e.note !== 'string' || e.note.length > 80) throw new Error('备注最多 80 字');
    const result={id:e.id,cents:e.cents,date:e.date,time:e.time,channel:e.channel,category:e.category,note:e.note,source:['scan','notification'].includes(e.source)?e.source:'manual'};
    if(e.sourceKey!==undefined){if(typeof e.sourceKey!=='string'||!/^[a-f0-9]{64}$/.test(e.sourceKey))throw new Error('扫描来源编号不正确');result.sourceKey=e.sourceKey;}
    return result;
  }
  const sum = entries => entries.reduce((s,e) => s+e.cents,0);
  const monthEntries = (entries, month) => entries.filter(e => e.date.startsWith(month+'-'));
  function monthDays(month) {
    const first = parseDate(month+'-01');
    return new Date(first.getFullYear(),first.getMonth()+1,0).getDate();
  }
  function weeks(entries, month) {
    const days = monthDays(month), result=[];
    let start=1;
    while (start<=days) {
      const weekday = (parseDate(`${month}-${pad(start)}`).getDay()+6)%7;
      const end = Math.min(days,start+6-weekday);
      const from = `${month}-${pad(start)}`, to = `${month}-${pad(end)}`;
      result.push({label:`第 ${result.length+1} 周`,range:`${start}–${end}日`,from,to,cents:sum(entries.filter(e=>e.date>=from&&e.date<=to))});
      start=end+1;
    }
    return result;
  }
  function annual(entries, year) {
    return Array.from({length:12},(_,i)=>({label:`${i+1}月`,month:`${year}-${pad(i+1)}`,cents:sum(monthEntries(entries,`${year}-${pad(i+1)}`))}));
  }
  function parseBackup(text) {
    const obj = JSON.parse(text);
    if (obj.version!==1 || !Array.isArray(obj.entries) || obj.entries.length>50000) throw new Error('请选择日常账本导出的有效备份');
    const entries=obj.entries.map(validateEntry);
    if(new Set(entries.map(e=>e.id)).size!==entries.length) throw new Error('备份含有重复编号');
    return entries;
  }
  function validateMemo(m){
    if(!m||typeof m.id!=='string'||!/^[a-zA-Z0-9_-]{1,100}$/.test(m.id)||typeof m.title!=='string'||typeof m.body!=='string'||m.title.trim().length>100||m.body.length>10101||(!m.title.trim()&&!m.body.trim()))throw new Error('备忘录需填写内容，标题最多 100 字，正文最多 10101 字');
    const done=m.done??false,slot=m.slot??-1,mark=m.mark??'',completedAt=m.completedAt??0;
    if(!Number.isSafeInteger(completedAt)||completedAt<0||typeof done!=='boolean'||!Number.isInteger(slot)||slot < -1||slot>35||typeof mark!=='string'||Array.from(mark).length>1)throw new Error('事项状态或象限位置无效');
    return {id:m.id,title:m.title.trim(),body:m.body,done,slot:done?-1:slot,mark,completedAt:done?completedAt:0};
  }
  const memoText=m=>[m.title,m.body].filter(Boolean).join('\n');
  function moveMemo(memos,id,slot){
    if(!Number.isInteger(slot)||slot < -1||slot>35)throw new Error('象限位置无效');
    const next=memos.map(validateMemo),m=next.find(m=>m.id===id);if(!m)throw new Error('事项已删除');if(m.done)throw new Error('请先恢复为未完成');
    const occupied=next.find(x=>x.id!==id&&x.slot===slot&&slot>=0);if(occupied)throw new Error('该格已有事项，请选择空格');m.slot=slot;return next;
  }
  function parseBackupMemos(text){const data=JSON.parse(text),memos=data.memos??[];if(!Array.isArray(memos)||memos.length>1000)throw new Error('备忘录备份无效');const result=memos.map(validateMemo);if(new Set(result.map(m=>m.id)).size!==result.length)throw new Error('备忘录编号重复');return result;}
  root.LedgerCore={pad,dateKey,parseDate,money,toCents,validateEntry,sum,monthEntries,monthDays,weeks,annual,parseBackup,validateMemo,parseBackupMemos,memoText,moveMemo};
  if(typeof module!=='undefined') module.exports=root.LedgerCore;
})(typeof window==='undefined'?globalThis:window);
