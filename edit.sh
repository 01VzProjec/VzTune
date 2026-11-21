#!/bin/bash
# VzTune HOTFIX ArtistScreen 537 - Falta um ')' no Text do nome do canal
# Erro: ArtistScreen.kt:537:35 Expecting ')'
# Causa: o FIX18, ao remover o check de verificado, usou um regex que
# parou no primeiro ')' dentro de Modifier.weight(1f, fill = false)
# e cortou o ')' final do Text(text = state.name, ...).
# Resultado: Text ficou aberto, o compilador esperava ')' e achou o proximo comando.
# Roda na raiz: bash VzTune-HOTFIX-ARTISTSCREEN-537.sh
set -e
python3 << 'PYEOF'
import pathlib, re
ROOT = pathlib.Path(".")
p = ROOT / "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistScreen.kt"
if not p.exists():
    # Fallback: procura qualquer ArtistScreen.kt
    cands = list(ROOT.rglob("ArtistScreen.kt"))
    if not cands:
        print("ERRO ArtistScreen.kt nao achado")
        raise SystemExit(1)
    p = cands[0]
    print(f"Usando {p}")

t = p.read_text(encoding="utf-8", errors="ignore")
orig = t

# 1) Caso principal (FIX17 + FIX18): Text do nome numa linha so,
#    terminando em Modifier.weight(1f, fill = false) com 1 ')' so.
#    Tem que terminar com 2 ')': 1 fecha o weight, 1 fecha o Text.
pat = re.compile(r"(Text\(text = state\.name[^\n]*?Modifier\.weight\(1f, fill = false\))(?!\))")
t2, n = pat.subn(r"\1)", t)
if n > 0:
    t = t2
    print(f"OK ArtistScreen: Text do nome do canal corrigido (+1 ')' ) - {n} ocorrencia(s)")
else:
    print("AVISO padrao de 1 linha nao achado, tentando varredura por parenteses...")

# 2) Varredura generica: acha Text(text = state.name e conta parenteses
#    ate o fim da linha. Se a linha termina aberta, fecha.
if t == orig:
    lines = t.splitlines()
    fixed2 = 0
    for i, line in enumerate(lines):
        if "Text(text = state.name" in line and "Modifier.weight(1f" in line:
            # Conta ( e ) na linha (ignora strings simples, aqui nao tem)
            opens = line.count("(")
            closes = line.count(")")
            if opens > closes:
                # Falta (opens - closes) fechamentos
                lines[i] = line.rstrip() + ")" * (opens - closes)
                fixed2 += 1
                print(f"OK linha {i+1}: faltavam {opens - closes} ')' -> {lines[i].strip()[:120]}")
    if fixed2:
        t = "\n".join(lines) + ("\n" if orig.endswith("\n") else "")

if t != orig:
    p.write_text(t, encoding="utf-8")
    print("HOTFIX-537 OK - pode dar Rebuild")
    print("Obs: nao confere ( / ) no arquivo todo porque tem ( ) dentro de textos/strings, isso confundiria a conta.")
else:
    print("OK ja estava corrigido - nada a mudar.")
PYEOF
