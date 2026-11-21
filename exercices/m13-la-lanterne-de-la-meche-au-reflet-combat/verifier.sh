# shellcheck shell=bash disable=SC2016
# Combat 13.1 du manoir : les sept jalons de ta Lanterne, sur main, testés comme un client ou un attaquant le ferait
# (le contrat : docs/CONTRAT.md de ta Lanterne).

# 3 · Le verre : tes tests, et ce qu'ils couvrent du service des commandes (JaCoCo, ajouté par le manoir).
echo "— 3 · Le verre : ./mvnw verify, avec la couverture des tests."
JACOCO=org.jacoco:jacoco-maven-plugin:0.8.12
mvn -B -ntp -q "$JACOCO:prepare-agent" verify "$JACOCO:report" >/tmp/manoir-verify.log 2>&1 && statut=0 || statut=$?
grep -E "Tests run:|FAIL|ERROR\]" /tmp/manoir-verify.log | tail -8
verifie "3 · Le verre : tes tests passent (./mvnw verify)" test "$statut" = 0
couverture=$(python3 - <<'PY'
import csv, os
f = "target/site/jacoco/jacoco.csv"
manque = couvert = 0
if os.path.exists(f):
    for l in csv.DictReader(open(f)):
        if "commande" in l["PACKAGE"].lower() + l["CLASS"].lower() and l["CLASS"].split(".")[0].endswith("Service"):
            manque += int(l["LINE_MISSED"]); couvert += int(l["LINE_COVERED"])
print(-1 if manque + couvert == 0 else round(100 * couvert / (manque + couvert)))
PY
)
if [ "$couverture" -lt 0 ]; then
  ko "3 · Le verre : aucune classe *Service des commandes trouvée dans la couverture (ex. CommandeService)"
else
  verifie "3 · Le verre : tes tests couvrent au moins 60 % du service des commandes ($couverture %)" test "$couverture" -ge 60
fi

# 4 · Le cadenas : aucun secret dans le code, ni dans l'historique de main.
FORMATS='ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{50,}|AKIA[0-9A-Z]{16}|sk-(proj-)?[A-Za-z0-9_-]{32,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|xox[baprs]-[0-9A-Za-z-]{10,}'
fuites=$(git log -p HEAD 2>/dev/null | grep -E '^\+' | grep -cE "$FORMATS" || true)
verifie "4 · Le cadenas : aucun jeton ni clé privée dans l'historique de main" test "$fuites" = 0
en_dur=$(grep -rnEi --include='*.properties' --include='*.yml' --include='*.yaml' \
  '^[[:space:]]*[A-Za-z0-9_.-]*(password|secret|api-key|apikey|token)[A-Za-z0-9_.-]*[[:space:]]*[=:][[:space:]]*[^[:space:]$"'"'"'{]' \
  src/main/resources 2>/dev/null | grep -viE '[=:][[:space:]]*(true|false)$' | head -3 || true)
[ -n "$en_dur" ] && echo "$en_dur"
verifie "4 · Le cadenas : aucun mot de passe ni secret écrit en dur dans la configuration" test -z "$en_dur"

# 5 · La poignée : l'image, et une CI.
ci=""
for f in .github/workflows/*.y*ml; do
  [ -f "$f" ] && [ "$(basename "$f")" != manoir.yml ] || continue
  j=$(yaml_json "$f" 2>/dev/null) || continue
  if jq_vrai "$j" '.on | (type == "object" and (has("push") or has("pull_request"))) or (type == "array" and (index("push") != null or index("pull_request") != null)) or . == "push"' \
    && grep -qE '\./mvnw[^#]*(verify|test)' "$f"; then
    ci=$f
  fi
done
verifie "5 · La poignée : une CI lance ./mvnw verify (ou test) sur chaque push ou PR" test -n "$ci"
image="${MANOIR_IMAGE_LOCALE:-}"
if [ -z "$image" ]; then
  if [ -f Dockerfile ] && docker build -q -t manoir/lanterne-eleve . >/tmp/manoir-build.log 2>&1; then
    image=manoir/lanterne-eleve
  else
    tail -15 /tmp/manoir-build.log 2>/dev/null
  fi
fi
verifie "5 · La poignée : l'image de ta Lanterne se construit (Dockerfile)" test -n "$image"
if [ -z "$image" ]; then
  ko "1, 2, 4, 6, 7 : sans image, le manoir ne peut pas lancer ta Lanterne"
  return 0
fi

# 1, 2, 4, 6, 7 : ta Lanterne, lancée avec PostgreSQL, RabbitMQ, un faux prestataire de paiement et une fausse IA.
while IFS=$'\t' read -r etat message; do
  if [ "$etat" = ok ]; then ok "$message"; else ko "$message"; fi
done < <(python3 "$MANOIR/banc/banc.py" jalons "$image")
