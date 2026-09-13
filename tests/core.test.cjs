const test=require('node:test');
const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/core.js');
const entry=(id,date,cents)=>({id,date,cents,time:'12:30',channel:'wechat',category:'餐饮',note:'午餐',source:'manual'});
test('decimal money is converted to integer cents without floating point loss',()=>{
  assert.equal(C.toCents('0.29'),29);assert.equal(C.toCents('12.3'),1230);
  assert.equal(C.toCents('9999999.99'),999999999);
  for(const value of ['0','-2','1.001','1e3','NaN','','10000000','12.3abc'])assert.throws(()=>C.toCents(value));
  assert.equal(C.sum([entry('a','2026-09-01',10),entry('b','2026-09-01',20)]),30);
});
test('dates reject impossible days and accept leap years',()=>{
  assert.equal(C.validateEntry(entry('a','2024-02-29',100)).date,'2024-02-29');
  for(const value of ['2025-02-29','2026-04-31','2026-13-01','1999-12-31'])assert.throws(()=>C.validateEntry(entry('a',value,100)));
});
test('Monday weeks split at month edges, all transactions counted exactly once',()=>{
  const entries=[entry('a','2026-08-31',999),entry('b','2026-09-01',101),entry('c','2026-09-06',202),entry('d','2026-09-07',303),entry('e','2026-09-30',404),entry('f','2026-10-01',999)];
  const result=C.weeks(entries,'2026-09');
  assert.equal(result.length,5);assert.equal(result[0].to,'2026-09-06');assert.equal(result[1].from,'2026-09-07');assert.equal(result[4].to,'2026-09-30');
  assert.deepEqual(result.map(w=>w.cents),[303,303,0,0,404]);
  assert.equal(C.sum(result),C.sum(C.monthEntries(entries,'2026-09')));
});
test('Sunday start produces partial first week and leap-month length is correct',()=>{
  const result=C.weeks([],'2026-03');assert.equal(result.length,6);assert.equal(result[0].from,result[0].to);assert.equal(result[0].to,'2026-03-01');
  assert.equal(C.monthDays('2024-02'),29);assert.equal(C.monthDays('2025-02'),28);
});
test('annual totals do not mix years',()=>{
  const result=C.annual([entry('a','2025-12-31',100),entry('b','2026-01-01',200),entry('c','2026-12-31',300)],'2026');
  assert.equal(result.length,12);assert.equal(result[0].cents,200);assert.equal(result[11].cents,300);assert.equal(C.sum(result),500);
});
test('backup validation rejects duplicated IDs and malformed money',()=>{
  const e=entry('test','2026-09-09',1234);
  assert.deepEqual(C.parseBackup(JSON.stringify({version:1,entries:[e]})),[e]);
  assert.throws(()=>C.parseBackup(JSON.stringify({version:1,entries:[e,e]})));
  assert.throws(()=>C.parseBackup(JSON.stringify({version:1,entries:[{...e,cents:1.23}]})));
  assert.throws(()=>C.parseBackup(JSON.stringify({version:2,entries:[]})));
  assert.throws(()=>C.parseBackup(JSON.stringify({version:1,entries:[{...e,channel:'fake'}]})));
});
test('scan identity survives validation and backup, while legacy booked entries remain compatible',()=>{
  const scanned={...entry('scan_1','2026-09-09',345),source:'scan',sourceKey:'a'.repeat(64)};
  assert.deepEqual(C.validateEntry(scanned),scanned);
  assert.deepEqual(C.parseBackup(JSON.stringify({version:1,entries:[scanned]})),[scanned]);
  const legacy={...entry('old_notification','2026-09-08',1234),source:'notification'};
  assert.deepEqual(C.validateEntry(legacy),legacy);
  assert.throws(()=>C.validateEntry({...scanned,sourceKey:'bad'}));
});

test('memo backup retains multiline text and accepts old expense-only backups',()=>{
  const m={id:'m_test',title:'购物清单',body:'牛奶\n面包'};
  assert.deepEqual(C.parseBackupMemos(JSON.stringify({version:1,entries:[],memos:[m]})),[C.validateMemo(m)]);
  assert.deepEqual(C.parseBackupMemos(JSON.stringify({version:1,entries:[]})),[]);
  assert.throws(()=>C.validateMemo({id:'m',title:' ',body:' '}));
  assert.throws(()=>C.parseBackupMemos(JSON.stringify({memos:[m,m]})));
  assert.throws(()=>C.validateMemo({...m,body:'a'.repeat(10102)}));
});

test('quadrants support 36 distinct slots, reject collisions, and completion releases slots',()=>{
 const tasks=Array.from({length:36},(_,i)=>C.validateMemo({id:'m_'+i,title:'',body:'事项'+i,slot:i}));
 assert.equal(new Set(tasks.map(m=>m.slot)).size,36);
 assert.throws(()=>C.moveMemo(tasks,'m_0',9));
 const moved=C.moveMemo(C.moveMemo(tasks,'m_9',-1),'m_0',9);
 assert.equal(moved[0].slot,9);assert.equal(tasks[0].slot,0);
 assert.equal(C.validateMemo({...tasks[0],done:true}).slot,-1);
 assert.throws(()=>C.moveMemo([{...tasks[0],done:true}],'m_0',1));
 assert.throws(()=>C.moveMemo(tasks,'m_0',36));
 const legacy=C.validateMemo({id:'old',title:'原标题',body:'原内容\n第二行'});
 assert.equal(C.memoText(legacy),'原标题\n原内容\n第二行');assert.equal(legacy.done,false);assert.equal(legacy.slot,-1);
 const m=C.validateMemo({...legacy,mark:'药',slot:22});assert.deepEqual(C.parseBackupMemos(JSON.stringify({memos:[m]})),[m]);
});
