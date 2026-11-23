#!/usr/bin/env python3
"""VzTune - novo sistema de Canal x Artista.

O que este script faz (sem criar nenhum backup / .bak):

1. Video abre o CANAL do YouTube primeiro (YouTube tem preferencia); se nao achar, tenta o
   artista do YT Music. Musica do YT Music continua abrindo so o artista.
2. Telas de Canal e de Artista: o icone de Compartilhar vira 3 pontinhos. O menu sobe de baixo
   pra cima com "Canais encontrados" (tela canal) / "Contas encontradas" (tela artista) e,
   abaixo, "Compartilhar".
3. "Canais encontrados" / "Contas encontradas" (icone Groups) abre outro menu com os perfis do
   OUTRO mundo com o MESMO nome (canal -> artistas do YT Music, artista -> canais do YouTube),
   pra trocar quando o app abriu o errado. A tela atual e substituida (voltar nao passa pela errada).
4. Tela Canal: ao lado da foto agora aparece o NOME do canal (antes sumia: Text com weight dentro
   de item do LazyColumn media 0 de altura) e a quantidade de videos foi removida.
5. Correcoes: a tela do artista decidia canal x artista pela presenca de musica (o canal do
   YouTube podia cair no layout errado); "Buscar mais playlists" e as opcoes de playlist do canal
   nao estavam ligadas no AppNavHost; clique em ArtistHit nao repassava isYouTubeChannel.

Rode na raiz do projeto VzTune:   python edit.py
(ou passe a raiz como primeiro argumento). Todos os trechos sao validados ANTES de gravar:
se algum nao bater, nada e alterado. Pode rodar de novo sem problema (idempotente).
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd().resolve()

APP = "app/src/main/kotlin/com/vztechcore/vztune"
DATA = "data/src/main/kotlin/com/vztechcore/vztune/data"

F_PARSER = f"{DATA}/youtube/YouTubeSearchParser.kt"
F_YT_REPO = f"{DATA}/youtube/YouTubeRepository.kt"
F_MUSIC_REPO = f"{DATA}/music/MusicRepository.kt"
F_STATE = f"{APP}/ui/screens/artist/ArtistUiState.kt"
F_VM = f"{APP}/ui/screens/artist/ArtistViewModel.kt"
F_HEADER = f"{APP}/ui/screens/artist/ArtistHeader.kt"
F_SCREEN = f"{APP}/ui/screens/artist/ArtistScreen.kt"
F_NAV = f"{APP}/ui/navigation/AppNavHost.kt"
F_MENU_VM = f"{APP}/ui/components/sheets/TrackMenuViewModel.kt"
F_SEARCH_VM = f"{APP}/ui/screens/search/SearchViewModel.kt"
F_NEW_SHEET = f"{APP}/ui/components/sheets/ProfileOptionsSheet.kt"


# ----------------------------------------------------------------------------------------
# Operacoes
# ----------------------------------------------------------------------------------------
class Replace:
    """Troca um trecho exato (tem que aparecer 1 unica vez)."""

    def __init__(self, old, new):
        self.old, self.new = old, new

    def apply(self, text):
        old, new = self.old, self.new
        if old in new:
            # Insercao: a ancora continua no texto depois de aplicar, entao so o texto novo prova que ja foi.
            if new in text:
                return text, "ja aplicado"
        elif old not in text:
            if new == "" or new in text:
                return text, "ja aplicado"
            raise ValueError(f"trecho nao encontrado (nem o antigo nem o novo):\n{old[:300]}")
        n = text.count(old)
        if n != 1:
            raise ValueError(f"trecho encontrado {n}x (esperado 1x):\n{old[:300]}")
        return text.replace(old, new, 1), "ok"


class InsertBefore(Replace):
    """Insere codigo antes de uma ancora exata (aparece 1 unica vez)."""

    def __init__(self, anchor, code):
        super().__init__(anchor, code + anchor)


class ReplaceBetween:
    """Troca tudo de [start, end) - start incluido, end preservado."""

    def __init__(self, start, end, new, done_marker):
        self.start, self.end, self.new, self.done = start, end, new, done_marker

    def apply(self, text):
        if self.done in text:
            return text, "ja aplicado"
        if text.count(self.start) != 1:
            raise ValueError(f"inicio encontrado {text.count(self.start)}x:\n{self.start[:300]}")
        i = text.index(self.start)
        j = text.find(self.end, i + len(self.start))
        if j < 0:
            raise ValueError(f"fim nao encontrado:\n{self.end[:300]}")
        return text[:i] + self.new + text[j:], "ok"


# ----------------------------------------------------------------------------------------
# Codigo novo
# ----------------------------------------------------------------------------------------
PARSE_CHANNELS = r'''    /**
     * Canais do YouTube numa busca com filtro de canais.
     * Devolve [Artist] com source = YOUTUBE (o app trata como canal, nunca como artista do YT Music).
     */
    fun parseChannels(json: JSONObject): List<Artist> {
        val out = mutableListOf<Artist>()
        val renderers = mutableListOf<JSONObject>()
        collect(json, "channelRenderer", renderers)
        for (r in renderers) {
            val id = r.optString("channelId").takeIf { it.startsWith("UC") } ?: continue
            val name = textOf(r.optJSONObject("title")) ?: continue
            val thumb = thumbnailOf(r.optJSONObject("thumbnail"))
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            // O YouTube troca os campos: o @handle costuma vir em subscriberCountText e os inscritos em videoCountText.
            val subs = listOf(r.optJSONObject("subscriberCountText"), r.optJSONObject("videoCountText"))
                .mapNotNull { textOf(it) }
                .firstOrNull { it.contains("inscrit", ignoreCase = true) || it.contains("subscriber", ignoreCase = true) }
            out += Artist(id = id, name = name, thumbnailUrl = thumb, source = Source.YOUTUBE, subscribers = subs)
        }
        return out.distinctBy { it.id }
    }

'''

SEARCH_CHANNELS = r'''    /**
     * Busca canais do YouTube pelo nome (menu "Canais encontrados" / "Contas encontradas").
     * InnerTube primeiro (filtro de canais), NewPipe como fallback. Devolve [Artist] com source = YOUTUBE.
     */
    suspend fun searchChannels(query: String, pageSize: Int = 15): List<Artist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val json = InnerTubeYouTube.search(query, "EgIQAg%3D%3D")
            val items = YouTubeSearchParser.parseChannels(json).take(pageSize)
            if (items.isNotEmpty()) return@withContext items
        }.onFailure { Log.w(TAG, "InnerTube searchChannels failed, fallback NewPipe: ${it.message}") }

        if (!NewPipeInitializer.ensureInitialized(downloader)) {
            Log.e(TAG, "searchChannels: NewPipe not initialized")
            return@withContext emptyList()
        }
        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("channels"), null)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<ChannelInfoItem>()
                .mapNotNull { item ->
                    val id = item.url.substringAfter("/channel/", "").substringBefore("?").substringBefore("/")
                    if (!id.startsWith("UC")) return@mapNotNull null
                    Artist(
                        id = id,
                        name = item.name ?: return@mapNotNull null,
                        thumbnailUrl = item.thumbnails.lastOrNull()?.url,
                        source = Source.YOUTUBE,
                    )
                }
                .distinctBy { it.id }
                .take(pageSize)
        }.getOrElse { e ->
            Log.e(TAG, "searchChannels failed for query=$query", e)
            emptyList()
        }
    }

'''

MUSIC_REPO_PROFILES = r'''    // =====================================================================
    //  Perfis: canal do YouTube x artista do YT Music
    // =====================================================================

    /**
     * Perfil que abre quando o usuario clica no artista/canal de uma faixa.
     * Video (Source.YOUTUBE): o canal do YouTube que postou tem PREFERENCIA; sem canal, tenta o artista do YT Music.
     * Musica (Source.YOUTUBE_MUSIC): so artista do YT Music (nunca vira canal).
     */
    suspend fun resolveProfileForTrack(track: Track, artistName: String): Artist? {
        if (track.source == Source.YOUTUBE) {
            val channel = runCatching { youTubeRepository.getChannelForVideo(track.id) }.getOrNull()
            if (channel != null) {
                return Artist(
                    id = channel.channelId,
                    name = channel.channelName.ifBlank { artistName },
                    thumbnailUrl = channel.avatarUrl,
                    source = Source.YOUTUBE,
                )
            }
        }
        return runCatching {
            ytMusicRepository.resolveArtist(
                artistName,
                trackTitle = track.title,
                albumTitle = track.album?.takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    /**
     * Perfis com o MESMO nome no outro mundo, pro usuario trocar quando o app abriu o errado.
     * [wantYouTubeChannels] = true -> canais do YouTube; false -> artistas/contas do YT Music.
     * Como a busca e sempre no outro mundo, o perfil atual nunca aparece na lista.
     */
    suspend fun findSameNameProfiles(name: String, wantYouTubeChannels: Boolean): List<Artist> {
        val clean = name.trim()
        if (clean.isBlank()) return emptyList()
        val candidates = runCatching {
            if (wantYouTubeChannels) youTubeRepository.searchChannels(clean) else ytMusicRepository.searchArtists(clean)
        }.getOrDefault(emptyList())
        return candidates.filter { sameProfileName(it.name, clean) }.distinctBy { it.id }
    }

    /** Mesmo nome = igual depois de normalizar (sem acento, sem " - Topic", sem pontuacao). Nome so com simbolos compara cru. */
    private fun sameProfileName(a: String, b: String): Boolean {
        val na = ArtistResolver.normalize(a)
        val nb = ArtistResolver.normalize(b)
        if (na.isNotBlank() && nb.isNotBlank()) return na == nb
        return a.trim().equals(b.trim(), ignoreCase = true)
    }

'''

STATE_FIELDS = r'''    val isFollowing: Boolean = false,
    // Perfis com o MESMO nome no outro mundo (canal <-> artista), usados no menu de 3 pontinhos.
    val foundProfiles: List<Artist> = emptyList(),
    val isLoadingFoundProfiles: Boolean = true,
'''

VM_FOUND = r'''    // Perfis com o MESMO nome no outro mundo: canal do YouTube -> artistas do YT Music; artista -> canais do YouTube.
    private fun loadFoundProfiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFoundProfiles = true) }
            val found = runCatching {
                musicRepository.findSameNameProfiles(route.name, wantYouTubeChannels = !route.isYouTubeChannel)
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(foundProfiles = found, isLoadingFoundProfiles = false) }
        }
    }

'''

MENU_VM_NEW = r'''    /**
     * Clique em artista/canal DENTRO de uma faixa.
     * Video: o canal do YouTube tem preferencia (fallback: artista do YT Music). Musica: artista do YT Music.
     */
    fun onOpenArtistForTrack(track: Track, artistName: String) {
        viewModelScope.launch {
            val profile = musicRepository.resolveProfileForTrack(track, artistName) ?: return@launch
            _navigationEvent.emit(MenuNavigationEvent.OpenArtist(profile))
        }
    }

'''

SEARCH_VM_NEW = r'''    /**
     * Clique em artista/canal DENTRO de uma faixa no Search.
     * Video: o canal do YouTube tem preferencia (fallback: artista do YT Music). Musica: artista do YT Music.
     */
    fun onOpenArtistForTrack(track: Track, artistName: String) {
        viewModelScope.launch {
            val profile = musicRepository.resolveProfileForTrack(track, artistName) ?: return@launch
            _navigationEvent.emit(
                NavigationEvent.OpenArtist(
                    profile.id, profile.name, profile.thumbnailUrl,
                    isYouTubeChannel = profile.source == Source.YOUTUBE,
                )
            )
        }
    }

'''

SCREEN_SHEETS = r'''    // Menu de 3 pontinhos: "Canais/Contas encontradas" + Compartilhar. O primeiro abre a lista de perfis com o mesmo nome.
    if (profileMenuOpen) {
        ProfileOptionsSheet(
            isYouTubeChannel = state.isYouTubeChannel,
            foundCount = state.foundProfiles.size,
            isLoadingFound = state.isLoadingFoundProfiles,
            onFoundProfilesClick = { profileMenuOpen = false; foundProfilesOpen = true },
            onShareClick = onShareClick,
            onDismiss = { profileMenuOpen = false },
        )
    }
    if (foundProfilesOpen) {
        FoundProfilesSheet(
            isYouTubeChannel = state.isYouTubeChannel,
            profiles = state.foundProfiles,
            isLoading = state.isLoadingFoundProfiles,
            onProfileClick = { profile -> foundProfilesOpen = false; onProfileClick(profile) },
            onDismiss = { foundProfilesOpen = false },
        )
    }
'''

NAV_WIRING = r'''                            onLoadMoreChannelVideos = vm::loadMoreChannelVideos,
                            onLoadMoreChannelPlaylists = vm::loadMoreChannelPlaylists,
                            onPlaylistPlayNext = vm::onPlaylistPlayNext,
                            onPlaylistAddToQueue = vm::onPlaylistAddToQueue,
                            onSavePlaylistToLibrary = vm::onSavePlaylistToLibrary,
                            // Trocar canal <-> artista com o mesmo nome: substitui a tela atual (voltar nao passa pela errada).
                            onProfileClick = { profile ->
                                navController.navigate(
                                    Routes.Artist(profile.id, profile.name, profile.thumbnailUrl, isYouTubeChannel = profile.source == Source.YOUTUBE)
                                ) { popUpTo<Routes.Artist> { inclusive = true } }
                            },
                        )
'''

NEW_SHEET_FILE = r'''package com.vztechcore.vztune.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.rows.SheetOptionRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Groups

private val ProfileSheetColor = Color(0xFF121829)

// Nomes do item do menu. Troque aqui se quiser outro texto.
private const val FOUND_CHANNELS_LABEL = "Canais encontrados" // aparece na tela do CANAL
private const val FOUND_ACCOUNTS_LABEL = "Contas encontradas" // aparece na tela do ARTISTA

private fun foundProfilesLabel(isYouTubeChannel: Boolean): String =
    if (isYouTubeChannel) FOUND_CHANNELS_LABEL else FOUND_ACCOUNTS_LABEL

/**
 * Menu de 3 pontinhos das telas de Canal e de Artista (sobe de baixo pra cima, igual aos outros):
 * "Canais encontrados" / "Contas encontradas" (icone Groups) e, abaixo, "Compartilhar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileOptionsSheet(
    isYouTubeChannel: Boolean,
    foundCount: Int,
    isLoadingFound: Boolean,
    onFoundProfilesClick: () -> Unit,
    onShareClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProfileSheetColor,
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetOptionRow(
                icon = VzIcons.Groups,
                label = foundProfilesLabel(isYouTubeChannel),
                subtitle = when {
                    isLoadingFound -> "Procurando..."
                    foundCount > 0 -> "$foundCount com o mesmo nome"
                    else -> "Nenhum com o mesmo nome"
                },
                onClick = onFoundProfilesClick,
            )
            SheetOptionRow(
                icon = VzIcons.Share,
                label = "Compartilhar",
                onClick = {
                    onShareClick()
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Segundo menu: perfis do OUTRO mundo com o mesmo nome.
 * Na tela do canal lista artistas/contas do YT Music; na tela do artista lista canais do YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundProfilesSheet(
    isYouTubeChannel: Boolean,
    profiles: List<Artist>,
    isLoading: Boolean,
    onProfileClick: (Artist) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProfileSheetColor,
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = foundProfilesLabel(isYouTubeChannel),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            when {
                isLoading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VzLoading(Modifier.size(96.dp))
                }

                profiles.isEmpty() -> Text(
                    text = if (isYouTubeChannel) "Nenhum artista com o mesmo nome foi encontrado." else "Nenhum canal com o mesmo nome foi encontrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )

                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(profiles, key = { it.source.name + "_" + it.id }) { profile ->
                        FoundProfileRow(profile = profile, onClick = { onProfileClick(profile) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FoundProfileRow(profile: Artist, onClick: () -> Unit) {
    val kind = if (profile.source == Source.YOUTUBE) "Canal do YouTube" else "Artista do YT Music"
    val subtitle = listOfNotNull(kind, profile.subscribers?.takeIf { it.isNotBlank() }).joinToString(" • ")
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
            if (!profile.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.thumbnailUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
'''

# ----------------------------------------------------------------------------------------
# Patches por arquivo
# ----------------------------------------------------------------------------------------
PATCHES = {
    # ---------------- data ----------------
    F_PARSER: [
        Replace(
            "import com.vztechcore.vztune.core.domain.music.Playlist\n",
            "import com.vztechcore.vztune.core.domain.music.Artist\nimport com.vztechcore.vztune.core.domain.music.Playlist\n",
        ),
        InsertBefore("    fun parseShorts(json: JSONObject): List<SearchHit.ShortHit> {", PARSE_CHANNELS),
    ],
    F_YT_REPO: [
        Replace(
            "import com.vztechcore.vztune.core.domain.music.Playlist\n",
            "import com.vztechcore.vztune.core.domain.music.Artist\nimport com.vztechcore.vztune.core.domain.music.Playlist\n",
        ),
        Replace(
            "import org.schabi.newpipe.extractor.playlist.PlaylistExtractor\n",
            "import org.schabi.newpipe.extractor.channel.ChannelInfoItem\nimport org.schabi.newpipe.extractor.playlist.PlaylistExtractor\n",
        ),
        InsertBefore(
            "    data class ShortPage(\n        val items: List<SearchHit.ShortHit>,\n        val hasMore: Boolean,\n    )\n",
            SEARCH_CHANNELS,
        ),
    ],
    F_MUSIC_REPO: [
        Replace(
            "import com.vztechcore.vztune.core.domain.music.Source\n",
            "import com.vztechcore.vztune.core.domain.music.Artist\nimport com.vztechcore.vztune.core.domain.music.Source\n",
        ),
        Replace(
            "import com.vztechcore.vztune.data.ytmusic.YTMusicRepository\n",
            "import com.vztechcore.vztune.data.ytmusic.ArtistResolver\nimport com.vztechcore.vztune.data.ytmusic.YTMusicRepository\n",
        ),
        InsertBefore('    private companion object { const val TAG = "VzTuneMusic" }', MUSIC_REPO_PROFILES),
    ],
    # ---------------- app: estado / viewmodel ----------------
    F_STATE: [
        Replace("    val isFollowing: Boolean = false,\n", STATE_FIELDS),
    ],
    F_VM: [
        Replace(
            "    init {\n        loadContent()\n        viewModelScope.launch {",
            "    init {\n        loadContent()\n        loadFoundProfiles()\n        viewModelScope.launch {",
        ),
        InsertBefore("    fun onFollowClick() {\n", VM_FOUND),
    ],
    # ---------------- app: telas ----------------
    F_HEADER: [
        Replace(
            "import com.vztechcore.vztune.ui.icons.actions.Share\n",
            "import com.vztechcore.vztune.ui.icons.actions.MoreVert\n",
        ),
        Replace(
            " * Barra superior: Voltar na esquerda, Compartilhar na direita.",
            " * Barra superior: Voltar na esquerda, 3 pontinhos (menu) na direita.",
        ),
        Replace(
            "            // Barra superior: Voltar esquerda, Compartilhar direita\n",
            "            // Barra superior: Voltar esquerda, 3 pontinhos direita\n",
        ),
        Replace(
            "    onShareClick: () -> Unit = {},\n    modifier: Modifier = Modifier,\n) {\n    val hasPlayableItems",
            "    onMoreClick: () -> Unit = {},\n    modifier: Modifier = Modifier,\n) {\n    val hasPlayableItems",
        ),
        Replace(
            '                MediaListCircleButton(VzIcons.Share, "Compartilhar artista", onShareClick)',
            '                MediaListCircleButton(VzIcons.MoreVert, "Mais opções", onMoreClick)',
        ),
    ],
    F_SCREEN: [
        # imports
        Replace("import com.vztechcore.vztune.ui.icons.actions.Share\n", ""),
        Replace(
            "import com.vztechcore.vztune.ui.icons.navigation.Search\n",
            "import com.vztechcore.vztune.ui.icons.navigation.Search\n"
            "import com.vztechcore.vztune.ui.components.sheets.FoundProfilesSheet\n"
            "import com.vztechcore.vztune.ui.components.sheets.ProfileOptionsSheet\n",
        ),
        # novo callback de trocar de perfil
        Replace(
            "    onLoadMoreChannelPlaylists: () -> Unit = {},\n    modifier: Modifier = Modifier,\n) {\n    val listState = rememberLazyListState()",
            "    onLoadMoreChannelPlaylists: () -> Unit = {},\n    onProfileClick: (Artist) -> Unit = {},\n    modifier: Modifier = Modifier,\n) {\n    val listState = rememberLazyListState()",
        ),
        # estados dos menus
        Replace(
            "    var playlistMenu by remember { mutableStateOf<Playlist?>(null) }\n",
            "    var playlistMenu by remember { mutableStateOf<Playlist?>(null) }\n"
            "    // Menu de 3 pontinhos (Canais/Contas encontradas + Compartilhar) e a lista de perfis com o mesmo nome.\n"
            "    var profileMenuOpen by remember { mutableStateOf(false) }\n"
            "    var foundProfilesOpen by remember { mutableStateOf(false) }\n",
        ),
        # texto do compartilhar: canal x artista
        Replace(
            r'''        val shareText = "Confere esse artista no VzTune 🎧\n${state.name}\n$url"''',
            r'''        val kindLabel = if (state.isYouTubeChannel) "esse canal" else "esse artista"
        val shareText = "Confere $kindLabel no VzTune 🎧\n${state.name}\n$url"''',
        ),
        Replace(
            r'''context.startActivity(Intent.createChooser(sendIntent, "Compartilhar artista"))''',
            r'''context.startActivity(Intent.createChooser(sendIntent, if (state.isYouTubeChannel) "Compartilhar canal" else "Compartilhar artista"))''',
        ),
        # sheets
        InsertBefore(
            "    Box(\n        modifier\n            .fillMaxSize()\n            .background(Color.Black)\n    ) {\n        LazyColumn(",
            SCREEN_SHEETS,
        ),
        # chamadas dos headers
        Replace(
            "                        onShareClick = onShareClick,\n                        onLoadMoreChannelClick =",
            "                        onMoreClick = { profileMenuOpen = true },\n                        onLoadMoreChannelClick =",
        ),
        Replace(
            "                        onFollowClick = onFollowClick,\n                        onShareClick = onShareClick,\n                    )",
            "                        onFollowClick = onFollowClick,\n                        onMoreClick = { profileMenuOpen = true },\n                    )",
        ),
        # quem decide canal x artista e a ROTA (isYouTubeChannel), nao a presenca de musica
        Replace(
            "                    // FIX14 REBUILD - Regra: tem musica = ARTISTA (so Home, sem abas). Sem musica = CANAL (abas Videos | Shorts | Playlists).\n"
            "                    val hasMusic = state.topSongs.isNotEmpty() || state.albums.isNotEmpty() || state.singles.isNotEmpty()\n"
            "                    val isChannel = !hasMusic\n",
            "                    // Quem decide e a rota: isYouTubeChannel = CANAL (abas Inicio | Videos | Playlists | Shorts), senao ARTISTA (so Home, sem abas).\n"
            "                    val isChannel = state.isYouTubeChannel\n",
        ),
        # barra colapsada: 3 pontinhos tambem
        Replace(
            "                onShareClick = if (state.isYouTubeChannel) onShareClick else null,\n",
            "                onMoreClick = { profileMenuOpen = true },\n",
        ),
        # ChannelHeader: parametro
        Replace(
            "    onFollowClick: () -> Unit,\n    onShareClick: () -> Unit,\n    onLoadMoreChannelClick: () -> Unit = {},",
            "    onFollowClick: () -> Unit,\n    onMoreClick: () -> Unit,\n    onLoadMoreChannelClick: () -> Unit = {},",
        ),
        # ChannelHeader: so inscritos (sem quantidade de videos)
        Replace(
            "    val subsVideos = listOfNotNull(\n"
            "        state.subscribers?.takeIf { it.isNotBlank() },\n"
            '        if (state.videos.isNotEmpty()) state.videos.size.toString() + " vídeos" else null,\n'
            '    ).joinToString(" • ")\n',
            "    // So inscritos: o app busca 1000 videos e depois 100 em 100, entao a contagem nunca seria o total real.\n"
            "    val subscribersLabel = state.subscribers?.takeIf { it.isNotBlank() }\n",
        ),
        # ChannelHeader: botao 3 pontinhos
        Replace(
            '                MediaListCircleButton(VzIcons.Share, "Compartilhar canal", onShareClick)',
            '                MediaListCircleButton(VzIcons.MoreVert, "Mais opções", onMoreClick)',
        ),
        # ChannelHeader: nome do canal (weight dentro de item do LazyColumn media 0 de altura e o nome sumia)
        Replace(
            "                Text(text = state.name, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))\n",
            "                Text(text = state.name, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)\n",
        ),
        Replace(
            "                if (subsVideos.isNotBlank()) {\n"
            "                    Spacer(Modifier.height(2.dp))\n"
            "                    Text(text = subsVideos, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)\n"
            "                }\n",
            "                if (!subscribersLabel.isNullOrBlank()) {\n"
            "                    Spacer(Modifier.height(2.dp))\n"
            "                    Text(text = subscribersLabel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)\n"
            "                }\n",
        ),
    ],
    F_NAV: [
        Replace(
            "                            onLoadMoreChannelVideos = vm::loadMoreChannelVideos,\n                        )\n",
            NAV_WIRING,
        ),
    ],
    # ---------------- app: video abre canal primeiro ----------------
    F_MENU_VM: [
        ReplaceBetween(
            "    /** Clique em artista DENTRO de uma faixa: verifica quem tem ESSA faixa / ESSE álbum no catálogo. */\n",
            "    /**\n     * FIX 6 (método do SimpMusic): abre o CANAL REAL que postou o vídeo.",
            MENU_VM_NEW,
            "resolveProfileForTrack",
        ),
    ],
    F_SEARCH_VM: [
        ReplaceBetween(
            "    /** FIX9: Clique em artista DENTRO de uma faixa no Search. Igual ao TrackMenuViewModel. */\n",
            "    /** FIX9: Canal real do video no Search.",
            SEARCH_VM_NEW,
            "resolveProfileForTrack",
        ),
        Replace(
            "                        NavigationEvent.OpenArtist(\n"
            "                            hit.artist.id,\n"
            "                            hit.artist.name,\n"
            "                            hit.artist.thumbnailUrl,\n"
            "                        )\n",
            "                        NavigationEvent.OpenArtist(\n"
            "                            hit.artist.id,\n"
            "                            hit.artist.name,\n"
            "                            hit.artist.thumbnailUrl,\n"
            "                            isYouTubeChannel = hit.artist.source == Source.YOUTUBE,\n"
            "                        )\n",
        ),
    ],
}

NEW_FILES = {F_NEW_SHEET: NEW_SHEET_FILE}


# ----------------------------------------------------------------------------------------
# Execucao: valida tudo na memoria e so entao grava
# ----------------------------------------------------------------------------------------
def main():
    pending = {}
    errors = []
    report = []

    for rel, ops in PATCHES.items():
        path = ROOT / rel
        if not path.is_file():
            errors.append(f"[{rel}] arquivo nao encontrado.")
            continue
        text = path.read_text(encoding="utf-8")
        original = text
        for idx, op in enumerate(ops, 1):
            try:
                text, status = op.apply(text)
                report.append((rel, idx, status))
            except ValueError as exc:
                errors.append(f"[{rel}] patch {idx}: {exc}")
        if text != original:
            pending[path] = text

    if errors:
        print("ERRO: nada foi alterado. Problemas encontrados:\n")
        for e in errors:
            print(" -", e, "\n")
        print("O codigo atual pode estar diferente do dump. Me mande o arquivo citado.")
        sys.exit(2)

    for rel, content in NEW_FILES.items():
        path = ROOT / rel
        if not path.is_file() or path.read_text(encoding="utf-8") != content:
            pending[path] = content

    for path, content in pending.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    print("Arquivos atualizados:" if pending else "Nada a fazer: tudo ja estava aplicado.")
    for path in sorted(pending):
        print("  *", path.relative_to(ROOT))
    ok = sum(1 for _, _, s in report if s == "ok")
    skipped = sum(1 for _, _, s in report if s != "ok")
    print(f"\nPatches aplicados: {ok} | ja aplicados: {skipped}")
    print("Nenhum backup foi criado. Agora rode o build no GitHub Actions.")


if __name__ == "__main__":
    main()
