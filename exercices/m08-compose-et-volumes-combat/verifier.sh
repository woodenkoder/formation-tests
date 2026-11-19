# shellcheck shell=bash disable=SC2016
# Combat 08.2 du manoir : Compose avec l'API, PostgreSQL 17, un volume nommé et un healthcheck.
fichier=""
for f in compose.yaml compose.yml docker-compose.yml docker-compose.yaml; do [ -f "$f" ] && fichier=$f && break; done
if [ -z "$fichier" ] || ! config=$(docker compose -f "$fichier" config --format json 2>/tmp/manoir-compose.log); then
  cat /tmp/manoir-compose.log 2>/dev/null
  ko "Ajoute un compose.yaml valide à la racine de ta Lanterne"
  return 0
fi
ok "compose.yaml est valide"
base=$(jq -r '.services | to_entries[] | select((.value.image // "") | startswith("postgres:17")) | .key' <<<"$config" | head -1)
api=$(jq -r '.services | to_entries[] | select(.value.build != null) | .key' <<<"$config" | head -1)
verifie "Un service PostgreSQL 17 (image postgres:17)" test -n "$base"
verifie "Un service pour l'API, construit depuis ton Dockerfile (build:)" test -n "$api"
[ -n "$base" ] && [ -n "$api" ] || return 0
verifie "Les données de PostgreSQL sont dans un volume nommé (/var/lib/postgresql/data)" jq_vrai "$config" \
  "[.services[\"$base\"].volumes[]? | select(.type == \"volume\" and .target == \"/var/lib/postgresql/data\" and (.source // \"\") != \"\")] | length > 0"
verifie "PostgreSQL a un healthcheck" jq_vrai "$config" ".services[\"$base\"].healthcheck.test != null"
verifie "L'API attend que la base soit prête (depends_on: condition: service_healthy)" jq_vrai "$config" \
  ".services[\"$api\"].depends_on[\"$base\"].condition == \"service_healthy\""
verifie "L'API se connecte à la base par le nom du service ($base), pas localhost" jq_vrai "$config" \
  "[.services[\"$api\"].environment // {} | to_entries[] | .value // \"\" | select(test(\"jdbc:postgresql://\"))] as \$u
   | (\$u | length > 0) and all(\$u[]; test(\"//${base}[:/]\"))"

if docker compose -f "$fichier" up -d --build --wait --wait-timeout 240 >/tmp/manoir-up.log 2>&1 \
  || attendre_http http://localhost:8080/actuator/health 60; then
  if attendre_http http://localhost:8080/actuator/health 120 && curl -fsS http://localhost:8080/actuator/health | grep -q '"UP"'; then
    ok "docker compose up -d démarre la base et l'API : /actuator/health répond UP"
    id=$(curl -fsS -X POST http://localhost:8080/api/commandes -H 'Content-Type: application/json' \
      -d '{"client": "manoir@exemple.test", "montant": 12.50}' | jq -r '.id')
    docker compose -f "$fichier" down >/dev/null 2>&1
    docker compose -f "$fichier" up -d --wait --wait-timeout 240 >/dev/null 2>&1 || true
    attendre_http http://localhost:8080/actuator/health 120 || true
    verifie "Une commande créée avant docker compose down existe toujours après (volume)" \
      curl -fsS "http://localhost:8080/api/commandes/$id"
  else
    ko "L'API doit répondre UP sur http://localhost:8080/actuator/health"
    docker compose -f "$fichier" logs --tail 30 2>&1
  fi
else
  tail -30 /tmp/manoir-up.log
  ko "docker compose up -d doit démarrer la base et l'API : lis l'erreur ci-dessus"
fi
docker compose -f "$fichier" down -v >/dev/null 2>&1 || true
