import { h, type Component } from 'vue'

// Small, local SVG glyphs share one stroke and the existing ElIcon wrapper.
function outline(paths: string[]): Component {
  return () => h('svg', {
    viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
    'stroke-width': 1.5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
    'aria-hidden': 'true',
  }, paths.map(d => h('path', { d })))
}

export const OverviewIcon = outline(['M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z'])
export const ToolboxIcon = outline(['M3 8h18v12H3z M9 8V5h6v3 M3 12h18 M10 12v3h4v-3'])
export const CategoryIcon = outline(['M3 6h6l2 2h10v12H3z'])
export const RecommendationIcon = outline(['M3 4h18v16H3z M3 15l5-5 4 4 3-3 6 6', 'M15 8h.01'])
export const AnnouncementIcon = outline(['M5 17h14l-2-3V9a5 5 0 0 0-10 0v5z M10 21h4 M12 2v2'])
export const UsersIcon = outline(['M8 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0', 'M4 21v-2a8 8 0 0 1 16 0v2z'])
export const FeedbackIcon = outline(['M4 4h16v12H9l-5 4z M8 8h8 M8 12h5'])
export const LogsIcon = outline(['M5 3h9l5 5v13H5z M14 3v5h5 M9 12h6 M9 16h6'])
export const SettingsIcon = outline(['M3 6h4 M11 6h10 M3 12h10 M17 12h4 M3 18h4 M11 18h10', 'M7 4h4v4H7z M13 10h4v4h-4z M7 16h4v4H7z'])
export const LinkIcon = outline(['M10 13a5 5 0 0 0 7 0l2-2a5 5 0 0 0-7-7l-1 1', 'M14 11a5 5 0 0 0-7 0l-2 2a5 5 0 0 0 7 7l1-1'])
