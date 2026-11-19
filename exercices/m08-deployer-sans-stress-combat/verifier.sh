# shellcheck shell=bash disable=SC2016
# Combat 08.5 du manoir : un déploiement automatique sur tag, vers ghcr.io.
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
  ko "Crée un workflow déclenché sur les tags v* (on: push: tags: ['v*'])"
  return 0
fi
ok "Le workflow $trouve se déclenche sur les tags v*"
texte=$(grep -v '^\s*#' "$trouve")
verifie "Il ne se déclenche pas sur les autres pushs (ni branches, ni pull_request)" jq_vrai "$wf" \
  '(.on.push.branches == null) and (.on | has("pull_request") | not)'
verifie "Il lance ./mvnw verify avant de publier" grep -qE "\./mvnw[^\n]*verify" <<<"$texte"
verifie "Il publie l'image sur ghcr.io" grep -q "ghcr.io" <<<"$texte"
verifie "L'image porte le nom du tag (github.ref_name ou docker/metadata-action)" \
  grep -qE "ref_name|GITHUB_REF_NAME|docker/metadata-action|type=(semver|ref,event=tag)" <<<"$texte"
verifie "Le job tourne dans l'environnement production" jq_vrai "$wf" \
  'any(.jobs[]; (.environment == "production") or (.environment.name? == "production"))'
verifie "Permissions minimales : contents: read et packages: write, rien d'autre" jq_vrai "$wf" \
  '(.permissions == {"contents": "read", "packages": "write"}) or ((.permissions == null or .permissions == {})
    and all(.jobs[]; .permissions == {"contents": "read", "packages": "write"}))'
if [ -f DEPLOIEMENT.md ]; then
  verifie "DEPLOIEMENT.md décrit le retour arrière" grep -qiE "retour arri(e|è)re|rollback" DEPLOIEMENT.md
  verifie "DEPLOIEMENT.md donne la commande (dans un bloc de code)" grep -q '```' DEPLOIEMENT.md
  verifie "DEPLOIEMENT.md dit quoi surveiller après un déploiement" grep -qiE "surveill|logs|m(e|é)triques|health" DEPLOIEMENT.md
else
  ko "Écris DEPLOIEMENT.md (retour arrière, ce qu'il faut surveiller)"
fi
