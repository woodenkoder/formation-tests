# shellcheck shell=bash disable=SC2016
# Combat 01.5 du manoir : deux tags annotés, un correctif parti du bon tag, reporté sur main.
annote() { [ "$(git cat-file -t "refs/tags/$1" 2>/dev/null)" = tag ]; }
verifie "Le tag v0.1.0 existe et il est annoté (git tag -a)" annote v0.1.0
verifie "Le tag v0.1.1 existe et il est annoté" annote v0.1.1
if git rev-parse -q --verify "v0.1.0^{commit}" >/dev/null && git rev-parse -q --verify "v0.1.1^{commit}" >/dev/null; then
  verifie "v0.1.1 descend de v0.1.0 (la branche hotfix part du tag)" git merge-base --is-ancestor v0.1.0 v0.1.1
  apres=$(git rev-list --first-parent "v0.1.0..origin/main")
  verifie "Main a avancé après v0.1.0 (le commit de la suite du travail)" test -n "$apres"
  intrus=0
  for c in $apres; do
    if git merge-base --is-ancestor "$c" v0.1.1; then intrus=$((intrus + 1)); fi
  done
  verifie "v0.1.1 ne contient pas le travail ajouté sur main après v0.1.0" test "$intrus" = 0
  git checkout -q -f --detach v0.1.1
  echo "— Sur v0.1.1 :"
  tests_caches com.nocturne.lanterne.manoir.TvaManoirTest
  git checkout -q -f --detach origin/main
  echo "— Sur main :"
  tests_caches com.nocturne.lanterne.manoir.TvaManoirTest
else
  ko "Pose les deux tags (v0.1.0 et v0.1.1) et pousse-les : git push origin v0.1.0 v0.1.1"
fi
