# shellcheck shell=bash disable=SC2016
# Combat 13.2 du manoir : un déploiement par tag, vérifié, qui revient tout seul à la version précédente.
trouve=""
for f in .github/workflows/*.y*ml; do
  [ -f "$f" ] || continue
  j=$(yaml_json "$f" 2>/dev/null) || continue
  if jq_vrai "$j" '.on.push.tags // [] | if type == "string" then [.] else . end | any(startswith("v"))'; then
    trouve=$f
    wf=$j
    break
  fi
done
if [ -z "$trouve" ]; then
  ko "Crée un workflow « Déployer » déclenché sur les tags v* (on: push: tags: ['v*'])"
else
  ok "Le workflow $trouve se déclenche sur un tag v*"
  texte=$(grep -v '^\s*#' "$trouve")
  constructions=$(grep -cE 'docker (buildx )?build|docker/build-push-action' <<<"$texte" || true)
  verifie "Il construit l'image une seule fois (ici : $constructions)" test "$constructions" = 1
  verifie "Il publie l'image avec le nom du tag (github.ref_name)" \
    grep -qE "ref_name|GITHUB_REF_NAME|docker/metadata-action|type=(semver|ref,event=tag)" <<<"$texte"
  verifie "Il déploie avec deploy/deployer.sh" grep -q "deploy/deployer.sh" <<<"$texte"
  verifie "Il passe l'image du tag précédent à deploy/deployer.sh (retour arrière)" \
    grep -qE "git describe|git tag|precedent|previous" <<<"$texte"
  verifie "Aucun secret écrit en dur : ils viennent des secrets GitHub" bash -c '
    ! grep -qE "ghp_|github_pat_|gho_|sk-[A-Za-z0-9]|(-p|--password)[ =][^\$ ]|password: *[^\$ {]" <<<"$1" &&
    { ! grep -q "docker login" <<<"$1" || grep -qE "secrets\.|GITHUB_TOKEN|--password-stdin" <<<"$1"; }' _ "$texte"
fi

if [ -f docs/runbook.md ]; then
  verifie "docs/runbook.md a une section « Mise en production »" grep -qiE "^#+ .*mise en prod" docs/runbook.md
  verifie "docs/runbook.md a une section « Vérifications »" grep -qiE "^#+ .*v(e|é)rification" docs/runbook.md
  verifie "docs/runbook.md a une section « Retour arrière »" grep -qiE "^#+ .*(retour arri(e|è)re|rollback)" docs/runbook.md
else
  ko "Écris docs/runbook.md (mise en production, vérifications, retour arrière)"
fi

if [ ! -f deploy/deployer.sh ]; then
  ko "deploy/deployer.sh manque"
  return 0
fi
verifie "deploy/deployer.sh passe une commande de test (POST /api/commandes)" grep -q "/api/commandes" deploy/deployer.sh
cp "$MANOIR/exercices/$id/plateforme.sh" deploy/plateforme.sh
chmod +x deploy/*.sh
echo "— La plateforme de test : ton image, puis une version qui ne démarre pas."
if ! docker build -q -t manoir/lanterne:bonne . >/tmp/manoir-build.log 2>&1; then
  tail -20 /tmp/manoir-build.log
  ko "L'image de ta Lanterne se construit (docker build)"
  return 0
fi
printf 'FROM manoir/lanterne:bonne\nENTRYPOINT ["sh", "-c", "echo Cette version ne démarre pas; exit 1"]\n' |
  docker build -q -t manoir/lanterne:cassee - >/dev/null
sante() { curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; }

timeout 200 bash deploy/deployer.sh manoir/lanterne:bonne >/tmp/manoir-deploy1.log 2>&1 && statut=0 || statut=$?
tail -5 /tmp/manoir-deploy1.log
verifie "Une bonne version : deploy/deployer.sh réussit, et la santé répond UP" bash -c "[ $statut = 0 ] && $(declare -f sante); sante"

timeout 300 bash deploy/deployer.sh manoir/lanterne:cassee manoir/lanterne:bonne >/tmp/manoir-deploy2.log 2>&1 && statut=0 || statut=$?
tail -5 /tmp/manoir-deploy2.log
verifie "Une version qui ne démarre pas : deploy/deployer.sh échoue" test "$statut" != 0 -a "$statut" != 124
en_prod=$(deploy/plateforme.sh image)
verifie "… et remet la version précédente en production (en prod : $en_prod)" bash -c \
  "[ '$en_prod' = manoir/lanterne:bonne ] && $(declare -f sante); for _ in \$(seq 1 30); do sante && exit 0; sleep 2; done; exit 1"
docker rm -f lanterne-prod >/dev/null 2>&1 || true
