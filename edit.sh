#!/bin/bash
# VzTune FIX15-CORRECAO - corrige os 3 pontos que ficaram tortos no FIX15
# Roda na raiz do projeto: bash VzTune-FIX15-CORRECAO.sh
# Corrige, na ordem:
# 1) YTMusicRepository.resolveArtist - faltava playlistId / playlistTitle na assinatura (usava mas nao declarava -> nao compilava)
# 2) YTMusicArtistParser.extractArtistsFromShelf - ligados ouvintes mensais nos relacionados
# 3) ArtistScreen - Playlists + Relacionados estavam DENTRO do item de Videos (item dentro de item) -> tira de la e deixa como secoes proprias, na ordem Destacado -> Videos -> Playlists -> Relacionados
# 4) ArtistScreen - share na barra colapsada + ArtistViewModel - channelPlaylists do artista nao zerado
set -e
python3 - << 'PY'
import pathlib, sys, re

def read(p):
    return pathlib.Path(p).read_text(encoding="utf-8")
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
    print("OK", p)
def edit(p, fn):
    t = read(p)
    nt = fn(t)
    if nt != t:
        write(p, nt)
    else:
        print("SEM MUDANCA", p)
    return nt
def must(cond, msg):
    if not cond:
        print(f"ERRO {msg}"); sys.exit(1)
    print(f"OK {msg}")

# ============ 1) YTMusicRepository - assinatura resolveArtist ============
def fix_repo(t):
    # Assinatura real no arquivo: "    suspend fun resolveArtist(\n        artistName: String,\n        trackTitle: String? = null,\n        albumTitle: String? = null,\n    ): Artist?"
    old = "    suspend fun resolveArtist(\n        artistName: String,\n        trackTitle: String? = null,\n        albumTitle: String? = null,\n    ): Artist?"
    new = "    suspend fun resolveArtist(\n        artistName: String,\n        trackTitle: String? = null,\n        albumTitle: String? = null,\n        playlistId: String? = null,\n        playlistTitle: String? = null,\n    ): Artist?"
    if old in t:
        t = t.replace(old, new)
    else:
        # Fallback por regex: acha a assinatura mesmo que a indentacao mude
        pat = re.compile(r"(suspend\s+fun\s+resolveArtist\(\s*artistName:\s*String,\s*trackTitle:\s*String\?\s*=\s*null,\s*albumTitle:\s*String\?\s*=\s*null,)\s*(\):\s*Artist\?)")
        t2, n = pat.subn(lambda m: m.group(1) + "\n        playlistId: String? = null,\n        playlistTitle: String? = null,\n    " + m.group(2), t, count=1)
        if n == 0:
            print("AVISO: assinatura resolveArtist nao achada, confere na mao")
        else:
            t = t2
    return t
edit("data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt", fix_repo)

# ============ 2) YTMusicArtistParser - ouvintes nos relacionados ============
def fix_parser(t):
    # formatRelatedListeners ja existe (criado no FIX15). So falta ligar no extractArtistsFromShelf.
    # Bloco real:
    #   out.add(
    #     Artist(
    #       id = browseId,
    #       name = name,
    #       thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnailRenderer")),
    #       source = Source.YOUTUBE_MUSIC
    #     )
    #   )
    # O subtitle do musicTwoRowItemRenderer vem com "1,55 mi ouvintes mensais" / "1,55 mi de inscritos" / "Artista"
    old = 'thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnailRenderer")),\n                        source = Source.YOUTUBE_MUSIC\n                    )'
    new = 'thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(renderer.optJSONObject("thumbnailRenderer")),\n                        source = Source.YOUTUBE_MUSIC,\n                        subscribers = formatRelatedListeners(YTMusicTextUtils.runsText(renderer.optJSONObject("subtitle"))),\n                    )'
    if old in t and "subscribers = formatRelatedListeners" not in t.split("fun extractArtistsFromShelf")[1].split("fun extractArtistsFromSearch")[0]:
        t = t.replace(old, new, 1)
    elif "subscribers = formatRelatedListeners" in t:
        print("Parser: subscribers ja ligado, pula")
    else:
        # Fallback: qualquer Artist( dentro de extractArtistsFromShelf ganha subscribers
        head, tail = t.split("fun extractArtistsFromShelf", 1)
        mid, rest = tail.split("fun extractArtistsFromSearch", 1)
        if "subscribers = formatRelatedListeners" not in mid:
            mid = mid.replace(
                "source = Source.YOUTUBE_MUSIC",
                "source = Source.YOUTUBE_MUSIC,\n                        subscribers = formatRelatedListeners(YTMusicTextUtils.runsText(renderer.optJSONObject(\"subtitle\")))",
                1
            )
        t = head + "fun extractArtistsFromShelf" + mid + "fun extractArtistsFromSearch" + rest
    return t
edit("data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicArtistParser.kt", fix_parser)

