#!/usr/bin/env bash
# Lance les tests cachés d'une épreuve Java dans la Lanterne de l'élève (dossier « eleve »), avec le Maven de la
# machine GitHub (jamais le mvnw de l'élève), puis vérifie dans les rapports que NOS tests ont vraiment tourné.
set -euo pipefail

id="$1"
manoir="$(cd "$(dirname "$0")/.." && pwd)"
exercice="$manoir/exercices/$id"
manifest="$exercice/manifest.json"
[ -f "$manifest" ] || { echo "::error::Épreuve inconnue : $id"; exit 1; }

classes=$(jq -r '.classes | join(",")' "$manifest")
minimum=$(jq -r '.minimumTests' "$manifest")

cd eleve
[ -f pom.xml ] || { echo "::error::Pas de pom.xml à la racine de ta Lanterne."; exit 1; }
# Nos tests remplacent tout fichier du même nom (le dossier des tests peut ne pas exister : Git ignore les dossiers vides).
mkdir -p src/test/java
cp -R "$exercice/tests/." src/test/java/
rm -rf target/surefire-reports

status=0
mvn -B -ntp test \
  -Dtest="$classes" \
  -Dsurefire.failIfNoSpecifiedTests=true \
  -DskipTests=false -Dmaven.test.skip=false -DfailIfNoTests=true || status=$?

total=0
for classe in $(jq -r '.classes[]' "$manifest"); do
  rapport="target/surefire-reports/TEST-$classe.xml"
  if [ ! -f "$rapport" ]; then
    echo "::error::Les tests du manoir ($classe) n'ont pas tourné."
    exit 1
  fi
  ligne=$(grep -m1 -o '<testsuite [^>]*>' "$rapport")
  tests=$(sed -E 's/.* tests="([0-9]+)".*/\1/' <<<"$ligne")
  echecs=$(sed -E 's/.* failures="([0-9]+)".*/\1/' <<<"$ligne")
  erreurs=$(sed -E 's/.* errors="([0-9]+)".*/\1/' <<<"$ligne")
  ignores=$(sed -E 's/.* skipped="([0-9]+)".*/\1/' <<<"$ligne")
  echo "$classe : $tests tests, $echecs échecs, $erreurs erreurs, $ignores ignorés"
  if [ "$echecs" != 0 ] || [ "$erreurs" != 0 ] || [ "$ignores" != 0 ]; then status=1; fi
  total=$((total + tests))
done

python3 "$manoir/scripts/bilan.py" "$manifest" --java target/surefire-reports/TEST-*.xml || true

if [ "$total" -lt "$minimum" ]; then
  echo "::error::Seulement $total tests du manoir ont tourné (au moins $minimum attendus)."
  exit 1
fi
if [ "$status" != 0 ]; then
  echo "::error::Des tests du manoir échouent : lis les messages ci-dessus, corrige, puis pousse à nouveau."
  exit 1
fi
echo "Tous les tests du manoir passent."
