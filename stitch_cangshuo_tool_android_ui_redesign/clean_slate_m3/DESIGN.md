---
name: Clean Slate M3
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#434654'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#737686'
  outline-variant: '#c3c5d7'
  surface-tint: '#1353d8'
  primary: '#003fb1'
  on-primary: '#ffffff'
  primary-container: '#1a56db'
  on-primary-container: '#d4dcff'
  inverse-primary: '#b5c4ff'
  secondary: '#515f74'
  on-secondary: '#ffffff'
  secondary-container: '#d5e3fc'
  on-secondary-container: '#57657a'
  tertiary: '#00544c'
  on-tertiary: '#ffffff'
  tertiary-container: '#006e65'
  on-tertiary-container: '#84f0e2'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dbe1ff'
  primary-fixed-dim: '#b5c4ff'
  on-primary-fixed: '#00174d'
  on-primary-fixed-variant: '#003dab'
  secondary-fixed: '#d5e3fc'
  secondary-fixed-dim: '#b9c7df'
  on-secondary-fixed: '#0d1c2e'
  on-secondary-fixed-variant: '#3a485b'
  tertiary-fixed: '#89f5e7'
  tertiary-fixed-dim: '#6bd8cb'
  on-tertiary-fixed: '#00201d'
  on-tertiary-fixed-variant: '#005049'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-mono:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: -0.01em
  data-metric:
    fontFamily: JetBrains Mono
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system establishes a high-performance, precision-engineered utility interface tailored for modern native Android (API levels 34 and 35). Emphasizing a local-first, privacy-driven architectural ethos, the visual language departs entirely from decorative AI tropes, glowing glassmorphic panels, and saturated neon gradients. Instead, it draws from classical industrial drafting tools, high-end measurement instrumentation, and refined Swiss modernism adapted to Google Material 3 foundations.

The brand personality is deliberate, reliable, uncompromising, and calm. Visual weight is communicated through typographic precision, micro-hairline borders, and calibrated tonal shifts rather than volumetric drop shadows. The resulting product evokes absolute functional trust, immediate tactile responsiveness, and structural longevity.

## Colors

The palette is anchored in an analytical, cold-slate light spectrum that minimizes visual fatigue during technical tasks:

- **Surface Tiers**:
  - `surface-canvas`: `#F8F9FA` (Default window background)
  - `surface-container-low`: `#F1F3F5` (Segmented group backing and inset search docks)
  - `surface-container`: `#FFFFFF` (Card surfaces, sheets, and active interactive plates)
  - `surface-container-high`: `#E9ECEF` (Interactive press states and chips)
- **Stroke & Structure**:
  - `border-subtle`: `#E2E6EA` (Standard 1px structural container hairline)
  - `border-strong`: `#D0D5DD` (Field borders, divider tracks, and unselected control rings)
- **Text & Hierarchy**:
  - `text-primary`: `#0F172A` (Deep ink-black for high-contrast legibility)
  - `text-secondary`: `#1E293B` (Body anchors, technical labels)
  - `text-muted`: `#475569` (Secondary descriptors, unit definitions)
  - `text-tertiary`: `#64748B` (Inactive states, placeholders, timestamps)
- **Accents**:
  - `primary`: `#1A56DB` (Precision electric cobalt; exclusively deployed for functional focus, active selection indicators, and primary execution targets)
  - `primary-container`: `#EBF2FE` (Subtle selection tinting)
  - `tertiary`: `#0D9488` (Muted calibration teal; used strictly for verified local-only status chips and network isolation indicators)

## Typography

The typographic hierarchy enforces immediate information parsing on handheld devices. `Plus Jakarta Sans` provides geometric clarity with crisp terminal angles for headings without appearing decorative. `Inter` serves as the primary engine for continuous text, UI controls, and body elements, ensuring high legibility at micro scales.

All calculators, memory counters, hex viewers, system telemetry monitors, and coordinates explicitly employ `JetBrains Mono` with OpenType tabular figures (`tnum`) enabled. This eliminates jitter during real-time data updates and establishes visual alignment across structured tables and numerical inputs.

## Layout & Spacing

The layout model is anchored to a strict 8-point baseline grid, utilizing 4-point increments solely for compact internal padding (micro-badges, segmented tabs, and hairline adjustments).

