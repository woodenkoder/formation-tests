#!/usr/bin/env bash
# Lance la vérification d'une épreuve docker ou ci (exercices/<id>/verifier.sh) dans la Lanterne de l'élève.
set -uo pipefail

id="$1"
MANOIR="$(cd "$(dirname "$0")/.." && pwd)"
export MANOIR
verifier="$MANOIR/exercices/$id/verifier.sh"
[ -f "$verifier" ] || { echo "::error::Épreuve inconnue : $id"; exit 1; }
python3 -c 'import yaml' 2>/dev/null || pip install -q pyyaml

cd eleve || exit 1
# shellcheck source=/dev/null
source "$MANOIR/scripts/outils.sh"
# shellcheck source=/dev/null
source "$verifier"
bilan
