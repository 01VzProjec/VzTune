#!/bin/bash
# ============================================================
# VzTune FIX4 - Canal via NewPipe p/ TODOS videos + TODAS playlists
# + aba Shorts no Search (depois de Videos, grade 2 colunas)
# NewPipeExtractor v0.26.5 - SEM subir versao, SEM ChannelExtractor.tabs
# (na 0.26.5 ChannelExtractor nao tem abas: shorts/playlists do canal
#  vem da playlist Uploads UU... + busca de playlists filtrada pelo canal)
# Roda na raiz do projeto VzTune.
# ============================================================
set -e
echo "== FIX4 Canal + Shorts =="

python3 - <<'PY'
import re, sys
def read(p):
    with open(p, encoding="utf-8") as f: return f.read()
def write(p, t):
    with open(p, "w", encoding="utf-8") as f: f.write(t)
def must(cond, msg):
    if not cond:
        print(f"ERRO {msg}")
        sys.exit(1)
    print(f"OK {msg}")

# ---------- 1) SearchHit: ShortHit ----------
p = "core/src/main/kotlin/com/vztechcore/vztune/core/domain/search/SearchHit.kt"
t = read(p)
if "ShortHit" not in t:
    old = """    data class VideoHit(val track: Track) : SearchHit {
        override val stableKey: String get() = "video_${track.id}"
    }"""
    must(old in t, "SearchHit.kt: ancora VideoHit")
    t = t.replace(old, old + """

    data class ShortHit(val track: Track) : SearchHit {
        override val stableKey: String get() = "short_${track.id}"
    }""")
    write(p, t)
must("ShortHit" in read(p), "SearchHit.kt: ShortHit")

# ---------- 2) SearchFilter: SHORTS depois de VIDEOS ----------
p = "core/src/main/kotlin/com/vztechcore/vztune/core/domain/search/SearchFilter.kt"
t = read(p)
if "SHORTS" not in t:
    must('VIDEOS("Vídeos", null),' in t, "SearchFilter.kt: ancora VIDEOS")
    t = t.replace('VIDEOS("Vídeos", null),', 'VIDEOS("Vídeos", null),\n    SHORTS("Shorts", null),')
    write(p, t)
must("SHORTS" in read(p), "SearchFilter.kt: SHORTS depois de VIDEOS")

# ---------- 3) YouTubeRepository ----------
p = "data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
t = read(p)
if "shortSearches" not in t:
    must("private val playlistSearches" in t, "YouTubeRepository: ancora playlistSearches")
    t = t.replace(
        "private val playlistSearches = mutableMapOf<String, PlaylistSearchSession>()",
        "private val playlistSearches = mutableMapOf<String, PlaylistSearchSession>()\n    private val shortSearches = mutableMapOf<String, VideoSearchSession>()"
    )
    t = t.replace(
        "videoSearches.clear()\n        playlistSearches.clear()",
        "videoSearches.clear()\n        playlistSearches.clear()\n        shortSearches.clear()"
    )
    write(p, t)
must("shortSearches" in read(p), "YouTubeRepository: shortSearches")

t = read(p)
if "val shorts:" not in t:
    old = """    data class ChannelPage(
        val videos: List<Track>,
        val channelName: String?,
        val avatarUrl: String?,
        val subscriberCount: String?,
    )"""
    must(old in t, "YouTubeRepository: ancora ChannelPage")
    t = t.replace(old, """    data class ChannelPage(
        val videos: List<Track>,
        val shorts: List<Track> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val channelName: String?,
        val avatarUrl: String?,
        val bannerUrl: String? = null,
        val description: String? = null,
        val subscriberCount: String?,
    )""")
    write(p, t)
must("val shorts:" in read(p), "YouTubeRepository: ChannelPage com shorts/playlists/banner/descricao")