- **Mobile Viewports (<600dp)**: Single fluid column layout bounded by a `margin` of `1rem` (16dp). Internal card modules use `space-md` (16dp) internal padding. Vertical rhythm follows multiples of 8dp (`space-sm`, `space-md`, `space-lg`).
- **Foldables & Tablets (600dp - 840dp+)**: Dual-pane adaptive layout. Master navigation collapses to an ergonomic leading navigation rail, while utility tools split across master-detail viewports using a 16dp `gutter` and `1.5rem` outer margins.
- **Edge-to-Edge Execution**: Conforms fully to Android 14/15 edge-to-edge requirements. Bottom navigation containers and scrollable sheet contents inject dynamic system window insets (`WindowInsetsCompat.Type.systemBars()`) to avoid clipping gesture navigation indicators and status bars.

## Elevation & Depth

Visual separation relies on calibrated tonal shifts paired with crisp 1px borders rather than diffuse drop shadows.

- **Level 0 (Base Canvas)**: Resting surface at `#F8F9FA`. Zero shadow, zero elevation.
- **Level 1 (Cards, Modules, Input Groups)**: `#FFFFFF` surface enclosed by a 1px solid hairline (`#E2E6EA`). Supplemented by a subtle tactile resting shadow: `0 1px 3px rgba(15, 23, 42, 0.04)`.
- **Level 2 (Active Sheets, Snackbars, Floating Modals)**: `#FFFFFF` surface bordered with `#D0D5DD` and a crisp, low-dispersion drop shadow: `0 4px 12px rgba(15, 23, 42, 0.08)`.
- **Surface Elevation via Tone**: Nested groups (e.g., utility parameters inside an expanded module) utilize `#F1F3F5` backings instead of stacked drop shadows, eliminating visual clutter.

## Shapes

The geometric framework balances structural discipline with modern touch-target friendliness:

- **Cards & Data Containers**: `rounded-md` (8dp / 0.5rem) or `rounded-lg` (16dp / 1rem) for primary tool groups, matching standard Android 14/15 corner-radius geometry.
- **Interactive Controls (Inputs, Buttons)**: 8dp (0.5rem) corner curvature, conveying physical solidity.
- **Pills & Status Indicators**: Full radial curvature (`9999px` / Pill-shape) applied to filter chips, status badges, active navigation pill selectors, and progress trackers.
- **Bottom Sheets**: Top-left and top-right corners styled with 24dp curvature.

## Components

### Buttons
- **Primary**: Solid `#1A56DB` background, `#FFFFFF` text, 0.5rem (8dp) radius, 44dp minimum touch height. Active state uses 10% black overlay without blur.
- **Secondary / Outlined**: `#FFFFFF` surface, 1px `#D0D5DD` border, `#0F172A` text. Press state applies `#F1F3F5`.
- **Ghost / Utility**: No border, `#475569` text, background becomes `#F1F3F5` on tap.

### Chips & Badges
- **Status Badges**: Pill-shaped (`rounded-full`), 24dp height, `#F1F3F5` background, `#1E293B` text, 1px `#E2E6EA` border.
- **Local/Offline Indicator**: `#E6FFFA` surface, `#0D9488` text, featuring a 6dp solid `#0D9488` circular dot indicator.
- **Interactive Filter Chips**: Pill-shaped; unselected uses `#FFFFFF` with `#E2E6EA` border; selected uses `#EBF2FE` with 1px `#1A56DB` border and `#1A56DB` text.

### Text Inputs & Search Bars
- Resting background: `#FFFFFF` encased in 1px `#D0D5DD` border.
- Focused state: 1.5px `#1A56DB` border with zero exterior blur rings.
- Integrated monospace fields: Uses `JetBrains Mono` at 13px with inline clear/copy actions embedded inside the trailing edge.

### Cards & Tool Modules
- Flat white surface (`#FFFFFF`), 1px hairline border (`#E2E6EA`), 12dp internal padding.
- Clickable tool cards feature a trailing `#64748B` minimal chevron (`16dp`) and state-layer highlighting without scale animation.

### Lists & Grouped Settings
- Native inset grouped style: Placed on `#F1F3F5` container or styled as bordered cards with zero elevation.
- Dividers: 1px hairline (`#E2E6EA`) inset by 16dp on the leading edge (aligning with label text).
- Switches: M3 standard thumb and track styling using cobalt `#1A56DB` for the active track and slate `#D0D5DD` for off-state outlines.

### Bottom Navigation Bar
- Native 80dp container adhering to Android 14/15 ergonomics.
- Surface: `#F8F9FA` with a 1px `#E2E6EA` top divider line.
- Active Indicator: Pill-shaped highlight (`#EBF2FE`), 64dp wide by 32dp high. Active icon tint: `#1A56DB`. Inactive icon tint: `#64748B`.
- Labels: `label-md` (Inter 12dp), transitioning from `#64748B` (unselected) to `#0F172A` (selected).
