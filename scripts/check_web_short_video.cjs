'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const E = require('../deploy/site/tools/short_video_parse/engine.js');
const id = 'a'.repeat(32), base = E.BASE + '/assets/' + id;
function fixture() { return {id,platform:'douyin',platformName:'抖音',sourceUrl:'https://www.douyin.com/note/1234567890',title:'test',description:'description',author:'author',durationSeconds:0,expiresAt:new Date(Date.now() + 900000).toISOString(),albumPath:E.BASE + '/albums/' + id,assets:[0,1,2].map(index => ({index,kind:index === 1 ? 'LIVE' : 'IMAGE',label:'',width:1080,height:1920,bitrate:0,source:'PLATFORM_SOURCE',previewPath:base + '/' + index,downloadPath:base + '/' + index + '?download=true',directUrl:'https://p.douyinpic.com/' + index + '.jpg'}))}; }
test('share text keeps signed queries and detects all eight exact platform hosts',() => {
  const value = E.input('复制链接 http://v.douyin.com/a/?sig=A%2FB&token=C%2BD#share，');
  assert.equal(value.url,'https://v.douyin.com/a/?sig=A%2FB&token=C%2BD');
  for (const [code,hosts] of Object.entries(E.HOSTS)) for (const host of hosts) assert.equal(E.input('https://' + host + '/share').platform,code);
});
test('ambiguous links, credentials, spoofed hosts and control input are rejected',() => {
  for (const text of ['',null,'a'.repeat(4097),'https://v.douyin.com/a\0','https://v.douyin.com/a https://b23.tv/b','https://u:p@v.douyin.com/a','https://v.douyin.com:8888/a','https://v.douyin.com./a','https://v.douyin.com.evil.example/a','https://127.0.0.1/a','javascript:alert(1)']) assert.throws(() => E.input(text),E.ToolError);
});
test('result schema requires exact opaque proxy paths, types and usable content',() => {
  assert.equal(E.result(fixture()).assets.length,3);
  for (const change of [r => r.assets[0].previewPath = 'https://evil.example/a',r => r.assets[0].downloadPath += '&url=http://localhost',r => r.assets[0].directUrl = 'javascript:alert(1)',r => r.assets[0].directUrl = 'https://u:p@evil.example/a',r => r.assets[0].index = 9,r => r.assets[0].kind = 'HTML',r => r.assets[0].width = -1,r => r.platform = 'unknown',r => r.albumPath = '//evil.example/a',r => r.assets.forEach(a => a.kind = 'COVER'),r => r.durationSeconds = -1,r => r.title = '<script>'.repeat(2000)]) {
    const value = fixture(); change(value); assert.throws(() => E.result(value),E.ToolError);
  }
});
test('expired results and oversized albums fail instead of silently truncating',() => {
  const expired = fixture(); expired.expiresAt = new Date(Date.now() - 1000).toISOString(); assert.throws(() => E.result(expired), e => e.code === 61004);
  const album = fixture(); album.assets = Array.from({length:51},(_,index) => ({...album.assets[0],index,previewPath:base + '/' + index,downloadPath:base + '/' + index + '?download=true'})); assert.throws(() => E.result(album));
});
test('ZIP selection contains only chosen static images in original order',() => {
  const media = E.result(fixture()); assert.equal(E.selectionUrl(media,[2,0]),media.albumPath + '?selection=0,2');
  for (const indices of [[],[0,0],[1],[-1],[9],['0'],[0.5]]) assert.throws(() => E.selectionUrl(media,indices));
});
test('download names reject path traversal and executable extensions',() => {
  assert.equal(E.filename('attachment; filename="douyin_video_1.mp4"','fallback.mp4'),'douyin_video_1.mp4');
  for (const header of ['attachment; filename="../../evil.mp4"','attachment; filename="run.exe"','attachment; filename="a/b.jpg"','attachment; filename="a\n.jpg"']) assert.equal(E.filename(header,'safe.jpg'),'safe.jpg');
});
test('streaming reports progress and detects truncated and excessive files',async () => {
  const signal = new AbortController().signal, progress = [];
  const data = await E.read(new Response(new Uint8Array([1,2,3]),{headers:{'Content-Length':'3'}}),10,signal,(bytes,total) => progress.push([bytes,total]));
  assert.equal(data.size,3); assert.deepEqual(progress,[[3,3]]);
  await assert.rejects(() => E.read(new Response(new Uint8Array([1,2]),{headers:{'Content-Length':'3'}}),10,signal), e => e.code === 'interrupted');
  await assert.rejects(() => E.read(new Response(new Uint8Array([1,2,3])),2,signal), e => e.code === 61005);
  await assert.rejects(() => E.read(new Response('body',{headers:{'Content-Length':'999999'}}),10,signal), e => e.code === 61005);
});
test('cancelled streams release their reader and never return a saved file',async () => {
  const controller = new AbortController(); controller.abort(); let cancelled = false;
  const body = new ReadableStream({start(c) {c.enqueue(new Uint8Array([1]));},cancel() {cancelled = true;}});
  await assert.rejects(() => E.read(new Response(body),100,controller.signal),e => e.name === 'AbortError'); assert.equal(cancelled,true);
});
test('JSON errors expose only fixed codes and sanitized request identifiers',async () => {
  await assert.rejects(() => E.json(new Response(JSON.stringify({code:61002,message:'secret upstream text',traceId:'<script>secret</script>'}),{status:422})), e => e.code === 61002 && e.trace === '' && !e.message.includes('secret'));
  await assert.rejects(() => E.json(new Response('<html>nginx error</html>',{status:502})), e => e.code === 'response');
  assert.deepEqual(await E.json(new Response('{"code":0,"data":{"available":true}}')),{available:true});
});
