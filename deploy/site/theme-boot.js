(() => {
  try {
    const saved = localStorage.getItem("cs-site-theme");
    const theme = saved === "light" || saved === "dark"
      ? saved
      : matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme === "dark" ? "#121412" : "#ffffff");
  } catch (_) {
    document.documentElement.dataset.theme = "light";
  }
})();
