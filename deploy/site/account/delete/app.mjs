import { createDeletionFlow, createRequest } from './flow.mjs';
const ids = ['login', 'email', 'password', 'loginButton', 'deletion', 'account', 'confirmPassword', 'confirm', 'deleteButton', 'cancel', 'status'];
const ui = Object.fromEntries(ids.map(id => [id, document.getElementById(id)]));
const local = ['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);
const request = createRequest(local ? `${location.origin}/api/v1` : 'https://toolapi.zhzgo.cn/api/v1');
const flow = createDeletionFlow(request, state => {
  ui.login.hidden = !!state.email; ui.deletion.hidden = !state.email; ui.account.textContent = state.email ?? '';
  ui.loginButton.disabled = ui.deleteButton.disabled = ui.cancel.disabled = state.busy;
  ui.email.disabled = ui.password.disabled = ui.confirmPassword.disabled = ui.confirm.disabled = state.busy;
  if (state.message) ui.status.textContent = state.message;
  if (!state.email) { ui.confirmPassword.value = ''; ui.confirm.checked = false; }
});
ui.login.addEventListener('submit', event => {
  event.preventDefault(); const password = ui.password.value; ui.password.value = ''; ui.status.textContent = '';
  void flow.login(ui.email.value, password);
});
ui.deletion.addEventListener('submit', event => {
  event.preventDefault(); const password = ui.confirmPassword.value; ui.confirmPassword.value = ''; ui.status.textContent = '';
  void flow.remove(password, ui.confirm.checked);
});
ui.cancel.addEventListener('click', () => { void flow.cancel(); });
document.addEventListener('visibilitychange', () => { if (document.hidden) { ui.password.value = ''; ui.confirmPassword.value = ''; } });
addEventListener('pagehide', () => { ui.password.value = ''; ui.confirmPassword.value = ''; ui.email.value = ''; flow.clear(); });
