/** QR web tool: renders payloads with qrcode-generator and offers SVG/PNG/JPEG export. */
(function () {
  'use strict';
  var payload = window.ToolboxQrPayload;
  var byId = function (id) { return document.getElementById(id); };
  var current = { content: '', code: null };
  // The bundled generator defaults to the low byte of each UTF-16 code unit.
  qrcode.stringToBytes = qrcode.stringToBytesFuncs['UTF-8'];

  function buildContent() {
    var type = byId('type').value;
    if (type === 'wifi') {
      return payload.buildWifi({
        ssid: byId('ssid').value,
        password: byId('password').value,
        security: byId('security').value,
        hidden: byId('hidden').value === 'on',
        eap: byId('eap').value,
        identity: byId('identity').value,
        anonymous: byId('anonymous').value,
        phase2: byId('phase2').value
      });
    }
    if (type === 'vcard') {
      return payload.buildVCard({
        name: byId('name').value,
        org: byId('org').value,
        phones: byId('phones').value,
        emails: byId('emails').value,
        addresses: byId('addresses').value
      });
    }
    return payload.buildText(byId('text').value);
  }

  function makeCode(content) {
    var code = qrcode(0, byId('ec').value);
    code.addData(content, 'Byte');
    code.make();
    return code;
  }

  function metrics(code) {
    var quiet = parseInt(byId('quiet').value, 10);
    var count = code.getModuleCount();
    var total = count + quiet * 2;
    var scale = Math.max(1, Math.floor(parseInt(byId('size').value, 10) / total));
    return { quiet: quiet, count: count, total: total, scale: scale, side: total * scale };
  }

  function drawCanvas(code) {
    var m = metrics(code);
    var canvas = byId('preview');
    canvas.width = m.side;
    canvas.height = m.side;
    var ctx = canvas.getContext('2d');
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, m.side, m.side);
    ctx.fillStyle = '#000000';
    for (var row = 0; row < m.count; row++) {
      for (var col = 0; col < m.count; col++) {
        if (code.isDark(row, col)) ctx.fillRect((col + m.quiet) * m.scale, (row + m.quiet) * m.scale, m.scale, m.scale);
      }
    }
    return m;
  }

  function svgMarkup(code) {
    var m = metrics(code);
    var paths = '';
    for (var row = 0; row < m.count; row++) {
      for (var col = 0; col < m.count; col++) {
        if (code.isDark(row, col)) paths += 'M' + ((col + m.quiet) * m.scale) + ' ' + ((row + m.quiet) * m.scale) + 'h' + m.scale + 'v' + m.scale + 'h-' + m.scale + 'z';
      }
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" width="' + m.side + '" height="' + m.side + '" viewBox="0 0 ' + m.side + ' ' + m.side + '">' +
      '<rect width="' + m.side + '" height="' + m.side + '" fill="#FFFFFF"/><path d="' + paths + '" fill="#000000"/></svg>';
  }

  function download(blob, name) {
    if (!blob) return;
    if ((navigator.userAgent || '').indexOf('CangshuoToolboxWebView/1') < 0) {
      var objectUrl = URL.createObjectURL(blob);
      var browserLink = document.createElement('a');
      browserLink.href = objectUrl;
      browserLink.download = name;
      document.body.appendChild(browserLink);
      browserLink.click();
      document.body.removeChild(browserLink);
      setTimeout(function () { URL.revokeObjectURL(objectUrl); }, 4000);
      return;
    }
    // Data URLs also reach the Android container's bounded native download handler.
    var reader = new FileReader();
    reader.onload = function () {
      var link = document.createElement('a');
      link.href = reader.result;
      link.download = name;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    };
    reader.readAsDataURL(blob);
  }

  function update() {
    var status = byId('status');
    current.code = null;
    current.content = '';
    byId('payload').value = '';
    byId('copy').disabled = true;
    ['png', 'jpeg', 'svg'].forEach(function (id) { byId(id).disabled = true; });
    byId('preview').getContext('2d').clearRect(0, 0, byId('preview').width, byId('preview').height);
    try {
      var content = buildContent();
      current.content = content;
      byId('payload').value = content;
      payload.validateContent(content);
      if (!content.length) {
        status.textContent = '输入内容后生成二维码';
        status.dataset.state = 'empty';
        return;
      }
      current.code = makeCode(content);
      var m = drawCanvas(current.code);
      status.textContent = '版本 ' + ((m.count - 17) / 4) + ' · 输出 ' + m.side + 'px · ' + m.scale + 'px/模块 · 容错 ' + byId('ec').value;
      status.dataset.state = 'ok';
      byId('copy').disabled = false;
      ['png', 'jpeg', 'svg'].forEach(function (id) { byId(id).disabled = false; });
    } catch (error) {
      current.code = null;
      status.textContent = error instanceof Error ? error.message : '内容超过当前容错等级的容量，请缩短内容或降低容错等级。';
      status.dataset.state = 'error';
    }
  }

  function toggleFields() {
    var type = byId('type').value;
    document.querySelectorAll('[data-for]').forEach(function (node) {
      node.hidden = node.getAttribute('data-for') !== type;
    });
    update();
  }

  window.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('input, select, textarea').forEach(function (node) {
      node.addEventListener('input', update);
      node.addEventListener('change', update);
    });
    byId('type').addEventListener('change', toggleFields);
    byId('copy').addEventListener('click', function () {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(current.content).catch(function () { fallbackCopy(); });
        return;
      }
      // Non-secure contexts (plain http on a LAN host) have no Clipboard API.
      fallbackCopy();
    });
    byId('png').addEventListener('click', function () {
      if (current.code) byId('preview').toBlob(function (blob) { download(blob, 'qrcode.png'); }, 'image/png');
    });
    byId('jpeg').addEventListener('click', function () {
      if (current.code) byId('preview').toBlob(function (blob) { download(blob, 'qrcode.jpg'); }, 'image/jpeg', 0.92);
    });
    byId('svg').addEventListener('click', function () {
      if (current.code) download(new Blob([svgMarkup(current.code)], { type: 'image/svg+xml' }), 'qrcode.svg');
    });
    toggleFields();
  });
  function fallbackCopy() {
    var field = byId('payload');
    field.removeAttribute('readonly');
    field.select();
    document.execCommand('copy');
    field.setAttribute('readonly', 'readonly');
  }
})();
