<div align="center">

<img src="https://hcti.io/v1/image/019d5f69-8cab-78a1-bd11-34e82ce47586" alt="EccoMeld - Bridge Discovery for Meld" width="800"/>

[![Latest release](https://img.shields.io/github/v/release/Nigh-Knight/EccoMeld?style=for-the-badge)](https://github.com/Nigh-Knight/EccoMeld/releases/latest)
[![GitHub license](https://img.shields.io/github/license/Nigh-Knight/EccoMeld?style=for-the-badge)](https://github.com/Nigh-Knight/EccoMeld/blob/eccomeld/LICENSE)

</div>

## What is EccoMeld?

**EccoMeld** is a fork of [Meld](https://github.com/FrancescoGrazioso/Meld) that adds **Bridge Discovery** — a new way to explore music by finding genre-bridging paths between any two artists. Pick Daft Punk and Radiohead, and EccoMeld finds a 5-7 hop journey through niche midpoint artists you've never heard of, then auto-plays the whole thing.

All existing Meld features stay fully intact — home feed, search, library, player, lyrics, downloads, Spotify integration, Discord RPC, everything. Bridge is added as a new tab.

<div align="center">

<img src="https://hcti.io/v1/image/019d5f69-d4be-7117-804b-dce13854e77c" alt="How Bridge Discovery Works" width="800"/>

</div>

## Bridge Discovery

<div align="center">

<img src="https://hcti.io/v1/image/019d5f6a-0f38-753d-85ea-49aebb240ad6" alt="Features" width="800"/>

</div>

- **Bidirectional beam search** — Native Kotlin algorithm traverses Last.fm's similarity graph to find genre-bridging paths between any two artists
- **Progressive streaming** — Music starts playing within seconds of finding the path. No waiting for full playlist resolution
- **Stacked bridge cards** — Run multiple bridges back-to-back. Each result is a collapsible card. Tap any artist to skip to their tracks
- **Smart track matching** — Fuzzy YT Music matching with video fallback, Unicode support for global catalogs, and electronic music normalization
- **Spotify seed suggestions** — Your Spotify and YT Music library artists as quick-pick starting points, with Known/NEW discovery badges
- **30% randomness** — Same two artists produce different paths each time, so every bridge is a unique journey
- **Collab artist splitting** — "Chris Brown & Young Thug" style Last.fm entries are split to the relevant single artist

## All Meld Features Included

EccoMeld is a superset of Meld. Everything Meld does, EccoMeld does too:

- **Spotify + YouTube Music fusion** — Spotify powers recommendations and search, YouTube Music handles playback
- **No Spotify Premium required** — Uses Spotify data APIs, not streaming
- Background playback with media controls
- Library management, playlists, downloads
- Live lyrics from multiple providers
- Discord Rich Presence
- Material 3 dynamic theming
- Android Auto support
- And everything else from [Meld's feature list](https://github.com/FrancescoGrazioso/Meld#features)

## Download

<div align="center">

<a href="https://github.com/Nigh-Knight/EccoMeld/releases/latest"><img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="82"></a>

</div>

> Download the APK from [Releases](https://github.com/Nigh-Knight/EccoMeld/releases) and install on your Android device (API 26+). You may need to allow installation from unknown sources.

## Setup

### Bridge Discovery

Bridge works out of the box — no configuration needed. Just open the Bridge tab and start searching.

For best results:
- **Log into Spotify** (Settings > Integrations > Spotify) to get personalized seed suggestions
- **Log into YouTube Music** (Settings > Account) for better track matching on niche artists

### Spotify Integration

Same as Meld — see [Meld's setup guide](https://github.com/FrancescoGrazioso/Meld#spotify-integration).

### Building from Source

```bash
# Clone with submodule
git clone --recurse-submodules https://github.com/Nigh-Knight/EccoMeld.git

# Add Last.fm API keys to local.properties
echo "LASTFM_API_KEY=your_key" >> local.properties
echo "LASTFM_SECRET=your_secret" >> local.properties

# Build
./gradlew installUniversalFossDebug
```

Get Last.fm keys from [last.fm/api/account/create](https://www.last.fm/api/account/create).

## How the Bridge Algorithm Works

EccoMeld uses a **bidirectional beam search** on Last.fm's artist similarity graph:

1. **Prefetch** — Fetch tags and metadata for both seed artists
2. **Genre distance** — Tag Jaccard similarity determines search depth (3-7 hops)
3. **Beam expansion** — Expand top K candidates from each side, scored by tag overlap, obscurity, and randomness
4. **Meeting point** — When forward and backward beams intersect, reconstruct the path
5. **Fallback BFS** — If beam search fails, exhaustive bidirectional BFS with 5-minute timeout
6. **Track resolution** — Each path artist gets 2 popular + up to 5 deep-cut tracks resolved via YouTube Music

Two-level caching (in-memory + Room DB) means repeated searches are near-instant.

## Credits

EccoMeld is a fork of [Meld](https://github.com/FrancescoGrazioso/Meld) by [Francesco Grazioso](https://github.com/FrancescoGrazioso). Bridge discovery is based on the algorithm from [EccoPath](https://github.com/Nigh-Knight/EccoPath).

### Upstream Projects

- **Meld** — [Francesco Grazioso](https://github.com/FrancescoGrazioso)
- **Metrolist** — [Mo Agamy](https://github.com/mostafaalagamy)
- **InnerTune** — [Zion Huang](https://github.com/z-huang) · [Malopieds](https://github.com/Malopieds)
- **OuterTune** — [Davide Garberi](https://github.com/DD3Boh) · [Michael Zh](https://github.com/mikooomich)

### Libraries

- [Kizzy](https://github.com/dead8309/Kizzy) — Discord Rich Presence
- [Better Lyrics](https://better-lyrics.boidu.dev) — Time-synced lyrics
- [metroserver](https://github.com/MetrolistGroup/metroserver) — Listen Together
- [MusicRecognizer](https://github.com/aleksey-saenko/MusicRecognizer) — Shazam integration

## Disclaimer

This project is not affiliated with YouTube, Google LLC, Spotify AB, or Last.fm. Use at your own risk. All trademarks belong to their respective owners. Licensed under GPL-3.0.
