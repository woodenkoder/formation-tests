# shellcheck shell=bash disable=SC2016
# Combat 00.1 du manoir : ton dépôt, ton prénom, ton premier workflow vert.
verifie "Ta Lanterne vient du modèle de la formation (pom.xml de la Lanterne, workflow Manoir)" \
  bash -c 'grep -q "<artifactId>lanterne</artifactId>" pom.xml && [ -f .github/workflows/manoir.yml ]'
prenom=$(grep -E '^nocturne\.dev\.prenom=' src/main/resources/application.properties 2>/dev/null | head -1 | cut -d= -f2- | tr -d '[:space:]' || true)
verifie "Ton prénom remplace A_REMPLIR (nocturne.dev.prenom, dans application.properties)" \
  bash -c '[ -n "$1" ] && [ "$1" != A_REMPLIR ]' _ "$prenom"
if mvn -B -q -ntp verify >/tmp/manoir-mvn.log 2>&1; then
  ok "Le build Maven passe (mvn verify)"
else
  grep -E "ERROR" /tmp/manoir-mvn.log | head -20
  ko "Le build Maven doit passer : lance ./mvnw verify sur ta machine, et lis l'erreur ci-dessus"
fi
if [ -n "$prenom" ] && [ "$prenom" != A_REMPLIR ]; then
  echo "🕯 Bienvenue au manoir, $prenom. La porte du Hall s'est refermée derrière toi."
fi
