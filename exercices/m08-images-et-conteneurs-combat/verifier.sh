# shellcheck shell=bash disable=SC2016
# Combat 08.1 du manoir : un Dockerfile multi-étapes, léger, sans root.
IMAGE=manoir-lanterne

verifie "Un .dockerignore exclut target/, .git/ et .env" bash -c '
  [ -f .dockerignore ] && grep -qE "^/?target/?$" .dockerignore && grep -qE "^/?\.git/?$" .dockerignore \
    && grep -qE "^/?\.env$" .dockerignore'
verifie "Le Dockerfile a deux étapes : construction en eclipse-temurin:21-jdk, exécution en eclipse-temurin:21-jre" \
  bash -c '[ "$(grep -ciE "^FROM " Dockerfile)" -ge 2 ] && grep -qiE "^FROM +eclipse-temurin:21-jdk" Dockerfile \
    && grep -qiE "^FROM +eclipse-temurin:21-jre" Dockerfile'

if docker build -t "$IMAGE" . >/tmp/manoir-build.log 2>&1; then
  ok "L'image se construit (docker build)"
  taille=$(docker image inspect "$IMAGE" --format '{{.Size}}')
  verifie "L'image fait moins de 350 Mo ($((taille / 1000000)) Mo)" test "$taille" -lt 350000000
  reste=$(docker run --rm --entrypoint sh "$IMAGE" -c \
    'command -v javac mvn; find / -xdev \( -name pom.xml -o -name mvnw -o -name "*.java" -o -name ".m2" \) 2>/dev/null | head -5')
  verifie "L'image finale ne contient ni JDK (javac), ni Maven, ni les sources${reste:+ (trouvé : $(echo "$reste" | tr '\n' ' '))}" \
    test -z "$reste"
  docker rm -f "$IMAGE" >/dev/null 2>&1
  docker run -d --name "$IMAGE" -p 18080:8080 "$IMAGE" >/dev/null
  if attendre_http http://localhost:18080/actuator/health 120 \
    && curl -fsS http://localhost:18080/actuator/health | grep -q '"UP"'; then
    ok "L'application démarre et /actuator/health répond UP"
  else
    ko "L'application doit démarrer et répondre UP sur /actuator/health (logs : docker logs)"
    docker logs "$IMAGE" 2>&1 | tail -20
  fi
  uid=$(docker exec "$IMAGE" sh -c 'stat -c %u /proc/1' 2>/dev/null || echo 0)
  verifie "Le processus Java ne tourne pas en root (uid $uid)" test "$uid" != 0
  docker rm -f "$IMAGE" >/dev/null 2>&1
else
  tail -30 /tmp/manoir-build.log
  ko "L'image doit se construire (docker build) : lis l'erreur ci-dessus"
fi
