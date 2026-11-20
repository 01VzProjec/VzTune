#!/bin/bash
# VzTune HOTFIX FIX10 - DateWrapper no YouTubeRepository
# Corrige: formatPublished esperava OffsetDateTime mas NewPipe devolve DateWrapper
# Rode na raiz do projeto: bash VzTune-HOTFIX-FIX10-DateWrapper.sh
set -e
python3 - << 'PY'
import pathlib
p = pathlib.Path("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt")
t = p.read_text(encoding="utf-8")
if "fun formatPublished(uploadDate: org.schabi.newpipe.extractor.localization.DateWrapper?)" in t:
    print("JA CORRIGIDO")
else:
    if "import org.schabi.newpipe.extractor.localization.DateWrapper" not in t:
        t = t.replace("import java.time.OffsetDateTime", "import java.time.OffsetDateTime\nimport org.schabi.newpipe.extractor.localization.DateWrapper")
    old = """    fun formatPublished(uploadDate: OffsetDateTime?): String? {
        if (uploadDate == null) return null
        val now = OffsetDateTime.now()
        val days = ChronoUnit.DAYS.between(uploadDate, now)"""
    new = """    fun formatPublished(uploadDate: DateWrapper?): String? {
        val odt = runCatching { uploadDate?.offsetDateTime() }.getOrNull() ?: return null
        val now = OffsetDateTime.now()
        val days = ChronoUnit.DAYS.between(odt, now)"""
    assert old in t, "formatPublished original nao encontrado - talvez ja corrigido manualmente"
    t = t.replace(old, new)
    p.write_text(t, encoding="utf-8")
    print("OK", p)
print("HOTFIX DateWrapper OK")
PY
