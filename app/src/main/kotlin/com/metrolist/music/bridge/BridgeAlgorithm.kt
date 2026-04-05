/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Kotlin port of eccopath/lib/bridgeCrawl.ts
 *
 * Bidirectional Beam Search bridge finder.
 *
 * Based on Stanford CS224W "Hip-Hop to Deep House" findings:
 * - Beam search (top K per level) instead of full BFS → ~30 API calls vs hundreds
 * - Tag Jaccard heuristic guides search toward target genre space
 * - Last.fm graph has small-world properties, diameter rarely exceeds 6 hops
 * - Expected meeting depth: 2-3 hops per side
 *
 * Budget: 2 prefetch + (BEAM_WIDTH × MAX_DEPTH × 2 sides) expand
 *         + (BEAM_WIDTH × MAX_DEPTH × 2 sides) tag lookups
 *         ≈ 2 + 40 + 40 = ~82 calls, ~25s at 3.3 req/s with parallelism
 */

package com.metrolist.music.bridge

import com.metrolist.music.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Structured progress update emitted during bridge search.
 */
data class BridgeProgressInfo(
    val phase: String,          // "analyzing", "searching", "deepening", "pathfinding", "fallback"
    val message: String,
    val progress: Float,        // 0.0–1.0
    val depth: Int = 0,
    val maxDepth: Int = 0,
)

/**
 * Result of a bridge search.
 */
data class BridgeResult(
    val found: Boolean,
    val path: List<String>,
)

/**
 * Kotlin port of eccopath/lib/bridgeCrawl.ts bidirectional beam search.
 *
 * Uses [KotlinBridgeCache] for two-level (ConcurrentHashMap + Room) caching and
 * [LastFmRateLimiter] to stay within Last.fm's 5 req/sec free-tier limit.
 */
