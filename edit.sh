#!/bin/bash
# VzTune HOTFIX FIX14 - Row + weight
# Causa: FIX14 usou Row() nas abas do canal e no SectionHeaderArrow, mas nao tinha
#        "import androidx.compose.foundation.layout.Row" no ArtistScreen.kt
#        -> Row nao resolve, e o weight() (que so existe dentro do RowScope) tambem nao resolve,
#        e tudo dentro do Row vira "Composable fora de @Composable".
#        Segundo bug: o weight() estava DENTRO de um forEachIndexed. Dentro de forEach o
#        RowScope some, entao mesmo com o import o weight continuaria nao resolvendo.
#        Fix: 1) adiciona o import do Row, 2) desenrola as 2 abas (Videos/Playlists) sem forEach,
#        cada Box com weight(1f) direto dentro do Row.
# Rode na raiz: bash VzTune-HOTFIX-FIX14-ROW.sh
set -e
python3 - << 'PY'
import pathlib, re

p = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt")
t = p.read_text(encoding="utf-8")

# 1) Import do Row (se faltar)
if "import androidx.compose.foundation.layout.Row" not in t:
    lines = t.splitlines()
    last_layout = -1
    for idx, ln in enumerate(lines):
        if ln.startswith("import androidx.compose.foundation.layout."):
            last_layout = idx
    if last_layout != -1:
        lines.insert(last_layout + 1, "import androidx.compose.foundation.layout.Row")
    else:
        # fallback: depois do ultimo import
        last = -1
        for idx, ln in enumerate(lines):
            if ln.startswith("import "):
                last = idx
        lines.insert(last + 1, "import androidx.compose.foundation.layout.Row")
    t = "\n".join(lines) + "\n"
    print("OK: import Row adicionado")
else:
    print("Import Row ja existe")

# 2) Desenrola as abas do canal: tira o forEachIndexed (weight dentro de forEach nao existe)
old_block = """                                listOf("Vídeos", "Playlists").forEachIndexed { idx, label ->
                                    val sel = selectedChannelTab == idx
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.Transparent).clickable { selectedChannelTab = idx }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }"""
new_block = """                                // Sem forEach: weight() so existe direto dentro do Row. Dentro de forEach o RowScope some e o weight nao resolve.
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 0) Color.White else Color.Transparent).clickable { selectedChannelTab = 0 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Vídeos", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedChannelTab == 1) Color.White else Color.Transparent).clickable { selectedChannelTab = 1 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Playlists", style = MaterialTheme.typography.labelLarge, color = if (selectedChannelTab == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }"""
if old_block in t:
    t = t.replace(old_block, new_block, 1)
    print("OK: abas do canal desenroladas (sem forEach)")
else:
    # Fallback por regex: acha o forEach das abas e troca por versao sem forEach
    pat = re.compile(r'\s*listOf\("Vídeos", "Playlists"\)\.forEachIndexed \{.*?Text\(text = label.*?\)\s*\n\s*\}\s*\n\s*\}', flags=re.DOTALL)
    m = pat.search(t)
    if m:
        t = t[:m.start()] + "\n" + new_block + "\n" + t[m.end():]
        print("OK: abas do canal desenroladas (via regex fallback)")
    else:
        print("AVISO: nao achei o bloco forEach das abas. Se o erro persistir, manda o trecho das abas que eu ajusto.")
        # Ainda assim salva o import
        p.write_text(t, encoding="utf-8")
        raise SystemExit(0)

if t.count("{") != t.count("}"):
    print("AVISO braces:", t.count("{"), t.count("}"))
else:
    print("Braces OK:", t.count("{"))
p.write_text(t, encoding="utf-8")
print("HOTFIX FIX14 ROW OK")
PY
