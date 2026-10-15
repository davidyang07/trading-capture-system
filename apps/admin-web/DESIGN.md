---
version: alpha
name: TCS-Operator-Console
description: The operator console's design language — a Stripe-inspired financial look (deep navy ink, one electric-indigo accent, hairline borders, tabular figures for every number) adapted for a dense, internal back-office tool, with IBM Carbon's data-table rules for the order, execution and ledger tables.

colors:
  primary: "#533afd"
  primary-deep: "#4434d4"
  primary-press: "#2e2b8c"
  primary-tint: "#ececfe"
  ink: "#0d253d"
  ink-secondary: "#273951"
  ink-mute: "#64748d"
  on-primary: "#ffffff"
  canvas: "#ffffff"
  canvas-soft: "#f6f9fc"
  canvas-hover: "#eef2f7"
  hairline: "#e3e8ee"
  hairline-input: "#a8c3de"
  success: "#18794a"
  success-tint: "#e3f6ec"
  warning: "#9a5b00"
  warning-tint: "#fdf1dc"
  danger: "#c0123c"
  danger-tint: "#fde8ee"
  info: "#4434d4"
  info-tint: "#ececfe"
  neutral: "#3c4f69"
  neutral-tint: "#edf1f6"

typography:
  page-title:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 26px
    fontWeight: 300
    lineHeight: 1.15
    letterSpacing: -0.26px
  section-title:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 18px
    fontWeight: 500
    lineHeight: 1.3
    letterSpacing: -0.1px
  stat-value:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 28px
    fontWeight: 300
    lineHeight: 1.1
    letterSpacing: -0.4px
    fontFeature: tnum
  body:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.45
    letterSpacing: 0
  body-tabular:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.45
    letterSpacing: -0.2px
    fontFeature: tnum
  table-header:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 13px
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: 0
  label:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: 0
  caption:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.4
    letterSpacing: -0.1px
  badge:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 1.0
    letterSpacing: 0
  button:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.0
    letterSpacing: 0
  display:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 68px
    fontWeight: 300
    lineHeight: 1.04
    letterSpacing: -1.7px
  display-section:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 42px
    fontWeight: 300
    lineHeight: 1.12
    letterSpacing: -0.84px
  lead:
    fontFamily: "'Inter var', Inter, system-ui, sans-serif"
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: 0

rounded:
  xs: 4px
  sm: 6px
  md: 8px
  lg: 12px
  pill: 9999px

spacing:
  xxs: 2px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  xxl: 32px

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 8px 16px
  button-secondary:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.primary}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 8px 16px
  button-danger-outline:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.danger}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 8px 16px
  text-input:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 8px 12px
  panel:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: 24px
  data-table-row:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink-secondary}"
    typography: "{typography.body-tabular}"
    rounded: "{rounded.xs}"
    padding: 0 16px
  status-badge:
    backgroundColor: "{colors.neutral-tint}"
    textColor: "{colors.neutral}"
    typography: "{typography.badge}"
    rounded: "{rounded.pill}"
    padding: 4px 8px
  nav-item-active:
    backgroundColor: "{colors.primary-tint}"
    textColor: "{colors.primary-deep}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: 0 12px
  nav-item-hover:
    backgroundColor: "{colors.canvas-hover}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: 0 12px
  button-primary-pressed:
    backgroundColor: "{colors.primary-press}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 8px 16px
  app-canvas:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink-secondary}"
    typography: "{typography.body}"
    padding: 24px
  detail-label:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink-mute}"
    typography: "{typography.label}"
    padding: 0px
  divider:
    backgroundColor: "{colors.hairline}"
    padding: 0px
  text-input-border:
    backgroundColor: "{colors.hairline-input}"
    padding: 0px
  status-badge-success:
    backgroundColor: "{colors.success-tint}"
    textColor: "{colors.success}"
    typography: "{typography.badge}"
    rounded: "{rounded.pill}"
    padding: 4px 8px
  status-badge-warning:
    backgroundColor: "{colors.warning-tint}"
    textColor: "{colors.warning}"
    typography: "{typography.badge}"
    rounded: "{rounded.pill}"
    padding: 4px 8px
  status-badge-danger:
    backgroundColor: "{colors.danger-tint}"
    textColor: "{colors.danger}"
    typography: "{typography.badge}"
    rounded: "{rounded.pill}"
    padding: 4px 8px
  status-badge-info:
    backgroundColor: "{colors.info-tint}"
    textColor: "{colors.info}"
    typography: "{typography.badge}"
    rounded: "{rounded.pill}"
    padding: 4px 8px
