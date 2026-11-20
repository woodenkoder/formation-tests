# shellcheck shell=bash disable=SC2016
# Combat 01.2 du manoir : la branche en retard est rebasée, nettoyée, et ses tests passent.
if ! git rev-parse --verify -q origin/exercice/en-retard >/dev/null; then
  ko "Lance exercices/preparer-rebase.sh, puis pousse main et exercice/en-retard"
  return 0
fi
git checkout -q -f --detach origin/exercice/en-retard
verifie "La branche part du dernier commit de main (rebasée sur main à jour)" \
  test "$(git merge-base HEAD origin/main)" = "$(git rev-parse origin/main)"
verifie "Le travail de la branche est toujours là (au moins un commit au-dessus de main)" \
  test "$(git rev-list --count origin/main..HEAD)" -ge 1
corrections=$(git log --format=%s origin/main..HEAD | grep -ciE '^fix (typo|test)' || true)
verifie "Plus de « fix typo » ni de « fix test » : ils sont fondus dans le commit qu'ils corrigent" test "$corrections" = 0
verifie "Aucun commit de merge sur la branche" test "$(git rev-list --merges --count origin/main..HEAD)" = 0
verifie "Le message de bienvenue est toujours là, sans faute (Bienvenue.java)" \
  grep -q '"Bienvenue dans la boutique' src/main/java/com/nocturne/lanterne/bienvenue/Bienvenue.java
if mvn -B -q -ntp test >/tmp/manoir-mvn.log 2>&1; then
  ok "Les tests passent sur la branche"
else
  grep -E "ERROR|FAIL" /tmp/manoir-mvn.log | head -15
  ko "Les tests doivent passer sur la branche rebasée"
fi
