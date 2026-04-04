# Phase 6: Linear Path Result View - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-04-04
**Phase:** 06-linear-path-result-view
**Areas discussed:** Path view location, Artist node content, Path persistence
**Mode:** Batched decisions (all phases discussed simultaneously)

---

## Path View Location

| Option | Description | Selected |
|--------|-------------|----------|
| Replace search inputs | Same tab, inputs collapse | |
| New screen on nav stack | Navigate to result screen | |
| Bottom sheet | Slides up over Bridge tab | ✓ |

**User's choice:** Bottom sheet

---

## Artist Node Content

| Option | Description | Selected |
|--------|-------------|----------|
| Tags + listener count | Clean and minimal | ✓ |
| Tags + listener count + thumbnail | Richer with images | |
| Tags + listener count + now playing | With highlight indicator | |

**User's choice:** Just tags + listener count (now playing highlight added as separate requirement)

---

## Path Persistence

| Option | Description | Selected |
|--------|-------------|----------|
| Replace old path | One bridge at a time | ✓ |
| Stack/history | Keep old bridges accessible | |

**User's choice:** Initially picked history (option 2), but accepted Claude's recommendation to defer history to P1 and use replace for MVP.
**Notes:** Bridge history is P1/deferred in PROJECT.md. Replace is simpler and validates core experience first.

---

## Claude's Discretion

- Bottom sheet implementation details
- Visual styling of artist nodes
- Now playing highlight animation
- Genre tag display style

## Deferred Ideas

- Bridge history / replay — P1, deferred to next milestone
