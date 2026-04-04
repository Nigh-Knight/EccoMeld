# Phase 5: Playlist Builder + Auto-Play - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-04-04
**Phase:** 05-playlist-builder-auto-play
**Areas discussed:** Track selection, Queue behavior
**Mode:** Batched decisions (all phases discussed simultaneously)

---

## Track Selection Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Last.fm top tracks only | Get track names from Last.fm | |
| YT Music search only | Search YT Music directly | |
| Both | Last.fm for names, YT Music for playback | ✓ |

**User's choice:** Both — Last.fm for track names, YT Music for matching to playable IDs

---

## Queue Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Replace current queue | Silently take over | |
| Play next | Queue after current song | |
| Ask the user | Dialog with options | ✓ |

**User's choice:** Dialog popup asking "Replace current queue?" vs "Play next"

---

## Claude's Discretion

- Fuzzy match algorithm details
- Deep cuts count per artist
- ListQueue vs custom BridgeQueue
- Dialog styling

## Deferred Ideas

None
