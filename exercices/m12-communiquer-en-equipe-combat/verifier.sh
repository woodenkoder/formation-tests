# shellcheck shell=bash
# Combat 12.3 du manoir : une PR que l'équipe peut relire sans poser de question.
pr="${MANOIR_PR:-}"
if [ -n "$pr" ] && [ -s "$pr" ] && jq -e 'type == "array" and length > 0' "$pr" >/dev/null 2>&1; then
  ok "Une pull request part de exercice/description-pr"
  verifie "Elle vise la branche main" jq -e '.[0].base.ref == "main"' "$pr"
  jq -r '.[0].body // ""' "$pr" >/tmp/manoir-description.md
  while IFS=$'\t' read -r etat message; do
    if [ "$etat" = ok ]; then ok "$message"; else ko "$message"; fi
  done < <(python3 "$MANOIR/exercices/$id/description.py" /tmp/manoir-description.md)
else
  ko "Ouvre une pull request depuis exercice/description-pr vers main, puis relance le combat (Actions → Manoir → Run workflow, branche exercice/description-pr)"
fi
tests_caches com.nocturne.lanterne.manoir.AnnulationManoirTest
