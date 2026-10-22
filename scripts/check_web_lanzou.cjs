'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const core = require('../deploy/site/tools/lanzou_direct/engine.js');

test('extracts one supported link, embedded password and preserves unrelated signed query',()=>{
  const value = core.validate({text:'复制 https://www.lanzouf.com/iAbC123?pwd=8xYz&signature=A%2FB 提取码：8xYz',password:''});
  assert.deepEqual(value.value,{text:'https://www.lanzouf.com/iAbC123?signature=A%2FB',password:'8xYz'});
  assert.equal(value.host,'www.lanzouf.com');
  for(const host of ['lanzou.com','www.lanzoux.com','ww1.lanzouf.com','api.lanzoui.com','apifile.lanzouw.com'])
    assert.ok(core.owned.test(host),host);
});
test('rejects ambiguous, spoofed, credentialed and controlled input',()=>{
  assert.equal(core.validate({text:'',password:''}).error,'urlRequired');
  assert.equal(core.validate({text:null,password:''}).error,'urlRequired');
  for(const text of ['a'.repeat(4097),'javascript:alert(1)','https://lanzoux.com/a\0',
    'https://lanzoux.com/a https://lanzouw.com/b','https://u:p@lanzoux.com/a','https://lanzoux.com:8443/a',
    'https://lanzoux.com./a','https://lanzoux.com.evil.example/a','https://a.b.lanzou.com/a','https://127.0.0.1/a'])
    assert.equal(core.validate({text,password:''}).error,'urlInvalid',String(text));
  assert.equal(core.validate({text:'https://lanzoux.com/a',password:'bad\r\nHost: evil'}).error,'passwordInvalid');
  assert.equal(core.validate({text:'https://lanzoux.com/a',password:'x'.repeat(65)}).error,'passwordInvalid');
});
test('explicit password overrides the share text and successful results require exact states',()=>{
  assert.equal(core.validate({text:'https://lanzoux.com/a 密码：abcd',password:'explicit'}).value.password,'explicit');
  const data={code:0,traceId:'a'.repeat(32),data:{shareUrl:'https://lanzoux.com/a',fileName:'a.zip',fileSize:'1 MB',state:'DIRECT',directUrl:'https://cdn.example.com/a.zip',downloadPageUrl:'https://download.lanzouf.com/file/x',verificationRequired:false,rateLimited:false,cached:false,expiresInSeconds:180}};
  assert.equal(core.result(data).state,'DIRECT'); assert.equal(core.safeTrace(data.traceId),'a'.repeat(32));
  for(const state of ['direct','SUCCESS','']) assert.equal(core.result({...data,data:{...data.data,state}}),null);
  assert.equal(core.safeTrace('<script>'),'');
});
test('fixed business codes map to local UI messages without provider text',()=>{
  assert.deepEqual([10001,10007,62001,62002,62003,62004,62005,62006,62007,62008,62009].map(core.errorKey),
    ['urlInvalid','rateLimited','urlInvalid','passwordRequired','passwordRejected','fileUnavailable','verification','providerLimited','providerFailed','timeout','folderUnsupported']);
  assert.equal(core.errorKey(999999),'providerFailed');
});
test('capabilities and official fallback stay explicit',()=>{
  assert.ok(core.capabilities({code:0,data:{available:true,verificationMode:'OFFICIAL_PAGE_FALLBACK'}}));
  assert.equal(core.capabilities({code:0,data:{available:'true'}}),null);
  const official=core.result({code:0,data:{shareUrl:'https://lanzoux.com/a',fileName:'b.txt',fileSize:'2 KB',state:'OFFICIAL_PAGE',directUrl:'',downloadPageUrl:'https://download.lanzouf.com/file/y',verificationRequired:true,rateLimited:false,cached:true,expiresInSeconds:0}});
  assert.equal(official.state,'OFFICIAL_PAGE'); assert.equal(official.directUrl,''); assert.equal(official.verificationRequired,true);
});
