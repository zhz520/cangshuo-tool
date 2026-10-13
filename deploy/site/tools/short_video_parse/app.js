(() => {
  'use strict';
  const E = window.ShortVideo, $ = id => document.getElementById(id);
  const zh = {
    brand:'沧烁工具箱',eyebrow:'视频 · 图集 · 封面',heading:'把分享链接，变成可用素材。',intro:'提取平台提供的视频与原图链接，在一个页面预览、选择和保存。',inputHeading:'粘贴分享链接',placeholder:'从短视频 App 复制分享链接，或直接粘贴整段分享文案…',inputHelp:'一次解析一条链接，支持整段分享文案',parse:'开始解析',parsing:'正在读取作品…',paste:'粘贴',clear:'清空',cancel:'取消',checking:'连接服务中…',ready:'服务已连接',offline:'当前网络不可用',disabled:'解析服务暂不可用',retry:'重试连接',sourceNotice:'优先提取平台原始播放资源；已写入画面的水印无法移除。私密、付费、直播及验证受限内容不支持。',copyInfo:'复制作品信息',quality:'视频资源',downloadVideo:'下载视频',copyLink:'复制直链',cover:'作品封面',downloadCover:'保存封面',selectAll:'全选',selectNone:'取消选择',downloadZip:'打包所选图片',zipHelp:'ZIP 仅打包静态图片，总大小上限 32 MB。实况视频可单独下载。',cancelDownload:'取消下载',how:'使用方法',howText:'在作品分享菜单复制链接，粘贴到上方解析。视频可切换资源；图集支持逐张保存、预览大图及选择打包。',privacy:'隐私与保存',privacyText:'链接由本站服务端请求对应平台，不保存到数据库。结果仅在服务器内存保留 15 分钟；资源地址可能更早失效。',faq:'为什么有些链接无法解析？',faqText:'作品可能已删除、私密、需要登录或触发平台验证。请重新复制公开作品链接。B 站仅支持可公开读取的单文件视频，不合并 DASH 音视频或分段内容。',footer:'沧烁工具箱 · 网页版工具',licenses:'开源参考与许可',privacyLink:'隐私政策',close:'关闭',previous:'上一张',next:'下一张',saveImage:'保存图片',previewError:'预览失败，可尝试其他清晰度或重新解析链接。',errorTitle:'未完成解析或下载',trace:'请求编号：',untitled:'未提供作品标题',author:'作者',seconds:'秒',album:'图集 · {n} 张',selected:'已选择 {n} 张',image:'图片 {n}',resource:'资源 {n}',original:'平台原始播放资源',unknown:'平台提供的资源，水印状态未确认',dimensions:'{w} × {h}',expired:'结果已过期，请重新解析链接。',expiry:'结果可用至 {time}，关闭或清空页面后不保留本机记录。',download:'下载',live:'实况',saveLive:'保存实况',loadingDownload:'正在下载…',preparingZip:'正在准备图集…',downloadDone:'已发送保存文件请求',downloadCancelled:'下载已取消',copied:'已复制',copyFailed:'复制失败，请长按文本手动复制',pasteFailed:'无法读取剪贴板，请长按输入框粘贴',selectNeeded:'请先选择要打包的图片',browserDownload:'已交给系统浏览器下载',previewFailed:'图片预览失败，可尝试保存或重新解析',badInput:'请输入一条完整分享链接，或包含链接的分享文案。',network:'连接失败，请检查网络后重试。',response:'服务响应异常，请稍后重新解析。',interrupted:'下载中断，文件未保存；请重新下载。',timeout:'请求超时，请稍后重试。',error10001:'输入无效。请只粘贴一条公开作品分享链接。',error10007:'请求过于频繁，请稍后再试。',error10008:'服务忙碌或已暂停，请稍后重试。',error61001:'暂不支持此链接平台，请使用下方列出的平台分享链接。',error61002:'未读到公开作品。作品可能已删除、需要登录或触发平台验证，请重新复制公开作品链接。',error61003:'平台请求失败，请稍后重新解析。',error61004:'结果已过期或服务已重启，请重新解析链接。',error61005:'资源超过处理上限：单文件 200 MB，图集打包 32 MB、最多 50 张。请减少选图或单独保存。',error61006:'平台响应超时，请稍后重新解析。',error61007:'此作品的资源格式暂不支持，可能是分段视频、直播或 DASH 音视频。'
  };
  const en = {
    brand:'Cangshuo Toolbox',eyebrow:'VIDEO · ALBUM · COVER',heading:'Turn a shared link into usable media.',intro:'Extract media provided by the platform. Preview, select and save in one place.',inputHeading:'Paste a shared link',placeholder:'Paste a link or the complete share text from a video app…',inputHelp:'One link at a time. Full share text is accepted.',parse:'Parse link',parsing:'Reading media…',paste:'Paste',clear:'Clear',cancel:'Cancel',checking:'Connecting…',ready:'Service connected',offline:'You are offline',disabled:'Service unavailable',retry:'Reconnect',sourceNotice:'Uses original playback sources when provided. Embedded watermarks cannot be removed. Private, paid, live and verification-restricted content is unsupported.',copyInfo:'Copy media info',quality:'Video source',downloadVideo:'Download video',copyLink:'Copy direct link',cover:'Cover image',downloadCover:'Save cover',selectAll:'Select all',selectNone:'Deselect all',downloadZip:'Download selected ZIP',zipHelp:'Static images only, up to 32 MB per ZIP. Live Photo videos can be saved separately.',cancelDownload:'Cancel download',how:'How it works',howText:'Copy a public media share link and paste it above. Switch video sources, save individual images, open full previews or download a selected album.',privacy:'Privacy & storage',privacyText:'Our server requests the corresponding platform. Links are not stored in a database. Results stay in server memory for 15 minutes; source links can expire sooner.',faq:'Why can some links not be parsed?',faqText:'Content may have been removed, made private or restricted by login or verification. Copy a fresh public link. Bilibili supports public single-file videos; DASH and segmented streams are not merged.',footer:'Cangshuo Toolbox · Web tools',licenses:'References & licenses',privacyLink:'Privacy policy',close:'Close',previous:'Previous',next:'Next',saveImage:'Save image',previewError:'Preview failed. Try another source or parse the link again.',errorTitle:'Parsing or download unsuccessful',trace:'Request ID: ',untitled:'Untitled media',author:'Author',seconds:'seconds',album:'Album · {n} images',selected:'{n} selected',image:'Image {n}',resource:'Source {n}',original:'Platform original playback source',unknown:'Platform media; watermark status unconfirmed',dimensions:'{w} × {h}',expired:'This result expired. Parse the link again.',expiry:'Available until {time}. No history is saved on this device.',download:'Save',live:'Live',saveLive:'Save live video',loadingDownload:'Downloading…',preparingZip:'Preparing album…',downloadDone:'File save requested',downloadCancelled:'Download cancelled',copied:'Copied',copyFailed:'Copy failed. Select and copy the text manually.',pasteFailed:'Clipboard unavailable. Paste directly into the input.',selectNeeded:'Select images to download first.',browserDownload:'Download opened in your browser',previewFailed:'Image preview failed. Try saving it or parse the link again.',badInput:'Enter a complete share link, or text containing one link.',network:'Connection failed. Check your network and retry.',response:'Unexpected response. Parse the link again later.',interrupted:'Download interrupted. No file was saved; try downloading again.',timeout:'Request timed out. Try again later.',error10001:'Invalid input. Paste a single public media share link.',error10007:'Too many requests. Try again later.',error10008:'Service is busy or paused. Try again later.',error61001:'This platform is unsupported. Use a share link from a listed platform.',error61002:'Public media could not be read. It may be removed, private or verification-restricted. Copy a fresh public link.',error61003:'Platform request failed. Parse the link again later.',error61004:'This result expired or the server restarted. Parse the link again.',error61005:'Budget exceeded: 200 MB per file, 32 MB per ZIP, up to 50 images. Select fewer images or save them individually.',error61006:'The platform timed out. Parse the link again later.',error61007:'Unsupported media format, such as a live stream, segmented video or DASH audio/video.'
  };
  let lang = navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en';
  try { const saved = localStorage.getItem('short-video-language'); if (saved === 'zh' || saved === 'en') lang = saved; } catch (_) {}
  let capabilities = null, media = null, selected = new Set(), parseJob = null, downloadJob = null, lightboxIndex = 0, capRevision = 0, toastTimer, counted = false;
  const t = (key, args = {}) => (lang === 'zh' ? zh : en)[key]?.replace(/\{(\w+)\}/g, (_, name) => String(args[name] ?? '')) || key;
  const platformName = code => ({douyin:lang === 'zh' ? '抖音' : 'Douyin',kuaishou:lang === 'zh' ? '快手' : 'Kuaishou',xiaohongshu:lang === 'zh' ? '小红书' : 'Xiaohongshu',bilibili:lang === 'zh' ? '哔哩哔哩' : 'Bilibili',weibo:lang === 'zh' ? '微博' : 'Weibo',xigua:lang === 'zh' ? '西瓜视频' : 'Xigua',pipixia:lang === 'zh' ? '皮皮虾' : 'Pipixia',tiktok:'TikTok'})[code];
  function translate() {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en'; $('language').value = lang;
    document.title = (lang === 'zh' ? '短视频解析' : 'Short video parser') + ' · ' + t('brand');
    document.querySelectorAll('[data-i18n]').forEach(node => node.textContent = t(node.dataset.i18n));
    document.querySelectorAll('[data-placeholder]').forEach(node => node.placeholder = t(node.dataset.placeholder));
    $('platforms').replaceChildren(...Object.keys(E.HOSTS).map(code => node('span', platformName(code))));
    $('platforms').setAttribute('aria-label',lang === 'zh' ? '支持的平台' : 'Supported platforms');
    if (media) render(); updateInput(); setService();
  }
  function node(tag, text, className) { const value = document.createElement(tag); if (text != null) value.textContent = text; if (className) value.className = className; return value; }
  function toast(message) { clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false; toastTimer = setTimeout(() => $('toast').hidden = true, 3200); }
  function showError(error) {
    $('errorTitle').textContent = t('errorTitle');
    $('errorMessage').textContent = t(typeof error.code === 'number' ? 'error' + error.code : error.code || (error.name === 'TimeoutError' ? 'timeout' : 'network'));
    if (error.code === 40001) $('errorMessage').textContent = t('error61005');
    else if ($('errorMessage').textContent === 'error' + error.code) $('errorMessage').textContent = t('response');
    $('trace').textContent = error.trace ? t('trace') + error.trace : ''; $('error').hidden = false;
  }
  function setService() {
    const state = !navigator.onLine ? 'offline' : !capabilities ? 'checking' : capabilities.available ? 'ready' : 'disabled';
    $('serviceStatus').textContent = t(state); $('retry').hidden = state === 'ready' || state === 'checking'; updateInput();
  }
  function updateInput() {
    const text = $('text').value; $('count').textContent = text.length + ' / 4096';
    try { $('platformHint').textContent = platformName(E.input(text).platform); $('platformHint').hidden = false; } catch (_) { $('platformHint').hidden = true; }
    $('submit').disabled = Boolean(parseJob) || !capabilities?.available || !navigator.onLine || !text.trim();
    $('submitLabel').textContent = t(parseJob ? 'parsing' : 'parse'); $('spinner').hidden = !parseJob; $('stop').hidden = !parseJob;
  }
  async function connect() {
    const revision = ++capRevision, controller = new AbortController(), timer = setTimeout(() => controller.abort(), 10000);
    capabilities = null; setService();
    try {
      const response = await fetch(E.BASE + '/capabilities', {cache:'no-store',signal:controller.signal});
      const value = await E.json(response, controller.signal);
      if (!value || typeof value.available !== 'boolean' || value.maxFileBytes !== E.MAX_FILE || value.maxZipBytes !== E.MAX_ZIP) throw new E.ToolError('response');
      if (revision === capRevision) {
        capabilities = value; $('error').hidden = true;
        if (value.available && !counted && !navigator.userAgent.includes('CangshuoToolboxWebView/')) {
          counted = true; fetch('/api/v1/tools/short_video_parse/usage',{method:'POST',keepalive:true}).catch(() => {});
        }
      }
    } catch (error) { if (revision === capRevision) { capabilities = {available:false}; if (navigator.onLine) showError(error); } }
    finally { clearTimeout(timer); if (revision === capRevision) setService(); }
  }
  function clearMedia() {
    if (media) fetch(E.BASE + '/results/' + media.id, {method:'DELETE',keepalive:true}).catch(() => {});
    $('video').pause(); $('video').removeAttribute('src'); $('video').load();
    $('largeImage').removeAttribute('src'); $('lightbox').close(); $('gallery').replaceChildren(); $('cover').removeAttribute('src');
    media = null; selected.clear(); $('result').hidden = true;
  }
  function stopParse() { if (parseJob) parseJob.controller.abort(); parseJob = null; updateInput(); }
  function stopDownload() { if (downloadJob) downloadJob.controller.abort(); downloadJob = null; $('downloadProgress').hidden = true; }
  async function parse(event) {
    event.preventDefault(); let value;
    try { value = E.input($('text').value); } catch (error) { showError(error); return; }
    stopParse(); stopDownload(); clearMedia(); $('error').hidden = true;
    const job = {controller:new AbortController()}; parseJob = job; updateInput();
    const timer = setTimeout(() => { job.timedOut = true; job.controller.abort(); }, 40000);
    try {
      const response = await fetch(E.BASE + '/parse', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:value.text}),signal:job.controller.signal,cache:'no-store'});
      const data = await E.json(response, job.controller.signal);
      if (parseJob !== job) { if (/^[a-f0-9]{32}$/.test(data?.id)) fetch(E.BASE + '/results/' + data.id, {method:'DELETE'}).catch(() => {}); return; }
      media = E.result(data); selected = new Set(media.assets.filter(a => a.kind === 'IMAGE').map(a => a.index)); render();
      $('result').focus({preventScroll:true}); $('result').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',block:'start'});
    } catch (error) { if (parseJob === job && error.name !== 'AbortError') showError(error); else if (parseJob === job && job.timedOut) showError(new E.ToolError('timeout')); }
    finally { clearTimeout(timer); if (parseJob === job) { parseJob = null; updateInput(); } }
  }
  function assets(kind) { return media ? media.assets.filter(a => a.kind === kind) : []; }
  function activeVideo() { return media?.assets[Number($('quality').value)]; }
  function render() {
    const videos = assets('VIDEO'), images = assets('IMAGE'), covers = assets('COVER');
    $('result').hidden = false; $('resultPlatform').textContent = platformName(media.platform); $('resultTitle').textContent = media.title || t('untitled');
    $('resultMeta').textContent = [media.author ? t('author') + ': ' + media.author : '',media.durationSeconds ? media.durationSeconds + ' ' + t('seconds') : ''].filter(Boolean).join(' · ');
    $('description').textContent = media.description && media.description !== media.title ? media.description : ''; $('description').hidden = !$('description').textContent;
    $('videoSection').hidden = videos.length === 0;
    if (videos.length) {
      const previousQuality = $('quality').value;
      $('quality').replaceChildren(...videos.map((asset, i) => { const option = node('option', asset.label || (asset.height ? asset.height + 'p' : t('resource',{n:i + 1}))); option.value = String(asset.index); return option; }));
      if (videos.some(asset => String(asset.index) === previousQuality)) $('quality').value = previousQuality;
      switchQuality(); $('coverSection').hidden = !covers.length;
      if (covers.length) { $('cover').src = covers[0].previewPath; $('cover').alt = t('cover'); }
    }
    $('albumSection').hidden = images.length === 0; $('albumTitle').textContent = t('album',{n:images.length});
    $('gallery').replaceChildren(...images.map((asset, ordinal) => {
      const tile = node('article', null, 'image-tile'), preview = node('button', null, 'image-preview'), image = node('img');
      image.src = asset.previewPath; image.alt = t('image',{n:ordinal + 1}); image.loading = 'lazy'; image.decoding = 'async';
      image.addEventListener('error', () => { image.alt = t('previewFailed'); }); preview.setAttribute('aria-label',t('image',{n:ordinal + 1})); preview.type = 'button';
      preview.append(image, node('span', ordinal + 1, 'image-number')); preview.addEventListener('click', () => openImage(ordinal));
      const actions = node('div', null, 'tile-actions'), label = node('label'), checkbox = document.createElement('input');
      checkbox.type = 'checkbox'; checkbox.checked = selected.has(asset.index); checkbox.dataset.index = String(asset.index);
      checkbox.addEventListener('change', () => { if (checkbox.checked) selected.add(asset.index); else selected.delete(asset.index); updateSelection(); });
      label.append(checkbox, node('span',t('image',{n:ordinal + 1}))); actions.append(label, action(t('download'),() => downloadAsset(asset)),action(t('copyLink'),() => copy(asset.directUrl)));
      tile.append(preview, actions);
      // Live videos follow their paired image in adapter order; never confuse them with the next image's resources.
      const next = media.assets[asset.index + 1];
      if (next?.kind === 'LIVE') {
        const live = document.createElement('video'); live.controls = true; live.playsInline = true; live.preload = 'none'; live.hidden = true; live.className = 'live-video'; live.src = next.previewPath;
        actions.append(action(t('live'), () => { live.hidden = !live.hidden; if (live.hidden) live.pause(); }),action(t('saveLive'), () => downloadAsset(next))); tile.append(live);
      }
      if (asset.source === 'UNKNOWN') tile.append(node('p', t('unknown'), 'help'));
      return tile;
    }));
    updateSelection(); updateExpiry();
  }
  function action(text, fn) { const button = node('button', text); button.type = 'button'; button.addEventListener('click',fn); return button; }
  function switchQuality() {
    const asset = activeVideo(); if (!asset) return;
    if ($('video').getAttribute('src') !== asset.previewPath) { $('video').pause(); $('video').src = asset.previewPath; $('video').load(); $('previewError').hidden = true; }
    $('qualityInfo').textContent = [asset.width && asset.height ? t('dimensions',{w:asset.width,h:asset.height}) : '', asset.bitrate ? (asset.bitrate / 1e6).toFixed(2) + ' Mbps' : '',t(asset.source === 'UNKNOWN' ? 'unknown' : 'original')].filter(Boolean).join(' · ');
  }
  function updateSelection() {
    $('selectionHint').textContent = t('selected',{n:selected.size}); $('downloadZip').disabled = !selected.size;
    $('gallery').querySelectorAll('input[type=checkbox]').forEach(input => input.checked = selected.has(Number(input.dataset.index)));
  }
  function updateExpiry() {
    if (!media) return; const expired = Date.now() >= Date.parse(media.expiresAt);
    $('expiry').textContent = expired ? t('expired') : t('expiry',{time:new Date(media.expiresAt).toLocaleTimeString(lang === 'zh' ? 'zh-CN' : 'en')});
  }
  function openImage(index) {
    lightboxIndex = index; const images = assets('IMAGE'), asset = images[index]; if (!asset) return;
    $('largeImage').src = asset.previewPath; $('largeImage').alt = t('image',{n:index + 1}); $('lightboxTitle').textContent = (index + 1) + ' / ' + images.length;
    $('previousImage').disabled = index === 0; $('nextImage').disabled = index === images.length - 1;
    if (!$('lightbox').open) $('lightbox').showModal();
  }
  async function copy(value) {
    try { await navigator.clipboard.writeText(value); toast(t('copied')); } catch (_) { toast(t('copyFailed')); }
  }
  async function downloadAsset(asset) { if (asset) await download(asset.downloadPath, 'media', 'video/mp4', E.MAX_FILE); }
  async function download(path, name, expectedType, maxBytes) {
    if (!media) return;
    if (Date.now() >= Date.parse(media.expiresAt)) { showError(new E.ToolError(61004)); return; }
    const isZip = expectedType === 'application/zip';
    stopDownload(); $('error').hidden = true;
    // WebView hands trusted attachment paths to its native host; do not create a 200 MB Blob in the App.
    if (/; wv\)/.test(navigator.userAgent)) { const a = document.createElement('a'); a.href = path; a.download = ''; document.body.append(a); a.click(); a.remove(); return; }
    const job = {controller:new AbortController()}; downloadJob = job; $('downloadProgress').hidden = false; $('progress').removeAttribute('value');
    $('downloadLabel').textContent = t(isZip ? 'preparingZip' : 'loadingDownload'); $('progressText').textContent = '';
    const timer = setTimeout(() => { job.timedOut = true; job.controller.abort(); },180000);
    try {
      const response = await fetch(path,{signal:job.controller.signal,cache:'no-store'});
      if (!response.ok) { await E.json(response, job.controller.signal); throw new E.ToolError('response'); }
      const mime = (response.headers.get('Content-Type') || '').split(';')[0];
      if (!(['image/jpeg','image/png','image/webp','image/gif','video/mp4','video/quicktime'].includes(mime) || isZip && mime === 'application/zip')) throw new E.ToolError('response');
      const data = await E.read(response,maxBytes,job.controller.signal,(bytes,total) => {
        if (downloadJob !== job) return; $('downloadLabel').textContent = t('loadingDownload');
        $('progressText').textContent = E.size(bytes) + (total ? ' / ' + E.size(total) : '');
        if (total) $('progress').value = Math.min(100,bytes / total * 100);
      });
      if (downloadJob !== job || !data.size) return;
      const blob = new Blob(data.chunks,{type:mime}), url = URL.createObjectURL(blob), a = document.createElement('a');
      a.href = url; a.download = E.filename(response.headers.get('Content-Disposition'),name + '.' + (isZip ? 'zip' : mime === 'video/mp4' ? 'mp4' : mime === 'video/quicktime' ? 'mov' : mime.split('/')[1]));
      document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url),60000); toast(t('downloadDone'));
    } catch (error) { if (downloadJob === job && error.name !== 'AbortError') showError(error); else if (downloadJob === job && job.timedOut) showError(new E.ToolError('timeout')); }
    finally { clearTimeout(timer); if (downloadJob === job) { downloadJob = null; $('downloadProgress').hidden = true; } }
  }
  $('form').addEventListener('submit',parse); $('quality').addEventListener('change',switchQuality);
  $('text').addEventListener('input',() => { stopParse(); stopDownload(); clearMedia(); $('error').hidden = true; updateInput(); });
  $('text').addEventListener('keydown',event => { if (event.key === 'Enter' && (event.ctrlKey || event.metaKey) && !$('submit').disabled) $('form').requestSubmit(); });
  $('clear').addEventListener('click',() => { stopParse(); stopDownload(); clearMedia(); $('text').value = ''; $('error').hidden = true; updateInput(); $('text').focus(); });
  $('stop').addEventListener('click',stopParse); $('retry').addEventListener('click',connect);
  $('paste').addEventListener('click',async () => { try { const value = await navigator.clipboard.readText(); if (value.length > 4096) throw new E.ToolError(10001); $('text').value = value; $('text').dispatchEvent(new Event('input')); } catch (error) { if (error instanceof E.ToolError) showError(error); else toast(t('pasteFailed')); } });
  $('language').addEventListener('change',() => { lang = $('language').value; try { localStorage.setItem('short-video-language',lang); } catch (_) {} translate(); });
  $('copyInfo').addEventListener('click',() => { if (media) copy([media.title,media.author,media.description,media.sourceUrl].filter(Boolean).join('\n')); });
  $('downloadVideo').addEventListener('click',() => downloadAsset(activeVideo())); $('copyVideo').addEventListener('click',() => { const asset = activeVideo(); if (asset) copy(asset.directUrl); });
  $('downloadCover').addEventListener('click',() => downloadAsset(assets('COVER')[0]));
  $('selectAll').addEventListener('click',() => { selected = new Set(assets('IMAGE').map(a => a.index)); updateSelection(); }); $('selectNone').addEventListener('click',() => { selected.clear(); updateSelection(); });
  $('downloadZip').addEventListener('click',() => { try { download(E.selectionUrl(media,[...selected]),'album','application/zip',E.MAX_ZIP); } catch (error) { showError(error); } });
  $('cancelDownload').addEventListener('click',() => { stopDownload(); toast(t('downloadCancelled')); });
  $('closeLightbox').addEventListener('click',() => $('lightbox').close()); $('previousImage').addEventListener('click',() => openImage(lightboxIndex - 1)); $('nextImage').addEventListener('click',() => openImage(lightboxIndex + 1)); $('saveImage').addEventListener('click',() => { const asset = assets('IMAGE')[lightboxIndex]; $('lightbox').close(); downloadAsset(asset); });
  $('lightbox').addEventListener('keydown',event => { if (event.key === 'ArrowLeft' && lightboxIndex > 0) openImage(lightboxIndex - 1); if (event.key === 'ArrowRight' && lightboxIndex < assets('IMAGE').length - 1) openImage(lightboxIndex + 1); });
  $('video').addEventListener('error',() => $('previewError').hidden = false);
  window.addEventListener('offline',() => { stopParse(); stopDownload(); setService(); }); window.addEventListener('online',connect);
  window.addEventListener('pagehide',() => { stopParse(); stopDownload(); if (media) fetch(E.BASE + '/results/' + media.id,{method:'DELETE',keepalive:true}).catch(() => {}); });
  setInterval(updateExpiry,30000); translate(); connect();
})();
