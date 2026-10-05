'use strict';
importScripts('vendor/pdf-lib-1.17.1.min.js', 'engine.js');
self.onmessage = async function (event) {
  try {
    const result = await ToolboxPdf.process(event.data, PDFLib);
    self.postMessage({ result }, [result.bytes.buffer]);
  } catch (error) {
    const allowed = ['files','size','range','pages','image','pdf','encrypted','output','mode','paper','rotation'];
    self.postMessage({ error: allowed.includes(error.message) ? error.message : 'processing' });
  }
};
