'use strict';
// Presentation only: no ledger reads or mutations.
window.LedgerUI=(()=>{
 const reduced=()=>document.documentElement.classList.contains('reduce-motion')||matchMedia('(prefers-reduced-motion: reduce)').matches;
 const attr=s=>String(s).replace(/[&"<>]/g,c=>({'&':'&amp;','"':'&quot;','<':'&lt;','>':'&gt;'}[c]));
 function icon(name){return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${(LedgerIcons[name]||[]).map(([tag,props])=>`<${tag} ${Object.entries(props).filter(([k])=>k!=='key').map(([k,v])=>`${k}="${attr(v)}"`).join(' ')}></${tag}>`).join('')}</svg>`;}
 function enhance(root=document){root.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML=icon(el.dataset.icon));}
 let opening=false;
 function show(dialog,anchor=null){
  if(dialog.open)return;
  dialog._motionToken=(dialog._motionToken||0)+1;
  dialog.classList.remove('is-closing');
  if(anchor&&document.startViewTransition&&!reduced()&&!document.querySelector('dialog[open]')&&!opening){opening=true;anchor.style.viewTransitionName='detail-focus';const t=document.startViewTransition(()=>{anchor.style.viewTransitionName='none';dialog.style.viewTransitionName='detail-focus';dialog.showModal();});t.ready.catch(()=>{});t.finished.catch(()=>{}).finally(()=>{dialog.style.viewTransitionName='none';opening=false;});}else dialog.showModal();
 }
 function close(dialog){if(!dialog.open||dialog.classList.contains('is-closing'))return;if(reduced()){dialog.close();return;}const token=dialog._motionToken=(dialog._motionToken||0)+1;dialog.classList.add('is-closing');setTimeout(()=>{if(dialog._motionToken!==token)return;if(dialog.open)dialog.close();dialog.classList.remove('is-closing');},170);}
 function beforeRender(root){if(reduced()||!root.animate)return null;return {page:root.dataset.currentPage,rows:new Map([...root.querySelectorAll('[data-motion-id]')].map(el=>[el.dataset.motionId,el.getBoundingClientRect()]))};}
 function afterRender(root,before,page){root.dataset.currentPage=page;if(!before||reduced()||!root.animate)return;if(before.page&&before.page!==page){root.animate([{opacity:.4},{opacity:1}],{duration:220,easing:'cubic-bezier(.16,1,.3,1)'});return;}for(const el of root.querySelectorAll('[data-motion-id]')){const old=before.rows.get(el.dataset.motionId);if(!old)continue;const delta=old.top-el.getBoundingClientRect().top;if(Math.abs(delta)>1&&Math.abs(delta)<900)el.animate([{transform:`translateY(${delta}px)`},{transform:'translateY(0)'}],{duration:250,easing:'cubic-bezier(.16,1,.3,1)'});}}
 return {icon,enhance,reduced,show,close,beforeRender,afterRender};
})();
