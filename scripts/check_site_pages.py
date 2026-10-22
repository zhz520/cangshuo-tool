#!/usr/bin/env python3
"""Static contract checks for the public site and web-tool directory."""

from __future__ import annotations

import re
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "deploy" / "site"
checks = 0


def require(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


class StructureParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.ids: list[str] = []
        self.scripts: list[tuple[str, str]] = []
        self.stylesheets: list[str] = []
        self.inline_scripts = 0
        self._inside_script = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if values.get("id"):
            self.ids.append(values["id"] or "")
        if tag == "script":
            self._inside_script = True
            self.scripts.append((values.get("src") or "", "defer" if "defer" in values else ""))
        if tag == "link" and "stylesheet" in (values.get("rel") or "").split():
            self.stylesheets.append(values.get("href") or "")

    def handle_endtag(self, tag: str) -> None:
        if tag == "script":
            self._inside_script = False

    def handle_data(self, data: str) -> None:
        if self._inside_script and data.strip():
            self.inline_scripts += 1


def parsed(path: Path) -> tuple[str, StructureParser]:
    text = path.read_text(encoding="utf-8")
    parser = StructureParser()
    parser.feed(text)
    return text, parser


def check_page(path: Path) -> tuple[str, StructureParser]:
    require(path.is_file(), f"missing page: {path.relative_to(ROOT)}")
    text, parser = parsed(path)
    name = path.relative_to(ROOT).as_posix()
    require(text.lower().startswith("<!doctype html>"), f"{name}: missing doctype")
    require('<html lang="zh-CN">' in text, f"{name}: missing Chinese document language")
    require("width=device-width" in text and "viewport-fit=cover" in text, f"{name}: incomplete viewport")
    require("Content-Security-Policy" in text and "script-src 'self'" in text, f"{name}: missing restrictive CSP")
    require(any(path.startswith("/site.css") for path in parser.stylesheets), f"{name}: missing shared site stylesheet")
    require(any(src.startswith("/theme-boot.js") for src, _ in parser.scripts), f"{name}: missing pre-paint theme script")
    require(any(src.startswith("/site.js") and defer == "defer" for src, defer in parser.scripts), f"{name}: missing deferred site script")
    require(parser.inline_scripts == 0, f"{name}: inline script conflicts with CSP")
    require(len(parser.ids) == len(set(parser.ids)), f"{name}: duplicate id attribute")
    require(not re.search(r'(?:src|href)=["\']https?://[^"\']+\.(?:js|css)(?:[?"\'])', text, re.I),
            f"{name}: external script or stylesheet dependency")
    require("data-theme-toggle" in text, f"{name}: missing theme control")
    require("data-nav-toggle" in text and "aria-controls=\"primary-navigation\"" in text,
            f"{name}: missing accessible mobile navigation")
    return text, parser


home, _ = check_page(SITE / "index.html")
require(home.count('data-catalog-count') >= 2, "home: missing hydrated catalog counters")
require('href="/tools/"' in home, "home: missing web-tool directory link")
require(home.count('class="tool-card"') == 3, "home: expected three featured web tools")
for code in ("short_video_parse", "lanzou_direct", "qr_studio"):
    require(f'href="/tools/{code}/"' in home, f"home: missing featured tool {code}")
require("product-preview" in home and "preview-metrics" in home, "home: missing admin-inspired workbench preview")
require('id="capabilities"' in home and 'id="workflow"' in home, "home: missing product and workflow sections")

tools, _ = check_page(SITE / "tools" / "index.html")
require('data-page="tools"' in tools, "tools: missing catalog page marker")
for marker in ("data-catalog-search", "data-catalog-sort", "data-catalog-grid", "data-catalog-status",
               "data-catalog-refresh", "data-catalog-retry", "data-catalog-connection"):
    require(marker in tools, f"tools: missing interaction marker {marker}")
for code in ("short_video_parse", "lanzou_direct", "qr_studio", "pdf_studio", "zepp_steps"):
    require(f'href="/tools/{code}/"' in tools, f"tools: missing static fallback for {code}")
for category in ("ALL", "NETWORK", "QR", "PDF", "LIFE"):
    require(f'data-tool-filter="{category}"' in tools, f"tools: missing filter {category}")
require("<noscript>" in tools and "网页工具静态列表" in tools, "tools: missing no-JavaScript fallback")

script = (SITE / "site.js").read_text(encoding="utf-8")
require('cache: "no-store"' in script, "site.js: catalog requests must bypass caches")
require('credentials: "omit"' in script, "site.js: public requests must omit credentials")
require('redirect: "error"' in script, "site.js: public requests must reject redirects")
require('"/api/v1/tools?page=1&pageSize=100"' in script, "site.js: missing catalog endpoint")
require('"/api/v1/tools/popular?limit=100"' in script, "site.js: missing popularity endpoint")
require('tool.mode === "WEB"' in script and 'tool.status === "ENABLED"' in script,
        "site.js: must only show enabled web tools")
require('state.tools = state.fallback.slice()' in script, "site.js: missing catalog fallback")
require('localStorage.setItem(THEME_KEY' in script, "site.js: theme choice is not persisted")
require('toLocaleLowerCase("zh-CN")' in script, "site.js: search is not locale-aware")
require('aria-busy' in script and 'aria-pressed' in script, "site.js: missing loading or filter semantics")
require('encodeURIComponent(tool.code)' in script, "site.js: tool path is not encoded")

css = (SITE / "site.css").read_text(encoding="utf-8")
for token in ("--site-bg", "--site-border", "--site-text", "--site-secondary", "--site-muted", "--site-accent"):
    require(token in css, f"site.css: missing design token {token}")
require(':root[data-theme="dark"]' in css, "site.css: missing dark theme")
require("grid-template-columns: 232px minmax(0, 1fr)" in css, "site.css: missing admin-style tool shell")
require("@media (max-width: 899px)" in css and "@media (max-width: 639px)" in css,
        "site.css: missing tablet or mobile layout")
require("prefers-reduced-motion" in css, "site.css: missing reduced-motion support")
require(".catalog-skeleton" in css, "site.css: missing loading state")
require(".catalog-empty" in css, "site.css: missing empty state")
require(".catalog-search input:focus" in css and ":focus-visible" in css, "site.css: missing focus styles")

nginx = (ROOT / "deploy" / "nginx" / "common" / "site-locations.conf").read_text(encoding="utf-8")
require("index index.html;" in nginx, "nginx: tools directory will not resolve index.html")
require("location ^~ /tools/" in nginx, "nginx: missing web-tool route")

print(f"Public site contract checks passed: {checks}/{checks}")
