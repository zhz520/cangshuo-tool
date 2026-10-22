(function (root) {
  'use strict';
  const owned = /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)?lanzou[a-z]?\.com$/i;
  const linkPattern = /https?:\/\/[^\s<>"'`]+/ig;
  const passwordPattern = /(?:分享密码|提取码|访问码|password|pwd)\s*[:：=]?\s*([a-z0-9_-]{1,64})/i;
  const control = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/;

  function decode(value) {
    try { return decodeURIComponent(value); } catch (_) { return ''; }
  }

  function queryPassword(url) {
    const query = url.search.replace(/^\?/, '');
    for (const pair of query.split('&')) {
      const at = pair.indexOf('=');
      if (at < 1) continue;
      const key = decode(pair.slice(0, at)).toLowerCase();
      if (['pwd', 'password', 'code'].includes(key)) return decode(pair.slice(at + 1)).trim();
    }
    return '';
  }

  function removeSecretQuery(url) {
    if (!url.search) return url.toString();
    const kept = url.search.slice(1).split('&').filter(pair => {
      const at = pair.indexOf('=');
      const key = decode(at < 0 ? pair : pair.slice(0, at)).toLowerCase();
      return pair && !['pwd', 'password', 'code'].includes(key);
    });
    url.search = kept.length ? '?' + kept.join('&') : '';
    return url.toString();
  }

  function extract(text) {
    if (typeof text !== 'string' || !text.trim()) return { error: 'urlRequired' };
    if (text.length > 4096 || control.test(text)) return { error: 'urlInvalid' };
    const links = Array.from(text.matchAll(linkPattern), match => match[0].replace(/[。！，、；：）】〉》\]}),.;!]+$/, ''));
    if (!links.length) return { error: 'urlInvalid' };
    if (links.length !== 1) return { error: 'urlInvalid' };
    let url;
    try { url = new URL(links[0]); } catch (_) { return { error: 'urlInvalid' }; }
    if (url.protocol === 'http:') url.protocol = 'https:';
    if (url.protocol !== 'https:' || url.username || url.password || url.port || url.hash ||
        !owned.test(url.hostname) || !url.pathname || url.pathname === '/') return { error: 'urlInvalid' };
    const embedded = queryPassword(url);
    const fromText = (text.match(passwordPattern) || [])[1] || '';
    const clean = removeSecretQuery(url);
    return { url: clean, host: url.hostname, password: embedded || fromText };
  }

  function validate(value) {
    const parsed = extract(value && value.text);
    if (parsed.error) return parsed;
    const explicit = typeof value.password === 'string' ? value.password.trim() : '';
    if (explicit.length > 64 || /[\u0000-\u001f\u007f]/.test(explicit)) return { error: 'passwordInvalid' };
    return { value: { text: parsed.url, password: explicit || parsed.password }, host: parsed.host };
  }

  function errorKey(code) {
    return ({
      10001: 'urlInvalid', 10007: 'rateLimited', 10008: 'unavailable',
      62001: 'urlInvalid', 62002: 'passwordRequired', 62003: 'passwordRejected',
      62004: 'fileUnavailable', 62005: 'verification', 62006: 'providerLimited',
      62007: 'providerFailed', 62008: 'timeout', 62009: 'folderUnsupported'
    })[Number(code)] || 'providerFailed';
  }

  function capabilities(envelope) {
    const data = envelope && envelope.code === 0 ? envelope.data : null;
    if (!data || typeof data.available !== 'boolean') return null;
    return { ...data, available: data.available === true, permanentLinksAvailable: data.permanentLinksAvailable === true };
  }

  function result(envelope) {
    if (!envelope || envelope.code !== 0 || !envelope.data) return null;
    const data = envelope.data;
    if (!['DIRECT', 'OFFICIAL_PAGE'].includes(data.state)) return null;
    return {
      state: data.state, shareUrl: String(data.shareUrl || ''), fileName: String(data.fileName || ''),
      fileSize: String(data.fileSize || ''), directUrl: String(data.directUrl || ''),
      downloadPageUrl: String(data.downloadPageUrl || ''), verificationRequired: data.verificationRequired === true,
      rateLimited: data.rateLimited === true, cached: data.cached === true,
      expiresInSeconds: Math.max(0, Number(data.expiresInSeconds) || 0)
    };
  }

  function safeTrace(value) {
    return typeof value === 'string' && /^[a-f0-9]{32}$/i.test(value) ? value : '';
  }

  function permanentLink(envelope) {
    if (!envelope || envelope.code !== 0 || !envelope.data) return null;
    const path = String(envelope.data.path || '');
    if (!/^\/api\/v1\/web-tools\/lanzou_direct\/download\/[A-Za-z0-9_-]{24,4096}$/.test(path)) return null;
    return {
      path, state: String(envelope.data.state || ''), fileName: String(envelope.data.fileName || ''),
      fileSize: String(envelope.data.fileSize || ''), verificationRequired: envelope.data.verificationRequired === true,
      rateLimited: envelope.data.rateLimited === true
    };
  }

  root.lanzouCore = { extract, validate, errorKey, capabilities, result, permanentLink, safeTrace, owned };
  if (typeof module !== 'undefined' && module.exports) module.exports = root.lanzouCore;
})(globalThis);
