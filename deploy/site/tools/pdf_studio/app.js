(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const text = {
    zh: {title:'PDF 工作台',operation:'操作',merge:'合并 PDF',extract:'提取 / 拆分页面',rotate:'旋转页面',images:'图片转 PDF',choose:'选择文件',limit:'最多 10 个文件，单个 8 MB，合计 20 MB，PDF 最多 200 页，输出最多 12 MB。JPG/PNG 单图最多 1200 万像素。',range:'页码范围（留空为全部）',rangeHelp:'提取按填写顺序输出，可重复页码；旋转保留所有页面，仅旋转所选页码。',angle:'顺时针旋转',paper:'页面大小',imageSize:'按图片大小（96 DPI）',run:'处理',cancel:'取消',clear:'清空',save:'保存 PDF',scope:'处理范围',scopeText:'处理发生在当前页面，文件不会上传。加密 PDF、电子签名保留、交互表单、书签、附件及 OCR 不支持；页面注释可能保留。图片保留像素方向，不自动应用 EXIF 旋转。修改后的 PDF 不保留数字签名有效性。拆分通过提取指定页码生成一份 PDF。',ready:'选择文件后处理。可用箭头调整文件顺序。',busy:'正在处理…',done:'已生成 {pages} 页 PDF，{size} KB。',cancelled:'已取消，可重新处理。',timeout:'处理超时，请减少文件或页面后重试。',files:'合并需选择 2–10 个 PDF；提取和旋转只选择 1 个，图片最多 10 张。',size:'文件大小超出限制。',rangeError:'页码范围无效、超过源文件页数或超过 200 页。',pages:'PDF 页数超出 1–200 页限制。',image:'图片损坏、尺寸超限或格式不支持，请使用静态 JPG/PNG。',pdf:'PDF 无法解析，请检查文件。',encrypted:'不支持加密 PDF，请先使用原文件密码解除加密。',output:'结果超过 12 MB，请减少文件或页面。',processing:'处理失败，请检查文件并重试。',up:'上移',down:'下移',remove:'删除',unsupported:'浏览器不支持后台文件处理，请更新浏览器。'},
    en: {title:'PDF Studio',operation:'Operation',merge:'Merge PDFs',extract:'Extract / split pages',rotate:'Rotate pages',images:'Images to PDF',choose:'Choose files',limit:'Up to 10 files, 8 MB each, 20 MB total, 200 PDF pages, 12 MB output. JPG/PNG images: up to 12 megapixels each.',range:'Page range (blank means all)',rangeHelp:'Extraction follows your order and allows repeated pages. Rotation keeps all pages and rotates only the selected pages.',angle:'Clockwise rotation',paper:'Page size',imageSize:'Image size (96 DPI)',run:'Process',cancel:'Cancel',clear:'Clear',save:'Save PDF',scope:'Processing scope',scopeText:'Files are processed in this page and are not uploaded. Encrypted PDFs, signature preservation, interactive forms, bookmarks, attachments and OCR are unsupported; page annotations may remain. Images keep pixel orientation; EXIF rotation is not applied. Modified PDFs do not retain valid digital signatures. Splitting extracts selected pages into one PDF.',ready:'Choose files to begin. Use the arrows to arrange their order.',busy:'Processing…',done:'Created {pages} pages, {size} KB.',cancelled:'Cancelled. You can try again.',timeout:'Timed out. Try fewer files or pages.',files:'Merge needs 2–10 PDFs. Extraction and rotation need exactly one. Up to 10 images.',size:'Files exceed the size limits.',rangeError:'Invalid page range, page outside the source, or more than 200 selected pages.',pages:'PDF must contain 1–200 pages in total.',image:'Invalid, oversized or unsupported image. Use a static JPG/PNG.',pdf:'Unable to read PDF. Check the file.',encrypted:'Encrypted PDFs are unsupported. Decrypt with the original password first.',output:'Output exceeds 12 MB. Try fewer files or pages.',processing:'Processing failed. Check the files and retry.',up:'Move up',down:'Move down',remove:'Remove',unsupported:'This browser does not support background file processing. Update your browser.'}
  };
  let language = /^en\b/i.test(navigator.language || '') ? 'en' : 'zh';
  let files = [], worker = null, timer = null, result = null, epoch = 0, busy = false, statusKey = 'ready', statusArgs = {}, statusError = false;
  function t(key) { return text[language][key] || text[language].processing; }
  function status(key, args = {}, error = false) {
    statusKey=key; statusArgs=args; statusError=error;
    $('status').textContent=t(key).replace(/\{(\w+)\}/g, (_, name) => args[name]);
    $('status').dataset.error=String(error);
  }
  function discard() { result=null; $('save').disabled=true; }
  function stop() { epoch++; if(worker) worker.terminate(); worker=null; clearTimeout(timer); timer=null; busy=false; controls(); }
  function controls() {
    for(const id of ['mode','files','range','rotation','paper','run']) $(id).disabled=busy;
    $('cancel').hidden=!busy; renderFiles();
  }
  function renderFiles() {
    $('fileList').replaceChildren();
    files.forEach((file,index) => {
      const li=document.createElement('li'); li.append(document.createTextNode(file.name+' ('+Math.ceil(file.size/1000)+' KB)'));
      [['up',-1],['down',1],['remove',0]].forEach(([key,offset]) => {
        const button=document.createElement('button');button.type='button';button.className='secondary';button.textContent=t(key);button.disabled=busy || offset && (index+offset<0 || index+offset>=files.length);
        button.onclick=()=>{discard(); if(!offset) files.splice(index,1);else [files[index],files[index+offset]]=[files[index+offset],files[index]];renderFiles();status('ready');};li.append(button);
      }); $('fileList').append(li);
    });
  }
  function modeChanged() {
    discard(); files=[]; $('files').value=''; renderFiles();
    const mode=$('mode').value; $('files').accept=mode==='images'?'image/jpeg,image/png,.jpg,.jpeg,.png':'application/pdf,.pdf';
    $('files').multiple=mode==='merge'||mode==='images';
    $('rangeOptions').hidden=!(mode==='extract'||mode==='rotate');$('rotationOptions').hidden=mode!=='rotate';$('paperOptions').hidden=mode!=='images';status('ready');
  }
  $('language').value=language;
  function translate() {
    document.documentElement.lang=language==='zh'?'zh-CN':'en';document.title=t('title')+' · 沧烁工具箱';
    document.querySelectorAll('[data-i18n]').forEach(el=>el.textContent=t(el.dataset.i18n));renderFiles();status(statusKey,statusArgs,statusError);
  }
  $('language').onchange=()=>{language=$('language').value;translate();};
  $('mode').onchange=modeChanged;
  $('files').onchange=()=>{discard();files=Array.from($('files').files);renderFiles();status('ready');};
  for(const id of ['range','rotation','paper']) $(id).oninput=()=>{discard();status('ready');};
  $('clear').onclick=()=>{stop();modeChanged();$('range').value='';};
  $('cancel').onclick=()=>{stop();discard();status('cancelled');};
  $('run').onclick=async()=>{
    if(busy)return;discard();const mode=$('mode').value,limits=ToolboxPdf.limits;
    if(!files.length||files.length>limits.files||(mode==='merge'&&files.length<2)||(['extract','rotate'].includes(mode)&&files.length!==1))return status('files',{},true);
    if(files.some(f=>!f.size||f.size>limits.fileBytes)||files.reduce((sum,f)=>sum+f.size,0)>limits.totalBytes)return status('size',{},true);
    if(typeof Worker==='undefined')return status('unsupported',{},true);
    busy=true;controls();status('busy');const token=++epoch;
    try {
      worker=new Worker('worker.js');
      timer=setTimeout(()=>{if(token===epoch){stop();discard();status('timeout',{},true);}},30000);
      worker.onerror=()=>{if(token===epoch){stop();discard();status('processing',{},true);}};
      worker.onmessage=event=>{
        if(token!==epoch)return;
        const value=event.data;stop();
        if(value.error)return status(value.error==='range'?'rangeError':value.error,{},true);
        result=value.result;$('save').disabled=false;status('done',{pages:result.pages,size:Math.ceil(result.bytes.length/1000)});
      };
      const inputs=[];
      for(const file of files){const bytes=new Uint8Array(await file.arrayBuffer());if(token!==epoch)return;inputs.push({bytes});}
      worker.postMessage({files:inputs,mode,range:$('range').value,rotation:Number($('rotation').value),paper:$('paper').value},inputs.map(f=>f.bytes.buffer));
    } catch(_){if(token===epoch){stop();discard();status('processing',{},true);}}
  };
  $('save').onclick=()=>{
    if(!result)return;const blob=new Blob([result.bytes],{type:'application/pdf'});
    if((navigator.userAgent||'').includes('CangshuoToolboxWebView/1')){
      const reader=new FileReader();reader.onload=()=>download(reader.result);reader.onerror=()=>status('processing',{},true);reader.readAsDataURL(blob);
    } else {const url=URL.createObjectURL(blob);download(url);setTimeout(()=>URL.revokeObjectURL(url),4000);}
  };
  function download(url){const link=document.createElement('a');link.href=url;link.download='toolbox.pdf';document.body.append(link);link.click();link.remove();}
  window.addEventListener('pagehide',()=>{stop();discard();});translate();modeChanged();
})();
