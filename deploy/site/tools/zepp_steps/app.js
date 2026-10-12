(function () {
  'use strict';
  const core = window.ZeppSteps, $ = id => document.getElementById(id);
  const text = {
    zh: { brand:'沧烁工具箱',web:'网页版工具',title:'微信运动步数',subtitle:'通过 Zepp Life 提交当天步数，让运动数据同步到已绑定的微信运动。',checking:'正在检查服务…',retry:'重试',accountHeading:'连接你的账号',account:'Zepp Life 账号',accountHelp:'使用 Zepp Life 的邮箱或中国区手机号，不能使用小米账号或微信账号。',password:'密码',show:'显示',hide:'隐藏',stepsHeading:'设置今日步数',unit:'步',stepHelp:'输入 1–50,000 的整数。提交的是当天总步数，不是在当前步数上累加；日期以北京时间为准。',consent:'我已在 Zepp Life 绑定微信运动，并同意使用此账号提交步数。',submit:'提交今日步数',clear:'清空账号',stop:'停止等待',privacy:'账号密码通过 HTTPS 发送至本站，再由服务器请求 Zepp Life。本站不保存账号密码及令牌；关闭页面后需重新输入。',receiptSteps:'提交步数',receiptDate:'同步日期',receiptAccount:'账号',copy:'复制提交结果',copied:'已复制',copyFailed:'复制失败，请选择结果文字手动复制。',guide:'开始前，完成这三步',guide1:'注册 Zepp Life',guide1Text:'在应用商店搜索 Zepp Life（原小米运动），注册邮箱或手机号账号。',guide2:'绑定微信运动',guide2Text:'在 Zepp Life「我的 → 第三方接入 → 微信」完成绑定，并在微信启用微信运动。菜单名称可能随版本变化。',guide3:'提交后查看同步',guide3Text:'提交完成后打开微信运动查看数据。Zepp Life 接收成功不代表微信排行榜已更新，同步时间由第三方决定。',privateHeading:'一次提交，一次连接',privateText:'每个账号至少间隔 60 秒。页面不会自动重复提交、定时运行或更改账号的设备绑定。',faq1:'Zepp Life 成功接收，微信没有更新？',faq1Text:'先检查账号的微信绑定、微信运动是否启用，并等待第三方同步。新账号可能需要在官方 App 完成设备绑定。先核对记录，再尝试重新提交。',faq2:'遇到验证或请求超时怎么办？',faq2Text:'验证码需要在 Zepp Life 官方 App 完成。提交阶段断网或超时可能已经写入，请先查看第三方记录；停止等待不会撤销已经发出的请求。',faq3:'支持哪些账号和日期？',faq3Text:'支持 Zepp Life 邮箱和中国大陆手机号，只提交北京时间当天。其他区号、小米账号、微信账号、历史补刷和后台自动任务暂不支持。',footer:'沧烁工具箱 · 第三方接口工具',privacyLink:'隐私政策',ready:'服务已就绪 · 北京时间当天',unavailable:'工具暂时不可用，请稍后重试。',notConfigured:'服务尚未配置，请联系管理员。',offline:'当前没有网络，请连接网络后重试。',httpsRequired:'请通过 HTTPS 安全页面使用此工具。',accountInvalid:'请输入有效的 Zepp Life 邮箱或中国大陆手机号。',passwordInvalid:'请输入 Zepp Life 密码（最多 128 个字符）。',stepsInvalid:'步数必须是 1–50,000 的整数。',consentInvalid:'请先确认已绑定微信运动，并勾选提交授权。',invalidRequest:'输入不符合要求，请检查账号、密码和步数。',credentialsRejected:'Zepp Life 拒绝了登录。请核对账号密码，并在官方 App 尝试登录。',verificationRequired:'账号需要验证，请先在 Zepp Life 官方 App 完成验证码或安全验证。',providerFailed:'Zepp Life 接口请求失败，请稍后重试；不要连续提交。',rateLimited:'请求过于频繁，请至少等待 60 秒再试。',cooldown:'此账号仍在冷却中，请等待 60 秒后再试。',uncertain:'暂时无法确认提交结果。步数可能已经写入，请先打开 Zepp Life 和微信运动查看，再决定是否重试。',pending:'正在登录并提交…',acceptedTitle:'Zepp Life 已接收',acceptedMessage:'当天总步数已提交到 Zepp Life。请在微信运动查看更新，微信侧同步尚未验证。',noDevice:'账号没有返回绑定的手环设备；若微信未更新，请在官方 App 检查账号和设备接入。',errorTitle:'未完成提交',uncertainTitle:'提交结果待确认',trace:'请求编号',wait:'秒后可再次提交',stopped:'已停止等待。服务器可能仍在处理，停止等待不会撤销提交，请先查看第三方记录。' },
    en: { brand:'Cangshuo Toolbox',web:'Web tool',title:'WeChat Activity steps',subtitle:'Submit today’s step total through Zepp Life for your linked WeChat Activity account.',checking:'Checking service…',retry:'Retry',accountHeading:'Connect your account',account:'Zepp Life account',accountHelp:'Use your Zepp Life email or mainland China phone number, not a Xiaomi or WeChat account.',password:'Password',show:'Show',hide:'Hide',stepsHeading:'Today’s step total',unit:'steps',stepHelp:'Enter a whole number from 1 to 50,000. This sets today’s total, not an increment. Dates use Beijing time.',consent:'I have linked WeChat Activity in Zepp Life and authorize this submission.',submit:'Submit today’s steps',clear:'Clear account',stop:'Stop waiting',privacy:'Credentials are sent over HTTPS to this server and then to Zepp Life. The site does not store credentials or tokens. Re-enter them after closing the page.',receiptSteps:'Submitted steps',receiptDate:'Date',receiptAccount:'Account',copy:'Copy receipt',copied:'Copied',copyFailed:'Copy failed. Select and copy the result manually.',guide:'Three steps to get started',guide1:'Register with Zepp Life',guide1Text:'Find Zepp Life (formerly Mi Fit) in your app store and register using email or phone.',guide2:'Link WeChat Activity',guide2Text:'In Zepp Life, open Profile → Third-party access → WeChat and finish linking. Enable WeChat Activity in WeChat. Menu names may vary.',guide3:'Check the update',guide3Text:'Open WeChat Activity after submission. Acceptance by Zepp Life does not confirm a leaderboard update. Sync timing is controlled by the third party.',privateHeading:'One submission, one connection',privateText:'Wait at least 60 seconds per account. This page never submits automatically, schedules tasks or changes device bindings.',faq1:'Accepted, but WeChat has not updated?',faq1Text:'Check the account’s WeChat link and Activity setting, then wait for sync. New accounts may need device setup in the official app. Check records before resubmitting.',faq2:'Verification or timeout?',faq2Text:'Complete verification in the official Zepp Life app. A timeout may occur after the write. Check records first; stopping the wait does not undo the request.',faq3:'Supported accounts and dates?',faq3Text:'Zepp Life email and mainland China phone numbers, for today in Beijing time. Other country codes, Xiaomi/WeChat accounts, backdated writes and scheduled tasks are not supported.',footer:'Cangshuo Toolbox · Third-party API tool',privacyLink:'Privacy policy',ready:'Service ready · Today in Beijing time',unavailable:'Tool unavailable. Please try again later.',notConfigured:'Service not configured. Contact the administrator.',offline:'You are offline. Reconnect and retry.',httpsRequired:'Use this tool from an HTTPS page.',accountInvalid:'Enter a valid Zepp Life email or mainland China phone number.',passwordInvalid:'Enter your Zepp Life password (up to 128 characters).',stepsInvalid:'Steps must be a whole number from 1 to 50,000.',consentInvalid:'Confirm your WeChat link and authorize the submission.',invalidRequest:'Check the account, password and step count.',credentialsRejected:'Zepp Life rejected the login. Check credentials and sign in with the official app.',verificationRequired:'Complete the captcha or security verification in the official Zepp Life app.',providerFailed:'Zepp Life request failed. Retry later; avoid repeated submissions.',rateLimited:'Too many requests. Wait at least 60 seconds.',cooldown:'This account is cooling down. Wait 60 seconds before retrying.',uncertain:'The result is unknown. Steps may already be written. Check Zepp Life and WeChat Activity before retrying.',pending:'Signing in and submitting…',acceptedTitle:'Accepted by Zepp Life',acceptedMessage:'Today’s total was submitted to Zepp Life. Check WeChat Activity for the update; WeChat sync is not verified.',noDevice:'No bound wearable was returned. If WeChat does not update, check account and device setup in the official app.',errorTitle:'Submission not completed',uncertainTitle:'Submission result unknown',trace:'Request ID',wait:'s before another submission',stopped:'Stopped waiting. The server may still be processing. This does not undo submission; check third-party records first.' }
  };
  let lang = navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en';
  let capability = null, availabilityKey = 'checking', busy = false, checking = false, active = null;
  let cooldownUntil = 0, lastResult = null, formErrorKey = '', copyKey = 'copy';
  const secure = location.protocol === 'https:' || ['localhost','127.0.0.1','[::1]'].includes(location.hostname);
  const t = key => text[lang][key] || text[lang].uncertain;
  const wait = () => Math.max(0, Math.ceil((cooldownUntil - Date.now()) / 1000));
  function render() {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.title = t('title') + ' · ' + t('brand');
    document.querySelectorAll('[data-i18n]').forEach(element => { element.textContent = t(element.dataset.i18n); });
    $('language').value = lang;
    $('steps').setAttribute('aria-label', t('stepsHeading'));
    $('presets').setAttribute('aria-label', t('stepsHeading'));
    $('visibility').textContent = t($('password').type === 'password' ? 'show' : 'hide');
    $('visibility').setAttribute('aria-pressed', String($('password').type !== 'password'));
    $('availabilityText').textContent = t(availabilityKey);
    $('availability').className = 'notice ' + (availabilityKey === 'ready' ? 'ready' : availabilityKey === 'checking' ? '' : 'error');
    $('retry').hidden = checking || busy || availabilityKey === 'ready' || !secure;
    $('retry').disabled = checking;
    $('fields').disabled = busy || !secure;
    $('clear').disabled = busy;
    $('spinner').hidden = !busy;
    $('stop').hidden = !busy;
    $('submit').disabled = busy || !capability || !capability.available || !secure || !navigator.onLine || wait() > 0;
    $('submitLabel').textContent = busy ? t('pending') : wait() > 0 ? wait() + ' ' + t('wait') : t('submit');
    $('form').setAttribute('aria-busy', String(busy));
    $('today').textContent = capability ? capability.date + (lang === 'zh' ? ' · 北京时间' : ' · Beijing') : '—';
    $('formError').hidden = !formErrorKey;
    $('formError').textContent = formErrorKey ? t(formErrorKey) : '';
    $('presets').querySelectorAll('button').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.steps === $('steps').value.trim())));
    $('result').hidden = !lastResult;
    if (lastResult) {
      const accepted = !!lastResult.data;
      $('result').className = 'result' + (accepted ? '' : ' error');
      $('resultMarker').textContent = accepted ? '✓' : lastResult.key === 'uncertain' || lastResult.key === 'stopped' ? '?' : '!';
      $('resultTitle').textContent = t(accepted ? 'acceptedTitle' : lastResult.key === 'uncertain' || lastResult.key === 'stopped' ? 'uncertainTitle' : 'errorTitle');
      $('resultMessage').textContent = accepted ? t('acceptedMessage') + (lastResult.data.boundDevice ? '' : ' ' + t('noDevice')) : t(lastResult.key);
      $('receipt').hidden = !accepted;
      $('copy').hidden = !accepted;
      $('copy').textContent = t(copyKey);
      if (accepted) {
        $('receiptSteps').textContent = lastResult.data.steps.toLocaleString(lang === 'zh' ? 'zh-CN' : 'en-US');
        $('receiptDate').textContent = lastResult.data.date;
        $('receiptAccount').textContent = lastResult.data.accountMasked;
      }
      $('resultTrace').textContent = lastResult.trace ? t('trace') + ': ' + lastResult.trace : '';
    }
  }
  async function loadCapability() {
    if (checking || busy) return;
    if (!secure) { availabilityKey = 'httpsRequired'; render(); return; }
    if (!navigator.onLine) { availabilityKey = 'offline'; capability = null; render(); return; }
    checking = true; availabilityKey = 'checking'; render();
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch('/api/v1/web-tools/zepp_steps/capabilities', {cache:'no-store',credentials:'omit',redirect:'error',signal:controller.signal,headers:{Accept:'application/json'}});
      if (!response.ok) throw new Error('unavailable');
      capability = core.capabilities(await response.json());
      availabilityKey = capability.available ? 'ready' : 'unavailable';
    } catch (_) { capability = null; availabilityKey = navigator.onLine ? 'unavailable' : 'offline'; }
    finally { clearTimeout(timer); checking = false; render(); }
  }
  $('form').addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || wait() > 0 || !capability || !capability.available || !secure) return;
    if (!navigator.onLine) { availabilityKey = 'offline'; render(); return; }
    const validated = core.validate({account:$('account').value,password:$('password').value,steps:$('steps').value,consent:$('consent').checked});
    ['account','password','steps','consent'].forEach(id => $(id).removeAttribute('aria-invalid'));
    lastResult = null; copyKey = 'copy'; formErrorKey = validated.error || '';
    if (validated.error) { render(); $(validated.field).setAttribute('aria-invalid','true'); $(validated.field).focus(); return; }
    busy = true; cooldownUntil = Date.now() + 60000;
    const controller = new AbortController(); active = controller;
    const timer = setTimeout(() => controller.abort(), 35000);
    let stopped = false;
    const stop = () => { stopped = true; controller.abort(); };
    $('stop').onclick = stop; render();
    try {
      const response = await fetch('/api/v1/web-tools/zepp_steps/submit', {method:'POST',credentials:'omit',redirect:'error',cache:'no-store',signal:controller.signal,
        headers:{'Content-Type':'application/json',Accept:'application/json'},body:JSON.stringify(validated.value)});
      const envelope = await response.json();
      if (response.ok && envelope.code === 0) lastResult = {data:core.receipt(envelope, validated.value.steps),trace:core.safeTrace(envelope)};
      else {
        lastResult = {key:core.errorKey(envelope.code),trace:core.safeTrace(envelope)};
        const retry = Number(response.headers.get('Retry-After'));
        if (Number.isFinite(retry) && retry > 0 && retry <= 3600) cooldownUntil = Math.max(cooldownUntil,Date.now()+retry*1000);
      }
    } catch (_) { lastResult = {key:stopped ? 'stopped' : 'uncertain',trace:''}; }
    finally {
      clearTimeout(timer); active = null; busy = false; $('stop').onclick = null;
      // Clear the submitted password even on failure; no credentials are retained in storage.
      validated.value.password = ''; $('password').value = ''; $('password').type = 'password';
      render(); $('result').focus({preventScroll:true});
    }
  });
  $('visibility').addEventListener('click', () => { $('password').type = $('password').type === 'password' ? 'text' : 'password'; render(); });
  $('presets').addEventListener('click', event => { const button = event.target.closest('button[data-steps]'); if (button && !busy) { $('steps').value = button.dataset.steps; render(); } });
  $('steps').addEventListener('input', render);
  $('language').addEventListener('change', () => { lang = $('language').value === 'en' ? 'en' : 'zh'; render(); });
  $('clear').addEventListener('click', () => {
    if (busy) return;
    $('account').value = ''; $('password').value = ''; $('password').type = 'password'; $('consent').checked = false;
    ['account','password','steps','consent'].forEach(id => $(id).removeAttribute('aria-invalid'));
    lastResult = null; formErrorKey = ''; render(); $('account').focus();
  });
  $('copy').addEventListener('click', async () => {
    if (!lastResult || !lastResult.data) return;
    const data = lastResult.data;
    try { await navigator.clipboard.writeText([t('acceptedTitle'),t('receiptSteps')+': '+data.steps,t('receiptDate')+': '+data.date,t('receiptAccount')+': '+data.accountMasked,t('acceptedMessage')].join('\n')); copyKey = 'copied'; }
    catch (_) { copyKey = 'copyFailed'; }
    render();
  });
  $('retry').addEventListener('click', loadCapability);
  window.addEventListener('online', () => { if (!busy) loadCapability(); });
  window.addEventListener('offline', () => { availabilityKey = 'offline'; render(); });
  window.addEventListener('pagehide', () => { if (active) active.abort(); $('password').value = ''; $('account').value = ''; });
  setInterval(() => { if (!busy) render(); },1000);
  render(); loadCapability();
})();
