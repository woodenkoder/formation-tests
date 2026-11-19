# shellcheck shell=bash disable=SC2016
# Combat 10.1 du manoir : un AGENTS.md utile à un agent (et à un humain).
if [ ! -f AGENTS.md ]; then
  ko "Crée AGENTS.md à la racine de ta Lanterne"
  return 0
fi
ok "AGENTS.md existe à la racine"
lignes=$(wc -l <AGENTS.md)
verifie "Il fait moins de 150 lignes ($lignes) : un agent lit tout, à chaque fois" test "$lignes" -lt 150
verifie "Il donne la commande de build et de tests (./mvnw verify)" grep -q "\./mvnw verify" AGENTS.md
verifie "Il donne la commande de formatage" grep -qiE "format|spotless" AGENTS.md
verifie "Il décrit l'architecture en couches et le rangement par domaine" \
  bash -c 'grep -qiE "couche|controller|service|repository" AGENTS.md && grep -qiE "domaine|module|package" AGENTS.md'
verifie "Il donne les conventions (langue, tests, montants)" \
  bash -c 'grep -qiE "convention" AGENTS.md && grep -qiE "BigDecimal|montant" AGENTS.md && grep -qiE "test" AGENTS.md'
# La section d'interdits : du titre qui parle d'interdits jusqu'au titre suivant, au moins 4 éléments de liste.
interdits=$(awk '/^#+ / { t = tolower($0); dedans = (t ~ /interdi|jamais|ne pas/); next }
  dedans && /^[[:space:]]*([-*+]|[0-9]+\.)[[:space:]]+/ { n++ }
  END { print n + 0 }' AGENTS.md)
verifie "Une section d'interdits avec au moins quatre règles ($interdits trouvées)" test "$interdits" -ge 4
verifie "Les interdits couvrent les migrations, les dépendances, les secrets et le push sur main" \
  bash -c 'grep -qiE "migration" AGENTS.md && grep -qiE "pendance" AGENTS.md && grep -qiE "secret" AGENTS.md \
    && grep -qiE "push|main" AGENTS.md'