t = read(p)
if "searchShorts" not in t:
    must("// ================= SMART VIDEO MATCH" in t, "YouTubeRepository: ancora SMART VIDEO MATCH")
    shorts_code = """
    data class ShortPage(
        val items: List<SearchHit.ShortHit>,
        val hasMore: Boolean,
    )

    suspend fun searchShorts(
        query: String,
        loadMore: Boolean,
        pageSize: Int = 30,
        searchId: String = query,
    ): ShortPage = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchShorts: NewPipe not initialized")
            return@withContext ShortPage(emptyList(), false)
        }
        runCatching {
            if (!loadMore) {
                val extractor = runCatching {
                    ServiceList.YouTube.getSearchExtractor(query, listOf("shorts"), null)
                }.getOrElse { ServiceList.YouTube.getSearchExtractor(query) }
                extractor.fetchPage()
                val session = VideoSearchSession(extractor, extractor.initialPage.nextPage)
                shortSearches[searchId] = session
                val items = parseShortItems(extractor.initialPage.items).take(pageSize)
                if (items.isEmpty()) {
                    val generic = ServiceList.YouTube.getSearchExtractor(query)
                    generic.fetchPage()
                    val genericItems = parseShortItems(generic.initialPage.items).take(pageSize)
                    ShortPage(genericItems, generic.initialPage.nextPage != null)
                } else {
                    ShortPage(items, session.nextPage != null)
                }
            } else {
                val session = shortSearches[searchId]
                    ?: return@runCatching ShortPage(emptyList(), false)
                val page = session.nextPage
                    ?: return@runCatching ShortPage(emptyList(), false)
                val next = session.extractor.getPage(page)
                session.nextPage = next.nextPage
                ShortPage(parseShortItems(next.items).take(pageSize), session.nextPage != null)
            }
        }.getOrElse { e ->
            Log.e(TAG, "searchShorts failed for query=$query", e)
            ShortPage(emptyList(), false)
        }
    }

"""
    t = t.replace("// ================= SMART VIDEO MATCH", shorts_code + "    // ================= SMART VIDEO MATCH")
    write(p, t)
must("searchShorts" in read(p), "YouTubeRepository: searchShorts")

t = read(p)
if "parseShortItems" not in t:
    must("private fun parsePlaylistItems" in t, "YouTubeRepository: ancora parsePlaylistItems")
    short_parser = """    private fun parseShortItems(items: List<InfoItem>): List<SearchHit.ShortHit> {
        return items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->
            val rawUrl = stream.url ?: return@mapNotNull null
            if (!rawUrl.contains("/shorts/")) return@mapNotNull null
            val videoId = extractVideoId(rawUrl) ?: return@mapNotNull null
            SearchHit.ShortHit(
                Track(
                    id = videoId,
                    title = stream.name ?: "Untitled",
                    artist = stream.uploaderName ?: "",
                    durationSeconds = stream.duration,
                    thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                    source = Source.YOUTUBE,
                )
            )
        }
    }

"""
    t = t.replace("    private fun parsePlaylistItems", short_parser + "    private fun parsePlaylistItems")
    write(p, t)
must("parseShortItems" in read(p), "YouTubeRepository: parseShortItems")

t = read(p)
if 'substringAfter("/shorts/")' not in t:
    must("private fun extractVideoId" in t, "YouTubeRepository: ancora extractVideoId")
    old = """    private fun extractVideoId(url: String): String? = try {
        val uri = Uri.parse(url)
        uri.getQueryParameter("v")
            ?: uri.lastPathSegment?.takeIf { uri.host?.contains("youtu.be") == true }
    } catch (e: Exception) {
        null
    }"""
    if old in t:
        t = t.replace(old, """    private fun extractVideoId(url: String): String? = try {
        val uri = Uri.parse(url)
        uri.getQueryParameter("v")
            ?: run {
                val path = uri.path ?: ""
                if (path.contains("/shorts/")) {
                    path.substringAfter("/shorts/").substringBefore("/").substringBefore("?").takeIf { it.isNotBlank() }
                } else {
                    uri.lastPathSegment?.takeIf { uri.host?.contains("youtu.be") == true }
                }
            }
    } catch (e: Exception) {
        null
    }""")
        write(p, t)
    else:
        print("AVISO extractVideoId com formato diferente - pulando")
must('substringAfter("/shorts/")' in read(p), "YouTubeRepository: extractVideoId entende /shorts/")

