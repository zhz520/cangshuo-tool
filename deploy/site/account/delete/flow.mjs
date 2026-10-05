// No browser storage, automatic refresh or retry of destructive requests.
export function createDeletionFlow(request, render) {
  let session = null, busy = false, generation = 0, controller = null;
  const show = (message = '') => render({ email: session?.email ?? null, busy, message });
  function clear(message = '') {
    generation++; controller?.abort(); controller = null; session = null; busy = false; show(message);
  }
  async function run(action) {
    if (busy) return;
    const own = ++generation; busy = true; controller = new AbortController(); show();
    try { await action(controller.signal, own); }
    catch (error) {
      if (own === generation) show(error?.code === 'AUTH' ? '身份核实失败或登录已失效，请检查账号密码并重新登录。 / Authentication failed or expired. Sign in again.' :
        error?.code === 'LIMIT' ? '请求过于频繁，请稍后重试。 / Too many requests. Try later.' :
        '请求未能确认完成。若正在删除，请重新登录确认账号是否仍存在；不会自动重复删除。 / Result unconfirmed. Sign in to check whether the account still exists.');
    } finally { if (own === generation) { busy = false; controller = null; show(); } }
  }
  return {
    login(email, password) {
      return run(async (signal, own) => {
        const result = await request('auth/login', 'POST', { email: email.trim(), password }, null, signal);
        if (own !== generation) return;
        if (!result.data || typeof result.data.accessToken !== 'string' || !result.data.accessToken ||
            typeof result.data.refreshToken !== 'string' || !result.data.refreshToken ||
            typeof result.data.user?.email !== 'string' || result.data.user.email.length > 254) throw new Error('Invalid response');
        session = { email: result.data.user.email, access: result.data.accessToken, refresh: result.data.refreshToken };
        show('身份已核实，请确认删除。 / Signed in. Confirm deletion.');
      });
    },
    remove(password, confirmed) {
      if (!session || !confirmed || !password || busy) return Promise.resolve();
      return run(async (signal, own) => {
        await request('auth/me', 'DELETE', { email: session.email, password }, session.access, signal);
        if (own === generation) clear('账号及云端数据已删除。请在其他设备清理本机副本。 / Account and cloud data deleted. Remove local copies on other devices.');
      });
    },
    async cancel() {
      const refresh = session?.refresh; clear('已退出本页面。 / Signed out of this page.');
      if (refresh) { try { await request('auth/logout', 'POST', { refreshToken: refresh }, null, new AbortController().signal); } catch { /* Already cleared locally. */ } }
    },
    clear,
  };
}

export function createRequest(base, fetcher = fetch) {
  return async (path, method, body, token, signal) => {
    const controller = new AbortController();
    const abort = () => controller.abort(); signal.addEventListener('abort', abort, { once: true });
    if (signal.aborted) abort();
    const timer = setTimeout(abort, 20_000);
    try {
      const response = await fetcher(`${base}/${path}`, { method, body: JSON.stringify(body), signal: controller.signal,
        credentials: 'omit', redirect: 'error', cache: 'no-store', referrerPolicy: 'no-referrer',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) } });
      if (Number(response.headers.get('Content-Length')) > 16_384 || !response.body) throw new Error('Invalid response');
      const reader = response.body.getReader(); const chunks = []; let size = 0;
      try {
        while (true) { const { value, done } = await reader.read(); if (done) break;
          size += value.length; if (size > 16_384) throw new Error('Invalid response'); chunks.push(value); }
      } finally { await reader.cancel(); }
      const bytes = new Uint8Array(size); let offset = 0;
      for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
      const result = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
      if (!result || !Number.isInteger(result.code) || typeof result.traceId !== 'string') throw new Error('Invalid response');
      if (!response.ok || result.code !== 0) { const error = new Error('Request failed');
        error.code = response.status === 401 ? 'AUTH' : response.status === 429 ? 'LIMIT' : 'FAILED'; throw error; }
      return result;
    } finally { clearTimeout(timer); signal.removeEventListener('abort', abort); }
  };
}
