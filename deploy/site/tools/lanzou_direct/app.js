(function () {
  'use strict';
  const core = globalThis.lanzouCore;
  const $ = id => document.getElementById(id);
  const text = {
    zh: {
      brand:'沧烁工具箱',webTag:'网页工具',eyebrow:'公开分享 · 临时链接',heading:'把蓝奏分享页，整理成可用链接。',intro:'识别文件名、大小和临时直链。需要密码、官方验证或链接失效时，会明确说明并给出安全的下一步。',shareLabel:'蓝奏云分享链接或分享文本',sharePlaceholder:'https://www.lanzouf.com/…',shareHelp:'可以粘贴整段分享文案；只包含密码时，请在下方单独填写。一次解析一个链接。',passwordLabel:'分享密码',optional:'可选',passwordPlaceholder:'没有密码可留空',show:'显示',hide:'隐藏',submit:'解析链接',stop:'停止等待',clear:'清空',privacy:'链接和密码仅用于本次解析。密码不写入浏览器存储，也不会出现在日志或缓存结果中。',checking:'正在检查服务…',ready:'服务已就绪 · 单次解析、短时缓存',unavailable:'工具暂时不可用，请稍后重试。',offline:'当前没有网络，请连接后重试。',httpsRequired:'请通过 HTTPS 安全页面使用此工具。',urlRequired:'请粘贴蓝奏云分享链接或分享文本。',urlInvalid:'未识别到一个有效的蓝奏云公开分享链接。',passwordInvalid:'密码最多 64 个字符，且不能包含控制字符。',passwordRequired:'此分享需要密码，请在下方填写。',passwordRejected:'分享密码不正确，请核对后重试。',fileUnavailable:'分享已失效、被取消，或文件已不可用。',verification:'蓝奏官方页面要求安全验证。请打开官方分享页完成验证后再下载；本站不会绕过验证。',providerLimited:'蓝奏暂时限制了服务器访问。请等待约 60 秒，或稍后使用官方分享页。',providerFailed:'蓝奏返回了无法识别的内容。链接可能刚改版，请稍后重试。',timeout:'解析等待超时，请稍后重试或打开官方分享页。',folderUnsupported:'当前仅支持单个文件分享，文件夹分享需要到官方页面处理。',rateLimited:'请求过于频繁，请等待约 60 秒后再试。',pending:'正在读取分享页…',directTitle:'临时直链已获取',officialTitle:'需要使用官方页面',directMessage:'直链可能随时间和来源变化而失效。复制后尽快使用；若返回 403，请重新解析或打开官方页。',officialMessage:'未生成直链。官方下载页仍可在新窗口打开，文件信息以官方页面为准。',cached:'命中 3 分钟缓存，本次未重复访问蓝奏。',fresh:'本次已向蓝奏发起受限请求。',fileName:'文件名',fileSize:'文件大小',validity:'结果有效期',copyLink:'复制链接',copied:'已复制',copyFailed:'复制失败，请手动选择链接。',openDirect:'打开临时直链',openOfficial:'打开官方页',again:'解析另一个',trace:'请求编号',howTitle:'解析流程',step1Title:'粘贴公开分享',step1Text:'支持当前蓝奏域名系列，自动识别分享文案中的链接和密码。',step2Title:'服务器轻量解析',step2Text:'3 分钟缓存、同链接请求合并、并发上限和退避，减少重复访问。',step3Title:'核对结果类型',step3Text:'直链会明确标注临时性；验证页只给出官方回退入口。',limitTitle:'请求预算',limitText:'每个访问来源每分钟最多 8 次解析，并提供 60 秒冷却。没有代理轮换、伪造来源 IP 或验证码破解。',faqTitle:'常见情况',faq1:'显示“需要官方验证”',faq1Text:'蓝奏官方页面触发了安全验证。请打开官方分享页完成验证后再下载；本站不会替你绕过验证。',faq2:'直链过期或返回 403',faq2Text:'临时直链会过期，也可能对来源地址敏感。重新解析获取新地址；仍失败时使用官方下载页。',faq3:'文件夹分享为什么失败',faq3Text:'当前只解析单个公开文件分享。文件夹列表不是本工具的下载代理，也不会遍历其中全部文件。',faq4:'频繁请求会怎样',faq4Text:'相同链接优先命中 3 分钟缓存。触发限额后按提示等待，不会自动重试或连续提交。',footer:'沧烁工具箱 · 第三方链接工具',notices:'第三方许可',privacyLink:'隐私政策',home:'返回首页'
    },
    en: {
      brand:'Cangshuo Toolbox',webTag:'Web tool',eyebrow:'Public share · temporary link',heading:'Turn a Lanzou share page into a usable link.',intro:'Read the file name, size and temporary direct URL. Passwords, official verification and unavailable links are reported clearly with a safe next step.',shareLabel:'Lanzou share URL or share text',sharePlaceholder:'https://www.lanzouf.com/…',shareHelp:'Paste the complete share message, or enter a separate password below. One link per request.',passwordLabel:'Share password',optional:'optional',passwordPlaceholder:'Leave blank if none',show:'Show',hide:'Hide',submit:'Resolve link',stop:'Stop waiting',clear:'Clear',privacy:'The link and password are used for this request only. Passwords are not written to browser storage, logs or cached results.',checking:'Checking service…',ready:'Service ready · one request, short cache',unavailable:'Tool unavailable. Try again later.',offline:'You are offline. Reconnect and retry.',httpsRequired:'Use this tool from an HTTPS page.',urlRequired:'Paste a Lanzou share URL or share text.',urlInvalid:'No single valid Lanzou public share link was found.',passwordInvalid:'Passwords may contain up to 64 characters and no control characters.',passwordRequired:'This share needs a password. Enter it below.',passwordRejected:'The share password was rejected. Check it and retry.',fileUnavailable:'The share expired, was cancelled, or the file is unavailable.',verification:'Lanzou requires security verification on its official page. Complete it there; this site does not bypass verification.',providerLimited:'Lanzou temporarily restricted this server. Wait about 60 seconds or use the official page.',providerFailed:'Lanzou returned an unknown page. The link format may be changing; retry later.',timeout:'Resolution timed out. Retry later or open the official page.',folderUnsupported:'Folder shares are not supported here. Continue on the official page.',rateLimited:'Too many requests. Wait about 60 seconds before retrying.',pending:'Reading the share page…',directTitle:'Temporary direct URL obtained',officialTitle:'Official page required',directMessage:'Direct URLs can expire or be sensitive to the requesting network. Use it soon; if it returns 403, resolve again or open the official page.',officialMessage:'No direct URL was produced. Open the official download page in a new window for the authoritative file.',cached:'Served from the 3-minute cache without another Lanzou request.',fresh:'One bounded request was sent to Lanzou.',fileName:'File name',fileSize:'File size',validity:'Result lifetime',copyLink:'Copy link',copied:'Copied',copyFailed:'Copy failed. Select the link manually.',openDirect:'Open temporary link',openOfficial:'Open official page',again:'Resolve another',trace:'Request ID',howTitle:'How it works',step1Title:'Paste a public share',step1Text:'Supports current Lanzou host families and extracts links and passwords from share text.',step2Title:'Bounded server lookup',step2Text:'A 3-minute cache, request coalescing, concurrency limits and backoff reduce repeated access.',step3Title:'Check the result type',step3Text:'Direct URLs are labeled temporary; verification pages produce an official fallback only.',limitTitle:'Request budget',limitText:'Up to eight resolutions per client per minute, with a 60-second cooldown. No proxy rotation, spoofed source IPs or captcha bypass.',faqTitle:'Common cases',faq1:'“Official verification required”',faq1Text:'Lanzou presented a security check. Complete it on the official page; this site does not bypass verification.',faq2:'Direct URL expired or returned 403',faq2Text:'Temporary URLs expire and can depend on the requesting network. Resolve again; if it still fails, use the official page.',faq3:'Why do folder shares fail?',faq3Text:'Only a single public file share is supported. The folder listing is not proxied and its files are not enumerated.',faq4:'What happens under repeated requests?',faq4Text:'The same link first uses the 3-minute cache. After a limit, wait for the stated cooldown; nothing retries automatically.',footer:'Cangshuo Toolbox · third-party link tool',notices:'Third-party licences',privacyLink:'Privacy',home:'Home'
    }
  };
  let lang = navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en';
  let capability = null, availabilityKey = 'checking', checking = false, busy = false, active = null, cooldownUntil = 0;
  let lastResult = null, lastTrace = '', formErrorKey = '', copyKey = 'copyLink';
  const secure = location.protocol === 'https:' || ['localhost','127.0.0.1','[::1]'].includes(location.hostname);
  const t = key => text[lang][key] || text[lang].providerFailed;
  const wait = () => Math.max(0, Math.ceil((cooldownUntil - Date.now()) / 1000));

  function render() {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.title = t('heading') + ' · ' + t('brand');
    document.querySelectorAll('[data-i18n]').forEach(element => { element.textContent = t(element.dataset.i18n); });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(element => { element.placeholder = t(element.dataset.i18nPlaceholder); });
    $('language').value = lang;
    $('availabilityText').textContent = t(availabilityKey);
    $('availability').className = 'status ' + (availabilityKey === 'ready' ? 'ready' : availabilityKey === 'checking' ? 'checking' : 'error');
    $('visibility').textContent = t($('password').type === 'password' ? 'show' : 'hide');
    $('visibility').setAttribute('aria-pressed', String($('password').type !== 'password'));
    $('submit').disabled = busy || !capability?.available || !secure || !navigator.onLine || wait() > 0;
    $('submitLabel').textContent = busy ? t('pending') : wait() > 0 ? wait() + 's' : t('submit');
    $('submitSpinner').hidden = !busy;
    $('stop').hidden = !busy;
    $('form').setAttribute('aria-busy', String(busy));
    $('shareText').disabled = busy || !secure;
    $('password').disabled = busy || !secure;
    $('visibility').disabled = busy || !secure;
    $('clear').disabled = busy;
    $('formError').hidden = !formErrorKey;
    $('formError').textContent = formErrorKey ? t(formErrorKey) : '';
    $('result').hidden = !lastResult;
    $('resultTrace').textContent = lastTrace ? t('trace') + ': ' + lastTrace : '';
    if (!lastResult) return;
    const direct = lastResult.state === 'DIRECT' && lastResult.directUrl;
    $('result').className = 'result ' + (direct ? 'direct' : 'official');
    $('resultBadge').textContent = direct ? t('directTitle') : t('officialTitle');
    $('resultTitle').textContent = lastResult.fileName || (direct ? t('directTitle') : t('officialTitle'));
    $('resultStateMark').textContent = direct ? '↗' : '!';
    let message = direct ? t('directMessage') : t('officialMessage');
    if (lastResult.verificationRequired) message = t('verification');
    if (lastResult.rateLimited) message += ' ' + t('providerLimited');
    $('resultMessage').textContent = message;
    $('fileName').textContent = lastResult.fileName || '—';
    $('fileSize').textContent = lastResult.fileSize || '—';
    $('validity').textContent = (lastResult.expiresInSeconds || 0) + (lang === 'zh' ? ' 秒' : ' s') + ' · ' + (lastResult.cached ? t('cached') : t('fresh'));
    const link = direct ? lastResult.directUrl : (lastResult.downloadPageUrl || lastResult.shareUrl);
    $('linkText').textContent = link;
    $('openLink').href = link;
    $('openLink').textContent = direct ? t('openDirect') : t('openOfficial');
    $('copy').textContent = t(copyKey);
  }

  async function loadCapability() {
    if (checking || busy) return;
    if (!secure) { availabilityKey = 'httpsRequired'; render(); return; }
    if (!navigator.onLine) { availabilityKey = 'offline'; capability = null; render(); return; }
    checking = true; availabilityKey = 'checking'; render();
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch('/api/v1/web-tools/lanzou_direct/capabilities', { cache:'no-store', credentials:'omit', redirect:'error', signal:controller.signal, headers:{Accept:'application/json'} });
      if (!response.ok) throw new Error('unavailable');
      capability = core.capabilities(await response.json());
      availabilityKey = capability?.available ? 'ready' : 'unavailable';
    } catch (_) { capability = null; availabilityKey = navigator.onLine ? 'unavailable' : 'offline'; }
    finally { clearTimeout(timer); checking = false; render(); }
  }

  function fail(key, trace = '') {
    lastResult = null; formErrorKey = key; lastTrace = core.safeTrace(trace); render(); $('formError').focus?.();
  }

  $('form').addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || wait() > 0 || !capability?.available || !secure) return;
    if (!navigator.onLine) { availabilityKey = 'offline'; render(); return; }
    const checked = core.validate({ text:$('shareText').value, password:$('password').value });
    ['shareText','password'].forEach(id => $(id).removeAttribute('aria-invalid'));
    lastResult = null; lastTrace = ''; copyKey = 'copyLink'; formErrorKey = checked.error || '';
    if (checked.error) {
      render(); $(checked.error === 'passwordInvalid' ? 'password' : 'shareText').setAttribute('aria-invalid','true');
      $(checked.error === 'passwordInvalid' ? 'password' : 'shareText').focus(); return;
    }
    busy = true; $('password').value = ''; $('password').type = 'password'; render();
    const controller = new AbortController(); active = controller;
    const timer = setTimeout(() => controller.abort(), 30000);
    $('stop').onclick = () => controller.abort();
    try {
      const response = await fetch('/api/v1/web-tools/lanzou_direct/parse', {
        method:'POST', credentials:'omit', redirect:'error', cache:'no-store', signal:controller.signal,
        headers:{'Content-Type':'application/json',Accept:'application/json'},
        body:JSON.stringify(checked.value)
      });
      let envelope;
      try { envelope = await response.json(); } catch (_) { throw new Error('provider'); }
      if (response.ok && envelope.code === 0) {
        lastResult = core.result(envelope); lastTrace = core.safeTrace(envelope.traceId);
        if (!lastResult) throw new Error('provider');
        fetch('/api/v1/tools/lanzou_direct/usage', { method:'POST', keepalive:true, credentials:'omit' }).catch(() => {});
      } else {
        const key = core.errorKey(envelope.code);
        lastTrace = core.safeTrace(envelope.traceId); formErrorKey = key;
        if (key === 'passwordRequired' || key === 'passwordRejected') {
          $('password').setAttribute('aria-invalid','true'); $('password').focus();
        }
        const retry = Number(response.headers.get('Retry-After'));
        if (Number.isFinite(retry) && retry > 0 && retry <= 3600) cooldownUntil = Date.now() + retry * 1000;
        else if (key === 'providerLimited' || key === 'rateLimited') cooldownUntil = Date.now() + 60000;
      }
    } catch (_) { formErrorKey = controller.signal.aborted ? 'timeout' : 'providerFailed'; }
    finally {
      clearTimeout(timer); active = null; busy = false; $('stop').onclick = null; render();
      if (lastResult) $('result').focus({preventScroll:true}); else $('formError').focus?.();
    }
  });

  $('visibility').addEventListener('click', () => { $('password').type = $('password').type === 'password' ? 'text' : 'password'; render(); });
  $('clear').addEventListener('click', () => {
    if (busy) return;
    $('form').reset(); $('password').type = 'password'; $('shareText').removeAttribute('aria-invalid'); $('password').removeAttribute('aria-invalid');
    lastResult = null; lastTrace = ''; formErrorKey = ''; render(); $('shareText').focus();
  });
  $('again').addEventListener('click', () => { $('clear').click(); });
  $('copy').addEventListener('click', async () => {
    if (!lastResult) return;
    const link = lastResult.state === 'DIRECT' ? lastResult.directUrl : (lastResult.downloadPageUrl || lastResult.shareUrl);
    try { await navigator.clipboard.writeText(link); copyKey = 'copied'; }
    catch (_) { copyKey = 'copyFailed'; }
    render();
  });
  $('language').addEventListener('change', () => { lang = $('language').value === 'en' ? 'en' : 'zh'; render(); });
  window.addEventListener('online', () => { if (!busy) loadCapability(); });
  window.addEventListener('offline', () => { availabilityKey = 'offline'; render(); });
  window.addEventListener('pagehide', () => { if (active) active.abort(); $('password').value = ''; });
  setInterval(() => { if (!busy && wait() > 0) render(); }, 1000);
  render(); loadCapability();
})();
