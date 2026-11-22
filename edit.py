# passo3_ui.py — PASSO 3 (UI). Roda na raiz do projeto: python3 passo3_ui.py
# 1) Moldura nas miniaturas: borda fina branca em MediaThumbnail + cards de playlist/shorts.
# 2) Topo do canal igual ao YouTube: voltar | lupa + 3 pontinhos.
#    Menu: "Buscar mais faixas e playlists" (500 videos + 500 playlists) e, abaixo, "Compartilhar".
#    Lupa: abre campo de busca que filtra os videos/playlists ja carregados do canal.
# Banner NAO mexido: ele ja existia no ChannelHeader.
# Cada arquivo e independente: se um falhar, os outros aplicam e o erro e avisado.
import pathlib, sys

ROOT = pathlib.Path(".")
errors = []

def add_import(text, line, anchor):
    if line in text:
        return text, False
    if text.count(anchor) != 1:
        return text, False
    return text.replace(anchor, anchor + "\n" + line, 1), True

def rep1(text, old, new, tag, fname):
    n = text.count(old)
    if n != 1:
        errors.append(f"{fname} [{tag}]: achei {n}x (esperava 1x) - arquivo NAO alterado")
        return None
    return text.replace(old, new, 1)

# ============ ArtistScreen.kt ============
p = ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
if p.exists():
    t = p.read_text(encoding="utf-8")
    orig = t
    ok = True
    for line, anchor in [
        ("import androidx.compose.material3.DropdownMenu", "import androidx.compose.material3.Text"),
        ("import androidx.compose.material3.DropdownMenuItem", "import androidx.compose.material3.Text"),
        ("import androidx.compose.runtime.LaunchedEffect", "import androidx.compose.runtime.Composable"),
        ("import androidx.compose.ui.focus.FocusRequester", "import androidx.compose.ui.platform.LocalContext"),
        ("import com.vztechcore.vztune.ui.components.layout.VzSearchInputBar", "import com.vztechcore.vztune.ui.components.layout.SectionHeader"),
        ("import com.vztechcore.vztune.ui.icons.navigation.Search", "import com.vztechcore.vztune.ui.icons.actions.Share"),
        ("import com.vztechcore.vztune.ui.icons.actions.MoreVert", "import com.vztechcore.vztune.ui.icons.actions.Share"),
    ]:
        t, _ = add_import(t, line, anchor)
    t2 = rep1(t, """    var homePlaylistsVisible by remember { mutableStateOf(4) }""",
        """    var homePlaylistsVisible by remember { mutableStateOf(4) }
    // Busca dentro do canal (lupa do topo): filtra os videos/playlists ja carregados pelo titulo. Vazio = tudo.
    var channelSearchOpen by remember { mutableStateOf(false) }
    var channelSearchQuery by remember { mutableStateOf("") }
    val displayState = if (channelSearchQuery.isBlank()) state else state.copy(
        videos = state.videos.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
        channelPlaylists = state.channelPlaylists.filter { it.title.contains(channelSearchQuery, ignoreCase = true) },
    )""", "estado-busca", p.name)
    if t2 is None: ok = False
    else: t = t2
    if ok:
        t2 = rep1(t, """                        onDescClick = { channelDescExpanded = !channelDescExpanded },
                        onBackClick = onBackClick,
                        onFollowClick = onFollowClick,
                        onShareClick = onShareClick,
                    )""",
            """                        onDescClick = { channelDescExpanded = !channelDescExpanded },
                        onBackClick = onBackClick,
                        onFollowClick = onFollowClick,
                        onShareClick = onShareClick,
                        onLoadMoreChannelClick = { onLoadMoreChannelVideos(); onLoadMoreChannelPlaylists() },
                        searchOpen = channelSearchOpen,
                        searchQuery = channelSearchQuery,
                        onSearchQueryChange = { channelSearchQuery = it },
                        onSearchToggle = { channelSearchOpen = !channelSearchOpen; if (!channelSearchOpen) channelSearchQuery = "" },
                    )""", "callsite-header", p.name)
        if t2 is None: ok = False
        else: t = t2
    if ok:
        t2 = rep1(t, """private fun ChannelHeader(
    state: ArtistUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    descExpanded: Boolean,
    onDescClick: () -> Unit,
    onBackClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
) {""",
            """private fun ChannelHeader(
    state: ArtistUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    descExpanded: Boolean,
    onDescClick: () -> Unit,
    onBackClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
    onLoadMoreChannelClick: () -> Unit = {},
    searchOpen: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchToggle: () -> Unit = {},
) {
    var channelMenuOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) { if (searchOpen) searchFocus.requestFocus() }""", "assinatura-header", p.name)
        if t2 is None: ok = False
        else: t = t2
    if ok:
        t2 = rep1(t, """            MediaListCircleButton(VzIcons.ArrowBackIos, "Voltar", onBackClick)
            MediaListCircleButton(VzIcons.Share, "Compartilhar", onShareClick)
        }""",
            """            MediaListCircleButton(VzIcons.ArrowBackIos, "Voltar", onBackClick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaListCircleButton(VzIcons.Search, "Buscar no canal", onSearchToggle)
                Box {
                    MediaListCircleButton(VzIcons.MoreVert, "Mais", { channelMenuOpen = true })
                    DropdownMenu(
                        expanded = channelMenuOpen,
                        onDismissRequest = { channelMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "Buscar mais faixas e playlists") },
                            onClick = { channelMenuOpen = false; onLoadMoreChannelClick() },
                        )
                        DropdownMenuItem(
                            text = { Text(text = "Compartilhar") },
                            onClick = { channelMenuOpen = false; onShareClick() },
                        )
                    }
                }
            }
        }
        // Lupa: campo de busca dentro do canal, filtra os videos/playlists ja carregados.
        if (searchOpen) {
            VzSearchInputBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClose = onSearchToggle,
                placeholder = "Buscar neste canal",
                focusRequester = searchFocus,
                onSearch = {},
                closeOnStart = true,
            )
        }""", "barra-topo", p.name)
        if t2 is None: ok = False
        else: t = t2
    if ok:
        start = t.find("fun ArtistScreen(")
        end = t.find("private fun ChannelHeader(", start)
        if start < 0 or end < 0 or end <= start:
            errors.append(f"{p.name} [displaystate-span]: nao achei o trecho ArtistScreen->ChannelHeader - arquivo NAO alterado")
            ok = False
        else:
            head, span, tail = t[:start], t[start:end], t[end:]
            span = span.replace("state.videos", "displayState.videos").replace("state.channelPlaylists", "displayState.channelPlaylists")
            t = head + span + tail
            print(f"OK [displaystate-span] ({span.count('displayState.')} usos)")
    if ok:
        bak = p.with_suffix(p.suffix + ".bak-passo3ui")
        bak.write_text(orig, encoding="utf-8")
        p.write_text(t, encoding="utf-8")
        print(f"OK [ArtistScreen.kt] (backup em {bak.name})")
