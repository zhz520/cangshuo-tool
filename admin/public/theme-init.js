/* Apply the saved appearance before the app and component styles load. */
(() => {
  let theme = 'light'
  try {
    if (localStorage.getItem('cangshuo.admin.ui.theme') === 'dark') theme = 'dark'
  } catch { /* Storage may be unavailable; keep the default theme usable. */ }
  const root = document.documentElement
  root.classList.toggle('dark', theme === 'dark')
  root.dataset.adminTheme = theme
  root.style.colorScheme = theme
  root.style.backgroundColor = theme === 'dark' ? '#171717' : '#ffffff'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#171717' : '#ffffff')
})()
