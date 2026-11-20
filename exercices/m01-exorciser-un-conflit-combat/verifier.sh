# shellcheck shell=bash disable=SC2016
# Combat 01.3 du manoir : les deux branches sont fusionnées dans main, le conflit est résolu pour de bon.
git checkout -q -f --detach origin/main
sujets=$(git log --format=%s origin/main)
verifie "« livraison gratuite dès 50 € » est fusionnée dans main" grep -q "livraison gratuite dès 50 €" <<<"$sujets"
verifie "« livraison gratuite pour les clients premium » est fusionnée dans main" \
  grep -q "livraison gratuite pour les clients premium" <<<"$sujets"
verifie "Aucun marqueur de conflit (<<<<<<<, >>>>>>>) dans le dépôt" \
  bash -c '! git grep -qE "^(<<<<<<<|>>>>>>>)( |$)" HEAD -- .'
tests_caches com.nocturne.lanterne.manoir.FraisPortManoirTest