else:
    errors.append("ArtistScreen.kt nao encontrado - pulado")

# ============ ChannelCards.kt (moldura playlist + shorts) ============
p = ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/ChannelCards.kt"
if p.exists():
    t = p.read_text(encoding="utf-8")
    orig = t
    t, _ = add_import(t, "import androidx.compose.foundation.border", "import androidx.compose.foundation.background")
    ok = True
    t2 = rep1(t, """Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {""",
        """Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))) {""", "moldura-playlist", p.name)
    if t2 is None: ok = False
    else: t = t2
    if ok:
        t2 = rep1(t, """Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))) {""",
            """Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))) {""", "moldura-shorts", p.name)
        if t2 is None: ok = False
        else: t = t2
    if ok:
        bak = p.with_suffix(p.suffix + ".bak-passo3ui")
        bak.write_text(orig, encoding="utf-8")
        p.write_text(t, encoding="utf-8")
        print(f"OK [ChannelCards.kt] (backup em {bak.name})")
else:
    errors.append("ChannelCards.kt nao encontrado - pulado")

# ============ MediaThumbnail.kt (moldura em TODAS as miniaturas, componente unico) ============
p = ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MediaThumbnail.kt"
if p.exists():
    t = p.read_text(encoding="utf-8")
    orig = t
    t, _ = add_import(t, "import androidx.compose.foundation.border", "import androidx.compose.foundation.background")
    t2 = rep1(t, """    Box(
        modifier = baseModifier
            .clip(clipShape)
            .background(Color.Black),
    ) {""",
        """    Box(
        modifier = baseModifier
            .clip(clipShape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.18f), clipShape),
    ) {""", "moldura-thumbnail", p.name)
    if t2 is None:
        pass
    else:
        bak = p.with_suffix(p.suffix + ".bak-passo3ui")
        bak.write_text(orig, encoding="utf-8")
        p.write_text(t2, encoding="utf-8")
        print(f"OK [MediaThumbnail.kt] (backup em {bak.name})")
else:
    errors.append("MediaThumbnail.kt nao encontrado - pulado")

if errors:
    print("\nAVISOS (esses arquivos NAO foram alterados):")
    for e in errors: print(" - " + e)
    print("Os arquivos com OK acima aplicaram. Me manda o aviso que eu ajusto a ancora.")
else:
    print("\nPASSO 3 (UI) aplicado em tudo. Comita e da push.")
