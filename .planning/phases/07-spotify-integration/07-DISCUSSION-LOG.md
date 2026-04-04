# Phase 7: Spotify Integration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-04-04
**Phase:** 07-spotify-integration
**Areas discussed:** Seed suggestions layout, Random Bridge location, Known/NEW badges
**Mode:** Batched decisions (all phases discussed simultaneously)

---

## Seed Suggestions Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Horizontal scrollable chips | Row of artist pills | ✓ |
| Grid of small artist cards | With thumbnails | |
| Simple text list | Minimal | |

**User's choice:** Horizontal scrollable chips
**Notes:** User expanded scope: include YT Music library artists too, not just Spotify. Mixed together in one row, not separated.

---

## Random Bridge Button Location

| Option | Description | Selected |
|--------|-------------|----------|
| Prominent button between inputs | Easy to discover | |
| FAB | Floating action button | ✓ |
| Inside suggestions section | Below inputs with chips | |

**User's choice:** FAB
**Notes:** Claude recommended FAB always visible (not gated behind Spotify), works with YT Music library artists too. User agreed.

---

## Known/NEW Badges

| Option | Description | Selected |
|--------|-------------|----------|
| Small colored pill | "NEW" accent, "known" muted | |
| Icon only | Star/sparkle | |
| Claude decides | Whatever fits Material 3 | ✓ |

**User's choice:** Claude's discretion — fit the Material 3 theme

---

## Claude's Discretion

- Badge visual design
- Chip row styling
- Number of seed suggestions
- FAB icon
- Tag Jaccard implementation

## Deferred Ideas

- Expanded seed suggestions with genre grouping
- "Bridge from your mood" — P2
- Smart random avoiding recently-bridged pairs