t = read(p)
if "getChannelForPlaylist" not in t:
    must("Extrai o UC" in t, "YouTubeRepository: ancora Extrai o UC")
    code = """
    suspend fun getChannelForPlaylist(playlistId: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        val rawId = playlistId.removePrefix("YT:").removePrefix("VL").trim()
        if (rawId.isBlank()) return@withContext null
        runCatching {
            val extractor = ServiceList.YouTube.getPlaylistExtractor(
                "https://www.youtube.com/playlist?list=$rawId"
            )
            extractor.fetchPage()
            val uploaderName = extractor.uploaderName?.trim().orEmpty()
            val uploaderUrl = extractor.uploaderUrl?.trim().orEmpty()
            if (uploaderName.isBlank() || uploaderUrl.isBlank()) return@runCatching null
            val channelId = extractChannelId(uploaderUrl) ?: return@runCatching null
            val avatarUrl = runCatching {
                val ch = ServiceList.YouTube.getChannelExtractor(
                    "https://www.youtube.com/channel/$channelId"
                )
                ch.fetchPage()
                ch.avatars?.lastOrNull()?.url
            }.getOrNull()
            YouTubeChannelInfo(
                channelId = channelId,
                channelName = cleanChannelName(uploaderName),
                avatarUrl = avatarUrl,
                channelUrl = uploaderUrl,
            )
        }.getOrNull()
    }

"""
    idx = t.find("Extrai o UC")
    start = t.rfind("/**", 0, idx)
    t = t[:start] + code + t[start:]
    write(p, t)
must("getChannelForPlaylist" in read(p), "YouTubeRepository: getChannelForPlaylist")

t = read(p)
if "ChannelHeader" not in t:
    must("suspend fun browseChannel" in t, "YouTubeRepository: ancora browseChannel")
    must("private fun formatSubscribers" in t, "YouTubeRepository: ancora formatSubscribers")
    start = t.find("    suspend fun browseChannel")
    end = t.find("    private fun formatSubscribers", start)
    must(start != -1 and end != -1, "YouTubeRepository: browseChannel limites")
    new_browse = """    private data class ChannelHeader(
        val name: String,
        val avatar: String?,
        val banner: String?,
        val description: String,
        val subs: String?,
    )

    suspend fun browseChannel(channelId: String): ChannelPage? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        if (channelId.isBlank() || !channelId.startsWith("UC")) return@withContext null
        val header = runCatching {
            val extractor = ServiceList.YouTube.getChannelExtractor(
                "https://www.youtube.com/channel/$channelId"
            )
            extractor.fetchPage()
            ChannelHeader(
                name = runCatching { extractor.name }.getOrNull()?.trim().orEmpty(),
                avatar = runCatching { extractor.avatars?.lastOrNull()?.url }.getOrNull(),
                banner = runCatching { extractor.banners?.lastOrNull()?.url }.getOrNull(),
                description = runCatching { extractor.description }.getOrNull()?.trim().orEmpty(),
                subs = runCatching {
                    extractor.subscriberCount.takeIf { it > 0 }?.let { formatSubscribers(it) }
                }.getOrNull(),
            )
        }.getOrNull()
        val channelName = header?.name.orEmpty()
        val allUploads = runCatching {
            val uploadsPlaylistId = "UU" + channelId.drop(2)
            val playlist = ServiceList.YouTube.getPlaylistExtractor(
                "https://www.youtube.com/playlist?list=$uploadsPlaylistId"
            )
            playlist.fetchPage()
            playlist.initialPage.items.filterIsInstance<StreamInfoItem>()
        }.getOrDefault(emptyList())
        val videos = allUploads.filterNot { it.url?.contains("/shorts/") == true }.mapNotNull { stream ->
            val videoId = extractVideoId(stream.url ?: return@mapNotNull null) ?: return@mapNotNull null
            Track(
                id = videoId,
                title = stream.name ?: "Untitled",
                artist = channelName,
                durationSeconds = stream.duration,
                thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                source = Source.YOUTUBE,
            )
        }
        val shorts = allUploads.filter { it.url?.contains("/shorts/") == true }.mapNotNull { stream ->
            val videoId = extractVideoId(stream.url ?: return@mapNotNull null) ?: return@mapNotNull null
            Track(
                id = videoId,
                title = stream.name ?: "Untitled",
                artist = channelName,
                durationSeconds = stream.duration,
                thumbnailUrl = stream.thumbnails.lastOrNull()?.url,
                source = Source.YOUTUBE,
            )
        }
        if (videos.isEmpty() && shorts.isEmpty()) return@withContext null
        val playlists = if (channelName.isBlank()) emptyList() else runCatching {
            searchPlaylists(channelName, loadMore = false, pageSize = 12, searchId = "channel_${channelId}_playlists")
                .filter { it.playlist.owner?.equals(channelName, ignoreCase = true) == true }
                .map { it.playlist }
        }.getOrDefault(emptyList())
        ChannelPage(
            videos = videos,
            shorts = shorts,
            playlists = playlists,
            channelName = channelName.takeIf { it.isNotBlank() },
            avatarUrl = header?.avatar,
            bannerUrl = header?.banner,
            description = header?.description?.takeIf { it.isNotBlank() },
            subscriberCount = header?.subs,
        )
    }

"""
    t = t[:start] + new_browse + t[end:]
    write(p, t)
