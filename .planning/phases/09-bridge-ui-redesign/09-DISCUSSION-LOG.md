# Phase 9: Bridge UI Redesign - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 09-bridge-ui-redesign
**Areas discussed:** Dropdown autocomplete style, Progressive disclosure animation, State transition animations, Seed suggestions placement
**Mode:** Auto (all decisions auto-selected as recommended defaults)

---

## Dropdown Autocomplete Style

| Option | Description | Selected |
|--------|-------------|----------|
| Standard dropdown below input | Familiar Android pattern, Material 3 ExposedDropdownMenu or custom | ✓ |
| Inline ghost text (current) | Single suggestion auto-fills, EccoPath web style | |
| Full-screen search overlay | Like YouTube/Spotify search | |

**User's choice:** [auto] Standard dropdown below input (recommended default)
**Notes:** 5 results max, dismiss on outside tap or back. Ghost text components fully removed.

---

## Progressive Disclosure Animation

| Option | Description | Selected |
|--------|-------------|----------|
| Slide down with fade-in | Second input slides into view smoothly | ✓ |
| Expand in place | Second input expands from zero height | |
| Crossfade swap | First input transforms into chip + second input | |

**User's choice:** [auto] Slide down with fade-in (recommended default)
**Notes:** Auto-focus on second input. Auto-trigger bridge after both confirmed.

---

## State Transition Animations

| Option | Description | Selected |
|--------|-------------|----------|
| Crossfade inputs → progress | Inputs collapse, progress indicator takes center | ✓ |
| Overlay progress on inputs | Keep inputs visible but dimmed with progress overlay | |
| Slide inputs up, progress below | Inputs slide up, progress bar appears below | |

**User's choice:** [auto] Crossfade with progress indicator replacing inputs (recommended default)
**Notes:** Bottom sheet slides up with spring for PathFound/PlaylistReady (Phase 6 consistency).

---

## Seed Suggestions Placement

| Option | Description | Selected |
|--------|-------------|----------|
| Below single search input | Most discoverable, visible in Idle state | ✓ |
| Above search input | Less conventional but eye-catching | |
| Collapsible section | Toggle to show/hide | |

**User's choice:** [auto] Below the single search input (recommended default)
**Notes:** Same SeedSuggestionsRow component, repositioned. Random Bridge FAB unchanged.

---

## Claude's Discretion

- Dropdown implementation approach (ExposedDropdownMenu vs custom)
- Animation durations and easing curves
- Confirmed artist chip styling
- Search input component choice (SearchBar vs TextField)

## Deferred Ideas

None
