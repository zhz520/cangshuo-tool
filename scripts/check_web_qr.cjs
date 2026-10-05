// Regression checks use the actual page scripts; no dependencies or files are generated.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../deploy/site/tools/qr_studio');
const payload = require(path.join(root, 'payload.js'));
let checks = 0;
function check(name, action) { action(); checks++; }
check('text preserves Unicode and spaces', () => assert.equal(payload.buildText(' 中文 😀\n '), ' 中文 😀\n '));
check('Wi-Fi removes line breaks and escapes reserved characters', () => assert.equal(payload.buildWifi({ssid:'A;\\\nB', password:'p:1\r\n', security:'WPA', hidden:true}), 'WIFI:T:WPA;S:A\\;\\\\B;P:p\\:1;H:true;;'));
check('open Wi-Fi omits password', () => assert.ok(!payload.buildWifi({ssid:'QA', security:'nopass', password:'synthetic'}).includes('P:')));
check('enterprise fields are escaped', () => assert.ok(payload.buildWifi({ssid:'QA', security:'WPA3-EAP', identity:'test;example', phase2:'GTC'}).includes('I:test\\;example;PH2:GTC;')));
check('vCard escapes CRLF and preserves multiple values', () => {
  const text = payload.buildVCard({name:'验收\nQA', org:'A;B', phones:'10001\n10002', emails:'qa@example.invalid\nqb@example.invalid', addresses:'A\nB'});
  assert.ok(text.includes('FN:验收\\nQA\r\nORG:A\\;B\r\n'));
  assert.equal((text.match(/TEL;TYPE=CELL:/g)||[]).length, 2);
  assert.equal((text.match(/EMAIL;TYPE=INTERNET:/g)||[]).length, 2);
  assert.ok(text.includes('ADR;TYPE=HOME:;;A;;;;\r\n'));
});
check('exact UTF-8 capacity allowed', () => payload.validateContent('a'.repeat(2953)));
check('UTF-8 capacity counts multibyte text', () => assert.throws(() => payload.validateContent('中'.repeat(985))));
check('oversized input rejected', () => assert.throws(() => payload.validateContent('a'.repeat(6001))));
check('valid surrogate pair allowed', () => payload.validateContent('😀'));
check('unpaired high and low surrogates rejected', () => { assert.throws(() => payload.validateContent('\ud800')); assert.throws(() => payload.validateContent('\udc00')); });
check('Wi-Fi requires a network name', () => assert.throws(() => payload.buildWifi({ssid:''})));
check('Wi-Fi accepts exact 32-byte boundary', () => assert.ok(payload.buildWifi({ssid:'中'.repeat(10)+'ab'}).startsWith('WIFI:')));
check('Wi-Fi rejects 33-byte names', () => assert.throws(() => payload.buildWifi({ssid:'中'.repeat(11)})));
check('vCard requires a name', () => assert.throws(() => payload.buildVCard({name:' '})));

// A tiny DOM/canvas stand-in exercises bindings and export events in the real app.js.
const nodes = {};
const inputs = {type:'text', text:'', ec:'L', size:'512', quiet:'4', ssid:'', password:'', security:'WPA', hidden:'', eap:'TTLS', phase2:'MSCHAPV2', identity:'', anonymous:'', name:'', org:'', phones:'', emails:'', addresses:'', payload:''};
Object.entries(inputs).forEach(([id,value]) => nodes[id] = {value, listeners:{}, addEventListener(event, action){this.listeners[event]=action;}});
['status','png','jpeg','svg','copy'].forEach(id => nodes[id] = {dataset:{}, disabled:false, listeners:{}, addEventListener(event, action){this.listeners[event]=action;}});
const canvasContext = {fillRect(){}, clearRect(){}};
nodes.preview = {width:320,height:320,getContext:()=>canvasContext,toBlob:(cb,type)=>cb({type})};
let lastDownload = null;
const sandbox = {TextEncoder, console, ToolboxQrPayload:payload, navigator:{userAgent:'CangshuoToolboxWebView/1'}, Blob, FileReader:class {readAsDataURL(blob){this.result='data:'+blob.type+';base64,AA==';this.onload();}},
  document:{getElementById:id=>nodes[id],querySelectorAll:selector=>selector==='[data-for]'?[]:Object.values(nodes).filter(n=>n.addEventListener),body:{appendChild(){},removeChild(){}},createElement:()=>({click(){lastDownload={href:this.href,name:this.download};}})}};
sandbox.window = {ToolboxQrPayload:payload,addEventListener(event, action){if(event==='DOMContentLoaded')sandbox.start=action;}};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(root, 'qrcode.js'),'utf8'), sandbox);
vm.runInContext(fs.readFileSync(path.join(root, 'app.js'),'utf8'), sandbox);
sandbox.start();
check('empty content disables exports', () => { assert.equal(nodes.status.dataset.state,'empty'); assert.equal(nodes.png.disabled,true); });
nodes.text.value = '中文😀'; nodes.text.listeners.input();
check('actual generator uses UTF-8 bytes', () => assert.deepEqual(Array.from(sandbox.qrcode.stringToBytes('中文😀')), Array.from(Buffer.from('中文😀'))));
check('version reports version rather than module count', () => { assert.ok(nodes.status.textContent.startsWith('版本 1 ·')); assert.equal(nodes.png.disabled,false); });
check('PNG event yields data URL and filename', () => { nodes.png.listeners.click(); assert.equal(lastDownload.name,'qrcode.png'); assert.ok(lastDownload.href.startsWith('data:image/png;base64,')); });
check('JPEG event yields correct MIME', () => { nodes.jpeg.listeners.click(); assert.ok(lastDownload.href.startsWith('data:image/jpeg;base64,')); });
check('SVG event yields correct MIME', () => { nodes.svg.listeners.click(); assert.ok(lastDownload.href.startsWith('data:image/svg+xml;base64,')); });
nodes.text.value = 'x'.repeat(2954); nodes.text.listeners.input();
check('failed input disables exports and discards old code', () => { assert.equal(nodes.status.dataset.state,'error'); assert.equal(nodes.png.disabled,true); lastDownload=null; nodes.png.listeners.click(); assert.equal(lastDownload,null); });
nodes.text.value = 'recover'; nodes.text.listeners.input();
check('valid input recovers after failure', () => assert.equal(nodes.status.dataset.state,'ok'));
console.log(`Web QR regression checks: ${checks} passed`);
