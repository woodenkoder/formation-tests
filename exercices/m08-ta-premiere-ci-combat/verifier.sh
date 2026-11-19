# shellcheck shell=bash disable=SC2016
# Combat 08.3 du manoir : une CI qui build et teste à chaque push.
f=.github/workflows/ci.yml
if [ ! -f "$f" ] || ! ci=$(yaml_json "$f" 2>/dev/null); then
  ko "Crée .github/workflows/ci.yml (YAML valide)"
  return 0
fi
ok ".github/workflows/ci.yml existe"
verifie "Il tourne sur chaque push vers main" jq_vrai "$ci" \
  '.on.push.branches // [] | if type == "string" then [.] else . end | index("main") != null'
verifie "Il tourne sur chaque pull request" jq_vrai "$ci" '.on | has("pull_request")'
verifie "Il installe Java 21 avec le cache Maven (actions/setup-java)" jq_vrai "$ci" \
  '[.jobs[].steps[]? | select((.uses // "") | startswith("actions/setup-java@"))
    | select((.with["java-version"] | tostring) == "21" and .with.cache == "maven")] | length > 0'
verifie "Il lance ./mvnw verify" jq_vrai "$ci" '[.jobs[].steps[]?.run // "" | select(test("\\./mvnw[^\\n]*verify"))] | length > 0'
verifie "Les permissions sont limitées à contents: read" jq_vrai "$ci" \
  '(.permissions == {"contents": "read"}) or ((.permissions == null or .permissions == {})
    and all(.jobs[]; .permissions == {"contents": "read"}))'
verifie "Chaque job a un timeout-minutes" jq_vrai "$ci" 'all(.jobs[]; ."timeout-minutes" != null)'
verifie "Les builds dépassés sont annulés (concurrency, cancel-in-progress: true)" jq_vrai "$ci" \
  '(.concurrency["cancel-in-progress"] == true) or all(.jobs[]; .concurrency["cancel-in-progress"] == true)'
verifie "Chaque action est à une version précise (pas @main ni @master)" jq_vrai "$ci" \
  'all(.jobs[].steps[]?.uses // empty; test("@") and (test("@(main|master|HEAD|latest)$") | not))'
