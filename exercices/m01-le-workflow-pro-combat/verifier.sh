# shellcheck shell=bash disable=SC2016
# Combat 01.1 du manoir : CONTRIBUTING.md arrive sur main par une pull request, avec des commits propres.
CONV='^(feat|fix|docs|chore|refactor|test|style|ci|build|perf)(\([a-z0-9-]+\))?!?: .+'
main=origin/main
if ! git cat-file -e "$main:CONTRIBUTING.md" 2>/dev/null; then
  ko "CONTRIBUTING.md doit exister sur main (après la fusion de ta pull request)"
  return 0
fi
ok "CONTRIBUTING.md existe sur main"
contenu=$(git show "$main:CONTRIBUTING.md")
verifie "Il explique comment nommer les branches et les commits" \
  bash -c 'grep -qi "branche" <<<"$1" && grep -qi "commit" <<<"$1"' _ "$contenu"
fusion=$(git log "$main" --merges --first-parent --format='%H %s' | grep "feature/contribuer" | head -1 | cut -d' ' -f1)
ecrase=$(git log "$main" --first-parent --no-merges --format='%H %s' -- CONTRIBUTING.md | grep -E ' \(#[0-9]+\)$' | head -1 | cut -d' ' -f1)
if [ -n "$fusion" ]; then
  ok "Arrivé sur main par une pull request fusionnée depuis feature/contribuer"
  commits=$(git log --no-merges --format=%s "$fusion^1..$fusion^2")
  nb=$(grep -c . <<<"$commits" || true)
  propres=$(grep -cE "$CONV" <<<"$commits" || true)
  verifie "Au moins deux commits dans la pull request ($nb)" test "$nb" -ge 2
  verifie "Tous ses commits suivent Conventional Commits ($propres sur $nb, par exemple « docs: … »)" test "$propres" = "$nb"
elif [ -n "$ecrase" ]; then
  ok "Arrivé sur main par une pull request (Squash and merge)"
  titre=$(git log -1 --format=%s "$ecrase" | sed -E 's/ \(#[0-9]+\)$//')
  verifie "Le titre de la pull request suit Conventional Commits (« $titre »)" grep -qE "$CONV" <<<"$titre"
else
  ko "CONTRIBUTING.md doit arriver par une pull request fusionnée depuis feature/contribuer (Create a merge commit ou Squash and merge), pas par un push direct sur main"
fi
