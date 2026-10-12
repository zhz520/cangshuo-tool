(function (root) {
  'use strict';
  const EMAIL = /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\.[A-Za-z]{2,63}$/;
  const PHONE = /^(?:\+86)?1[3-9][0-9]{9}$/;
  function validate(input) {
    const account = String(input.account || '').trim();
    if (!account || account.length > 254 || (!PHONE.test(account) && (!EMAIL.test(account) || account.includes('..')))) return { error: 'accountInvalid', field: 'account' };
    const password = String(input.password || '');
    if (!password.trim() || password.length > 128) return { error: 'passwordInvalid', field: 'password' };
    const raw = String(input.steps || '').trim();
    if (!/^[0-9]{1,5}$/.test(raw) || Number(raw) < 1 || Number(raw) > 50000) return { error: 'stepsInvalid', field: 'steps' };
    if (input.consent !== true) return { error: 'consentInvalid', field: 'consent' };
    return { value: { account, password, steps: Number(raw), consent: true } };
  }
  function isDate(value) {
    return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
      && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
  }
  function capabilities(envelope) {
    const data = envelope && envelope.data;
    if (!envelope || envelope.code !== 0 || !data || typeof data.available !== 'boolean'
      || !isDate(data.date) || data.minSteps !== 1 || data.maxSteps !== 50000
      || data.cooldownSeconds !== 60 || data.timeoutSeconds !== 30) throw new Error('invalidResponse');
    return data;
  }
  function receipt(envelope, expectedSteps) {
    const data = envelope && envelope.data;
    if (!envelope || envelope.code !== 0 || !data || data.status !== 'ACCEPTED'
      || data.steps !== expectedSteps || !Number.isInteger(data.steps) || data.steps < 1 || data.steps > 50000
      || !isDate(data.date) || typeof data.submittedAt !== 'string' || !Number.isFinite(Date.parse(data.submittedAt))
      || typeof data.accountMasked !== 'string' || data.accountMasked.length > 254 || !data.accountMasked.includes('***')
      || typeof data.boundDevice !== 'boolean' || data.wechatVerified !== false) throw new Error('invalidResponse');
    return data;
  }
  function errorKey(code) {
    return ({10001:'invalidRequest',10007:'rateLimited',10008:'unavailable',40001:'invalidRequest',
      60001:'credentialsRejected',60002:'verificationRequired',60003:'providerFailed',60004:'rateLimited',
      60005:'uncertain',60006:'notConfigured',60007:'cooldown'})[code] || 'uncertain';
  }
  function safeTrace(envelope) {
    return envelope && typeof envelope.traceId === 'string' && /^[0-9a-f]{32}$/.test(envelope.traceId) ? envelope.traceId : '';
  }
  const api = { validate, capabilities, receipt, errorKey, safeTrace };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.ZeppSteps = api;
})(typeof window !== 'undefined' ? window : globalThis);
