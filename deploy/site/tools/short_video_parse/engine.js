(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.ShortVideo = api;
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';
  const BASE = '/api/v1/web-tools/short_video_parse';
  const MAX_FILE = 200 * 1024 * 1024, MAX_ZIP = 32 * 1024 * 1024;
  const HOSTS = {
    douyin: ['v.douyin.com', 'www.douyin.com', 'www.iesdouyin.com', 'iesdouyin.com'],
    kuaishou: ['v.kuaishou.com', 'www.kuaishou.com', 'www.gifshow.com', 'v.m.chenzhongtech.com'],
    xiaohongshu: ['www.xiaohongshu.com', 'xiaohongshu.com', 'xhslink.com', 'xhslink.cn'],
    bilibili: ['www.bilibili.com', 'm.bilibili.com', 'bilibili.com', 'b23.tv'],
    weibo: ['weibo.com', 'www.weibo.com', 'm.weibo.cn', 'weibo.cn', 'h5.video.weibo.com', 't.cn'],
    xigua: ['www.ixigua.com', 'v.ixigua.com', 'm.ixigua.com', 'ixigua.com'],
    pipixia: ['h5.pipix.com', 'pipix.com'], tiktok: ['www.tiktok.com', 'tiktok.com', 'vm.tiktok.com', 'vt.tiktok.com']
  };
  class ToolError extends Error {
    constructor(code, trace = '') { super(String(code)); this.code = code; this.trace = /^[a-zA-Z0-9-]{1,64}$/.test(trace) ? trace : ''; }
  }
  function input(text) {
    if (typeof text !== 'string' || !text.trim() || text.length > 4096 || /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/.test(text)) throw new ToolError(10001);
    const links = text.match(/https?:\/\/[^\s<>"'`]+/gi) || [];
    if (links.length !== 1) throw new ToolError(10001);
    let url;
    try { url = new URL(links[0].replace(/[。！，、；：）】〉》\]}).,;!]+$/, '')); } catch (_) { throw new ToolError(10001); }
    if (url.username || url.password || (url.port && url.port !== '443') || url.hostname.endsWith('.')) throw new ToolError(10001);
    const platform = Object.keys(HOSTS).find(key => HOSTS[key].includes(url.hostname.toLowerCase()));
    if (!platform) throw new ToolError(61001);
    url.protocol = 'https:'; url.hash = '';
    return { platform, url: url.href, text };
  }
  function path(value, expected) {
    if (value !== expected) throw new ToolError('response');
    return value;
  }
  function result(raw, now = Date.now()) {
    if (!raw || !/^[a-f0-9]{32}$/.test(raw.id) || !HOSTS[raw.platform] || !Array.isArray(raw.assets)
        || raw.assets.length < 1 || raw.assets.length > 120 || !Number.isFinite(Date.parse(raw.expiresAt))) throw new ToolError('response');
    if (Date.parse(raw.expiresAt) <= now) throw new ToolError(61004);
    const assets = raw.assets.map((asset, index) => {
      if (!asset || asset.index !== index || !['VIDEO', 'IMAGE', 'LIVE', 'COVER'].includes(asset.kind)
          || !['PLATFORM_SOURCE', 'UNKNOWN'].includes(asset.source)) throw new ToolError('response');
      const expected = BASE + '/assets/' + raw.id + '/' + index;
      path(asset.previewPath, expected); path(asset.downloadPath, expected + '?download=true');
      let direct;
      try { direct = new URL(asset.directUrl); } catch (_) { throw new ToolError('response'); }
      if (direct.protocol !== 'https:' || direct.username || direct.password || (direct.port && direct.port !== '443') || asset.directUrl.length > 8192) throw new ToolError('response');
      for (const key of ['width', 'height', 'bitrate']) if (!Number.isSafeInteger(asset[key]) || asset[key] < 0) throw new ToolError('response');
      if (typeof asset.label !== 'string' || asset.label.length > 200) throw new ToolError('response');
      return { ...asset };
    });
    if (!assets.some(a => a.kind === 'IMAGE' || a.kind === 'VIDEO') || assets.filter(a => a.kind === 'IMAGE').length > 50) throw new ToolError('response');
    for (const [key, max] of [['title', 8000], ['description', 40000], ['author', 400], ['platformName', 80]])
      if (typeof raw[key] !== 'string' || raw[key].length > max) throw new ToolError('response');
    if (!Number.isSafeInteger(raw.durationSeconds) || raw.durationSeconds < 0 || raw.durationSeconds > 86400) throw new ToolError('response');
    if (input(raw.sourceUrl).platform !== raw.platform) throw new ToolError('response');
    path(raw.albumPath, BASE + '/albums/' + raw.id);
    return { ...raw, assets };
  }
  function selectionUrl(media, indices) {
    if (!Array.isArray(indices) || !indices.length || indices.length > 50 || new Set(indices).size !== indices.length
        || indices.some(i => !Number.isInteger(i) || !media.assets[i] || media.assets[i].kind !== 'IMAGE')) throw new ToolError(10001);
    return media.albumPath + '?selection=' + indices.slice().sort((a, b) => a - b).join(',');
  }
  function filename(disposition, fallback) {
    const match = /(?:^|;)\s*filename="?([^";]+)"?/i.exec(disposition || '');
    return match && /^[a-z0-9_-]{1,80}\.(jpg|jpeg|png|webp|gif|mp4|mov|zip)$/i.test(match[1]) ? match[1] : fallback;
  }
  async function read(response, limit, signal, onProgress) {
    const lengthHeader = response.headers.get('Content-Length'), length = lengthHeader === null ? 0 : Number(lengthHeader);
    if (!Number.isSafeInteger(length) || length < 0 || length > limit) throw new ToolError(61005);
    if (!response.body || !response.body.getReader) throw new ToolError('response');
    const reader = response.body.getReader(), chunks = []; let size = 0, completed = false;
    try {
      for (;;) {
        if (signal && signal.aborted) throw new DOMException('Aborted', 'AbortError');
        const value = await reader.read(); if (value.done) break;
        size += value.value.byteLength;
        if (size > limit) throw new ToolError(61005);
        chunks.push(value.value); if (onProgress) onProgress(size, length);
      }
      if (length && size !== length) throw new ToolError('interrupted');
      completed = true; return { chunks, size };
    } finally { if (!completed) await reader.cancel().catch(() => {}); reader.releaseLock(); }
  }
  async function json(response, signal) {
    let envelope;
    try {
      const data = await read(response, 2 * 1024 * 1024, signal);
      envelope = JSON.parse(await new Blob(data.chunks).text());
    } catch (error) { if (error.name === 'AbortError' || error instanceof ToolError) throw error; throw new ToolError('response'); }
    if (!envelope || !Number.isInteger(envelope.code)) throw new ToolError('response');
    if (!response.ok || envelope.code !== 0) throw new ToolError(envelope.code || 'response', envelope.traceId || '');
    return envelope.data;
  }
  function size(bytes) { return (bytes / 1048576).toFixed(bytes >= 10485760 ? 0 : 1) + ' MB'; }
  return { BASE, HOSTS, MAX_FILE, MAX_ZIP, ToolError, input, result, selectionUrl, filename, read, json, size };
});