must("ChannelHeader" in read(p), "YouTubeRepository: browseChannel completo")

# ---------- 4) MusicRepository ----------
p = "data/src/main/kotlin/com/vztechcore/vztune/data/music/MusicRepository.kt"
t = read(p)
if "SearchFilter.SHORTS" not in t:
    must("SearchFilter.VIDEOS -> searchVideos" in t, "MusicRepository: ancora searchVideos dispatch")
    t = t.replace(
        "SearchFilter.VIDEOS -> searchVideos(query, loadMore, state)",
        "SearchFilter.VIDEOS -> searchVideos(query, loadMore, state)\n            SearchFilter.SHORTS -> searchShorts(query, loadMore, state)"
    )
    write(p, t)
must("SearchFilter.SHORTS" in read(p), "MusicRepository: dispatch SHORTS")
t = read(p)
if "private suspend fun searchShorts" not in t:
    must("private suspend fun searchSongs" in t, "MusicRepository: ancora searchSongs")
    code = """    private suspend fun searchShorts(query: String, loadMore: Boolean, state: SearchState): SearchResult {
        val shortPage = youTubeRepository.searchShorts(query, loadMore, 30, searchId = "$query|SHORTS")
        state.ytHasMore = shortPage.hasMore
        val newHits = shortPage.items.filter { state.seenKeys.add(it.stableKey) }
        return SearchResult.Success(newHits, state.ytHasMore)
    }

"""
    t = t.replace("    private suspend fun searchSongs", code + "    private suspend fun searchSongs")
    write(p, t)
must("private suspend fun searchShorts" in read(p), "MusicRepository: searchShorts")
t = read(p)
if "getChannelForPlaylist" not in t:
    must("import com.vztechcore.vztune.data.youtube.YouTubeRepository" in t, "MusicRepository: import YouTubeRepository")
    t = t.replace(
        "import com.vztechcore.vztune.data.youtube.YouTubeRepository",
        "import com.vztechcore.vztune.data.youtube.YouTubeChannelInfo\nimport com.vztechcore.vztune.data.youtube.YouTubeRepository"
    )
    code2 = """    suspend fun getChannelForVideo(videoId: String): YouTubeChannelInfo? =
        runCatching { youTubeRepository.getChannelForVideo(videoId) }.getOrNull()

    suspend fun getChannelForPlaylist(playlistId: String): YouTubeChannelInfo? =
        runCatching { youTubeRepository.getChannelForPlaylist(playlistId) }.getOrNull()

"""
    if "    /** Extração de áudio" in t:
        t = t.replace("    /** Extração de áudio", code2 + "    /** Extração de áudio")
    else:
        t = t.replace("    suspend fun getStreamUrl", code2 + "    suspend fun getStreamUrl")
    write(p, t)
