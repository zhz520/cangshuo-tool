'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const directory = path.join(__dirname, '../deploy/site/tools/pdf_studio');
const library = require(path.join(directory, 'vendor/pdf-lib-1.17.1.min.js'));
const engine = require(path.join(directory, 'engine.js'));
let passed = 0;
async function check(name, work) { await work(); passed++; }
async function pdf(sizes, rotations = []) {
  const document = await library.PDFDocument.create();
  sizes.forEach((size, index) => document.addPage(size).setRotation(library.degrees(rotations[index] || 0)));
  return new Uint8Array(await document.save());
}
async function main() {
  await check('pinned distribution', () => assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(directory,'vendor/pdf-lib-1.17.1.min.js'))).digest('hex'), '0f9a5cad07941f0826586c94e089d89b918c46e5c17cf2d5a3c6f666e3bc694f'));
  await check('default range', () => assert.deepEqual(engine.pageIndices('',3),[0,1,2]));
  await check('ordered repeated ranges', () => assert.deepEqual(engine.pageIndices('3,1-2,2',3),[2,0,1,1]));
  for(const range of ['0','4','2-1','1,,2','1-3x','-1','1.5','1-201','01'])
    await check('reject range '+range,()=>assert.throws(()=>engine.pageIndices(range,3),/range/));
  await check('range character budget',()=>assert.throws(()=>engine.pageIndices('1'.repeat(1001),3),/range/));
  await check('source page budget',()=>assert.throws(()=>engine.pageIndices('',201),/pages/));
  const one=await pdf([[100,200],[300,400],[500,600]],[0,90,180]), two=await pdf([[700,800]]);
  await check('merge order and page rotation',async()=>{
    const result=await engine.process({mode:'merge',files:[{bytes:two},{bytes:one}]},library);
    const output=await library.PDFDocument.load(result.bytes);assert.equal(result.pages,4);
    assert.deepEqual(output.getPages().map(p=>p.getWidth()),[700,100,300,500]);
    assert.deepEqual(output.getPages().map(p=>p.getRotation().angle),[0,0,90,180]);
  });
  await check('extract preserves requested order and repetitions',async()=>{
    const result=await engine.process({mode:'extract',range:'3,1-2,2',files:[{bytes:one}]},library);
    const output=await library.PDFDocument.load(result.bytes);assert.deepEqual(output.getPages().map(p=>p.getWidth()),[500,100,300,300]);
  });
  await check('relative rotation preserves unselected pages',async()=>{
    const result=await engine.process({mode:'rotate',range:'2',rotation:270,files:[{bytes:one}]},library);
    const output=await library.PDFDocument.load(result.bytes);assert.equal(result.pages,3);
    assert.deepEqual(output.getPages().map(p=>p.getRotation().angle),[0,0,180]);
  });
  await check('all pages rotate and wrap',async()=>{
    const result=await engine.process({mode:'rotate',range:'',rotation:270,files:[{bytes:one}]},library);
    assert.deepEqual((await library.PDFDocument.load(result.bytes)).getPages().map(p=>p.getRotation().angle),[270,0,90]);
  });
  for (const job of [{mode:'merge',files:[{bytes:one}]},{mode:'extract',files:[{bytes:one},{bytes:two}]},{mode:'extract',files:[]},{mode:'merge',files:Array(11).fill({bytes:two})}])
    await check('file count limit',()=>assert.rejects(()=>engine.process(job,library),/files/));
  await check('per-file byte limit',()=>assert.rejects(()=>engine.process({mode:'extract',files:[{bytes:new Uint8Array(8_000_001)}]},library),/size/));
  await check('total byte limit before parsing',()=>assert.rejects(()=>engine.process({mode:'merge',files:Array(3).fill({bytes:new Uint8Array(7_000_000)})},library),/size/));
  await check('damaged PDF fixed error',()=>assert.rejects(()=>engine.process({mode:'extract',files:[{bytes:new Uint8Array(Buffer.from('%PDF-1.7\ninvalid'))}]},library),/pdf/));
  await check('encrypted PDF is refused',async()=>{
    const doc=await library.PDFDocument.create();doc.addPage();
    doc.context.trailerInfo.Encrypt=doc.context.register(doc.context.obj({Filter:'Standard',V:1,R:2}));
    const bytes = new Uint8Array(await doc.save());
    await assert.rejects(()=>engine.process({mode:'extract',files:[{bytes}]},library),/encrypted/);
  });
  await check('aggregate source pages',async()=>{
    const large=await pdf(Array.from({length:101},()=>[10,10]));
    await assert.rejects(()=>engine.process({mode:'merge',files:[{bytes:large},{bytes:large}]},library),/pages/);
  });
  const png=new Uint8Array(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64'));
  await check('PNG dimensions before decode',()=>assert.deepEqual(engine.imageSize(png),{width:1,height:1,type:'png'}));
  await check('oversized PNG before decode',()=>{const large=png.slice();new DataView(large.buffer).setUint32(16,12001);assert.throws(()=>engine.imageSize(large),/image/);});
  await check('APNG refused before decoder',()=>{const animated=png.slice();animated.set(Buffer.from('acTL'),37);assert.throws(()=>engine.imageSize(animated),/image/);});
  for(const paper of ['a4','image']) await check('images to '+paper,async()=>{
    const result=await engine.process({mode:'images',paper,files:[{bytes:png},{bytes:png}]},library);
    const doc=await library.PDFDocument.load(result.bytes);assert.equal(result.pages,2);
    assert.equal(doc.getPage(0).getWidth(),paper==='a4'?595.28:0.75);
    assert.ok(result.bytes.length>100);
  });
  await check('cancellation checkpoint',()=>assert.rejects(()=>engine.process({mode:'extract',files:[{bytes:one}]},library,()=>{throw Error('cancelled');}),/cancelled/));
  await check('invalid rotation',()=>assert.rejects(()=>engine.process({mode:'rotate',rotation:45,files:[{bytes:one}]},library),/rotation/));
  await check('no file uploads or CDN dependency',()=>{
    const app=fs.readFileSync(path.join(directory,'app.js'),'utf8');assert.doesNotMatch(app,/fetch\(|XMLHttpRequest|sendBeacon/);
    const html=fs.readFileSync(path.join(directory,'index.html'),'utf8');assert.match(html,/connect-src 'none'/);assert.doesNotMatch(html,/(?:src|href)="https?:/);
  });
  console.log(`PDF: ${passed}/${passed} checks passed`);
}
main().catch(error=>{console.error(error);process.exitCode=1;});
