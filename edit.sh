#!/bin/bash
# VzTune FIX12 - Abas UNIFICADAS Artista + Canal (mesma pagina, mesmo header)
# Abas: Inicio (Tudo) | Musicas | Albuns & Singles | Playlists | Videos | Shorts
# - Nao separa mais canal de artista: decide pelo conteudo que a conta tem.
# - Header volta a ter Seguir + Aleatorio para TODO MUNDO.
# - Barrinha colada no header, antes de qualquer lista.
# Rode na raiz: bash VzTune-FIX12-ABAS-UNIFICADAS.sh
set -e
python3 - << 'PY'
import pathlib

# ---------- 1) Header: Seguir + Aleatorio para todo mundo (reverte FIX11) ----------
for rel in [
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistHeader.kt",
    "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/components/ArtistHeader.kt",
]:
    p = pathlib.Path(rel)
    if not p.exists():
        continue
    t = p.read_text(encoding="utf-8")
    t = t.replace("if (hasPlayableItems && !state.isYouTubeChannel) {", "if (hasPlayableItems) {")
    p.write_text(t, encoding="utf-8")
    print("OK header", rel)

# ---------- 2) ArtistScreen: abas unificadas ----------
p2 = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt")
t2 = p2.read_text(encoding="utf-8")

new_else = """                else -> {
                    // ---------- Abas UNIFICADAS: mesma para Artista e Canal ----------
                    // Nao separa mais por isYouTubeChannel. A pagina e uma so, o que muda e o conteudo que a conta tem.
                    // Ordem: Inicio | Musicas | Albuns & Singles | Playlists | Videos | Shorts
                    // Aba so aparece se tiver conteudo, menos Inicio que sempre aparece.
                    val hasAlbumsSingles = state.albums.isNotEmpty() || state.singles.isNotEmpty()
                    val allPlaylists = (state.channelPlaylists + state.featured).distinctBy { it.id }
                    val tabs = listOf(
                        "home" to "Início",
                        "songs" to "Músicas",
                        "albums" to "Álbuns & Singles",
                        "playlists" to "Playlists",
                        "videos" to "Vídeos",
                        "shorts" to "Shorts",
                    ).filter { (key, _) ->
                        when (key) {
                            "home" -> true
                            "songs" -> state.topSongs.isNotEmpty()
                            "albums" -> hasAlbumsSingles
                            "playlists" -> allPlaylists.isNotEmpty()
                            "videos" -> state.videos.isNotEmpty()
                            "shorts" -> state.shorts.isNotEmpty()
                            else -> true
                        }
                    }
                    val safeTabIndex = selectedChannelTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
                    val selectedKey = tabs.getOrNull(safeTabIndex)?.first ?: "home"

                    // Barrinha colada no header, antes de qualquer lista
                    item(key = "unified_tabs") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(tabs, key = { it.first }) { (key, label) ->
                                val sel = key == selectedKey
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.White.copy(alpha = 0.08f)).clickable { selectedChannelTab = tabs.indexOfFirst { it.first == key } }.padding(horizontal = 18.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(text = label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item(key = "unified_tabs_space") { Spacer(Modifier.height(16.dp)) }

                    when (selectedKey) {
                        "songs" -> {
                            item(key = "songs_tab_header") {
                                SectionHeader(title = "Músicas", onActionClick = seeAll(ArtistListKind.SONGS, state.songsMore))
                            }
                            itemsIndexed(state.topSongs, key = { _, t -> "song_${t.id}" }) { index, track ->
                                TrackListRow(track = track, number = index + 1, onClick = { onSongClick(track) }, onMoreClick = { onTrackInfoClick(track) })
                            }
                            item(key = "songs_tab_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        "albums" -> {
                            rowSection(
                                key = "albums_tab", title = "Álbuns", items = state.albums,
                                onMoreClick = seeAll(ArtistListKind.ALBUMS, state.albumsMore),
                                itemKey = { it.id },
                            ) { album ->
                                CoverCard(title = album.title, subtitle = "Álbum", imageUrl = album.thumbnailUrl, onClick = { onAlbumClick(album) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "singles_tab", title = "Singles", items = state.singles,
                                onMoreClick = seeAll(ArtistListKind.SINGLES, state.singlesMore),
                                itemKey = { it.id },
                            ) { single ->
                                CoverCard(title = single.title, subtitle = "Single", imageUrl = single.thumbnailUrl, onClick = { onAlbumClick(single) }, modifier = Modifier.width(CoverWidth))
                            }
                        }
                        "playlists" -> {
                            itemsIndexed(allPlaylists, key = { _, pl -> "pl_${pl.id}" }) { _, playlist ->
                                ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist })
                            }
                            item(key = "playlists_tab_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        "videos" -> {
                            itemsIndexed(state.videos, key = { _, v -> "cv_${v.id}" }) { _, video ->
                                ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) })
                            }
                            item(key = "videos_tab_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        "shorts" -> {
                            if (state.shorts.isNotEmpty()) {
                                item(key = "shorts_tab") { ShortsGridRows(tracks = state.shorts, onClick = { onShortClick(it) }, onMoreClick = { shortMenuTrack = it }) }
                            }
                            item(key = "shorts_tab_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        else -> {
                            // ---------- Início (Tudo): igual era antes, sem separar canal/artista ----------
                            if (state.topSongs.isNotEmpty()) {
                                item(key = "songs_header") {
                                    SectionHeader(title = "Músicas populares", onActionClick = seeAll(ArtistListKind.SONGS, state.songsMore))
                                }
                                itemsIndexed(state.topSongs.take(5), key = { _, t -> "song_${t.id}" }) { index, track ->
                                    TrackListRow(track = track, number = index + 1, onClick = { onSongClick(track) }, onMoreClick = { onTrackInfoClick(track) })
                                }
                                item(key = "songs_space") { Spacer(Modifier.height(SectionGap)) }
                            }
                            rowSection(
                                key = "albums", title = "Álbuns", items = state.albums,
                                onMoreClick = seeAll(ArtistListKind.ALBUMS, state.albumsMore),
                                itemKey = { it.id },
                            ) { album ->
                                CoverCard(title = album.title, subtitle = "Álbum", imageUrl = album.thumbnailUrl, onClick = { onAlbumClick(album) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "singles", title = "Singles", items = state.singles,
                                onMoreClick = seeAll(ArtistListKind.SINGLES, state.singlesMore),
                                itemKey = { it.id },
                            ) { single ->
                                CoverCard(title = single.title, subtitle = "Single", imageUrl = single.thumbnailUrl, onClick = { onAlbumClick(single) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "videos", title = "Vídeos", items = state.videos,
                                onMoreClick = seeAll(ArtistListKind.VIDEOS, state.videosMore),
                                itemKey = { it.id },
                            ) { video ->
                                val playback = nowPlaying.playbackOf(video)
                                MediaListVideoCard(track = video, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }, modifier = Modifier.width(VideoWidth), horizontalPadding = 0.dp)
                            }
                            rowSection(
                                key = "shorts", title = "Shorts", items = state.shorts,
                                onMoreClick = null,
                                itemKey = { it.id },
                            ) { short ->
                                val playback = nowPlaying.playbackOf(short)
                                MediaListVideoCard(track = short, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(short) }, onMoreClick = { onTrackInfoClick(short) }, modifier = Modifier.width(160.dp), horizontalPadding = 0.dp)
                            }
                            rowSection(
                                key = "channelPlaylists", title = "Playlists", items = state.channelPlaylists,
                                onMoreClick = null,
                                itemKey = { it.id },
                            ) { playlist ->
                                CoverCard(title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "featured", title = "Destacado", items = state.featured,
                                onMoreClick = seeAll(ArtistListKind.FEATURED, state.featuredMore),
                                itemKey = { it.id },
                            ) { playlist ->
                                CoverCard(title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "related", title = "Artistas relacionados", items = state.relatedArtists,
                                onMoreClick = null, spacing = 16.dp,
                                itemKey = { it.id },
                            ) { artist ->
                                ArtistCircleCard(name = artist.name, imageUrl = artist.thumbnailUrl, onClick = { onArtistClick(artist) }, size = RelatedArtistSize)
                            }
                            item(key = "description") { ArtistDescription(description = state.description) }
                        }
                    }
                }
            }
"""

start_marker = "                else -> {\n                    // ---------- Popular songs"
start = t2.find(start_marker)
if start == -1:
    start = t2.find("                else -> {")
    print("AVISO: marcador exato nao achado, usando else generico em", start)
    if start == -1:
        raise SystemExit("Nao achei o else da ArtistScreen, abortando sem mexer")
end_marker = '            item(key = "credits")'
end = t2.find(end_marker, start)
if end == -1:
    raise SystemExit("Nao achei o fim (credits), abortando")
t2_new = t2[:start] + new_else + "\n" + t2[end:]
if t2_new.count("{") != t2_new.count("}"):
    print("AVISO braces:", t2_new.count("{"), t2_new.count("}"))
else:
    print("Braces OK:", t2_new.count("{"))
p2.write_text(t2_new, encoding="utf-8")
print("OK ArtistScreen abas unificadas")
print("FIX12 OK")
PY
