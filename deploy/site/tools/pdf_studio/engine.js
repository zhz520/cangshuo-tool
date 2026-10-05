/* PDF processing shared by the worker and Node regression checks. */
(function (root) {
  'use strict';
  const limits = Object.freeze({ files: 10, fileBytes: 8_000_000, totalBytes: 20_000_000, outputBytes: 12_000_000, pages: 200, pixels: 12_000_000 });
  function fail(code) { throw new Error(code); }
  function pageIndices(text, count) {
    if (!Number.isInteger(count) || count < 1 || count > limits.pages) fail('pages');
    if (typeof text !== 'string' || text.length > 1000) fail('range');
    if (!text.trim()) return Array.from({ length: count }, (_, i) => i);
    const result = [];
    for (const part of text.split(',')) {
      const match = /^\s*([1-9]\d*)(?:\s*-\s*([1-9]\d*))?\s*$/.exec(part);
      if (!match) fail('range');
      const start = Number(match[1]), end = Number(match[2] || match[1]);
      if (start > end || end > count || result.length + end - start + 1 > limits.pages) fail('range');
      for (let page = start; page <= end; page++) result.push(page - 1);
    }
    return result;
  }
  function imageSize(bytes) {
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let width, height, type;
    if (bytes.length >= 33 && [137,80,78,71,13,10,26,10].every((v,i) => bytes[i] === v) &&
        String.fromCharCode(...bytes.slice(12,16)) === 'IHDR') {
      width = view.getUint32(16); height = view.getUint32(20); type = 'png';
      // Reject APNG before its decoder can allocate multiple frames.
      for (let at = 8; at + 12 <= bytes.length;) {
        const size = view.getUint32(at);
        if (size > bytes.length - at - 12) fail('image');
        if (String.fromCharCode(...bytes.slice(at + 4, at + 8)) === 'acTL') fail('image');
        at += size + 12;
      }
    } else if (bytes.length >= 4 && bytes[0] === 255 && bytes[1] === 216) {
      type = 'jpg';
      for (let at = 2; at + 4 <= bytes.length;) {
        if (bytes[at++] !== 255) fail('image');
        while (at < bytes.length && bytes[at] === 255) at++;
        const marker = bytes[at++];
        if (marker === 217 || marker === 218) break;
        if (marker === 1 || marker >= 208 && marker <= 215) continue;
        if (at + 2 > bytes.length) fail('image');
        const size = view.getUint16(at);
        if (size < 2 || at + size > bytes.length) fail('image');
        if ([192,193,194].includes(marker)) {
          if (size < 8) fail('image');
          height = view.getUint16(at + 3); width = view.getUint16(at + 5); break;
        }
        at += size;
      }
    } else fail('image');
    if (!width || !height || width * height > limits.pixels || width > 12000 || height > 12000) fail('image');
    return { width, height, type };
  }
  async function process(job, library, checkpoint = () => {}) {
    const { files, mode } = job;
    if (!['merge','extract','rotate','images'].includes(mode)) fail('mode');
    if (!Array.isArray(files) || !files.length || files.length > limits.files) fail('files');
    if ((mode === 'extract' || mode === 'rotate') && files.length !== 1) fail('files');
    if (mode === 'merge' && files.length < 2) fail('files');
    let total = 0;
    for (const file of files) {
      if (!(file.bytes instanceof Uint8Array) || !file.bytes.length || file.bytes.length > limits.fileBytes) fail('size');
      total += file.bytes.length;
    }
    if (total > limits.totalBytes) fail('size');
    const output = await library.PDFDocument.create({ updateMetadata: false });
    let inputPages = 0;
    for (const file of files) {
      checkpoint();
      if (mode === 'images') {
        const info = imageSize(file.bytes);
        const image = await (info.type === 'png' ? output.embedPng(file.bytes) : output.embedJpg(file.bytes));
        if (!['a4','image'].includes(job.paper)) fail('paper');
        const dimensions = job.paper === 'a4' ? [595.28, 841.89] : [info.width * 0.75, info.height * 0.75];
        const page = output.addPage(dimensions);
        const margin = job.paper === 'a4' ? 24 : 0;
        const scale = Math.min((dimensions[0] - 2 * margin) / info.width, (dimensions[1] - 2 * margin) / info.height);
        const width = info.width * scale, height = info.height * scale;
        page.drawImage(image, { x: (dimensions[0]-width)/2, y: (dimensions[1]-height)/2, width, height });
        inputPages++;
      } else {
        if (String.fromCharCode(...file.bytes.slice(0,5)) !== '%PDF-') fail('pdf');
        let source;
        try { source = await library.PDFDocument.load(file.bytes, { updateMetadata: false, throwOnInvalidObject: true, ignoreEncryption: true }); }
        catch (_) { fail('pdf'); }
        // Detection only: never process an encrypted document or pretend its content was decrypted.
        if (source.isEncrypted) fail('encrypted');
        let count;
        try { count = source.getPageCount(); } catch (_) { fail('pdf'); }
        if (count < 1 || inputPages + count > limits.pages) fail('pages');
        inputPages += count;
        const selected = mode === 'merge' ? pageIndices('', count) : pageIndices(job.range || '', count);
        if (output.getPageCount() + selected.length > limits.pages) fail('pages');
        // Copy page content into a fresh document; document scripts/attachments/outlines are not carried over.
        const rotationTargets = new Set(selected);
        if (mode === 'rotate') {
          if (![90,180,270].includes(job.rotation)) fail('rotation');
          // Rotation exports the whole document, applying the angle only to selected page numbers.
          const all = await output.copyPages(source, source.getPageIndices());
          for (let i=0; i<all.length; i++) {
            if (rotationTargets.has(i)) all[i].setRotation(library.degrees(((all[i].getRotation().angle + job.rotation) % 360 + 360) % 360));
            output.addPage(all[i]);
          }
        } else for (const page of await output.copyPages(source, selected)) output.addPage(page);
      }
      checkpoint();
    }
    const bytes = await output.save({ addDefaultPage: false, updateFieldAppearances: false, objectsPerTick: 20 });
    if (!bytes.length || bytes.length > limits.outputBytes) fail('output');
    checkpoint();
    return { bytes, pages: output.getPageCount(), inputPages };
  }
  const api = { limits, pageIndices, imageSize, process };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.ToolboxPdf = api;
})(typeof self === 'undefined' ? globalThis : self);
