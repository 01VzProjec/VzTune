#!/bin/bash
# VzTune HOTFIX FIX10-2 - Creditos + icones + weight
# Corrige 5 erros do build:
# 1) TrackCreditsSheet: falta import fillMaxSize
# 2) TrackCreditsSheet: smart cast de description (propriedade de outro modulo)
# 3) TrackOptionsSheet: import MusicNoteDone que nao existe
# 4) TrackOptionsSheet: falta import DownloadDone (o icone existe, so faltava importar)
# 5) TrackOptionsSheet: TopActionCard usa weight fora de RowScope
# Rode na raiz: bash VzTune-HOTFIX-FIX10-2-Credits-Icons-Weight.sh
set -e
python3 - << 'PY'
import pathlib
# --- 1+2) Creditos ---
p = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackCreditsSheet.kt")
t = p.read_text(encoding="utf-8")
if "import androidx.compose.foundation.layout.fillMaxSize" not in t:
    t = t.replace("import androidx.compose.foundation.layout.fillMaxWidth",
                  "import androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.layout.fillMaxWidth", 1)
old_desc = """                if (!c.description.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Sobre", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(text = c.description, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }"""
new_desc = """                val desc = c.description
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Sobre", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }"""
if old_desc in t:
    t = t.replace(old_desc, new_desc)
else:
    # Fallback: qualquer uso direto de c.description dentro de Text
    t = t.replace("Text(text = c.description,", "Text(text = c.description ?: \"\",")
    print("AVISO: bloco descricao exato nao achado, usado fallback")
p.write_text(t, encoding="utf-8")
print("OK", p)

# --- 3+4+5) TrackOptionsSheet ---
p2 = pathlib.Path("app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt")
t2 = p2.read_text(encoding="utf-8")
# 3) Remove import de icone que nao existe (MusicNoteDone nao existe no projeto)
t2 = t2.replace("import com.vztechcore.vztune.ui.icons.media.MusicNoteDone\n", "")
# 3b) Remove import duplicado de Download (tinha 2x, nao adjacentes: Download / MusicNote / Download)
_lines = t2.splitlines(keepends=True)
_seen_dl = False
_out = []
for _ln in _lines:
    if _ln.strip() == "import com.vztechcore.vztune.ui.icons.media.Download":
        if _seen_dl:
            continue
        _seen_dl = True
    _out.append(_ln)
t2 = "".join(_out)
# 4) DownloadDone EXISTE como arquivo, so faltava importar
if "import com.vztechcore.vztune.ui.icons.media.DownloadDone" not in t2:
    t2 = t2.replace("import com.vztechcore.vztune.ui.icons.media.Download\n",
                    "import com.vztechcore.vztune.ui.icons.media.Download\nimport com.vztechcore.vztune.ui.icons.media.DownloadDone\n", 1)
# 5) TopActionCard e chamado dentro de um Row, entao precisa ser RowScope para usar weight
if "private fun RowScope.TopActionCard" not in t2:
    t2 = t2.replace("private fun TopActionCard(", "private fun RowScope.TopActionCard(")
p2.write_text(t2, encoding="utf-8")
print("OK", p2)
print("HOTFIX FIX10-2 OK")
PY
