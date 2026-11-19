# shellcheck shell=bash disable=SC2016
# Combat 08.6 du manoir : un script de diagnostic, sûr, vérifié par ShellCheck, lancé par la CI.
s=scripts/diagnostic.sh
if [ ! -f "$s" ]; then
  ko "Écris scripts/diagnostic.sh"
  return 0
fi
ok "scripts/diagnostic.sh existe"
verifie "Il commence par set -euo pipefail" bash -c "grep -v '^#' $s | grep -m1 -v '^\s*$' | grep -q 'set -euo pipefail'"
if shellcheck "$s" >/tmp/manoir-shellcheck.log 2>&1; then
  ok "ShellCheck ne signale rien"
else
  head -30 /tmp/manoir-shellcheck.log
  ko "ShellCheck doit passer sans avertissement (lis ses remarques ci-dessus)"
fi
verifie "Aucune commande destructive (rm, kill, prune, reboot…)" bash -c \
  "! grep -v '^\s*#' $s | grep -qwE 'rm|kill|pkill|killall|prune|reboot|shutdown|mkfs|dd|truncate|shred'"

# Le terrain d'essai : une fausse API (/actuator/health), de faux logs, et un faux df (disque rempli à N %).
essai=$(mktemp -d)
cat >"$essai/api.py" <<'PY'
from http.server import BaseHTTPRequestHandler, HTTPServer
class H(BaseHTTPRequestHandler):
    def do_GET(self):
        corps = b'{"status":"UP"}' if self.path.startswith("/actuator/health") else b"{}"
        self.send_response(200 if self.path.startswith("/actuator/health") else 404)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(corps)
    def log_message(self, *a):
        pass
HTTPServer(("127.0.0.1", 18099), H).serve_forever()
PY
python3 "$essai/api.py" &
api=$!
for i in $(seq -w 1 30); do printf 'INFO tout va bien %s\nERROR erreur-%s\n' "$i" "$i"; done >"$essai/app.log"
faux_df() {
  mkdir -p "$essai/bin-$1"
  cat >"$essai/bin-$1/df" <<SH
#!/usr/bin/env bash
if [[ "\$*" == *--output* ]]; then echo "Use%"; echo " $1%"; exit 0; fi
echo "Filesystem     1024-blocks      Used Available Capacity Mounted on"
echo "/dev/manoir      100000000  ${1}000000  $((100 - $1))000000      $1% /"
SH
  chmod +x "$essai/bin-$1/df"
}
faux_df 85
faux_df 40
attendre_http http://127.0.0.1:18099/actuator/health 10 || true
plein=$(PATH="$essai/bin-85:$PATH" timeout 60 bash "$s" http://127.0.0.1:18099 "$essai/app.log" 2>&1 || true)
leger=$(PATH="$essai/bin-40:$PATH" timeout 60 bash "$s" http://127.0.0.1:18099 "$essai/app.log" 2>&1 || true)
kill "$api" 2>/dev/null || true
verifie "Il affiche la réponse de /actuator/health (UP) de l'adresse donnée en 1er paramètre" grep -q "UP" <<<"$plein"
verifie "Il affiche la mémoire libre" grep -qiE "m(e|é)moire|mem" <<<"$plein"
verifie "Il avertit quand le disque est utilisé à plus de 80 %" grep -qiE "attention|avertissement|alerte|warning" <<<"$plein"
verifie "Il n'avertit pas quand le disque est à 40 %" bash -c "! grep -qiE 'attention|avertissement|alerte|warning' <<<\"\$1\"" _ "$leger"
verifie "Il montre les 20 dernières lignes d'erreur du fichier de logs donné en 2e paramètre" bash -c \
  'grep -q "erreur-30" <<<"$1" && grep -q "erreur-11" <<<"$1" && ! grep -q "erreur-10" <<<"$1"' _ "$plein"
texte=$(cat .github/workflows/*.y*ml 2>/dev/null | grep -v '^\s*#' || true)
verifie "La CI lance le script contre l'API démarrée avec Compose" \
  bash -c 'grep -q "diagnostic.sh" <<<"$1" && grep -qE "docker compose|compose up" <<<"$1"' _ "$texte"
