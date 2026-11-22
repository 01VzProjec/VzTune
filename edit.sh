#!/bin/bash
# REVERTER - bora evitar remendo em cima de remendo.
# O que aconteceu: o edit.sh criou ChannelListSession / ChannelVideosSession
# duplicado, e o edit_fix.sh tentou apagar uma data class sem corpo { }
# e acabou apagando o bloco seguinte junto (videoSearches, channelVideoSessions...).
# Por isso o 2o log ficou PIOR: Unresolved reference 'videoSearches', 'channelVideoSessions'.
#
# Este script NAO edita texto. Ele so usa git para voltar o arquivo para
# o ultimo commit em que ele compilava (sem ChannelListSession / ChannelVideosSession).
# Rode na raiz do projeto: bash reverter.sh
set -e
F="data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt"
if [ ! -f "$F" ]; then echo "ERRO: nao achei $F. Rode na raiz."; exit 1; fi

echo "1) Salvando o estado quebrado numa branch, pra nao perder nada:"
git checkout -b backup-quebrado 2>/dev/null || git checkout backup-quebrado 2>/dev/null || true
git add -A 2>/dev/null || true
git commit -m "backup quebrado antes do revert" 2>/dev/null || echo "   (nada novo pra commitar, ok)"
git checkout main 2>/dev/null || git checkout master 2>/dev/null || true
echo ""

echo "2) Procurando o ultimo commit em que $F compilava..."
CLEAN=""
for C in $(git log --format=%H -- "$F" | head -n 20); do
  if git show "$C:$F" 2>/dev/null | grep -q "ChannelListSession\|ChannelVideosSession\|ChannelPlaylistsSession"; then
    echo "   $C -> ainda tem duplicata, pulando"
  else
    if git show "$C:$F" 2>/dev/null | grep -q "private val videoSearches"; then
      CLEAN="$C"
      echo "   $C -> LIMPO (tem videoSearches, sem Channel*Session)"
      break
    else
      echo "   $C -> sem videoSearches, pulando"
    fi
  fi
done
if [ -z "$CLEAN" ]; then
  echo "ERRO: nao achei commit limpo nos ultimos 20. Roda: git log --oneline -- $F"
  echo "e me manda a lista que eu te digo qual voltar."
  exit 1
fi

echo ""
echo "3) Restaurando $F do commit $CLEAN..."
git checkout "$CLEAN" -- "$F"

echo ""
echo "4) Arquivos do app que ainda chamam o codigo duplicado (nao vou apagar, so listar):"
git grep -l -e "ChannelListSession" -e "ChannelVideosSession" -e "loadMoreChannelVideos" -e "loadMoreChannelPlaylists" || echo "   nenhum - so o YouTubeRepository tinha o problema"
echo ""
echo "Se aparecer algum arquivo do app ai em cima, me manda a lista."
echo "A gente volta so esses arquivos pro mesmo commit limpo, sem perder a UI."
echo ""
echo "5) Verificacao do YouTubeRepository.kt restaurado:"
echo -n "   ChannelListSession: "; grep -c "ChannelListSession" "$F" || echo 0
echo -n "   ChannelVideosSession: "; grep -c "ChannelVideosSession" "$F" || echo 0
echo -n "   videoSearches: "; grep -c "videoSearches" "$F" || echo 0
echo ""
echo "Agora: git add -A && git commit -m 'revert YouTubeRepository para versao que compilava' && git push"
echo "Depois disso o CI tem que voltar a passar no :data. A parte de canal (Inicio / Buscar mais) a gente faz DEPOIS, em passo pequeno, sem criar sessao nova."
