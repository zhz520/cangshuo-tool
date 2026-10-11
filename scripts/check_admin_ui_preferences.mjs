// Run with Node after installing the admin's locked dependencies.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'

const require = createRequire(new URL('../admin/package.json', import.meta.url))
const { createPinia, disposePinia, setActivePinia } = require('pinia')
const ts = require('typescript')
const bootstrap = readFileSync(new URL('../admin/public/theme-init.js', import.meta.url), 'utf8')
const storeSource = readFileSync(new URL('../admin/src/stores/ui.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(storeSource, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const themeKey = 'cangshuo.admin.ui.theme'
const sidebarKey = 'cangshuo.admin.ui.sidebar'

function environment(entries = {}, blocked = false) {
  const values = new Map(Object.entries(entries))
  const classes = new Set()
  const listeners = new Map()
  const root = { dataset: {}, style: {}, classList: { toggle(name, on) { on ? classes.add(name) : classes.delete(name) } } }
  const meta = { content: '', setAttribute(name, value) { this[name] = value } }
  const storage = {
    getItem(key) { if (blocked) throw new Error('Storage denied'); return values.get(key) ?? null },
    setItem(key, value) { if (blocked) throw new Error('Storage denied'); values.set(key, value) },
  }
  return {
    document: { documentElement: root, querySelector: () => meta },
    localStorage: storage,
    window: { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name) },
    root, meta, classes, values, listeners,
  }
}

for (const [saved, expected] of [[undefined, 'light'], ['light', 'light'], ['dark', 'dark'], ['invalid', 'light']]) {
  const env = environment(saved === undefined ? {} : { [themeKey]: saved })
  vm.runInNewContext(bootstrap, env)
  assert.equal(env.root.dataset.adminTheme, expected)
  assert.equal(env.root.style.colorScheme, expected)
  assert.equal(env.classes.has('dark'), expected === 'dark')
}
const blockedBootstrap = environment({}, true)
vm.runInNewContext(bootstrap, blockedBootstrap)
assert.equal(blockedBootstrap.root.dataset.adminTheme, 'light')

function createStore(env) {
  const module = { exports: {} }
  vm.runInNewContext(compiled, { ...env, require, module, exports: module.exports })
  const pinia = createPinia()
  setActivePinia(pinia)
  return { store: module.exports.useUiStore(), dispose: () => disposePinia(pinia) }
}

const env = environment({ [themeKey]: 'light', [sidebarKey]: 'collapsed', 'unrelated-token': 'unchanged' })
vm.runInNewContext(bootstrap, env)
const { store, dispose } = createStore(env)
assert.equal(store.theme, 'light')
assert.equal(store.sidebarCollapsed, true)
store.toggleTheme()
store.toggleSidebar()
assert.equal(env.values.get(themeKey), 'dark')
assert.equal(env.values.get(sidebarKey), 'expanded')
assert.equal(env.root.dataset.adminTheme, 'dark')
assert.equal(env.meta.content, '#171717')
assert.equal(env.values.get('unrelated-token'), 'unchanged')
const storageEvent = env.listeners.get('storage')
storageEvent({ storageArea: {}, key: themeKey, newValue: 'light' })
assert.equal(store.theme, 'dark', 'Ignore events from other storage areas')
storageEvent({ storageArea: env.localStorage, key: themeKey, newValue: 'light' })
assert.equal(store.theme, 'light')
assert.equal(env.root.style.colorScheme, 'light')
storageEvent({ storageArea: env.localStorage, key: sidebarKey, newValue: 'collapsed' })
assert.equal(store.sidebarCollapsed, true)
storageEvent({ storageArea: env.localStorage, key: null, newValue: null })
assert.equal(store.theme, 'light')
assert.equal(store.sidebarCollapsed, false)
dispose()
assert.equal(env.listeners.size, 0, 'Dispose cross-tab listeners with the store')

const reloadEnv = environment({ [themeKey]: 'light', [sidebarKey]: 'collapsed' })
vm.runInNewContext(bootstrap, reloadEnv)
const reloaded = createStore(reloadEnv)
assert.equal(reloaded.store.theme, 'light')
assert.equal(reloaded.store.sidebarCollapsed, true)
reloaded.dispose()

const noStorage = environment({}, true)
const fallback = createStore(noStorage)
fallback.store.toggleTheme()
fallback.store.toggleSidebar()
assert.equal(fallback.store.theme, 'dark')
assert.equal(fallback.store.sidebarCollapsed, true)
assert.equal(noStorage.root.dataset.adminTheme, 'dark')
fallback.dispose()
console.log('PASS: first paint, invalid/blocked storage, preference persistence, reload, cross-tab sync, token isolation and cleanup')
