import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {allowedHost, allowedUrl, publicAddress, requestId, normalizeAweme} from '../deploy/media-browser/policy.mjs';
import {startProxy} from '../deploy/media-browser/proxy.mjs';

test('browser and proxy permit only owned HTTPS hosts and public addresses', () => {
  assert.equal(allowedUrl('https://p3.douyinpic.com/a.jpg'), true);
  assert.equal(allowedUrl('https://lf-waf-js.byted-static.com/public.js'), true);
  for (const input of ['https://douyin.com.evil.example/a', 'http://www.douyin.com/a', 'https://u:p@www.douyin.com/a', 'https://www.douyin.com:444/a', 'file:///etc/passwd', 'https://127.0.0.1/a']) assert.equal(allowedUrl(input), false);
  for (const host of ['www.douyin.com.', 'www..douyin.com', 'DOUYIN.com', 'localhost', 'evil.example']) assert.equal(allowedHost(host), false);
  for (const address of ['0.0.0.0','127.0.0.1','10.1.1.1','169.254.169.254','172.16.0.1','192.168.0.1','100.64.0.1','198.18.0.1','203.0.113.1','224.0.0.1','::1','::ffff:8.8.8.8','2001:db8::1','2002:808:808::1']) assert.equal(publicAddress(address), false);
  assert.equal(publicAddress('8.8.8.8'), true); assert.equal(publicAddress('2606:4700:4700::1111'), true);
});
test('typed browser endpoint accepts only one work ID, never URLs or injected queries', () => {
  assert.equal(requestId('/douyin/7589870649747125477'), '7589870649747125477');
  for (const input of ['/douyin/a','/douyin/12345?url=http://localhost','/douyin/../12345','/douyin/12345/','/douyin/'+'1'.repeat(26),'/json/version']) assert.equal(requestId(input), '');
});
test('browser extracts a complete ordered album with exact live associations and unchanged signatures', () => {
  const raw = {awemeId:'12345', desc:'test', images:[{urlList:['//p.douyinpic.com/a.jpg?sign=A%2FB'], video:{playAddr:[{src:'https://v.douyinvod.com/live.mp4'}]}},{urlList:['https://p.douyinpic.com/b.webp']}]};
  const result = normalizeAweme(raw,'12345');
  assert.equal(result.images.length,2); assert.equal(result.images[0].url_list[0],'https://p.douyinpic.com/a.jpg?sign=A%2FB');
  assert.equal(result.images[0].video.play_addr.url_list[0],'https://v.douyinvod.com/live.mp4');
  assert.equal(result.images[1].video,undefined);
  assert.equal(normalizeAweme(raw,'99999'),null);
});
test('partial, oversized or unowned albums are rejected, rather than returned as success', () => {
  const image = {urlList:['https://p.douyinpic.com/a.jpg']};
  assert.equal(normalizeAweme({images:[image,{}]},'12345'),null);
  assert.equal(normalizeAweme({images:[{urlList:['https://evil.example/a.jpg']}]},'12345'),null);
  assert.equal(normalizeAweme({images:[{...image,video:{playAddr:['https://evil.example/live.mp4']}}]},'12345'),null);
  assert.deepEqual(normalizeAweme({images:new Array(51).fill(image)},'12345'),{code:61005});
  assert.equal(normalizeAweme({images:[]},'12345'),null);
});
test('loopback proxy denies regular HTTP requests and private or spoofed CONNECT targets', async () => {
  const proxy = await startProxy();
  try {
    assert.equal((await fetch(proxy.url + '/?url=http://localhost')).status,403);
    for (const target of ['127.0.0.1:443','www.douyin.com:80','douyin.com.evil.example:443','user:pass@www.douyin.com:443']) {
      const code = await new Promise((resolve,reject) => {
        const request = http.request(proxy.url,{method:'CONNECT',path:target});
        request.once('connect',(response,socket)=>{socket.destroy();resolve(response.statusCode);});
        request.once('error',reject);request.end();
      });
      assert.equal(code,403);
    }
  } finally {await proxy.close();}
});