must("getChannelForPlaylist" in read(p), "MusicRepository: getChannelForPlaylist")

# ---------- 5) SearchViewModel ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt"
t = read(p)
if "SearchHit.ShortHit -> playRadio" not in t:
    must("is SearchHit.VideoHit -> playRadio" in t, "SearchViewModel: ancora onHitClick VideoHit")
    t = t.replace(
        "is SearchHit.VideoHit -> playRadio(hit.track)",
        "is SearchHit.VideoHit -> playRadio(hit.track)\n            is SearchHit.ShortHit -> playRadio(hit.track)"
    )
    write(p, t)
must("SearchHit.ShortHit -> playRadio" in read(p), "SearchViewModel: ShortHit toca")

# ---------- 6) SearchResultCards ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/components/SearchResultCards.kt"
t = read(p)
if "LazyVerticalGrid" not in t:
    must("import androidx.compose.foundation.lazy.LazyColumn" in t, "SearchResultCards: ancora imports LazyColumn")
    t = t.replace(
        "import androidx.compose.foundation.lazy.LazyColumn",
        "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.grid.GridCells\nimport androidx.compose.foundation.lazy.grid.GridItemSpan\nimport androidx.compose.foundation.lazy.grid.LazyVerticalGrid\nimport androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed\nimport androidx.compose.foundation.lazy.grid.rememberLazyGridState"
    )
    if "import coil.compose.AsyncImage" not in t:
        t = t.replace(
            "import com.vztechcore.vztune.core.domain.music.Track",
            "import coil.compose.AsyncImage\nimport com.vztechcore.vztune.core.domain.music.Track"
        )
    if "import androidx.compose.ui.layout.ContentScale" not in t:
        t = t.replace(
            "import androidx.compose.ui.graphics.Color",
            "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.layout.ContentScale"
        )
    write(p, t)
must("LazyVerticalGrid" in read(p), "SearchResultCards: imports grid")
t = read(p)
if "isShortsMode" not in t:
    must("val isVideoMode" in t, "SearchResultCards: ancora isVideoMode")
    t = t.replace(
        "    val isVideoMode = hits.all { it is SearchHit.VideoHit } && hits.isNotEmpty()",
        "    val isVideoMode = hits.all { it is SearchHit.VideoHit } && hits.isNotEmpty()\n    val isShortsMode = hits.all { it is SearchHit.ShortHit } && hits.isNotEmpty()\n    if (isShortsMode) {\n        ShortsGridContent(\n            hits = hits.filterIsInstance<SearchHit.ShortHit>(),\n            isLoadingMore = isLoadingMore,\n            isRefreshing = isRefreshing,\n            onRefresh = onRefresh,\n            onLoadMore = onLoadMore,\n            onHitClick = onHitClick,\n            onTrackMoreClick = onTrackMoreClick,\n            modifier = modifier,\n        )\n        return\n    }"
    )
    write(p, t)
must("isShortsMode" in read(p), "SearchResultCards: branch ShortsGrid")
t = read(p)
if "is SearchHit.ShortHit -> hit.track" not in t:
    must("is SearchHit.VideoHit -> hit.track" in t, "SearchResultCards: ancora onMoreClick VideoHit")
    t = t.replace(
        "is SearchHit.VideoHit -> hit.track",
        "is SearchHit.VideoHit -> hit.track\n                                is SearchHit.ShortHit -> hit.track"
    )
    write(p, t)
must("is SearchHit.ShortHit -> hit.track" in read(p), "SearchResultCards: onMoreClick ShortHit")
t = read(p)
if t.count("is SearchHit.ShortHit -> {") < 1:
    must("is SearchHit.PlaylistHit -> {" in t, "SearchResultCards: ancora PlaylistHit")
    short_branch = """        is SearchHit.ShortHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            SwipeableRow(onPlayNext = { onPlayNextClick(hit.track) }) {
                VzRowCard(
                    imageUrl = hit.track.thumbnailUrl, shape = ThumbnailShape.SQUARE, title = hit.track.title,
                    subtitle = { TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f)) },
                    durationLabel = formatDuration(hit.track.durationSeconds),
                    isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying,
                    onClick = onClick, onMoreClick = onMoreClick, showMoreButton = true,
                )
            }
        }
"""
    if "VzRowCard(" not in t:
        short_branch = short_branch.replace("VzRowCard(", "RowCard(")
    t = t.replace("        is SearchHit.PlaylistHit -> {", short_branch + "        is SearchHit.PlaylistHit -> {", 1)
    write(p, t)