# ============ 3) ArtistViewModel - artista tambem mostra Playlists do canal ============
def fix_vm(t):
    # FIX14 zerava: "shorts = emptyList(), channelPlaylists = emptyList(), bannerUrl = null, isYouTubeChannel = false,"
    # No ramo hasMusic, channel ja foi buscado (browseChannel) - se for o mesmo UC, as playlists sao do proprio artista.
    # Nao mistura os mundos: topSongs/albuns/etc continuam so InnerTube, so a fileira Playlists ganha as do canal.
    old = "shorts = emptyList(), channelPlaylists = emptyList(), bannerUrl = null, isYouTubeChannel = false,"
    new = "shorts = emptyList(), channelPlaylists = channel?.playlists ?: emptyList(), bannerUrl = null, isYouTubeChannel = false,"
    if old in t:
        t = t.replace(old, new, 1)
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt", fix_vm)

# ============ 4) ArtistScreen - tira Playlists/Relacionados de dentro de Videos + share na colapsada ============
def fix_screen(t):
    # --- 4a) Reescreve o trecho quebrado inteiro: do "// 3) Playlists" ate o "item(description)" do ramo artista ---
    start_marker = '// 3) Playlists: fileira + > (artista usa featured do YT Music)'
    start = t.find(start_marker)
    must(start != -1, "ArtistScreen: marcador // 3) Playlists achado")
    # O description do ramo artista e o primeiro 'item(key = "description")' depois do start
    end = t.find('item(key = "description")', start)
    must(end != -1, "ArtistScreen: item description achado")

    # Bloco correto, na ordem: Destacado -> Videos -> Playlists -> Relacionados
    # Indentacao 24 espacos (dentro do else do LazyColumn), igual o original.
    correct = """// 3) Destacado: fileira + > (featured do YT Music - playlists onde o artista aparece)
                        val artistPlaylists = state.featured
                        if (artistPlaylists.isNotEmpty()) {
                            item(key = "playlists_header") {
                                SectionHeaderArrow(title = "Destacado", onArrowClick = seeAll(ArtistListKind.FEATURED, state.featuredMore))
                            }
                            item(key = "playlists_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(artistPlaylists, key = { it.id }) { playlist ->
                                        CoverCard(title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth))
                                    }
                                }
                            }
                            item(key = "playlists_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 4) Videos: fileira 16:9 + > (so videos do artista, do InnerTube)
                        if (state.videos.isNotEmpty()) {
                            item(key = "videos_header") {
                                SectionHeaderArrow(title = "Vídeos", onArrowClick = seeAll(ArtistListKind.VIDEOS, state.videosMore))
                            }
                            item(key = "videos_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.videos, key = { it.id }) { video ->
                                        val playback = nowPlaying.playbackOf(video)
                                        MediaListVideoCard(track = video, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }, modifier = Modifier.width(VideoWidth), horizontalPadding = 0.dp)
                                    }
                                }
                            }
                            item(key = "videos_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 5) Playlists do canal (YouTube) - separado do Destacado (featured do YT Music)
                        if (state.channelPlaylists.isNotEmpty()) {
                            item(key = "channel_playlists_header") {
                                SectionHeaderArrow(title = "Playlists")
                            }
                            item(key = "channel_playlists_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.channelPlaylists, key = { it.id }) { playlist ->
                                        CoverCard(title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth))
                                    }
                                }
                            }
                            item(key = "channel_playlists_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 6) Artistas relacionados: foto, nome, ouvintes mensais
                        if (state.relatedArtists.isNotEmpty()) {
                            item(key = "related_header") {
                                SectionHeaderArrow(title = "Artistas relacionados")
                            }
                            item(key = "related_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.relatedArtists, key = { it.id }) { artist ->
                                        RelatedArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                                    }
                                }
                            }
                            item(key = "related_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        """
    t = t[:start] + correct + t[end:]

    # --- 4b) Share na barra colapsada ---
    # Chamada real:
    #   MediaListCollapsedHeader(
    #     title = state.name,
    #     thumbnailUrl = state.thumbnailUrl,
    #     onBackClick = onBackClick,
    #     dominantColor = Color.Black,
    #     circularThumbnail = true,
    #   )
    # O onShareClick ja existe como val dentro do ArtistScreen (abre ACTION_SEND). So faltava passar.
    old_collapsed = "                onBackClick = onBackClick,\n                dominantColor = Color.Black,\n                circularThumbnail = true,"
    new_collapsed = "                onBackClick = onBackClick,\n                onShareClick = onShareClick,\n                dominantColor = Color.Black,\n                circularThumbnail = true,"
    if old_collapsed in t:
        t = t.replace(old_collapsed, new_collapsed, 1)
    else:
        print("AVISO: chamada MediaListCollapsedHeader no ArtistScreen nao achada com essa indentacao, confere na mao")

    # --- 4c) Sanidade: nao pode sobrar item() dentro de item() no trecho do artista ---
    # Checa se ainda tem "item(key = \"videos_header\") {" seguido de "if (state.channelPlaylists" dentro dele
    bad = 'item(key = "videos_header")' in t and 'item(key = "channel_playlists_header")' in t.split('item(key = "videos_header")')[1].split('item(key = "videos_row")')[0] if 'item(key = "videos_header")' in t else False
    if bad:
        print("ERRO: Playlists ainda dentro de Videos, aborta")
        sys.exit(1)
    return t
edit("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt", fix_screen)

print("FIX15-CORRECAO OK")
PY
