// Run with PLAYWRIGHT_MODULE pointing to an installed Playwright module; uses Chrome.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const assets=path.resolve(__dirname,'../app/src/main/assets');
(async()=>{
 const server=http.createServer((req,res)=>{
  const file=({'/':'index.html','/app.js':'app.js','/core.js':'core.js','/style.css':'style.css'})[req.url];
  if(!file){res.writeHead(404);return res.end();}
  res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html; charset=utf-8');res.end(fs.readFileSync(path.join(assets,file)));
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
 try{
  browser=await chromium.launch({channel:'chrome',headless:true});
  for(const width of [390,320]){
   const context=await browser.newContext({viewport:{width,height:900},isMobile:true,hasTouch:true});
   await context.addInitScript(()=>{
    localStorage.setItem('daily-ledger-v1',JSON.stringify({entries:[],pending:[],memos:Array.from({length:45},(_,i)=>({id:'task_'+i,title:'',body:'待办 '+i+'：准备下周事项并检查清单',done:false,slot:-1,mark:'事',completedAt:0}))}));
   });
   const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.goto(`http://127.0.0.1:${server.address().port}`);
   await page.locator('[data-page="memos"]').click();await page.locator('[data-memo-view="matrix"]').click();
   await page.evaluate(()=>{window.scrollTo(0,120);document.querySelector('.task-tray').scrollTop=680;});
   const client=await context.newCDPSession(page);
   const snapshot=()=>page.evaluate(()=>({top:document.querySelector('.task-tray').scrollTop,y:scrollY,rows:[...document.querySelectorAll('.task-tray .todo-row')].map(r=>({id:r.querySelector('[data-edit-memo]').dataset.editMemo,top:r.getBoundingClientRect().top,height:r.getBoundingClientRect().height}))}));
   const visible=()=>page.evaluate(()=>{const t=document.querySelector('.task-tray').getBoundingClientRect();return [...document.querySelectorAll('.task-tray [data-drag-memo]')].filter(e=>{const b=e.getBoundingClientRect();return b.top>=t.top+5&&b.bottom<=Math.min(t.bottom,innerHeight-85);}).map(e=>e.dataset.dragMemo);});
   const point=async selector=>{const b=await page.locator(selector).boundingBox();assert.ok(b);return {x:b.x+b.width/2,y:b.y+b.height/2};};
   async function drag(from,to,touch=false,cancel=false){
    const a=await point(from),b=typeof to==='string'?await point(to):to;
    if(touch){
     await client.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[a]});
     for(let i=1;i<=8;i++)await client.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:a.x+(b.x-a.x)*i/8,y:a.y+(b.y-a.y)*i/8}]});
     await client.send('Input.dispatchTouchEvent',{type:cancel?'touchCancel':'touchEnd',touchPoints:[]});
    }else{await page.mouse.move(a.x,a.y);await page.mouse.down();await page.mouse.move(b.x,b.y,{steps:8});await page.mouse.up();}
    await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
   }
   async function stable(before,label){
    const after=await snapshot();assert.equal(after.top,before.top,label+': tray scroll');assert.equal(after.y,before.y,label+': page scroll');assert.deepEqual(after.rows,before.rows,label+': same order and geometry');
    assert.equal(await page.locator('.memo-drag-ghost,.drop-target').count(),0);
   }
   const ids=await visible();assert.ok(ids.length>=2,'need two visible rows for consecutive drags');
   let before=await snapshot();await drag(`.task-tray [data-drag-memo="${ids[0]}"]`,'[data-slot="18"]');await stable(before,'first drop');
   assert.equal(await page.locator('[data-slot="18"]').getAttribute('data-drag-memo'),ids[0]);
   before=await snapshot();await drag(`.task-tray [data-drag-memo="${ids[1]}"]`,'[data-slot="27"]',true);await stable(before,'second touch drop');
   assert.equal(await page.locator('[data-slot="27"]').getAttribute('data-drag-memo'),ids[1]);
   assert.equal(await page.locator('.task-tray .todo-tag.q3').textContent(),'不重要不紧急');
   before=await snapshot();await drag('[data-slot="18"]','[data-slot="19"]',true);await stable(before,'reassignment');
   assert.equal(await page.locator('[data-slot="19"]').getAttribute('data-drag-memo'),ids[0]);
   before=await snapshot();await drag('[data-slot="19"]','[data-slot="27"]',true);await stable(before,'occupied cell');
   assert.equal(await page.locator('[data-slot="19"]').getAttribute('data-drag-memo'),ids[0]);
   before=await snapshot();await drag('[data-slot="19"]','[data-slot="20"]',true,true);await stable(before,'cancelled touch');
   assert.equal(await page.locator('[data-slot="20"]').getAttribute('data-drag-memo'),null);
   before=await snapshot();await drag('[data-slot="19"]',{x:3,y:100},true);await stable(before,'drop outside');
   await page.evaluate(()=>refreshLedger());await stable(before,'native refresh');
   await page.evaluate(()=>moveTask(document.querySelector('[data-slot="19"]').dataset.dragMemo,-1));await stable(before,'unassign');
   await page.waitForTimeout(450);await page.locator(`[data-toggle-memo="${ids[0]}"]`).click();
   assert.equal(await page.locator('.task-tray .completed [data-edit-memo]').getAttribute('data-edit-memo'),ids[0]);
   assert.equal(await page.locator('.task-tray .todo-row').last().locator('[data-edit-memo]').getAttribute('data-edit-memo'),ids[0]);
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(errors,[]);
   console.log(`PASS ${width}px: consecutive mouse/touch drops, order/geometry/scroll, tags, reassignment, occupied/cancel/outside, refresh, unassign, completion.`);
   await context.close();
  }
 }finally{if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});