@Singleton
class BridgeAlgorithm @Inject constructor(
    private val cache: KotlinBridgeCache,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    companion object {
        private const val LIMIT_PER_NODE = 100
        private const val FALLBACK_CONCURRENCY = 4
        private const val FALLBACK_TIMEOUT_MS = 300_000L // 5 minutes
        private const val TAG = "BridgeAlgorithm"
    }

    // Iterative deepening checkpoints — matches TypeScript exactly
    private data class Checkpoint(val maxDepth: Int, val beamWidth: Int, val minHops: Int)

    private val checkpoints = listOf(
        Checkpoint(3, 4, 3),
        Checkpoint(4, 5, 3),
        Checkpoint(5, 6, 3),
    )

    private data class Candidate(
        val name: String,
        val similarity: Float,
        val parent: String,
    )

    /**
     * Find a genre-bridging path between [startArtist] and [endArtist].
     *
     * Uses bidirectional beam search with iterative deepening, then falls back to
     * exhaustive BFS if beam search fails.
     *
     * @param onProgress Progress callback fired throughout the search
     * @return [BridgeResult] with found=true and the path, or found=false on failure
     */
    suspend fun findBridge(
        startArtist: String,
        endArtist: String,
        onProgress: (BridgeProgressInfo) -> Unit = {},
    ): BridgeResult {
        // ── Same-artist shortcut ─────────────────────────────────────────────
        if (startArtist == endArtist) {
            return BridgeResult(found = true, path = listOf(startArtist))
        }

        // ── Prefetch: source and target metadata (parallel) ──────────────────
        onProgress(
            BridgeProgressInfo(
                phase = "analyzing",
                message = "Analyzing $startArtist and $endArtist...",
                progress = 0f,
            )
        )

        val rateLimiter = LastFmRateLimiter(appScope)

        val (sourceMeta, targetMeta) = coroutineScope {
            val sourceDeferred = async(Dispatchers.IO) {
                rateLimiter.acquire()
                cache.getArtistMeta(startArtist)
            }
            val targetDeferred = async(Dispatchers.IO) {
                rateLimiter.acquire()
                cache.getArtistMeta(endArtist)
            }
            Pair(sourceDeferred.await(), targetDeferred.await())
        }

        val sourceTags = sourceMeta?.tags ?: emptyList()
        val targetTags = targetMeta?.tags ?: emptyList()

        // ── State tracking (matching TypeScript variables) ───────────────────
        val forwardParent = HashMap<String, String>()
        val backwardParent = HashMap<String, String>()
        val forwardVisited = mutableSetOf(startArtist)
        val backwardVisited = mutableSetOf(endArtist)
        val metaCache = ConcurrentHashMap<String, CachedArtistMeta>()
        sourceMeta?.let { metaCache[startArtist] = it }
        targetMeta?.let { metaCache[endArtist] = it }

        // Adjacency for fallback path scoring
        val adjacency = HashMap<String, MutableSet<String>>()

        fun addEdge(a: String, b: String) {
            adjacency.getOrPut(a) { mutableSetOf() }.add(b)
            adjacency.getOrPut(b) { mutableSetOf() }.add(a)
        }

        var forwardBeam = mutableListOf(startArtist)
        var backwardBeam = mutableListOf(endArtist)
        var meetingNode: String? = null
        var forwardDepth = 0
        var backwardDepth = 0

        /**
         * Expand all beam nodes in parallel, fetch metadata for top candidates, score and return.
         * Port of TypeScript expandBeam().
         */
        suspend fun expandBeam(
            beam: List<String>,
            ownVisited: MutableSet<String>,
            ownParent: HashMap<String, String>,
            oppositeVisited: Set<String>,
            oppositeTags: List<String>,
        ): Pair<List<Candidate>, String?> {
            // Expand all beam nodes in parallel
            val expansions = coroutineScope {
                beam.map { name ->
                    async(Dispatchers.IO) {
                        rateLimiter.acquire()
                        val artists = cache.getSimilarArtists(name, LIMIT_PER_NODE)
                        Pair(name, artists)
                    }
                }.awaitAll()
            }

            var foundMeeting: String? = null
            val allCandidates = mutableListOf<Candidate>()

            for ((parent, artists) in expansions) {
                for (a in artists) {
                    addEdge(parent, a.name)

                    if (oppositeVisited.contains(a.name)) {
                        foundMeeting = a.name
                        if (!ownParent.containsKey(a.name)) {
                            ownParent[a.name] = parent
                        }
                    }

                    if (!ownVisited.contains(a.name)) {
                        allCandidates.add(Candidate(a.name, a.match, parent))
                    }
                }
            }

            // Deduplicate: keep highest-similarity version of each candidate
            val bestByName = HashMap<String, Candidate>()
            for (c in allCandidates) {
                val existing = bestByName[c.name]
                if (existing == null || c.similarity > existing.similarity) {
                    bestByName[c.name] = c
                }
            }

            // Pre-filter by weirdness (lowest similarity first = weirdest), top 10
            val preFiltered = bestByName.values
                .sortedBy { it.similarity } // weirdest first
                .take(10)

            // Fetch metadata only for candidates missing from cache (~10 calls max)
            val uncached = preFiltered.filter { !metaCache.containsKey(it.name) }
            if (uncached.isNotEmpty()) {
                coroutineScope {
                    uncached.map { c ->
                        async(Dispatchers.IO) {
                            rateLimiter.acquire()
                            val meta = cache.getArtistMeta(c.name)
                            if (meta != null) {
                                metaCache[c.name] = meta
                            }
                        }
                    }.awaitAll()
                }
            }

            // Score candidates
            val scored = preFiltered.map { c ->
                val weirdness = 1f - c.similarity
                val meetingBonus = if (oppositeVisited.contains(c.name)) 0.8f else 0f
                val cachedMeta = metaCache[c.name]
                val tagScore = if (cachedMeta != null) tagJaccard(cachedMeta.tags, oppositeTags) else 0f
                val score = tagScore * 0.35f + weirdness * 0.15f + meetingBonus + 0.2f * Math.random().toFloat()
                Pair(c, score)
            }
            val sortedCandidates = scored.sortedByDescending { it.second }.map { it.first }

            return Pair(sortedCandidates, foundMeeting)
        }

        // ── Iterative deepening beam search ──────────────────────────────────
        // State carries forward between checkpoints — work isn't wasted.

        val totalCheckpoints = checkpoints.size
        var ci = 0
        while (ci < checkpoints.size && meetingNode == null) {
            val cp = checkpoints[ci]
            val cpBaseProgress = ci.toFloat() / totalCheckpoints
            val cpProgressRange = 1f / totalCheckpoints

            onProgress(
                BridgeProgressInfo(
                    phase = if (ci == 0) "searching" else "deepening",
                    message = if (ci == 0)
                        "Searching from $startArtist..."
                    else
                        "Deepening search (pass ${ci + 1}/$totalCheckpoints)...",
                    progress = cpBaseProgress,
                    depth = forwardDepth,
                    maxDepth = cp.maxDepth,
                )
            )

            var depth = forwardDepth
            while (depth < cp.maxDepth && meetingNode == null) {
                val totalHopsIfMet = (depth + 1) + (backwardDepth + 1)
                val acceptMeeting = totalHopsIfMet >= cp.minHops

                // ── Forward expansion ──
                val depthProgress = cpBaseProgress +
                    ((depth - forwardDepth + 0.0f) / max(cp.maxDepth - forwardDepth, 1)) * cpProgressRange

                onProgress(
                    BridgeProgressInfo(
                        phase = if (ci == 0) "searching" else "deepening",
                        message = "Searching from $startArtist...",
                        progress = depthProgress,
                        depth = depth + 1,
                        maxDepth = cp.maxDepth,
                    )
                )

                val (fCandidates, fMeeting) = expandBeam(
                    forwardBeam,
                    forwardVisited,
                    forwardParent,
                    if (acceptMeeting) backwardVisited else emptySet(),
                    targetTags,
                )
                forwardDepth = depth + 1

                if (fMeeting != null && acceptMeeting) {
                    meetingNode = fMeeting
                    break
                }

                val fBeamCandidates = fCandidates.take(cp.beamWidth)
                for (c in fBeamCandidates) {
                    forwardVisited.add(c.name)
                    forwardParent[c.name] = c.parent
                }
                forwardBeam = fBeamCandidates.map { it.name }.toMutableList()

                val totalHopsIfMetBack = forwardDepth + (backwardDepth + 1)
                val acceptMeetingBack = totalHopsIfMetBack >= cp.minHops

                // ── Backward expansion ──
                onProgress(
                    BridgeProgressInfo(
                        phase = if (ci == 0) "searching" else "deepening",
                        message = "Searching from $endArtist...",
                        progress = depthProgress + cpProgressRange * 0.5f / max(cp.maxDepth - (forwardDepth - 1), 1),
                        depth = depth + 1,
                        maxDepth = cp.maxDepth,
                    )
                )

                val (bCandidates, bMeeting) = expandBeam(
                    backwardBeam,
                    backwardVisited,
                    backwardParent,
                    if (acceptMeetingBack) forwardVisited else emptySet(),
                    sourceTags,
                )
                backwardDepth = depth + 1

                if (bMeeting != null && acceptMeetingBack) {
                    meetingNode = bMeeting
                    break
                }

                val bBeamCandidates = bCandidates.take(cp.beamWidth)
                for (c in bBeamCandidates) {
                    backwardVisited.add(c.name)
                    backwardParent[c.name] = c.parent
                }
                backwardBeam = bBeamCandidates.map { it.name }.toMutableList()

                depth++
            }
            ci++
        }

        if (meetingNode == null) {
            // ── Final fallback: exhaustive BFS with safety cap ───────────────
            onProgress(BridgeProgressInfo(phase = "fallback", message = "Widening search...", progress = 0f))

            val result = fallbackBFS(
                startArtist = startArtist,
                endArtist = endArtist,
                limitPerNode = LIMIT_PER_NODE,
                minHops = 3,
                rateLimiter = rateLimiter,
                onProgress = { count, status ->
                    onProgress(
                        BridgeProgressInfo(
                            phase = "fallback",
                            message = status,
                            progress = minOf(count.toFloat() / 200f, 0.95f),
                        )
                    )
                },
            )
            return result
        }

        // ── Reconstruct path through the meeting node ─────────────────────────
        onProgress(BridgeProgressInfo(phase = "pathfinding", message = "Building bridge path...", progress = 0.95f))

        val forwardPath = mutableListOf<String>()
        var node: String? = meetingNode
        while (node != null && node != startArtist) {
            forwardPath.add(0, node)
            node = forwardParent[node]
        }
        forwardPath.add(0, startArtist)

        val backwardPath = mutableListOf<String>()
        node = backwardParent[meetingNode]
        while (node != null && node != endArtist) {
            backwardPath.add(node)
            node = backwardParent[node]
        }
        backwardPath.add(endArtist)

        val path = forwardPath + backwardPath
        Timber.tag(TAG).d("Bridge found: %s", path.joinToString(" -> "))
        return BridgeResult(found = true, path = path)
    }

    // ── Tag Jaccard similarity ───────────────────────────────────────────────

    /**
     * Jaccard similarity between two tag sets.
     * Port of tagJaccard() from bridgeCrawl.ts.
     *
     * Returns 0 when either set is empty. Returns 0 when both sets are empty
     * (matching TypeScript: `if (a.length === 0 || b.length === 0) return 0`).
     */
    internal fun tagJaccard(a: List<String>, b: List<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val setA = a.map { it.lowercase() }.toSet()
        val setB = b.map { it.lowercase() }.toSet()
        val intersection = setA.count { setB.contains(it) }
        val union = (setA + setB).size
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0f
    }

    // ── Fallback: full bidirectional BFS ────────────────────────────────────

    /**
     * Exhaustive bidirectional BFS with 5-minute safety cap.
     * Port of fallbackBFS() from bridgeCrawl.ts.
     */
    private suspend fun fallbackBFS(
        startArtist: String,
        endArtist: String,
        limitPerNode: Int,
        minHops: Int,
        rateLimiter: LastFmRateLimiter,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): BridgeResult {
        val visited = HashMap<String, String>() // node -> "start" | "end"
        visited[startArtist] = "start"
        visited[endArtist] = "end"

        // Adjacency graph for degree-weighted path selection
        val adjacency = HashMap<String, MutableSet<String>>()

        fun addEdge(a: String, b: String) {
            adjacency.getOrPut(a) { mutableSetOf() }.add(b)
            adjacency.getOrPut(b) { mutableSetOf() }.add(a)
        }

        val depthMap = HashMap<String, Int>()
        depthMap[startArtist] = 0
        depthMap[endArtist] = 0

        val startQueue = ArrayDeque<String>().apply { add(startArtist) }
        val endQueue = ArrayDeque<String>().apply { add(endArtist) }

        var expanded = 0
        var connected = false

        val timedResult = withTimeoutOrNull(FALLBACK_TIMEOUT_MS) {
            while (!connected && (startQueue.isNotEmpty() || endQueue.isNotEmpty())) {
                val queue = when {
                    startQueue.isNotEmpty() &&
                        (endQueue.isEmpty() || startQueue.size <= endQueue.size) -> startQueue
                    endQueue.isNotEmpty() -> endQueue
                    else -> break
                }

                val batch = mutableListOf<String>()
                repeat(FALLBACK_CONCURRENCY) {
                    if (queue.isNotEmpty()) batch.add(queue.removeFirst())
                }
                if (batch.isEmpty()) break

                val results = coroutineScope {
                    batch.map { name ->
                        async(Dispatchers.IO) {
                            onProgress(expanded, "Expanding $name...")
                            rateLimiter.acquire()
                            val artists = cache.getSimilarArtists(name, limitPerNode)
                            Pair(name, artists)
                        }
                    }.awaitAll()
                }

                for ((parent, artists) in results) {
                    val pSide = visited[parent] ?: continue
                    val parentDepth = depthMap[parent] ?: 0

                    for (a in artists) {
                        addEdge(parent, a.name)

                        if (!visited.containsKey(a.name)) {
                            visited[a.name] = pSide
                            depthMap[a.name] = parentDepth + 1
                            if (pSide == "start") startQueue.add(a.name)
                            else endQueue.add(a.name)
                        } else if (visited[a.name] != pSide) {
                            val otherDepth = depthMap[a.name] ?: 0
                            val totalHops = (parentDepth + 1) + otherDepth
                            if (totalHops >= minHops) {
                                connected = true
                            }
                        }
                    }
                    if (connected) break
                    expanded++
                }
            }
        }

        if (!connected) return BridgeResult(found = false, path = emptyList())

        onProgress(expanded, "Finding path through obscure artists...")
        val path = degreeWeightedPath(adjacency, startArtist, endArtist, minHops)

        return BridgeResult(found = path.isNotEmpty(), path = path)
    }

    // ── Degree-weighted Dijkstra ─────────────────────────────────────────────

    private data class DijkstraEntry(val cost: Double, val depth: Int, val node: String) : Comparable<DijkstraEntry> {
        override operator fun compareTo(other: DijkstraEntry): Int = this.cost.compareTo(other.cost)
    }

    /**
     * Dijkstra where edge cost = degree of target node.
     * High-degree nodes (popular hubs) are expensive. Low-degree nodes (niche) are cheap.
     * Also enforces a minimum path length.
     *
     * Port of degreeWeightedPath() from bridgeCrawl.ts.
     */
    internal fun degreeWeightedPath(
        adjacency: Map<String, Set<String>>,
        start: String,
        end: String,
        minHops: Int,
    ): List<String> {
        if (start == end) return listOf(start)

        fun degree(node: String): Int = adjacency[node]?.size ?: 0

        val dist = HashMap<String, Double>()
        val parent = HashMap<String, String>()
        val hops = HashMap<String, Int>()

        val pq = PriorityQueue<DijkstraEntry>()

        dist[start] = 0.0
        hops[start] = 0
        pq.add(DijkstraEntry(0.0, 0, start))

        while (pq.isNotEmpty()) {
            val entry = pq.poll()!!
            val currentCost = entry.cost
            val currentDepth = entry.depth
            val current = entry.node

            // Only accept end node if we've taken enough hops
            if (current == end && currentDepth >= minHops) {
                val path = mutableListOf(end)
                var node = end
                while (node != start) {
                    node = parent[node]!!
                    path.add(0, node)
                }
                return path
            }

            if (currentCost > (dist[current] ?: Double.MAX_VALUE)) continue

            val neighbors = adjacency[current] ?: continue

            for (neighbor in neighbors) {
                // Block premature arrival at end
                if (neighbor == end && currentDepth + 1 < minHops) continue

                val edgeCost = degree(neighbor).toDouble()
                val newCost = currentCost + edgeCost
                val newDepth = currentDepth + 1

                if (newCost < (dist[neighbor] ?: Double.MAX_VALUE)) {
                    dist[neighbor] = newCost
                    hops[neighbor] = newDepth
                    parent[neighbor] = current
                    pq.add(DijkstraEntry(newCost, newDepth, neighbor))
                }
            }
        }

        return emptyList()
    }
}
