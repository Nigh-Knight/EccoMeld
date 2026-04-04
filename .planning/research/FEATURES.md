# Feature Landscape: Bridge-Based Music Discovery

**Domain:** Graph/bridge-based artist exploration with playlist generation
**Researched:** 2026-04-03
**Scope:** Bridge discovery UX only — general music player features already exist in the app

---

## Context: The Competitive Landscape

The closest analogues in the wild:

- **Boil the Frog** (Paul Lamere, ex-Spotify) — the direct predecessor. Two artist inputs, Spotify similarity graph to find a path, one track per artist in energy-matching order. Minimal UI: just the inputs, the result, a bypass button, a preview click, and a "Save to Spotify" action. No loading state feedback documented. Stopped working when Spotify killed third-party API access.
- **WhoSampled Six Degrees** — path between artists via samples/covers/credits. Displays the chain with playable tracks at each step. Social sharing (FB/Twitter). Read-only: no playlist customization.
- **Six Degrees of Spotify** (open source) — collaboration-graph path finder. Chain display, no playback.
- **MusicLynx** — artist similarity graph with progressive node expansion. Click a node to expand its neighbors. Artist bio + external links alongside graph. Discovery-oriented but no destination.
- **Music-Map** — visual scatter of similar artists around a seed. Browsing, not path-finding. No playlist output.
- **Every Noise at Once** — genre scatter plot with audio preview per genre. Deep micro-genre exploration. Static, no path generation.

EccoMeld is the only one combining: Last.fm-based niche-weighted path finding + unified playlist output + actual playback (via YT Music) + Android native UX. The gap in the market is real.

---

## Table Stakes

Features users expect from bridge discovery. Missing any of these and the feature feels broken or unfinished.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Two artist search inputs with autocomplete** | Every path-finder app (Boil the Frog, WhoSampled, Six Degrees) starts here. Without it, the feature is unusable. | Low | Autocomplete against Last.fm or YT Music search. Partial-name matching matters — "Red Hot C" must suggest "Red Hot Chili Peppers". |
| **Meaningful loading feedback during bridge computation** | Beam search takes 5-30 seconds on slow connections. A blank screen reads as broken. Users abandon if nothing happens in ~3 seconds. | Low | Animated progress with copy that sets expectations: "Crawling Last.fm graph…", "Found 3 of 6 hops…". Not a spinner. |
| **Linear path result display with intermediate artists labeled** | The core output of every path-finder tool is the visible chain. Without it users have no idea what happened. WhoSampled, Boil the Frog, Six Degrees all show the chain explicitly. | Low-Med | Seed at top, target at bottom, bridge artists between with connecting lines. Genre tags and listener counts on each bridge artist (niche signal). |
| **Immediate auto-play on bridge completion** | If there's a 10-second delay between "path found" and "music starts", users interpret it as a second loading failure. The PRD explicitly calls this out. | Med | Start fetching YT Music tracks during path computation, not after. Optimistic queue population. |
| **Silent skip on track match failures** | YT Music matching will fail for some tracks. Interrupting playback with error dialogs for each failure destroys the experience. Boil the Frog's model: energy-matched selection gracefully handles gaps. | Low | Skip silently. Optionally surface a subtle count ("3 tracks unavailable") in the path view, but never block playback. |
| **Niche bridge artists, not mainstream hubs** | The entire value proposition over Spotify Radio/Discover Weekly. If the path from Death Grips to Taylor Swift goes through Drake, users will correctly perceive this as "just a recommendation algorithm". EccoPath's niche-weighted scoring is what delivers this. | High (already built in EccoPath) | EccoPath already does this via degree-weighted Dijkstra and niche scoring. The display must reinforce it: show listener counts explicitly so users can see the niche. |
| **Error state when no path found** | Some artist pairs have no graph connection within the available Last.fm data. A silent failure (blank screen or infinite spinner) is worse than a clear "couldn't find a path" message. | Low | Clear message with suggestion: "Try a less obscure artist as one endpoint" or "Retry with more hops". |
| **Artist search works for obscure artists** | The core use case involves niche artists as endpoints or bridge waypoints. Search that only returns popular artists breaks the feature for power users. | Med | Use Last.fm artist.search as the primary suggest source — it has far broader coverage than YT Music search for niche acts. Fall back to YT Music for confirmation. |

---

## Differentiators

