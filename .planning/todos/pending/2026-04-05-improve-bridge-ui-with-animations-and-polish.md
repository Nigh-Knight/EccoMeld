---
created: 2026-04-05T02:43:40.854Z
title: Improve Bridge UI with animations and polish
area: ui
files:
  - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
  - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/PathSheet.kt
---

## Problem

The Bridge screen UI needs polish. The bottom sheet path visualization is functional but lacks visual refinement — no animations on path reveal, no smooth transitions between states (Idle → Searching → PathFound → PlaylistReady), and the overall layout needs design attention. The collapsed sheet peek and expanded path view need better visual hierarchy.

## Solution

- Add entry animations for path nodes (staggered fade-in as each artist appears)
- Animate state transitions (searching spinner → path reveal)
- Polish the bottom sheet: better collapsed preview, smoother expand/collapse
- Consider the EccoPath web UI as design reference (dark theme, glassmorphic panels)
- Use the ui-ux-pro-max skill for design guidance
