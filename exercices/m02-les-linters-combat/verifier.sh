# shellcheck shell=bash disable=SC2016
# Combat 02.2 du manoir : le code respecte le format et les règles de l'équipe, sans toucher aux règles.
empreinte() { tr -d ' \t\r\n' <"$1" | sha256sum | cut -d' ' -f1; }
verifie "La configuration de Checkstyle est inchangée (checkstyle.xml)" test "$(empreinte checkstyle.xml 2>/dev/null)" = "6239d335901af0d811e3256a608a46849ed45da8741d899a375977be6d2c8df9"
verifie "La configuration de Spotless et le pom.xml sont inchangés" test "$(empreinte pom.xml 2>/dev/null)" = "ac1a4eb8c5560ab038bddb1595d5f646b605c601fa5b09772bdd2b8148d4951f"
verifie "Le workflow de CI est inchangé (.github/workflows/ci.yml)" test "$(empreinte .github/workflows/ci.yml 2>/dev/null)" = "c7566ad3f748fd5efb3e6aaa7212804c81aeefa692c9a4aad4f6d4942605768a"
verifie "Aucune alerte réduite au silence (@SuppressWarnings, NOSONAR, CHECKSTYLE:OFF)" \
  bash -c '! grep -rqE "@SuppressWarnings|NOSONAR|CHECKSTYLE[:.]?(OFF|SUPPRESS)" src'
if mvn -B -q -ntp spotless:check >/tmp/manoir-spotless.log 2>&1; then
  ok "Le format est propre (spotless:check)"
else
  grep -E "^\[ERROR\] +src/" /tmp/manoir-spotless.log | head -10
  ko "Le format n'est pas celui de l'équipe : lance ./mvnw spotless:apply"
fi
if mvn -B -ntp checkstyle:check >/tmp/manoir-checkstyle.log 2>&1; then
  ok "Les règles de l'équipe sont respectées (checkstyle:check)"
else
  grep -E "^\[(WARN|WARNING|ERROR)\] .*\.java" /tmp/manoir-checkstyle.log | sed 's|.*/src/|src/|' | head -15
  ko "Il reste des alertes Checkstyle (la liste est juste au-dessus) : corrige le code, pas la règle"
fi
if mvn -B -q -ntp test >/tmp/manoir-mvn.log 2>&1; then
  ok "Les tests passent toujours"
else
  grep -E "ERROR|FAIL" /tmp/manoir-mvn.log | head -10
  ko "Les tests doivent passer (as-tu renommé une méthode sans mettre à jour son test ?)"
fi