Features that make EccoMeld's bridge discovery distinctly better than anything in the market. Not expected by users, but create the "oh, this is actually different" moment.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Listener count + genre tags on each bridge artist** | Makes niche visible. A bridge artist with 8,000 Last.fm listeners next to a "hyperpop" tag tells the user instantly: "this is someone I've never heard of and that's the point." No competitor shows this. | Low | Data comes directly from Last.fm artist.getInfo. Display inline in the path view. |
| **2 popular + 3-5 deep cuts track selection per artist** | Boil the Frog picks one well-known track per artist. EccoMeld gives you enough time with each artist to actually develop a taste for them. "5-7 tracks per bridge artist so you have time to actually enjoy each one" — this is a fundamental philosophy difference. | Med | Requires fetching top tracks + some deeper catalog tracks per artist. Last.fm artist.getTopTracks + some offset into the results. |
| **Random Bridge from Spotify liked artists (lowest Tag Jaccard overlap)** | Removes activation energy from discovery. "Surprise me" is a well-known serendipity pattern (multiple random song generators have this). The genre-opposite selection from your own library makes it personally relevant, not just random. | Med | Requires Spotify liked artists + tag fetching for each + Jaccard distance computation. The EccoPath algorithm already has this logic. |
| **Path view persists while music plays** | Users can refer back to "where am I in the journey" during playback. No competitor does this — Boil the Frog shows you the playlist then you lose the path context. | Low | Keep the linear path view accessible alongside the player. Highlight the currently-playing artist's node. |
| **Currently-playing artist highlighted in path view** | Contextualizes each song within the larger journey. "I'm in the hyperpop section of the Death Grips → Taylor Swift bridge" is more engaging than a generic "now playing" display. | Low-Med | Requires mapping queue position back to bridge path position. |
| **Known vs. unknown artist visual distinction** | After Spotify import, EccoMeld can mark bridge artists you've already heard as "known" and surface ones you haven't as discoveries. Boil the Frog had no such distinction. | Med | Requires Spotify top artists + saved artists as a "known" set. Visual indicator (e.g., dot color or "NEW" badge) on path nodes. |
| **Meaningful loading copy** | "Computing your bridge…" with step-by-step progress (hops found, artists checked) turns waiting time into anticipation. Every generic spinner is a missed opportunity to reinforce the product's personality. | Low | Just copy and an animated counter. No technical complexity. |
| **Seed artist suggestions from Spotify library** | "Bridge from your taste" — showing 3-5 of the user's top Spotify artists as quick-pick seeds means the user never stares at a blank input. Reduces cold start friction. | Low | Already available from Spotify import. Surface as chips below the From/To inputs. |

---

## Anti-Features

Things to deliberately NOT build in this milestone. Each one either doesn't fit the MVP scope, would increase complexity without proportional value, or would undermine the core UX.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Editable playlist / track removal before play** | Boil the Frog's "bypass" button and track preview are table stakes for a web tool but add significant complexity for a mobile app. The bridge playlist is a curated journey — editing it breaks the coherence of the genre transition. | Auto-play immediately. If a user skips tracks during playback, that's already supported by the existing Meld player. Don't add a pre-play editing step. |
| **Social sharing of bridge paths** | WhoSampled and Boil the Frog both added sharing. It's a reasonable P2 feature but adds zero discovery value and significant implementation overhead (deep links, sharable state, web preview). | Defer to P2. Focus on the core playback loop first. |
| **In-path graph visualization (network graph with nodes/edges)** | MusicLynx, Vibe Graph, and Music Galaxy all built force-directed artist graphs. They're visually impressive but the interaction model is complex (pan, zoom, tap nodes) and adds latency. The PRD explicitly defers this to the Graph Screen in P1. | Use the linear path view (vertical journey layout). Much simpler, equally communicative for a 5-7 hop chain, and works naturally in a scrollable Android layout. |
| **Track preview before committing to bridge** | Boil the Frog offered 30-second previews before saving. For EccoMeld, the entire point is exploration — previewing before starting undermines the "just go" philosophy. The random bridge button embodies this. | Let the music start. Users can skip tracks they don't like with existing player controls. |
| **Configurable hop count / path depth slider** | Sounds like a power-user feature. In practice, it increases the surface area for confusion ("what does hop count mean?") and the bridge algorithm already auto-selects a good path length. | Expose no path-depth controls in MVP. If research shows users want this, add it in P1. |
| **Real-time "who else has walked this bridge" stats** | Community features require a backend, user accounts, and data retention. The app is personal use, GPL, sideload-only. | Not applicable. Keep it local. |
| **AI-generated bridge explanation / narration** | A tempting LLM add-on but adds API cost, latency, and complexity. The genre tags + listener counts on the path view already do the explanatory work. | Use genre tags and listener counts. They're honest, always available (Last.fm), and load fast. |
| **Manual path editing / artist swap** | Letting users swap bridge artists requires re-running partial segments of the algorithm, handling invalid paths, and updating the playlist mid-stream. High complexity, low discovery value. | If users want a different path, they re-run the bridge with a new pair. That's simpler and reinforces the serendipity model. |
| **Popularity-based track selection (billboard hits only)** | Boil the Frog selected "well-known" tracks. EccoMeld's differentiator is the 2 popular + 3-5 deep cuts model. Reverting to hits-only would make the bridge feel like Spotify Radio. | Always include deep cuts. The 2 popular tracks per artist serve as the hook; deep cuts are the discovery. |

