// Run with PLAYWRIGHT_MODULE set to an installed Playwright; uses local Chrome.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const assets=path.resolve(__dirname,'../app/src/main/assets');
(async()=>{
 const server=http.createServer((req,res)=>{const name=req.url==='/'?'index.html':req.url.slice(1);if(!['index.html','style.css','core.js','icons.js','ui.js','app.js'].includes(name)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':'text/html; charset=utf-8');res.end(fs.readFileSync(path.join(assets,name)));});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
 try{
  browser=await chromium.launch({channel:'chrome',headless:true});
  for(const width of [390,320]){
   const page=await browser.newPage({viewport:{width,height:900},isMobile:true,hasTouch:true}),errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.addInitScript(()=>{const month=new Date().toISOString().slice(0,7);localStorage.setItem('daily-ledger-v1',JSON.stringify({entries:Array.from({length:12},(_,i)=>({id:'chart_fixture_'+i,cents:1000+i*327,date:month+'-'+String(i+1).padStart(2,'0'),time:'12:10',channel:i%2?'alipay':'wechat',category:'餐饮',note:'模拟消费'})),pending:[],memos:[]}));});
   await page.goto(`http://127.0.0.1:${server.address().port}`);await page.waitForTimeout(450);
   const fold=page.locator('[data-month-summary]');assert.equal(await fold.getAttribute('aria-expanded'),'false');assert.equal(await page.locator('#month-summary-rows button').count(),12);assert.equal(await page.locator('#month-summary-rows').evaluate(e=>e.getBoundingClientRect().height),0);
   await page.evaluate(()=>{window.originalCalendar=document.querySelector('.calendar');});
   const snapshot=()=>page.evaluate(()=>{const r=document.querySelector('#spending-chart').getBoundingClientRect(),b=document.querySelector('[data-mode="month"]').getBoundingClientRect();return {y:scrollY,top:r.top,height:r.height,buttonTop:b.top,docHeight:document.documentElement.scrollHeight};});
   async function switchStable(mode){const before=await snapshot();await page.locator(`[data-mode="${mode}"]`).tap();await page.waitForTimeout(450);const after=await snapshot();for(const key of Object.keys(before))assert.ok(Math.abs(before[key]-after[key])<=1,`${width}/${mode}: ${key} moved ${before[key]} -> ${after[key]}`);assert.ok(await page.evaluate(()=>originalCalendar===document.querySelector('.calendar')),'calendar must not be rebuilt');}
   // Test at a middle scroll offset and near the bottom where a shorter document
   // used to clamp the viewport upward. Tap real visible controls, not JS click.
   for(const offset of [120,360]){
    await page.evaluate(y=>window.scrollTo(0,document.querySelector('#spending-chart').offsetTop-y),offset);
    await switchStable('month');await switchStable('week');await switchStable('month');
   }
   if(process.env.CHART_SCREENSHOTS){fs.mkdirSync(process.env.CHART_SCREENSHOTS,{recursive:true});await page.evaluate(()=>window.scrollTo(0,document.querySelector('#spending-chart').offsetTop-120));await page.screenshot({path:path.join(process.env.CHART_SCREENSHOTS,`month-closed-${width}.png`)});}
   await fold.tap();await page.waitForTimeout(350);assert.equal(await fold.getAttribute('aria-expanded'),'true');assert.ok(await page.locator('#month-summary-rows').evaluate(e=>e.getBoundingClientRect().height)>500);
   await page.evaluate(()=>window.scrollTo(0,document.querySelector('#spending-chart').offsetTop-120));await switchStable('week');await switchStable('month');assert.equal(await fold.getAttribute('aria-expanded'),'true');
   if(process.env.CHART_SCREENSHOTS){await page.evaluate(()=>window.scrollTo(0,document.querySelector('#month-summary').offsetTop-100));await page.screenshot({path:path.join(process.env.CHART_SCREENSHOTS,`month-open-${width}.png`)});}
   await fold.tap();await page.waitForTimeout(350);assert.equal(await fold.getAttribute('aria-expanded'),'false');assert.equal(await page.locator('#month-summary-rows').evaluate(e=>e.getBoundingClientRect().height),0);assert.ok(await page.locator('#month-summary-rows button').evaluateAll(es=>es.every(e=>e.tabIndex===-1)));
   assert.deepEqual(errors,[]);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   console.log(`PASS ${width}: physical week/month taps preserve scroll, panel/control geometry and document height; 12 months default collapsed, expand/collapse and keep state, hidden rows unfocusable, no overflow/errors`);await page.close();
  }
 }finally{if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});
