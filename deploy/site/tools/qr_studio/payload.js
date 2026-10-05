/** Payload builders shared by the QR web tool; no DOM access so node can test them. */
(function (root) {
  'use strict';
  var WIFI_RESERVED = '\\;,:"';
  var VCARD_RESERVED = '\\;,';
  function escapeWifi(value) {
    var out = '';
    for (var i = 0; i < value.length; i++) {
      var ch = value.charAt(i);
      if (WIFI_RESERVED.indexOf(ch) >= 0) out += '\\';
      if (ch !== '\n' && ch !== '\r') out += ch;
    }
    return out;
  }
  function escapeVCard(value) {
    var out = '';
    for (var i = 0; i < value.length; i++) {
      var ch = value.charAt(i);
      if (ch === '\n') out += '\\n';
      else if (ch === '\r') continue;
      else {
        if (VCARD_RESERVED.indexOf(ch) >= 0) out += '\\';
        out += ch;
      }
    }
    return out;
  }
  function buildText(text) { return text; }
  function validateContent(text) {
    if (text.length > 6000) throw new Error('内容过长，请缩短后重试。');
    for (var i = 0; i < text.length; i++) {
      var unit = text.charCodeAt(i);
      if (unit >= 0xd800 && unit <= 0xdbff) {
        var next = text.charCodeAt(++i);
        if (!(next >= 0xdc00 && next <= 0xdfff)) throw new Error('内容含不完整 Unicode 字符。');
      } else if (unit >= 0xdc00 && unit <= 0xdfff) throw new Error('内容含不完整 Unicode 字符。');
    }
    if (new TextEncoder().encode(text).length > 2953) throw new Error('内容超过二维码最大容量，请缩短后重试。');
  }
  function buildWifi(o) {
    o = o || {};
    if (!o.ssid) throw new Error('请输入网络名称 (SSID)。');
    validateContent(o.ssid);
    if (new TextEncoder().encode(o.ssid).length > 32) throw new Error('网络名称不能超过 32 个 UTF-8 字节。');
    var security = o.security || 'WPA';
    var out = 'WIFI:T:' + security + ';S:' + escapeWifi(o.ssid || '') + ';';
    if (security !== 'nopass') out += 'P:' + escapeWifi(o.password || '') + ';';
    if (security === 'WPA-EAP' || security === 'WPA3-EAP') {
      out += 'E:' + (o.eap || 'TTLS') + ';';
      if (o.identity) out += 'I:' + escapeWifi(o.identity) + ';';
      if (o.anonymous) out += 'A:' + escapeWifi(o.anonymous) + ';';
      if (o.phase2) out += 'PH2:' + o.phase2 + ';';
    }
    out += 'H:' + (o.hidden ? 'true' : 'false') + ';;';
    return out;
  }
  function pushValues(lines, prefix, value, suffix) {
    (value || '').split(/\r?\n/).forEach(function (line) {
      var trimmed = line.trim();
      if (trimmed) lines.push(prefix + escapeVCard(trimmed) + (suffix || ''));
    });
  }
  function buildVCard(o) {
    o = o || {};
    if (!(o.name || '').trim()) throw new Error('请输入联系人姓名。');
    var lines = ['BEGIN:VCARD', 'VERSION:3.0'];
    lines.push('FN:' + escapeVCard((o.name || '').trim()));
    if (o.org) lines.push('ORG:' + escapeVCard(o.org.trim()));
    pushValues(lines, 'TEL;TYPE=CELL:', o.phones);
    pushValues(lines, 'EMAIL;TYPE=INTERNET:', o.emails);
    pushValues(lines, 'ADR;TYPE=HOME:;;', o.addresses, ';;;;');
    lines.push('END:VCARD');
    return lines.join('\r\n');
  }
  root.ToolboxQrPayload = { buildText: buildText, buildWifi: buildWifi, buildVCard: buildVCard, validateContent: validateContent };
})(typeof window !== 'undefined' ? window : globalThis);
if (typeof module !== 'undefined' && module.exports) {
  module.exports = (typeof window !== 'undefined' ? window : globalThis).ToolboxQrPayload;
}