---

## Feature Dependencies

```
Artist search autocomplete
    └── Bridge computation (needs valid artist IDs)
            └── Path result display (needs completed path)
                    ├── Queue/playlist population (needs path + artist IDs)
                    │       └── YT Music track matching (needs artist names + track names)
                    │               └── Auto-play (needs at least partial queue populated)
                    │                       └── Currently-playing highlight in path view (needs queue position)
                    └── Listener count + genre tags display (needs Last.fm artist.getInfo per node)

Random Bridge button
    └── Spotify liked artists (needs Spotify auth + import)
            └── Tag Jaccard distance computation (needs tags for each artist)
                    └── Bridge computation (same as above)

Known/unknown artist distinction
    └── Spotify liked artists + top artists (needs Spotify auth + import)
            └── Path result display (overlays known/unknown signal on nodes)
```

Critical path for MVP: autocomplete → bridge computation → path display → YT Music matching → auto-play. Everything else is additive.

---

## MVP Recommendation

Prioritize:
1. **Two artist search inputs with Last.fm autocomplete** — blocks everything
2. **Meaningful loading feedback with step progress** — prevents perceived failures
3. **Linear path result with genre tags + listener counts** — this is the product's face
4. **Optimistic queue population + auto-play** — the payoff
5. **Silent skip on YT match failures** — prevents broken playback from degrading the experience
6. **Clear "no path found" error state** — prevents silent failures
7. **Random Bridge from Spotify liked artists** — one-tap discovery, strong differentiator

Defer to P1:
- **Currently-playing highlight in path view** — valuable but requires queue-to-path position mapping; ship after core playback is stable
- **Known/unknown artist distinction** — requires Spotify import to be reliable first
- **Staging cards / path walker** — already scoped to P1 in PRD

Defer to P2:
- **Social sharing** — no backend, personal use app
- **Graph visualization** — already scoped to P1 Graph Screen; force-directed graph is a separate UX mode

---

## Sources

- [Boil the Frog — Music Machinery (original writeup)](https://musicmachinery.com/2013/01/02/boil-the-frog-2/)
- [Boil the Frog — RouteNote feature breakdown](https://routenote.com/blog/boil-the-frog-generates-a-playlist-that-takes-you-between-two-artists/)
- [WhoSampled Six Degrees](https://www.whosampled.com/six-degrees/)
- [Six Degrees of Spotify — Emily Louie](https://emily.louie.ca/sixdegrees/)
- [MusicLynx — ACM paper](https://dl.acm.org/doi/fullHtml/10.1145/3184558.3186970)
- [Music-Map — The Tourist Map of Music](https://www.music-map.com/)
- [Every Noise at Once](https://everynoise.com/)
- [Graph Embeddings for Music Discovery — Casey Primozic](https://cprimozic.net/blog/graph-embeddings-for-music-discovery/)
- [Why I Built a Platform That Finds, Not Buries, Niche Artists — Hypebot](https://www.hypebot.com/why-i-built-a-music-discovery-platform-that-finds-not-buries-niche-artists/)
- [Recommendation Fatigue — Godmode Newsletter](https://godmodemusic.substack.com/p/recommendation-fatigue-music-discovery)
- [How to break free of Spotify's algorithm — MIT Technology Review](https://www.technologyreview.com/2024/08/16/1096276/spotify-algorithms-music-discovery-ux/)
- [Redesigning Search: Building A Music Discovery App — Bits And Music](https://bitsandmusic.com/post/building-music-discovery-app-4/)

Confidence levels:
- Table stakes: HIGH — these are verified by existing products (Boil the Frog, WhoSampled, Six Degrees)
- Differentiators: MEDIUM — derived from gap analysis between existing tools and EccoMeld's stated design philosophy; some are novel claims
- Anti-features: MEDIUM — reasoning based on competitor analysis and PRD design decisions, not empirical user testing
