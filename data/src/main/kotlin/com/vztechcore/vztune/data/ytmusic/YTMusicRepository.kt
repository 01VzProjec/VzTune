package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.innertube.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YTMusicRepository @Inject constructor(
    private val enricher: YTMusicEnricher,
) {

    data class SearchPage(val items: List<SearchHit>, val continuation: String?)

    data class AlbumResult(
        val coverUrl: String?,
        val artist: String?,
        val title: String?,
        val description: String?,
        val views: String?,
        val tracks: List<Track>,
        val artistId: String? = null,
        val year: String? = null,
    )

    data class PlaylistResult(
        val coverUrl: String?,
        val owner: String?,
        val ownerAvatarUrl: String?,
        val title: String?,
        val description: String?,
        val trackCount: Int,
        val year: String?,
        val views: String?,
        val tracks: List<Track>,
        // UC... do canal dono da playlist (vem do header do InnerTube).
        // Sem isso a playlist caía no findChannelOrArtist() por nome e abria artista errado.
        val ownerChannelId: String? = null,
    )

    data class ArtistResult(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
        val subscribers: String?,
        val description: String?,
        val topSongs: List<Track>,
        val albums: List<Album>,
        val singles: List<Album>,
        val relatedArtists: List<Artist>,
        val views: String? = null,
        val songsMore: BrowseRef? = null,
        val albumsMore: BrowseRef? = null,
        val singlesMore: BrowseRef? = null,
        val videos: List<Track> = emptyList(),
        val videosMore: BrowseRef? = null,
        val featured: List<Playlist> = emptyList(),
        val featuredMore: BrowseRef? = null,
    )

    /** One page of an artist "see all" list. Only the list matching the requested kind is filled. */
    data class ArtistListPage(
        val tracks: List<Track> = emptyList(),
        val albums: List<Album> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val continuation: String? = null,
    )

    data class VideoDetails(
        val viewCount: String?,
        val likeCount: String?,
        val description: String?,
    )

    private data class ResolvedAlbumRef(
        val browseId: String,
        val title: String,
    )

    private data class PlaylistHeaderExtras(
        val ownerAvatarUrl: String?,
        val year: String?,
        val channelId: String?,
    )

    // ================= SEARCH =================

    suspend fun search(
        query: String,
        filter: SearchFilter,
        continuation: String?,
    ): SearchPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.search(query, filter.params)
            else InnerTube.searchContinuation(continuation)
        }.getOrNull() ?: return@withContext SearchPage(emptyList(), null)

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val next = InnerTube.findContinuationToken(json)
        val hits = parsed.mapNotNull { YTMusicSearchParser.toHit(it, filter) }
        val enriched = enricher.enrichHits(hits)
        SearchPage(enriched, next)
    }

    /**
     * Resolves the official YouTube Music [Album] (or Single/EP) that contains [track]
     * using the song's exact `MPREb_` browseId from InnerTube instead of guessing.
     */
    suspend fun findAlbumForTrack(track: Track): Album? = withContext(Dispatchers.IO) {
        val primaryArtist = track.artist
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore("feat.")
            .substringBefore("ft.")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }

        val albumHint = track.album?.takeIf {
            it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
        }

        // 1. Search the exact song in YT Music to read its official MPREb_ album reference
        val songQuery = listOfNotNull(track.title, primaryArtist).joinToString(" ").trim()
        val exactRef = if (songQuery.isNotBlank()) {
            runCatching {
                val songJson = InnerTube.search(songQuery, SearchFilter.SONGS.params)
                extractAlbumRefFromSongSearch(songJson, track)
            }.getOrNull()
        } else {
            null
        }

        if (exactRef != null) {
            val header = runCatching { YTMusicAlbumParser.parseAlbumHeader(exactRef.browseId) }.getOrNull()
            return@withContext Album(
                id = exactRef.browseId,
                title = header?.title?.takeIf { it.isNotBlank() } ?: exactRef.title,
                artist = header?.artist?.takeIf { it.isNotBlank() } ?: primaryArtist ?: track.artist,
                thumbnailUrl = header?.coverUrl ?: track.thumbnailUrl,
                views = header?.views,
                source = Source.YOUTUBE_MUSIC,
            )
        }

        // 2. Fallback only when track already carried an explicit album name
        val targetTitle = albumHint ?: return@withContext null
        val albumSearchQuery = listOfNotNull(targetTitle, primaryArtist).joinToString(" ").trim()
        if (albumSearchQuery.isBlank()) return@withContext null

        val json = runCatching { InnerTube.search(albumSearchQuery, SearchFilter.ALBUMS.params) }.getOrNull()
            ?: return@withContext null

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val albums = parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.ALBUMS) }
            .filterIsInstance<SearchHit.AlbumHit>()
            .map { it.album }

        albums.firstOrNull { album ->
            val titleMatches = album.title.equals(targetTitle, ignoreCase = true) ||
                album.title.contains(targetTitle, ignoreCase = true)
            val artistMatches = primaryArtist == null ||
                album.artist.contains(primaryArtist, ignoreCase = true) ||
                primaryArtist.contains(album.artist, ignoreCase = true)
            titleMatches && artistMatches
        }
    }

    /**
     * Fills missing views / owner on playlist hits that did not come from YouTube Music
     * (e.g. NewPipe results). Cover and mosaic are left untouched.
     */
    suspend fun fillPlaylistStats(hits: List<SearchHit.PlaylistHit>): List<SearchHit.PlaylistHit> =
        enricher.fillPlaylistStats(hits)

    // ================= SUGGESTIONS =================

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()

            coroutineScope {
                val queriesDeferred = async {
                    runCatching {
                        val json = InnerTube.searchSuggestions(query)
                        YTMusicSearchParser.parseSuggestions(json)
                    }.getOrDefault(emptyList())
                }

                val songsDeferred = async {
                    runCatching {
                        val limit = when {
                            query.length <= 2 -> 3
                            query.length == 3 -> 4
                            else -> 5
                        }

                        val json = InnerTube.search(query, SearchFilter.SONGS.params)
                        val parsed = YTMusicSearchParser.parseSearchResults(json)
                        parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.SONGS) }
                            .filterIsInstance<SearchHit.SongHit>()
                            .take(limit)
                            .map { it.track }
                    }.getOrDefault(emptyList())
                }

                SearchSuggestions(
                    songs = songsDeferred.await(),
                    queries = queriesDeferred.await()
                )
            }
        }

    // ================= BROWSE =================

    suspend fun browseAlbum(albumId: String): AlbumResult = withContext(Dispatchers.IO) {
        // Normalize OLAK5uy_ playlist-backed album IDs so InnerTube browse always resolves them
        val browseId = if (albumId.startsWith("OLAK5uy_")) "VL$albumId" else albumId

        val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
            ?: return@withContext AlbumResult(null, null, null, null, null, emptyList())

        val headerInfo = YTMusicAlbumParser.parseFromJson(json)
        val cover = headerInfo?.coverUrl
        val title = headerInfo?.title
        val artist = headerInfo?.artist
        val views = headerInfo?.views
        val desc = extractDescription(json)
        val (artistId, year) = extractAlbumHeaderExtras(json)

        // Check both musicShelfRenderer and musicPlaylistShelfRenderer used by two-column album layouts
        val primaryShelf = YTMusicTextUtils.findFirst(json, "musicShelfRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicPlaylistShelfRenderer")

        var rawTracks = (primaryShelf?.let { collectMusicItems(it) } ?: emptyList())
            .ifEmpty { collectMusicItems(json) }
            .distinctBy { it.id }

        // Fallback: some MPREb_ pages only expose the album's OLAK5uy_ playlistId in the Play button
        if (rawTracks.isEmpty()) {
            val fallbackPlaylistId = findAlbumAudioPlaylistId(json)
            if (!fallbackPlaylistId.isNullOrBlank() && fallbackPlaylistId != browseId) {
                val playlistFallback = runCatching { browsePlaylist(fallbackPlaylistId) }.getOrNull()
                if (playlistFallback != null && playlistFallback.tracks.isNotEmpty()) {
                    rawTracks = playlistFallback.tracks
                }
            }
        }

        val normalizedTracks = rawTracks.map { item ->
            val resolvedArtist = if (item.artist.isBlank() || item.artist.equals("Desconhecido", ignoreCase = true)) {
                artist ?: item.artist
            } else {
                item.artist
            }
            val resolvedAlbum = item.album?.takeIf { it.isNotBlank() } ?: title
            item.copy(
                artist = resolvedArtist,
                album = resolvedAlbum,
                thumbnailUrl = item.thumbnailUrl ?: cover,
                source = Source.YOUTUBE_MUSIC,
            )
        }

        val finalTracks = enricher.enrichTracks(normalizedTracks, artist)

        AlbumResult(
            coverUrl = cover,
            artist = artist,
            title = title,
            description = desc,
            views = views,
            tracks = finalTracks,
            artistId = artistId,
            year = year,
        )
    }

    /**
     * Fetches the artist profile and other albums/singles by the same artist,
     * excluding the currently opened album.
     */
    suspend fun getMoreAlbumsByArtist(
        artistName: String?,
        artistId: String?,
        excludeAlbumId: String,
        excludeAlbumTitle: String?,
    ): Pair<Artist?, List<Album>> = withContext(Dispatchers.IO) {
        val resolvedId = artistId?.takeIf { it.startsWith("UC") }
            ?: artistName?.takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
                ?.let { name ->
                    val primary = name.substringBefore(",").substringBefore("&").trim()
                    // Verifica que o artista tem esse álbum no catálogo, senão pega o homônimo errado
                    runCatching {
                        resolveArtist(primary, albumTitle = excludeAlbumTitle)?.id
                    }.getOrNull()
                }
            ?: return@withContext (null to emptyList())

        val artistDetail = runCatching { browseArtist(resolvedId) }.getOrNull()
            ?: return@withContext (null to emptyList())

        val artist = Artist(
            id = artistDetail.browseId,
            name = artistDetail.name,
            thumbnailUrl = artistDetail.thumbnailUrl,
            source = Source.YOUTUBE_MUSIC,
        )

        val cleanExcludeId = excludeAlbumId.removePrefix("VL")
        val otherReleases = (artistDetail.albums + artistDetail.singles)
            .distinctBy { it.id }
            .filter { candidate ->
                val sameId = candidate.id == excludeAlbumId || candidate.id.removePrefix("VL") == cleanExcludeId
                val sameTitle = !excludeAlbumTitle.isNullOrBlank() &&
                    candidate.title.equals(excludeAlbumTitle, ignoreCase = true)
                !sameId && !sameTitle
            }
            .take(15)

        artist to otherReleases
    }

    suspend fun browsePlaylist(playlistId: String): PlaylistResult = withContext(Dispatchers.IO) {
        val cleanId = YTMusicTextUtils.normalizePlaylistBrowseId(playlistId)
        val json = runCatching { InnerTube.browse(cleanId) }.getOrNull()
            ?: return@withContext PlaylistResult(null, null, null, null, null, 0, null, null, emptyList(), null)

        val parsed = YTMusicPlaylistParser.parsePlaylist(json)
        val headerExtras = extractPlaylistHeaderExtras(json)
        val finalTracks = enricher.enrichTracks(parsed.tracks, null)

        val resolvedAvatar = parsed.ownerAvatarUrl?.takeIf { it.isNotBlank() }
            ?: headerExtras.ownerAvatarUrl
            ?: headerExtras.channelId?.let { cid ->
                runCatching { browseArtist(cid).thumbnailUrl }.getOrNull()
            }

        val resolvedYear = parsed.year?.takeIf { it.isNotBlank() } ?: headerExtras.year

        PlaylistResult(
            coverUrl = parsed.coverUrl,
            owner = parsed.owner,
            ownerAvatarUrl = resolvedAvatar,
            title = parsed.title,
            description = parsed.description,
            trackCount = parsed.trackCount.takeIf { it > 0 } ?: finalTracks.size,
            year = resolvedYear,
            views = parsed.views,
            tracks = finalTracks,
            ownerChannelId = headerExtras.channelId,
        )
    }

    // ================= ARTISTS =================

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Artist>()
        runCatching {
            val json = InnerTube.search(query, SearchFilter.ARTISTS.params)
            YTMusicArtistParser.extractArtistsFromSearch(json, out)
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.take(30)
    }

    /**
     * Resolves an artist or YouTube channel profile (including avatar thumbnail) by name.
     * Sem contexto de faixa/álbum: usa só o nome mais parecido (Levenshtein, SimpMusic).
     * Com contexto, usa resolveArtist() com verificação de catálogo.
     */
    suspend fun findChannelOrArtist(name: String): Artist? =
        resolveArtist(name, trackTitle = null, albumTitle = null)

    /**
     * Resolve o artista CERTO pra um nome clicado dentro de álbum/faixa.
     *
     * Antigo: searchArtists(nome).firstOrNull() -> pegava o primeiro, que com nome
     * repetido era quase sempre o artista errado ou o "- Topic".
     *
     * Novo (SimpMusic + verificação que você pediu):
     * 1. Busca candidatos (até 30), rankeia por Levenshtein no nome normalizado,
     *    desempata Topic (oficial sem " - Topic" ganha).
     * 2. Se tem nome exato não-Topic, devolve ele direto sem gastar browse.
     * 3. Se tem faixa/álbum de contexto, abre a página dos 3 mais parecidos e só
     *    aceita quem tem aquela faixa / aquele álbum no catálogo. Quem não tem, não é ele.
     * 4. Sem verificação ou ninguém verificado, cai pro mais parecido.
     */
    suspend fun resolveArtist(
        artistName: String,
        trackTitle: String? = null,
        albumTitle: String? = null,
        playlistId: String? = null,
        playlistTitle: String? = null,
    ): Artist? = withContext(Dispatchers.IO) {
        val cleanQuery = artistName.trim()
        if (cleanQuery.isBlank() || cleanQuery.equals("YouTube Music", ignoreCase = true)) {
            return@withContext null
        }

        // 1. Candidatos: filtro ARTISTS primeiro, igual antes
        var candidates = searchArtists(cleanQuery)

        // 2. Fallback pra busca geral (canal/curador UC...) quando ARTISTS não achou nada
        if (candidates.isEmpty()) {
            val fallbackOut = mutableListOf<Artist>()
            runCatching {
                val json = InnerTube.search(cleanQuery, null)
                YTMusicArtistParser.extractArtistsFromSearch(json, fallbackOut)
            }
            candidates = fallbackOut.distinctBy { it.id }
                .filter { !it.thumbnailUrl.isNullOrBlank() }
        }
        if (candidates.isEmpty()) return@withContext null

        // Rankeia: menor Levenshtein primeiro, não-Topic antes de Topic, ordem original desempata
        val ranked = candidates.mapIndexed { idx, artist ->
            Triple(artist, ArtistResolver.distance(cleanQuery, artist.name), idx)
        }.sortedWith(
            compareBy<Triple<Artist, Int, Int>> { it.second }
                .thenBy { if (ArtistResolver.isTopicName(it.first.name)) 1 else 0 }
                .thenBy { it.third }
        )

        // FIX15 th vs TH: 'th' e 'TH' normalizam igual (distancia 0) mas sao contas diferentes.
        // Se um candidato tem o nome EXATAMENTE igual (case-sensitive), ele ganha - sem gastar browse quando nao tem contexto.
        val caseSensitiveExact = ranked.firstOrNull { it.first.name == cleanQuery && !ArtistResolver.isTopicName(it.first.name) }
        if (caseSensitiveExact != null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank() && playlistId.isNullOrBlank() && playlistTitle.isNullOrBlank()) {
            return@withContext caseSensitiveExact.first
        }
        // Exato não-Topic: é ele, sem gastar browse
        val exactNonTopic = ranked.firstOrNull {
            it.second == 0 && !ArtistResolver.isTopicName(it.first.name)
        }
        if (exactNonTopic != null && caseSensitiveExact == null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank() && playlistId.isNullOrBlank() && playlistTitle.isNullOrBlank()) {
            return@withContext exactNonTopic.first
        }
        // Exato (mesmo que seja Topic) sem contexto: também é ele, sem gastar browse
        val exactAny = ranked.firstOrNull { it.second == 0 }
        if (exactAny != null && caseSensitiveExact == null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank() && playlistId.isNullOrBlank() && playlistTitle.isNullOrBlank()) {
            return@withContext exactAny.first
        }

        // 3. Verificação de catálogo: só pros 3 mais parecidos, pra não estourar rede
        val hasContext = !trackTitle.isNullOrBlank() || !albumTitle.isNullOrBlank() || !playlistId.isNullOrBlank() || !playlistTitle.isNullOrBlank()
        if (hasContext) {
            for ((candidate, _, _) in ranked.take(3)) {
                val detail = runCatching { browseArtist(candidate.id) }.getOrNull() ?: continue
                val contains = ArtistResolver.catalogContains(
                    topSongTitles = detail.topSongs.map { it.title },
                    videoTitles = detail.videos.map { it.title },
                    albumTitles = detail.albums.map { it.title },
                    singleTitles = detail.singles.map { it.title },
                    trackTitle = trackTitle,
                    albumTitle = albumTitle,
                )
                val playlistHit = ArtistResolver.playlistContains(
                    featured = detail.featured,
                    playlistId = playlistId,
                    playlistTitle = playlistTitle,
                )
                if (playlistHit) {
                    return@withContext Artist(
                        id = candidate.id,
                        name = detail.name.takeUnless { it == "Artista" || it == "Unknown Artist" } ?: candidate.name,
                        thumbnailUrl = detail.thumbnailUrl ?: candidate.thumbnailUrl,
                        source = candidate.source,
                    )
                }
                if (contains) {
                    // Devolve com o nome/foto da página verificada (mais confiável que o da busca)
                    return@withContext Artist(
                        id = candidate.id,
                        name = detail.name.takeUnless { it == "Artista" || it == "Unknown Artist" }
                            ?: candidate.name,
                        thumbnailUrl = detail.thumbnailUrl ?: candidate.thumbnailUrl,
                        source = candidate.source,
                    )
                }
            }
            // Ninguém verificado: se tem exato case-sensitive (th vs TH), ele ganha do mais parecido. Senão cai pro mais parecido abaixo.
            if (caseSensitiveExact != null) return@withContext caseSensitiveExact.first
            // Ninguém verificado: cai pro mais parecido abaixo (não retorna null, senão o clique morre)
        }

        // 4. Mais parecido (SimpMusic: bestMatchingIndex por Levenshtein)
        // FIX7 threshold: se NENHUM candidato tem nome parecido, nao navega.
        // Antes devolvia ranked.first() mesmo com distancia 11 (ex: "Sanary Xenon"
        // -> "360View Lab", que so tem "Sanary" num video "SANARY SUR MER").
        // Agora retorna null e a UI nao abre canal errado.
        // Threshold: max(3, len/3) - erro de digitacao pequeno passa, nome totalmente
        // diferente nao passa.
        val best = ranked.first()
        val maxDist = maxOf(3, cleanQuery.length / 3)
        if (best.second > maxDist) {
            return@withContext null
        }
        return@withContext best.first
    }

    suspend fun featuredArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val seeds = listOf(
            "top hits", "pop", "funk brasileiro", "sertanejo",
            "rock", "trap", "mpb", "lofi",
        ).shuffled().take(3)

        val out = mutableListOf<Artist>()
        for (seed in seeds) {
            runCatching {
                val json = InnerTube.search(seed, SearchFilter.ARTISTS.params)
                YTMusicArtistParser.extractArtistsFromSearch(json, out)
            }
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.shuffled().take(30)
    }

    suspend fun browseArtist(browseId: String): ArtistResult =
        withContext(Dispatchers.IO) {
            val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
                ?: return@withContext ArtistResult(
                    browseId = browseId, name = "Artista",
                    thumbnailUrl = null, subscribers = null, description = null,
                    topSongs = emptyList(), albums = emptyList(), singles = emptyList(),
                    relatedArtists = emptyList()
                )

            val parsed = YTMusicArtistParser.parseArtistPage(browseId, json)

            ArtistResult(
                browseId = parsed.browseId,
                name = parsed.name,
                thumbnailUrl = parsed.thumbnailUrl,
                subscribers = parsed.subscribers,
                description = parsed.description,
                topSongs = parsed.topSongs,
                albums = parsed.albums,
                singles = parsed.singles,
                relatedArtists = parsed.relatedArtists,
                views = parsed.views,
                songsMore = parsed.songsMore,
                albumsMore = parsed.albumsMore,
                singlesMore = parsed.singlesMore,
                videos = parsed.videos,
                videosMore = parsed.videosMore,
                featured = parsed.featured,
                featuredMore = parsed.featuredMore,
            )
        }

    /**
     * Loads one page of an artist "see all" list (songs, videos, albums, singles or featured playlists).
     * Pass [continuation] = null for the first page, then the token returned in the previous page.
     */
    suspend fun browseArtistList(
        kind: ArtistListKind,
        artistName: String,
        ref: BrowseRef,
        continuation: String?,
    ): ArtistListPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.browse(ref.browseId, ref.params)
            else InnerTube.browseContinuation(continuation)
        }.getOrNull() ?: return@withContext ArtistListPage()

        val page = YTMusicArtistParser.parseListPage(kind, artistName, json)
        ArtistListPage(page.tracks, page.albums, page.playlists, page.continuation)
    }

    /**
     * FIX 6 (método do SimpMusic): artista/canal DONO de um vídeo, pelo channelId
     * que o próprio vídeo carrega (player.videoDetails.channelId).
     *
     * NUNCA busca por nome: o channelId do player é a fonte da verdade.
     *
     * Distinção que faltava (era isso que abria artista parecido):
     *  - Se o YT Music tem página de artista pra esse UC... (topSongs/álbuns/
     *    vídeos/relacionados), devolve como YOUTUBE_MUSIC, com nome/foto da página.
     *  - Se NÃO tem página (canal puro de vídeo: podcast, gamer, vlogger...),
     *    devolve como YOUTUBE, com o nome do author do vídeo. A tela de artista
     *    entende source=YOUTUBE como "canal" e carrega os vídeos DELE via NewPipe,
     *    em vez de chutar um artista do YT Music com nome parecido.
     */
    suspend fun getArtistFromVideo(videoId: String): Artist? =
        withContext(Dispatchers.IO) {
            val playerJson = runCatching { InnerTube.player(videoId) }.getOrNull()
                ?: return@withContext null
            val videoDetails = playerJson.optJSONObject("videoDetails")
                ?: return@withContext null
            val channelId = videoDetails.optString("channelId")
                .takeIf { it.isNotBlank() && it != "null" }
            val author = videoDetails.optString("author")
                .takeIf { it.isNotBlank() && it != "null" }
            if (channelId == null || author == null) return@withContext null
            val cleanName = YTMusicTextUtils.cleanArtistName(author)
            if (cleanName.isBlank()) return@withContext null
            // FIX7: "Ir para o canal" NUNCA vira artista do YT Music.
            // O author do video e quem postou. Se o YT Music tiver pagina
            // pra esse UC, ela pode ser de OUTRO artista com nome parecido
            // (ex: musica "Jazzghost" do Moldrin dentro do video do gamer
            // Jazzghost) e era isso que abria o Moldrin. Sempre YOUTUBE.
            return@withContext Artist(
                id = channelId,
                name = cleanName,
                thumbnailUrl = null,
                source = Source.YOUTUBE,
            )
        }

    // ================= QUEUE =================

    suspend fun buildSongQueue(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val query = buildString {
            if (seed.artist.isNotBlank()) append(seed.artist)
            if (seed.title.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(seed.title)
            }
        }.trim()

        if (query.isBlank()) return@withContext listOf(seed)

        val json = runCatching {
            InnerTube.search(query, SearchFilter.SONGS.params)
        }.getOrNull() ?: return@withContext listOf(seed)

        val found = collectMusicItems(json)
            .distinctBy { it.id }
            .filter { it.source == Source.YOUTUBE_MUSIC }

        val normalized = found.map { item ->
            val uploader = item.artist.takeIf {
                it.isNotBlank() && !it.equals("Unknown", true)
            } ?: seed.artist
            item.copy(artist = uploader)
        }

        val list = if (normalized.any { it.id == seed.id }) {
            listOf(seed) + normalized.filter { it.id != seed.id }
        } else {
            listOf(seed) + normalized
        }
        list.take(50)
    }

    // ================= VIDEO DETAILS =================

    suspend fun getVideoDetails(videoId: String): VideoDetails? =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = InnerTube.player(videoId)
                val videoDetails = json.optJSONObject("videoDetails")
                    ?: return@runCatching null

                val viewCountRaw = videoDetails.optString("viewCount")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.toLongOrNull()

                var description = videoDetails.optString("shortDescription")
                    .takeIf { it.isNotBlank() && it != "null" }

                if (description == null) {
                    description = json.optJSONObject("microformat")
                        ?.optJSONObject("playerMicroformatRenderer")
                        ?.optJSONObject("description")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                }

                VideoDetails(
                    viewCount = viewCountRaw?.let { formatNumber(it) },
                    likeCount = null,
                    description = description,
                )
            }.getOrNull()
        }


    /** Creditos que o InnerTube disponibiliza: artista(s), album, ano, views, descricao + compositores/produtores/letra extraidos da descricao. */
    data class TrackCredits(
        val artists: List<String>,
        val albumTitle: String?,
        val albumYear: String?,
        val viewCount: String?,
        val description: String?,
        val composers: List<String>,
        val producers: List<String>,
        val lyricists: List<String>,
    )

    suspend fun getTrackCredits(track: Track): TrackCredits? = withContext(Dispatchers.IO) {
        val details = runCatching { getVideoDetails(track.id) }.getOrNull()
        val album = runCatching { findAlbumForTrack(track) }.getOrNull()
        val albumRes = if (album != null) runCatching { browseAlbum(album.id) }.getOrNull() else null
        val desc = details?.description ?: albumRes?.description
        val artists = track.artist.split(",", "&", "feat.", "ft.", " e ").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        fun parseRole(keywords: List<String>): List<String> {
            if (desc.isNullOrBlank()) return emptyList()
            val out = mutableListOf<String>()
            for (line in desc.lines()) {
                val low = line.lowercase()
                if (keywords.any { low.contains(it) }) {
                    val after = line.substringAfter(":", line).substringAfter("-", line).trim()
                    if (after.isNotBlank() && after != line) {
                        after.split(",", "&", ";").map { it.trim() }.filter { it.isNotBlank() }.forEach { out.add(it) }
                    }
                }
            }
            return out.distinct()
        }
        TrackCredits(
            artists = artists,
            albumTitle = album?.title ?: track.album,
            albumYear = albumRes?.year,
            viewCount = details?.viewCount,
            description = desc,
            composers = parseRole(listOf("compositor", "composer", "escrito por", "written by")),
            producers = parseRole(listOf("produtor", "producer", "produzido por")),
            lyricists = parseRole(listOf("letra", "lyricist", "letrista")),
        )
    }

    // ================= HELPERS =================

    private fun extractPlaylistHeaderExtras(json: JSONObject): PlaylistHeaderExtras {
        val header = YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: return PlaylistHeaderExtras(null, null, null)

        val avatarUrl = YTMusicThumbnailUtils.thumbnailOf(header.optJSONObject("straplineThumbnail"))
            ?: YTMusicThumbnailUtils.thumbnailOf(
                header.optJSONObject("facepile")
                    ?.optJSONObject("avatarStackViewModel")
                    ?.optJSONArray("avatars")
                    ?.optJSONObject(0)
                    ?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")
            )

        var year: String? = null
        var channelId: String? = null

        val runArrays = listOfNotNull(
            header.optJSONObject("straplineTextOne")?.optJSONArray("runs"),
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in runArrays) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                if (channelId == null && browseId != null && browseId.startsWith("UC")) {
                    channelId = browseId
                }
                if (year == null && YTMusicTextUtils.isYear(text)) {
                    year = text
                }
            }
        }

        return PlaylistHeaderExtras(
            ownerAvatarUrl = avatarUrl,
            year = year,
            channelId = channelId,
        )
    }

    private fun extractAlbumHeaderExtras(json: JSONObject): Pair<String?, String?> {
        var artistId: String? = null
        var year: String? = null

        val header = YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: return (null to null)

        val runArrays = listOfNotNull(
            header.optJSONObject("straplineTextOne")?.optJSONArray("runs"),
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in runArrays) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                if (artistId == null && browseId != null && browseId.startsWith("UC")) {
                    artistId = browseId
                }
                if (year == null && YTMusicTextUtils.isYear(text)) {
                    year = text
                }
            }
        }
        return artistId to year
    }

    private fun findAlbumAudioPlaylistId(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val pid = node.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
                if (!pid.isNullOrBlank() && pid != "null" && pid.startsWith("OLAK5uy_")) {
                    return pid
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findAlbumAudioPlaylistId(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findAlbumAudioPlaylistId(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }

    private fun extractAlbumRefFromSongSearch(
        node: Any?,
        targetTrack: Track,
        candidates: MutableList<Pair<Boolean, ResolvedAlbumRef>> = mutableListOf(),
    ): ResolvedAlbumRef? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val rowTitle = columns?.let { YTMusicTextUtils.flexText(it, 0) }.orEmpty()
                    val videoId = extractVideoIdFromRenderer(r, columns)

                    val exactIdMatch = !videoId.isNullOrBlank() && videoId == targetTrack.id
                    val titleMatch = rowTitle.isNotBlank() &&
                        rowTitle.equals(targetTrack.title, ignoreCase = true)

                    if ((exactIdMatch || titleMatch) && columns != null) {
                        val albumRef = findAlbumRunInColumns(columns)
                        if (albumRef != null) {
                            candidates += (exactIdMatch to albumRef)
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    extractAlbumRefFromSongSearch(node.opt(keys.next()), targetTrack, candidates)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractAlbumRefFromSongSearch(node.opt(i), targetTrack, candidates)
                }
            }
        }
        return candidates.firstOrNull { it.first }?.second ?: candidates.firstOrNull()?.second
    }

    private fun findAlbumRunInColumns(columns: JSONArray): ResolvedAlbumRef? {
        for (c in 0 until columns.length()) {
            val runs = columns.optJSONObject(c)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: continue

            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" } ?: continue

                if (browseId.startsWith("MPREb_")) {
                    val albumTitle = run.optString("text").trim()
                    if (albumTitle.isNotBlank()) {
                        return ResolvedAlbumRef(browseId = browseId, title = albumTitle)
                    }
                }
            }
        }
        return null
    }

    private fun extractVideoIdFromRenderer(r: JSONObject, columns: JSONArray?): String? {
        // 1. Standard playlist/search item data
        r.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 2. Top-level navigationEndpoint
        r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 3. First column title run navigationEndpoint (common on album tracklists)
        columns?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 4. Overlay play button endpoint (used on newer YTMusic album rows without playlistItemData)
        r.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        return null
    }

    private fun extractDescription(json: JSONObject): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
            ?: return null
        val descNode = shelf.optJSONObject("description") ?: return null
        val text = YTMusicTextUtils.extractRunsText(descNode)
        return text.takeIf { it.isNotBlank() }
    }

    private fun collectMusicItems(
        node: Any?,
        out: MutableList<Track> = mutableListOf(),
    ): List<Track> {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val title = columns?.let { YTMusicTextUtils.flexText(it, 0) }
                    val videoId = extractVideoIdFromRenderer(r, columns)

                    if (title != null && !videoId.isNullOrBlank()) {
                        val subtitleCol = columns.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val subtitle = YTMusicTextUtils.runsText(subtitleCol)
                        val source = YTMusicSearchParser.detectSource(videoId, r)
                        val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                            ?: subtitle.substringBefore("•").trim().ifBlank { "Desconhecido" }
                        val albumTitle = columns.let { findAlbumRunInColumns(it)?.title }
                        out += Track(
                            id = videoId,
                            title = title,
                            artist = uploader,
                            album = albumTitle,
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnail")),
                            durationSeconds = YTMusicTextUtils
                                .parseDuration(YTMusicTextUtils.fixedText(r)),
                            source = source,
                            isExplicit = YTMusicSearchParser.hasExplicitBadge(r),
                        )
                    }
                }
                node.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                    val videoId = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId")
                    val title = r.optJSONObject("title")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text")
                    if (!videoId.isNullOrBlank() && !title.isNullOrBlank()) {
                        val subtitle = YTMusicTextUtils.runsText(r.optJSONObject("subtitle"))
                        out += Track(
                            id = videoId, title = title,
                            artist = subtitle.substringBefore("•").trim()
                                .ifBlank { "Desconhecido" },
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnailRenderer")),
                            durationSeconds = 0L,
                            source = Source.YOUTUBE,
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) collectMusicItems(node.opt(keys.next()), out)
            }
            is JSONArray -> for (i in 0 until node.length()) {
                collectMusicItems(node.opt(i), out)
            }
        }
        return out
    }

    private fun formatNumber(n: Long): String = when {
        n >= 1_000_000_000L -> String.format("%.1fB", n / 1_000_000_000.0)
        n >= 1_000_000L -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000L -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
