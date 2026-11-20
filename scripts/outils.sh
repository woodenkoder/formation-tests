#!/usr/bin/env bash
# shellcheck disable=SC2154 # id (l'épreuve) vient de script.sh, qui source ce fichier
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

# tests_caches Classe… : copie les tests cachés de l'épreuve dans la Lanterne (à la version déjà extraite), les
# lance avec Maven, et compte un ✔ ou un ✖ par test. Les fichiers copiés sont retirés ensuite.
tests_caches() {
  local classes copies
  classes=$(IFS=,; echo "$*")
  copies=$(cd "$MANOIR/exercices/$id/tests" && find . -type f)
  mkdir -p src/test/java
  cp -R "$MANOIR/exercices/$id/tests/." src/test/java/
  rm -rf target/surefire-reports
  mvn -B -q -ntp test -Dtest="$classes" -Dsurefire.failIfNoSpecifiedTests=false >/tmp/manoir-mvn.log 2>&1
  if compgen -G "target/surefire-reports/TEST-*.xml" >/dev/null; then
    while IFS=$'\t' read -r etat message; do
      if [ "$etat" = ok ]; then ok "$message"; else ko "$message"; fi
    done < <(python3 "$MANOIR/scripts/cas.py" target/surefire-reports/TEST-*.xml)
  else
    grep -E "ERROR|error:" /tmp/manoir-mvn.log | head -15
    ko "Les tests ne compilent pas ou ne tournent pas sur cette version (lis l'erreur ci-dessus)"
  fi
  (cd src/test/java && echo "$copies" | xargs -r rm -f)
}

bilan() {
  python3 "$MANOIR/scripts/bilan.py" "$MANOIR/exercices/$id/manifest.json" --lignes "$LIGNES" || true
  echo "$REUSSIES vérification(s) réussie(s), $ECHECS en échec."
  if [ "$ECHECS" -gt 0 ]; then
    echo "::error::Des vérifications du manoir échouent : lis les ✖ ci-dessus, corrige, puis pousse à nouveau."
    exit 1
  fi
  echo "Toutes les vérifications du manoir passent."
}