must("is SearchHit.ShortHit -> {" in read(p), "SearchResultCards: SearchResultCard ShortHit")
t = read(p)
if "fun ShortsGridContent" not in t:
    grid_code = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsGridContent(
    hits: List<SearchHit.ShortHit>,
    isLoadingMore: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onHitClick: (SearchHit) -> Unit,
    onTrackMoreClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 4
        }.collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(bottom = 160.dp, top = 8.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            gridItemsIndexed(hits, key = { _, item -> item.stableKey }) { _, hit ->
                ShortResultCard(
                    hit = hit,
                    onClick = { onHitClick(hit) },
                    onMoreClick = { onTrackMoreClick(hit.track) },
                )
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(96.dp))
                    }
                }
            }
            if (!isLoadingMore) {
                item(span = { GridItemSpan(2) }) { AppCredits() }
            }
        }
    }
}

@Composable
fun ShortResultCard(hit: SearchHit.ShortHit, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))) {
            if (hit.track.thumbnailUrl != null) {
                AsyncImage(
                    model = hit.track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (hit.track.durationSeconds > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        formatDuration(hit.track.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
            if (isCurrent) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Text(
                        if (nowPlaying.isPlaying) "Tocando" else "Pausado",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(hit.track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                Icon(VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
"""
    t = t.rstrip() + "\n" + grid_code
    write(p, t)
must("fun ShortsGridContent" in read(p), "SearchResultCards: ShortsGridContent + ShortResultCard")

# ---------- 7) MediaListViewModel: playlist YouTube -> canal via NewPipe ----------
p = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
t = read(p)
if "youTubeRepository" not in t:
    must("private val ytMusicRepository: YTMusicRepository" in t, "MediaListViewModel: ancora ytMusicRepository")
    t = t.replace(
        "    private val ytMusicRepository: YTMusicRepository,",
        "    private val ytMusicRepository: YTMusicRepository,\n    private val youTubeRepository: com.vztechcore.vztune.data.youtube.YouTubeRepository,"
    )
    write(p, t)
must("youTubeRepository" in read(p), "MediaListViewModel: YouTubeRepository injetado")
t = read(p)
if "getChannelForPlaylist(route.id)" not in t:
    must("loadPlaylistOwnerAvatar(resolvedOwner)" in t, "MediaListViewModel: ancora loadPlaylistOwnerAvatar")
    old_block = """                    if (playlist.ownerChannelId.isNullOrBlank() &&
                        playlist.ownerAvatarUrl.isNullOrBlank() &&
                        !resolvedOwner.isNullOrBlank() && !isYouTubePlaylist
                    ) {
                        loadPlaylistOwnerAvatar(resolvedOwner)
                    }"""
    if old_block in t:
        t = t.replace(old_block, old_block + """

                    // Playlist do YouTube sem UC no InnerTube: resolve via NewPipe
                    if (isYouTubePlaylist && _uiState.value.artistId.isNullOrBlank()) {
                        val channel = runCatching { youTubeRepository.getChannelForPlaylist(route.id) }.getOrNull()
                        if (channel != null) {
                            _uiState.update {
                                it.copy(
                                    artistId = channel.channelId,
                                    ownerAvatarUrl = channel.avatarUrl ?: it.ownerAvatarUrl,
                                )
                            }
                        }
                    }""")
        write(p, t)
    else:
        print("AVISO MediaListViewModel: bloco com formato diferente - pulando canal playlist")
must("getChannelForPlaylist(route.id)" in read(p) or True, "MediaListViewModel: canal playlist via NewPipe (ou aviso)")

print("\nFIX4 aplicado. Roda o build.")
PY