---

## Overview

The Trading Capture System console is an internal back-office tool: operators and investors read
orders, executions, accounts, ledgers and reference data, and administrators act on them. The design
borrows a **financial-infrastructure look** — deep navy ink on white and cool off-white, a single
electric-indigo accent, hairline borders, whisper-soft blue-tinted shadows and tabular figures for
every number — and applies **IBM Carbon's data-table rules** to the tables that make up most of the
product.

**Sources.** The colour, type, radius and button language is adapted from the Stripe-inspired
`DESIGN.md` in [VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md) (MIT; an
unofficial interpretation of publicly visible design patterns, not Stripe's own system). The table
rules come from Carbon's data-table [usage](https://carbondesignsystem.com/components/data-table/usage/)
and [style](https://carbondesignsystem.com/components/data-table/style/) guidance.

**Deliberate departures from the source.** The source describes a marketing site; this is a dense
working tool, so:
- No gradient-mesh hero, cream bands or decorative depth: the console is flat white panels on
  `{colors.canvas-soft}`.
- Weight 300 only for large display text (page titles, stat values). Body, tables, labels and buttons
  use 400–600, because 300 at 13–14px is too faint for screens read all day.
- `ss01` is not applied: it is a Sohne feature, and on Inter it swaps the digits. Numbers use `tnum`.
- Semantic colours (success, warning, danger, info, neutral) are added for status badges; the source
  has none.

**Key characteristics**
- One accent: `{colors.primary}` marks the page's main action, links, focus and the active nav item.
- Navy ink, never pure black: `{colors.ink}` for primary text, `{colors.ink-secondary}` for table
  text, `{colors.ink-mute}` for labels and help.
- Every number uses tabular figures and every numeric column is right-aligned.
- Pill buttons; 6px inputs; 12px panels; 1px hairlines everywhere.
- Medium-density tables (40px rows), one row size per layout.

## Colors

### Brand & accent
- **Indigo** (`{colors.primary}` `#533afd`): filled primary button, links, focus ring, active tab ink
  bar, active paginator page. Use it sparingly: one filled button per panel.
- **Indigo deep** (`{colors.primary-deep}` `#4434d4`): hover on primary, active nav text.
- **Indigo press** (`{colors.primary-press}` `#2e2b8c`): pressed primary.
- **Indigo tint** (`{colors.primary-tint}` `#ececfe`): active nav background, selected rows,
  info badges.

### Surface
- **Canvas** (`{colors.canvas}` `#ffffff`): panels, cards, tables, top bar, dialogs.
- **Canvas soft** (`{colors.canvas-soft}` `#f6f9fc`): the app background behind panels; table row hover.
- **Canvas hover** (`{colors.canvas-hover}` `#eef2f7`): hover on nav items and ghost buttons.
- **Hairline** (`{colors.hairline}` `#e3e8ee`): every 1px border and divider (panels, rows, top bar).
- **Hairline input** (`{colors.hairline-input}` `#a8c3de`): form-control borders.

### Text
- **Ink** (`{colors.ink}` `#0d253d`): headings, primary values.
- **Ink secondary** (`{colors.ink-secondary}` `#273951`): body and table cell text.
- **Ink mute** (`{colors.ink-mute}` `#64748d`): labels, column headers' quiet text, captions, help.
- Never use indigo as a body-text colour; never use pure black.

### Semantic (status)
| Tone | Text | Background | Used for |
|---|---|---|---|
| success | `#18794a` | `#e3f6ec` | Active, Filled, Completed, Allocated |
| warning | `#9a5b00` | `#fdf1dc` | Pending, Partially filled, Trade correct |
| danger | `#c0123c` | `#fde8ee` | Suspended, Trade cancel |
| info | `#4434d4` | `#ececfe` | New, Trade |
| neutral | `#3c4f69` | `#edf1f6` | Closed, Unallocated, Inactive, anything else |

Each pair meets WCAG AA (≥ 4.5:1) at the badge size. Colour is never the only signal: badges carry
their word and an icon.

## Typography

**Inter** (variable, `"Inter var"`) everywhere — it ships self-hosted with the PrimeReact Lara theme,
so no extra font loading. Inter's `cv` features from the theme stay on.

| Token | Size | Weight | Use |
|---|---|---|---|
| `{typography.page-title}` | 26px | 300 | Page title (`h1`), -0.26px tracking |
| `{typography.stat-value}` | 28px | 300 | Dashboard summary numbers, `tnum` |
| `{typography.section-title}` | 18px | 500 | Panel titles (`h2`) |
| `{typography.body}` | 14px | 400 | Default UI text |
| `{typography.body-tabular}` | 14px | 400 | Table cells and any number, `tnum` |
| `{typography.table-header}` | 13px | 600 | Column headers (Carbon: compact heading, semibold) |
| `{typography.label}` | 13px | 500 | Form labels, nav items, detail-list labels |
| `{typography.caption}` | 13px | 400 | Help text, breadcrumbs, descriptions |
| `{typography.badge}` | 12px | 500 | Status badges |
| `{typography.button}` | 14px | 500 | Buttons |

Rules:
- Sentence case for titles, descriptions, column headers and buttons (Carbon).
- `font-variant-numeric: tabular-nums` on every table, detail value, balance and stat.

## Layout

- **App shell:** a 64px white top bar with a hairline bottom border; a 240px left navigation; content
  on `{colors.canvas-soft}` with 24px padding, max content width 1360px.
- **Page header:** breadcrumb (caption, ink-mute) → page title → one-line description (caption).
  Page-level actions sit on the right of the title row.
- **Panels:** white, 1px hairline, `{rounded.lg}`, 24px padding, 24px apart. A panel title row holds the
  title on the left and the panel's actions on the right.
- **Spacing:** 8px base; 4 / 12 for fine adjustments; 16 inside groups, 24 between panels.
- **Detail lists** (`dl`): label (`{typography.label}`, ink-mute) above value (`{typography.body}`, ink),
  in a responsive grid of 220px-minimum columns, 16px × 24px gaps.

## Elevation & depth

| Level | Treatment | Use |
|---|---|---|
| 0 | flat, 1px hairline | Panels, tables, top bar |
| 1 | `0 1px 3px rgba(0,55,112,0.08)` | Summary cards, hover lift |
| 2 | `0 8px 24px rgba(0,55,112,0.08), 0 2px 6px rgba(0,55,112,0.04)` | Dialogs, menus, dropdown panels, toasts |

## Shapes

| Token | Value | Use |
|---|---|---|
| `{rounded.xs}` | 4px | Table chrome, small tags |
| `{rounded.sm}` | 6px | Inputs, dropdowns, paginator buttons |
| `{rounded.md}` | 8px | Nav items, alerts, banners |
| `{rounded.lg}` | 12px | Panels, cards, dialogs |
| `{rounded.pill}` | 9999px | All buttons and status badges |

## Components

### Buttons
- **Primary** (`button-primary`): indigo fill, white text, pill, 8px 16px, 36px tall (40px on touch).
  Hover `{colors.primary-deep}`, pressed `{colors.primary-press}`. One per panel or page header.
- **Secondary** (`button-secondary`): white, 1px indigo border, indigo text. Hover fills
  `{colors.primary-tint}`.
- **Danger** (`button-danger-outline`): white, 1px danger border, danger text — destructive lifecycle
  actions (Close, Cancel execution).
- **Ghost / text:** no border, ink-secondary text, hover `{colors.canvas-hover}` — links like
  "Back to accounts".
- Icons 14px, 8px gap. Never replace the pill with a rounded rectangle.

### Inputs, dropdowns, text areas
- 40px tall, `{rounded.sm}`, 1px `{colors.hairline-input}`, 8px 12px padding, `{typography.body}` ink.
- Focus: border `{colors.primary}` + `0 0 0 3px` indigo at 18% opacity.
- Invalid: border and message in `{colors.danger}`; the message sits under the field in caption size.
- Labels above fields in `{typography.label}`; required marker `*` in danger.

### Panels and summary cards
- Panel: see Layout. Title `{typography.section-title}` ink.
- Summary card: white, hairline, `{rounded.lg}`, 20px 24px padding, Level 1 on hover. A 36px round icon
  chip in `{colors.primary-tint}` with indigo icon, the value in `{typography.stat-value}` and the label
  in caption ink-mute.

### Navigation
- Left nav items: 40px tall, `{rounded.md}`, 12px horizontal padding, icon 16px + 12px gap, label in
  `{typography.label}` ink-secondary. No list bullets.
- Hover `{colors.canvas-hover}`; active `{colors.primary-tint}` background with `{colors.primary-deep}`
  text and icon (`aria-current="page"`).
- Top bar: logo 32px, product name 15px/600 ink, tagline caption ink-mute; the user name and role on the
  right with a secondary "Log out" pill.

### Status badges
Pill, `{typography.badge}`, 4px 8px padding, 10px icon, tone colours from the semantic table. The
badge says the state in words (sentence case); never colour-only.

### Data tables (IBM Carbon rules)
- **Row size:** medium — 40px body rows; the header row matches (40px). One size per layout. Use small
  (32px) only for a deliberately dense view, and say so in its title.
- **Cells:** 16px left/right padding, vertically centred, one line (`nowrap`),
  `{typography.body-tabular}` ink-secondary. Long record ids (UUIDs) truncate at 184px with an ellipsis;
  the link's `title` carries the full id, and the detail page shows it in full.
- **Header:** `{typography.table-header}` ink, white background, 1px hairline under it. Column titles are
  one or two words, sentence case.
- **Numbers:** quantities, prices, amounts and counts are right-aligned with tabular figures, header
  included. Text and identifiers are left-aligned. Quantities never carry a currency; money always does.
- **Dividers:** 1px hairline between rows; no zebra striping by default.
- **States:** hover `{colors.canvas-soft}` on every row (even non-interactive); selected
  `{colors.primary-tint}`.
- **Toolbar** (above the table, inside the panel): filters on the left, at most five actions on the
  right; its controls match the row height.
- **Pagination:** always at the bottom, right-aligned, 40px controls; items-per-page selector at the end.
- **Empty state:** centred, icon + one sentence in caption ink-mute, inside the table area.
- **Loading:** a skeleton or the shared loading state, never an empty table flashing.
- **Frame:** a table inside a panel runs edge to edge and the panel is its frame; a list page's table
  (filters above it, no panel) gets its own white frame — 1px hairline, `{rounded.lg}`.
- Never put a table inside another table or a narrow container.

### Tabs
Underline tabs: label `{typography.label}` ink-mute, active ink with a 2px indigo ink bar, 1px hairline
under the tab row.

### Dialogs
White, `{rounded.lg}`, Level 2 shadow, 24px padding, title 18px/600 ink, mask `rgba(13,37,61,0.4)`.
Primary action bottom-left (form order), Escape and the close icon dismiss.

### Login
A non-dense surface: a centred 400px white card (`{rounded.lg}`, Level 2) on
`{colors.canvas-soft}` with a faint indigo wash, logo, title in `{typography.page-title}` and a
full-width primary pill.

### Landing (`/`, signed out)
The public front door, and the one place the marketing side of the source applies. Signed-in users
never see it: the route sends them to the dashboard.
- **Structure:** sticky translucent top bar (logo, section links, "Sign in" pill) → centred hero →
  facts strip → Platform cards → How it works → Controls → Roles → closing call to action → footer.
  Content width 1160px; sections 104px apart vertically (72px on phones), alternating white and
  `{colors.canvas-soft}` bands separated by hairlines.
- **Hero:** eyebrow pill, headline in `{typography.display}` (scales down to 38px) with the second
  sentence in `{colors.primary}` — the only place indigo is used for text that is not a link —
  `{typography.lead}` paragraph, a primary and a secondary pill (44px tall), then a static slice of
  the executions console. The slice follows the table rules above and keeps its pagination row so the
  floating event card covers only empty space. It is `role="img"` with a text description.
- **Section heads:** a 14px/600 indigo kicker, the title in `{typography.display-section}`, a 17px
  description; centred, max 680px. Headings use `text-wrap: balance`.
- **Cards:** white, hairline, `{rounded.lg}`, 28px × 24px padding, a 40px icon chip, a 17px/600 title;
  hover lifts 2px with Level 2.
- **Depth:** the console slice alone gets a deeper shadow
  (`0 32px 64px -16px rgba(13,37,61,0.18), 0 16px 32px -16px rgba(0,55,112,0.16)`); everything else
  stays at Levels 0–2. Background washes are soft radial indigo at ≤ 12% opacity, never a mesh.
- **Copy:** describe what the platform does. No invented metrics, customer logos or testimonials.
- **Widths:** laptop only, like the console. Designed for 1280px and up; between 1024px and 1099px the
  platform cards go to two columns and the console slice drops its Order and Time columns. There is
  no tablet or phone layout.

## Number & text formatting
- Decimals are strings end to end (ADR-0014); format with grouping separators, keep the scale the API
  returned, never through floating point.
- Money: `95,000.0000 USD` (amount, space, ISO currency). Quantity: `400.0000` (no currency). Price:
  `101.25000000` (no currency unless the column is money).
- Dates `YYYY-MM-DD`, timestamps `YYYY-MM-DD HH:mm:ss` in UTC.
- An empty value renders as an em dash `—`.

## Do's and don'ts

### Do
- Keep indigo for the main action, links, focus and the current location.
- Right-align and `tnum` every numeric column; label every number's unit (currency) where it has one.
- Use the semantic badge tones consistently for the same state everywhere.
- Keep one table density per page and the header row the same height as the rows.

### Don't
- Don't add new accent colours or use ruby/magenta from the source palette — status colours come only
  from the semantic table.
- Don't render quantities with a currency.
- Don't use weight 300 below 22px.
- Don't put two filled primary buttons in the same panel.
- Don't show list bullets in navigation.

## Responsive behaviour
| Width | Changes |
|---|---|
| ≥ 1200px | Full shell: 240px nav + content |
| 900–1199px | Nav 200px; detail grids drop to 2–3 columns |
| < 900px | Nav becomes a horizontal scroller under the top bar; panels go full-width; tables scroll horizontally inside their panel |
| < 640px | Top bar hides the tagline and role; buttons and inputs 44px tall |

## Accessibility
- Visible focus on every interactive element: 2px `{colors.primary}` outline, 2px offset.
- Text contrast ≥ 4.5:1 (all tokens above on white / canvas-soft pass).
- Status is never colour-only; tables keep their `aria-label`; motion respects
  `prefers-reduced-motion`.

## Where it lives in code
- Tokens: CSS custom properties in `src/style/global.scss` (`--tcs-*`), mirrored from the front matter.
- PrimeReact: the `lara-light-indigo` theme supplies component internals; `src/style/primereact.scss`
  maps the prominent surfaces (buttons, inputs, tables, tabs, paginator, dialog) onto these tokens.
- Shell and pages: `src/components/Layout/*.scss`, `src/components/Table/Table.scss`,
  `src/style/app.scss`.
- Landing: `src/components/Landing/Landing.tsx` and `src/style/landing.scss` (scoped under `.landing`).

## Iteration guide
1. Change one component at a time and update its section here.
2. Reference tokens by name (`{colors.primary}`, `{rounded.lg}`); add new tokens to the front matter
   first.
3. Lint the file with `npx @google/design.md lint apps/admin-web/DESIGN.md`.
4. Check every new table against the Carbon rules above before merging.

---

Portions adapted from awesome-design-md — Copyright (c) 2026 VoltAgent — used under the MIT License:
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions: The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS",
WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
