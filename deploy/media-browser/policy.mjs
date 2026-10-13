import {isIP} from 'node:net';

export const ROOTS = ['douyin.com', 'iesdouyin.com', 'douyinpic.com', 'douyinvod.com', 'douyinstatic.com',
  'byteimg.com', 'bytecdn.cn', 'bytegecko.com', 'bytegoofy.com', 'bytedance.com', 'bytedance.net',
  'bytedstatic.com', 'byted-static.com', 'ibytedtos.com', 'pstatp.com', 'amemv.com', 'snssdk.com', 'toutiaoimg.com', 'zjcdn.com'];
export function allowedHost(host) {
  return typeof host === 'string' && /^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/.test(host)
    && !host.includes('..') && ROOTS.some(root => host === root || host.endsWith('.' + root));
}
export function allowedUrl(raw) {
  try {
    const url = new URL(raw);
    return url.protocol === 'https:' && !url.username && !url.password && (!url.port || url.port === '443')
      && allowedHost(url.hostname);
  } catch { return false; }
}
export function publicAddress(address) {
  if (isIP(address) === 4) {
    const [a, b, c] = address.split('.').map(Number);
    return !(a === 0 || a === 10 || a === 127 || a >= 224 || a === 169 && b === 254
      || a === 100 && b >= 64 && b <= 127 || a === 172 && b >= 16 && b <= 31
      || a === 192 && (b === 168 || b === 0 || b === 2 || b === 88 && c === 99)
      || a === 198 && (b === 18 || b === 19 || b === 51 && c === 100)
      || a === 203 && b === 0 && c === 113);
  }
  if (isIP(address) !== 6) return false;
  const value = address.toLowerCase();
  return /^[23][0-9a-f]{3}:/.test(value) && !/^200[12]:/.test(value);
}
export function requestId(path) {
  return /^\/douyin\/([0-9]{5,25})$/.exec(path)?.[1] || '';
}
export function normalizeAweme(raw, expectedId) {
  if (!raw || !/^[0-9]{5,25}$/.test(expectedId)) return null;
  const id = String(raw.aweme_id || raw.awemeId || raw.id || '');
  if (id && id !== expectedId) return null;
  const images = raw.images || raw.image_post_info?.images;
  if (!Array.isArray(images) || !images.length) return null;
  if (images.length > 50) return {code: 61005};
  const addresses = value => {
    const values = typeof value === 'string' ? [value] : Array.isArray(value) ? value : value?.url_list || value?.urlList || [];
    return values.map(row => typeof row === 'string' ? row : row?.url || row?.src || '')
      .filter(Boolean).slice(0, 8).map(url => url.startsWith('//') ? 'https:' + url : url);
  };
  const result = [];
  for (const image of images) {
    const urls = addresses(image.url_list || image.urlList);
    if (!urls.length || urls.some(url => !allowedUrl(url))) return null;
    const live = addresses(image.video?.play_addr || image.video?.playAddr);
    if (live.some(url => !allowedUrl(url))) return null;
    result.push({url_list: urls, width: Math.max(0, image.width || 0), height: Math.max(0, image.height || 0),
      ...(live.length ? {video: {play_addr: {url_list: live}}} : {})});
  }
  return {aweme_id: expectedId, desc: String(raw.desc || '').slice(0, 4000),
    author: {nickname: String(raw.author?.nickname || raw.author?.nickName || '').slice(0, 200)}, images: result};
}
