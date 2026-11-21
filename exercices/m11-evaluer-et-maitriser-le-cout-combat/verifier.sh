# shellcheck shell=bash
# Combat 11.3 du manoir : un jeu d'évaluation de l'assistant, avec un score, un seuil, et hors de la CI de chaque PR.
tests_caches com.nocturne.lanterne.manoir.EvaluationManoirTest

tague=$(grep -rlE '@Tag\("evaluation"\)' src/test/java 2>/dev/null | head -1 || true)
verifie "Un test porte l'étiquette @Tag(\"evaluation\")" test -n "$tague"
if [ -n "$tague" ]; then
  classe=$(basename "$tague" .java)
  verifie "Ce test pose les questions avec Evaluation.evaluer(...)" grep -q 'Evaluation.evaluer' "$tague"
  verifie "Il échoue quand le score n'est pas suffisant (resultat.suffisant())" grep -q 'suffisant()' "$tague"
  verifie "Il affiche le score et les questions ratées" grep -qE 'ratees\(\)' "$tague"
  rm -rf target/surefire-reports
  mvn -B -q -ntp test >/tmp/manoir-ci.log 2>&1 && statut=0 || statut=$?
  verifie "Les tests de chaque PR passent (mvn test)" test "$statut" = 0
  verifie "L'évaluation ne tourne pas dans les tests de chaque PR (étiquette exclue)" \
    test -z "$(find target/surefire-reports -name "TEST-*$classe.xml" 2>/dev/null)"
fi
