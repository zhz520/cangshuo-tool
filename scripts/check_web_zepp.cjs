'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const core = require('../deploy/site/tools/zepp_steps/engine.js');
const input = {account:'test@example.com',password:' a+b&%=中 ',steps:'10000',consent:true};
test('email and mainland phone validation preserves passwords',()=>{
  assert.deepEqual(core.validate(input).value,{...input,steps:10000});
  for(const account of ['13812345678','+8613812345678','Name@EXAMPLE.COM']) assert.ok(core.validate({...input,account}).value);
  for(const account of ['+12025550111','a..b@example.com','a@example','1381234567','a\n@example.com']) assert.equal(core.validate({...input,account}).error,'accountInvalid');
});
test('strict integer, consent and empty-password boundaries',()=>{
  for(const steps of ['','0','50001','1.5','1e4','-1','999999','1,000']) assert.equal(core.validate({...input,steps}).error,'stepsInvalid');
  for(const steps of ['1','50000']) assert.ok(core.validate({...input,steps}).value);
  assert.equal(core.validate({...input,consent:false}).error,'consentInvalid');
  assert.equal(core.validate({...input,password:'   '}).error,'passwordInvalid');
});
const caps={code:0,data:{available:true,date:'2026-10-12',minSteps:1,maxSteps:50000,cooldownSeconds:60,timeoutSeconds:30}};
test('capabilities reject incompatible ranges and invalid calendar dates',()=>{
  assert.equal(core.capabilities(caps).available,true);
  for(const data of [{...caps.data,date:'2026-02-30'},{...caps.data,maxSteps:99999},{...caps.data,available:'true'}]) assert.throws(()=>core.capabilities({code:0,data}));
});
const receipt={code:0,traceId:'a'.repeat(32),data:{status:'ACCEPTED',steps:10000,date:'2026-10-12',submittedAt:'2026-10-12T12:00:00Z',accountMasked:'te***@example.com',boundDevice:true,wechatVerified:false}};
test('receipts require the exact requested step count and no unverifiable WeChat success',()=>{
  assert.equal(core.receipt(receipt,10000).steps,10000);
  for(const data of [{...receipt.data,steps:9999},{...receipt.data,wechatVerified:true},{...receipt.data,status:'success'},{...receipt.data,accountMasked:'test@example.com'}]) assert.throws(()=>core.receipt({code:0,data},10000));
});
test('fixed error messages and trace identifiers exclude provider-controlled content',()=>{
  assert.equal(core.errorKey(60001),'credentialsRejected');assert.equal(core.errorKey(60005),'uncertain');
  assert.equal(core.errorKey(123456),'uncertain');assert.equal(core.safeTrace(receipt),'a'.repeat(32));
  assert.equal(core.safeTrace({traceId:'<script>secret</script>'}),'');
});
