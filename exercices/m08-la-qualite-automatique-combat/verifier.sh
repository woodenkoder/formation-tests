# shellcheck shell=bash disable=SC2016
# Combat 08.4 du manoir : la qualité automatique (analyse des dépendances, secrets, Dependabot).
texte=$(cat .github/workflows/*.y*ml 2>/dev/null | grep -v '^\s*#' || true)
verifie "La CI analyse les dépendances (OWASP Dependency-Check ou équivalent)" \
  grep -qiE "dependency-check|dependency-review-action|osv-scanner|trivy|snyk" <<<"$texte"
verifie "L'analyse échoue sur une CVE de score 7 ou plus (failBuildOnCVSS=7 / fail-on-severity: high…)" \
  grep -qiE "(failBuildOnCVSS|failOnCVSS)[^0-9]{0,10}7([^0-9]|$)|fail-on-severity: *['\"]?(high|critical)|severity[^\n]*(HIGH|CRITICAL)" <<<"$texte"
verifie "gitleaks tourne dans la CI" grep -qi "gitleaks" <<<"$texte"
verifie "Aucune règle désactivée : pas de continue-on-error ni de suppression de CVE" bash -c '
  ! cat .github/workflows/*.y*ml 2>/dev/null | grep -qiE "continue-on-error: *true" \
  && ! grep -rqiE "<suppress|CVE-[0-9]{4}-[0-9]+" --include="*suppress*" --include="*.xml" . --exclude=pom.xml 2>/dev/null'
if [ -f .github/dependabot.yml ] && dep=$(yaml_json .github/dependabot.yml 2>/dev/null); then
  for eco in maven github-actions; do
    verifie "Dependabot met à jour $eco une fois par semaine" jq_vrai "$dep" \
      "[.updates[]? | select(.\"package-ecosystem\" == \"$eco\" and .schedule.interval == \"weekly\")] | length > 0"
  done
else
  ko "Ajoute .github/dependabot.yml (maven et github-actions, chaque semaine)"
fi
version=$(mvn -B -q dependency:list -DincludeArtifactIds=commons-text -DoutputFile=/tmp/manoir-deps.txt >/dev/null 2>&1 \
  && grep -oE "commons-text:jar:[0-9.]+" /tmp/manoir-deps.txt | cut -d: -f3 | head -1)
verifie "La dépendance vulnérable est mise à jour (commons-text ${version:-introuvable}, 1.10.0 au moins)" \
  python3 -c "import sys; v=tuple(int(x) for x in '${version:-0}'.split('.')); sys.exit(0 if v >= (1, 10, 0) else 1)"
verifie "Le projet compile toujours (mvn package)" mvn -B -q -DskipTests package
