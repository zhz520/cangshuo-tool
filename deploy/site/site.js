(() => {
  "use strict";

  const THEME_KEY = "cs-site-theme";
  const root = document.documentElement;
  const categories = {
    ALL: "全部",
    QR: "二维码",
    PDF: "PDF 文档",
    LIFE: "生活",
    NETWORK: "网络",
    IMAGE: "图片",
    TEXT: "文本",
    CALC: "计算",
    DEV: "开发",
    CONVERT: "转换",
    DEVICE: "设备",
    SENSOR: "传感器",
    OTHER: "其他"
  };
  const iconPaths = {
    arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    qr: '<rect x="3.5" y="3.5" width="6" height="6" rx="1"/><rect x="14.5" y="3.5" width="6" height="6" rx="1"/><rect x="3.5" y="14.5" width="6" height="6" rx="1"/><path d="M14.5 14.5h2v2h-2zM19 14.5h1.5M14.5 19h6M18 17.5v3.5"/>',
    pdf: '<path d="M6.5 3.5h7l4 4v13h-11z"/><path d="M13.5 3.5v4h4M9 12h6M9 15.5h6M9 19h3"/>',
    video: '<rect x="3" y="5" width="13" height="14" rx="2"/><path d="m16 10 5-3v10l-5-3zM7 9l3 3-3 3"/>',
    link: '<path d="M9.5 14.5 14.5 9.5M7.8 17.2l-1.1 1.1a3.5 3.5 0 0 1-5-5l3.1-3.1a3.5 3.5 0 0 1 5 0M16.2 6.8l1.1-1.1a3.5 3.5 0 0 1 5 5l-3.1 3.1a3.5 3.5 0 0 1-5 0"/>',
    steps: '<path d="M5 19V9M9 19V5M13 19v-7M17 19V7M3 19h18"/><path d="m15.5 9.5 2-2 2 2"/>',
    image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8" cy="9" r="1.5"/><path d="m21 15-5-5L5 20"/>',
    code: '<path d="m8 8-4 4 4 4M16 8l4 4-4 4M13.5 5l-3 14"/>',
    grid: '<rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4 4"/>',
    moon: '<path d="M20.4 15.2A8.5 8.5 0 0 1 8.8 3.6 8.5 8.5 0 1 0 20.4 15.2Z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    external: '<path d="M14 4h6v6M20 4l-9 9"/><path d="M18 13v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h5"/>',
    refresh: '<path d="M20 6v5h-5M4 18v-5h5"/><path d="M18.4 9A7 7 0 0 0 6.2 6.2L4 11M5.6 15A7 7 0 0 0 17.8 17.8L20 13"/>',
    layers: '<path d="m12 3 9 5-9 5-9-5zM3 12l9 5 9-5M3 16l9 5 9-5"/>',
    shield: '<path d="M12 3 5 6v5c0 4.6 2.9 8.1 7 10 4.1-1.9 7-5.4 7-10V6z"/><path d="m9 12 2 2 4-5"/>',
    smartphone: '<rect x="7" y="2.5" width="10" height="19" rx="2"/><path d="M10 5h4M11 18.5h2"/>',
    tool: '<path d="M14.5 6.5a4 4 0 0 0-5 5L4 17l3 3 5.5-5.5a4 4 0 0 0 5-5l-3 3-3-3z"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    empty: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4 4M8.5 11h5"/>'
  };

  function icon(name, className = "") {
    const path = iconPaths[name] || iconPaths.tool;
    return `<svg${className ? ` class="${className}"` : ""} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
  }

  function safeText(value, fallback = "") {
    return typeof value === "string" && value.trim() ? value.trim().slice(0, 500) : fallback;
  }

  function normalizeTool(tool) {
    if (!tool || typeof tool !== "object") return null;
    const code = safeText(tool.code);
    if (!/^[a-z0-9_]{1,80}$/.test(code)) return null;
    const categoryCode = safeText(tool.categoryCode, "OTHER").toUpperCase();
    return {
      code,
      name: safeText(tool.name, code),
      description: safeText(tool.description, "打开网页工具开始使用。"),
      categoryCode: /^[A-Z][A-Z0-9_]{0,31}$/.test(categoryCode) ? categoryCode : "OTHER",
      keywords: Array.isArray(tool.keywords) ? tool.keywords.filter((item) => typeof item === "string").slice(0, 24) : [],
      mode: tool.mode === "WEB" ? "WEB" : safeText(tool.mode, "WEB"),
      status: safeText(tool.status, "ENABLED"),
      sortOrder: Number.isFinite(Number(tool.sortOrder)) ? Number(tool.sortOrder) : 9999,
      useCount: Number.isFinite(Number(tool.useCount)) ? Math.max(0, Number(tool.useCount)) : 0
    };
  }

  function applyTheme(theme, persist = false) {
    const next = theme === "dark" ? "dark" : "light";
    root.dataset.theme = next;
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", next === "dark" ? "#121412" : "#ffffff");
    try {
      if (persist) localStorage.setItem(THEME_KEY, next);
    } catch (_) {}
    document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
      button.innerHTML = icon(next === "dark" ? "sun" : "moon");
      button.setAttribute("aria-label", next === "dark" ? "切换到浅色主题" : "切换到深色主题");
      button.setAttribute("title", next === "dark" ? "切换到浅色主题" : "切换到深色主题");
    });
  }

  function initialTheme() {
    const current = root.dataset.theme;
    if (current === "light" || current === "dark") return current;
    try {
      const saved = localStorage.getItem(THEME_KEY);
      if (saved === "light" || saved === "dark") return saved;
    } catch (_) {}
    return matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }

  function setupTheme() {
    applyTheme(initialTheme());
    document.addEventListener("click", (event) => {
      const button = event.target.closest("[data-theme-toggle]");
      if (!button) return;
      applyTheme(root.dataset.theme === "dark" ? "light" : "dark", true);
    });
  }

  function setupNavigation() {
    const header = document.querySelector(".site-header");
    const toggle = document.querySelector("[data-nav-toggle]");
    if (!header || !toggle) return;
    const close = () => {
      header.classList.remove("menu-open");
      document.body.classList.remove("menu-open");
      toggle.setAttribute("aria-expanded", "false");
      toggle.setAttribute("aria-label", "打开导航");
      toggle.innerHTML = icon("menu");
    };
    toggle.addEventListener("click", () => {
      const open = !header.classList.contains("menu-open");
      header.classList.toggle("menu-open", open);
      document.body.classList.toggle("menu-open", open);
      toggle.setAttribute("aria-expanded", String(open));
      toggle.setAttribute("aria-label", open ? "关闭导航" : "打开导航");
      toggle.innerHTML = icon(open ? "close" : "menu");
    });
    header.querySelectorAll(".primary-nav a").forEach((link) => link.addEventListener("click", close));
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") close();
    });
    document.addEventListener("click", (event) => {
      if (!header.contains(event.target)) close();
    });
    matchMedia("(min-width: 900px)").addEventListener("change", (event) => {
      if (event.matches) close();
    });
  }

  async function fetchJson(path, timeoutMs = 7000) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(path, {
        cache: "no-store",
        credentials: "omit",
        redirect: "error",
        signal: controller.signal,
        headers: { Accept: "application/json" }
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } finally {
      clearTimeout(timeout);
    }
  }

  function validCatalog(payload) {
    return payload && payload.code === 0 && payload.data && Array.isArray(payload.data.records)
      ? payload.data.records.map(normalizeTool).filter(Boolean)
      : null;
  }

  async function hydrateHomeMetrics() {
    const totalTargets = document.querySelectorAll("[data-catalog-count]");
    const webTargets = document.querySelectorAll("[data-web-count]");
    if (!totalTargets.length && !webTargets.length) return;
    try {
      const payload = await fetchJson("/api/v1/tools?page=1&pageSize=100");
      const tools = validCatalog(payload);
      if (!tools) return;
      const webCount = tools.filter((tool) => tool.mode === "WEB" && tool.status === "ENABLED").length;
      const total = Number.isFinite(Number(payload.data.total)) ? Number(payload.data.total) : tools.length;
      totalTargets.forEach((target) => { target.textContent = String(total); });
      webTargets.forEach((target) => { target.textContent = String(webCount); });
    } catch (_) {}
  }

  const fallbackTools = [
    { code: "short_video_parse", name: "短视频解析", description: "解析分享链接，提取视频、图集、实况照片和封面，支持预览与选图打包。", categoryCode: "NETWORK", sortOrder: 630, useCount: 56 },
    { code: "lanzou_direct", name: "蓝奏直链解析", description: "解析蓝奏云公开分享链接，显示文件名、大小、临时直链和官方回退页；支持分享密码与失败说明。", categoryCode: "NETWORK", sortOrder: 631, useCount: 2 },
    { code: "qr_studio", name: "二维码工作台", description: "网页版二维码工作台：尺寸与留白控制、SVG/JPEG 导出、WPA3 与企业 Wi-Fi、vCard 多值联系人。", categoryCode: "QR", sortOrder: 211, useCount: 0 },
    { code: "pdf_studio", name: "PDF 工作台", description: "合并 PDF、提取或拆分页面、旋转页面、JPG/PNG 图片转 PDF。", categoryCode: "PDF", sortOrder: 310, useCount: 0 },
    { code: "zepp_steps", name: "微信运动步数", description: "通过 Zepp Life 提交当天步数，支持邮箱与中国区手机号、预设步数和同步指引。", categoryCode: "LIFE", sortOrder: 510, useCount: 0 }
  ].map(normalizeTool);

  function setupToolsCatalog() {
    const grid = document.querySelector("[data-catalog-grid]");
    if (!grid) return;
    const search = document.querySelector("[data-catalog-search]");
    const sort = document.querySelector("[data-catalog-sort]");
    const refresh = document.querySelector("[data-catalog-refresh]");
    const retry = document.querySelector("[data-catalog-retry]");
    const status = document.querySelector("[data-catalog-status]");
    const connection = document.querySelector("[data-catalog-connection]");
    const empty = document.querySelector("[data-catalog-empty]");
    const count = document.querySelector("[data-visible-count]");
    const state = {
      tools: fallbackTools.slice(),
      fallback: fallbackTools.slice(),
      query: "",
      category: "ALL",
      sort: "recommended",
      connection: "loading",
      loading: false
    };

    function toolIcon(tool) {
      if (tool.code === "qr_studio") return "qr";
      if (tool.code === "pdf_studio") return "pdf";
      if (tool.code === "short_video_parse") return "video";
      if (tool.code === "lanzou_direct") return "link";
      if (tool.code === "zepp_steps") return "steps";
      if (tool.categoryCode === "NETWORK") return "link";
      if (tool.categoryCode === "IMAGE") return "image";
      if (tool.categoryCode === "PDF") return "pdf";
      if (tool.categoryCode === "QR") return "qr";
      if (["DEV", "TEXT"].includes(tool.categoryCode)) return "code";
      return "tool";
    }

    function categoryName(code) {
      return categories[code] || code;
    }

    function filteredTools() {
      const needle = state.query.toLocaleLowerCase("zh-CN");
      const result = state.tools.filter((tool) => {
        if (state.category !== "ALL" && tool.categoryCode !== state.category) return false;
        if (!needle) return true;
        return [tool.name, tool.description, categoryName(tool.categoryCode), ...tool.keywords]
          .join("\n").toLocaleLowerCase("zh-CN").includes(needle);
      });
      if (state.sort === "usage") return result.sort((a, b) => b.useCount - a.useCount || a.sortOrder - b.sortOrder);
      if (state.sort === "name") return result.sort((a, b) => a.name.localeCompare(b.name, "zh-CN"));
      return result.sort((a, b) => a.sortOrder - b.sortOrder || a.code.localeCompare(b.code));
    }

    function card(tool) {
      const link = document.createElement("a");
      link.className = "catalog-card";
      link.href = `/tools/${encodeURIComponent(tool.code)}/`;
      link.dataset.toolCode = tool.code;
      link.setAttribute("aria-label", `打开${tool.name}`);
      const usage = tool.useCount > 0 ? `${tool.useCount.toLocaleString("zh-CN")} 次使用` : "网页工具";
      const meta = document.createElement("div");
      meta.className = "catalog-card-meta";
      const source = document.createElement("span");
      source.textContent = "无需安装";
      const usageNode = document.createElement("span");
      usageNode.textContent = usage;
      meta.append(source, usageNode);

      const iconNode = document.createElement("span");
      iconNode.className = "catalog-card-icon";
      iconNode.innerHTML = icon(toolIcon(tool));
      const body = document.createElement("div");
      body.className = "catalog-card-body";
      const heading = document.createElement("div");
      heading.className = "catalog-card-heading";
      const name = document.createElement("h2");
      name.textContent = tool.name;
      const category = document.createElement("span");
      category.className = "catalog-card-category";
      category.textContent = categoryName(tool.categoryCode);
      heading.append(name, category);
      const description = document.createElement("p");
      description.textContent = tool.description;
      body.append(heading, description, meta);
      const arrow = document.createElement("span");
      arrow.className = "catalog-card-arrow";
      arrow.innerHTML = icon("arrow");
      link.append(iconNode, body, arrow);
      return link;
    }

    function updateFilterCounts() {
      document.querySelectorAll("[data-tool-filter]").forEach((button) => {
        const category = button.dataset.toolFilter || "ALL";
        const amount = category === "ALL"
          ? state.tools.length
          : state.tools.filter((tool) => tool.categoryCode === category).length;
        const countNode = button.querySelector("[data-filter-count]");
        if (countNode) countNode.textContent = String(amount);
        button.classList.toggle("active", category === state.category);
        button.setAttribute("aria-pressed", String(category === state.category));
      });
    }

    function updateCatalogMetrics() {
      const categoryCount = new Set(state.tools.map((tool) => tool.categoryCode)).size;
      const usageCount = state.tools.reduce((sum, tool) => sum + tool.useCount, 0);
      document.querySelectorAll("[data-catalog-total]").forEach((node) => { node.textContent = String(state.tools.length); });
      document.querySelectorAll("[data-category-total]").forEach((node) => { node.textContent = String(categoryCount); });
      document.querySelectorAll("[data-usage-total]").forEach((node) => { node.textContent = usageCount.toLocaleString("zh-CN"); });
    }

    function render() {
      const tools = filteredTools();
      grid.replaceChildren(...tools.map(card));
      grid.hidden = tools.length === 0;
      grid.setAttribute("aria-busy", String(state.loading));
      empty.hidden = tools.length !== 0;
      count.textContent = `显示 ${tools.length} / ${state.tools.length}`;
      updateFilterCounts();
      updateCatalogMetrics();
    }

    function setStatus(message, tone = "neutral") {
      status.textContent = message;
      status.dataset.tone = tone;
    }

    function setConnection(value, label) {
      state.connection = value;
      if (!connection) return;
      connection.dataset.state = value;
      connection.textContent = label;
    }

    async function load() {
      if (state.loading) return;
      state.loading = true;
      refresh.disabled = true;
      refresh.textContent = "同步中…";
      setConnection("loading", "正在同步目录");
      setStatus(state.tools.length ? "正在检查工具目录更新…" : "正在加载网页工具…");
      grid.setAttribute("aria-busy", "true");
      const [catalogResult, popularResult] = await Promise.allSettled([
        fetchJson("/api/v1/tools?page=1&pageSize=100"),
        fetchJson("/api/v1/tools/popular?limit=100")
      ]);

      let updatedTools = null;
      if (catalogResult.status === "fulfilled") {
        const tools = validCatalog(catalogResult.value);
        if (tools) updatedTools = tools.filter((tool) => tool.mode === "WEB" && tool.status === "ENABLED");
      }

      if (updatedTools && updatedTools.length) {
        const usage = new Map();
        if (popularResult.status === "fulfilled" && popularResult.value && popularResult.value.code === 0 && Array.isArray(popularResult.value.data)) {
          popularResult.value.data.forEach((item) => {
            if (item && typeof item.code === "string" && Number.isFinite(Number(item.useCount))) usage.set(item.code, Math.max(0, Number(item.useCount)));
          });
        }
        state.tools = updatedTools.map((tool) => ({ ...tool, useCount: usage.get(tool.code) ?? tool.useCount }));
        setConnection("ready", "目录已同步");
        setStatus(`已同步 ${state.tools.length} 个网页工具，数据来自公开工具目录。`);
      } else {
        state.tools = state.fallback.slice();
        setConnection("error", "显示内置入口");
        setStatus("工具目录暂时无法更新，当前仍可使用下面列出的内置网页工具。", "warning");
      }

      state.loading = false;
      refresh.disabled = false;
      refresh.textContent = "重新同步";
      render();
    }

    document.addEventListener("click", (event) => {
      const filter = event.target.closest("[data-tool-filter]");
      if (!filter) return;
      state.category = filter.dataset.toolFilter || "ALL";
      render();
    });
    search.addEventListener("input", () => {
      state.query = search.value.trim();
      render();
    });
    sort.addEventListener("change", () => {
      state.sort = sort.value;
      render();
    });
    refresh.addEventListener("click", load);
    if (retry) retry.addEventListener("click", load);
    render();
    load();
  }

  setupTheme();
  setupNavigation();
  document.querySelectorAll("[data-year]").forEach((node) => { node.textContent = String(new Date().getFullYear()); });
  if (document.body.dataset.page === "tools") setupToolsCatalog();
  else hydrateHomeMetrics();
})();
