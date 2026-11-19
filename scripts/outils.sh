#!/usr/bin/env bash
# Les outils communs des épreuves vérifiées par un script (types docker et ci). Sourcé par chaque verifier.sh,
# lancé depuis la Lanterne de l'élève (dossier « eleve »). $MANOIR = la racine de formation-tests.

REUSSIES=0
ECHECS=0
LIGNES=$(mktemp)

ok() { echo "✔ $1"; REUSSIES=$((REUSSIES + 1)); printf 'ok\t%s\n' "$1" >>"$LIGNES"; }
ko() { echo "::error::✖ $1"; ECHECS=$((ECHECS + 1)); printf 'ko\t%s\n' "$1" >>"$LIGNES"; }

# verifie "ce qu'on vérifie" commande… : ✔ si la commande réussit, ✖ sinon.
verifie() {
  local message="$1"
  shift
  if "$@" >/dev/null 2>&1; then ok "$message"; else ko "$message"; fi
}

# Un fichier YAML en JSON (pour jq). La clé « on: » des workflows reste « on ».
yaml_json() { python3 "$MANOIR/scripts/yaml2json.py" "$1"; }

# jq_vrai <json> <filtre> : réussit si le filtre jq renvoie true.
jq_vrai() { jq -e "$2" <<<"$1" >/dev/null; }

# Tous les workflows de l'élève (sauf celui du manoir), fusionnés en un tableau JSON.
workflows_json() {
  local f
  for f in .github/workflows/*.y*ml; do
    [ -f "$f" ] && [ "$(basename "$f")" != manoir.yml ] && yaml_json "$f"
  done | jq -s '.'
}

# attendre_http <url> <secondes> : attend une réponse HTTP 200.
attendre_http() {
  local url="$1" fin=$((SECONDS + $2))
  until curl -fsS "$url" >/dev/null 2>&1; do
    [ "$SECONDS" -ge "$fin" ] && return 1
    sleep 2
  done
}

bilan() {
  # shellcheck disable=SC2154 # id vient de script.sh
  python3 "$MANOIR/scripts/bilan.py" "$MANOIR/exercices/$id/manifest.json" --lignes "$LIGNES" || true
  echo "$REUSSIES vérification(s) réussie(s), $ECHECS en échec."
  if [ "$ECHECS" -gt 0 ]; then
    echo "::error::Des vérifications du manoir échouent : lis les ✖ ci-dessus, corrige, puis pousse à nouveau."
    exit 1
  fi
  echo "Toutes les vérifications du manoir passent."
}
