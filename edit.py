#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
edit.py - Corrige o UNICO erro de compile restante:
ShortsViewModel.kt -> pickStream(cached.streams, ...) com cached nullable.
Uso: python3 edit.py --dry | python3 edit.py
"""
import os, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
REL = "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/shorts/ShortsViewModel.kt"

OLD = "selectedQuality = pickStream(cached.streams, _state.value.qualityMode)?.qualityLabel,"
NEW = "selectedQuality = pickStream(cached?.streams.orEmpty(), _state.value.qualityMode)?.qualityLabel,"

def main():
    dry = "--dry" in sys.argv
    path = os.path.join(ROOT, REL)
    if not os.path.exists(path):
        print("ERRO: nao achei", REL); sys.exit(1)
    s = open(path, encoding="utf-8").read()
    if NEW in s:
        print("ja aplicado:", REL); return
    if s.count(OLD) != 1:
        print("ERRO: trecho encontrado %dx (esperado 1)" % s.count(OLD)); sys.exit(1)
    if dry:
        print("DRY-RUN ok: corrigiria", REL); return
    open(path, "w", encoding="utf-8", newline="\n").write(s.replace(OLD, NEW))
    print("alterado:", REL)

if __name__ == "__main__":
    main()