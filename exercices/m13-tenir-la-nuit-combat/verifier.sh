# shellcheck shell=bash
# Combat 13.3 du manoir : la Nuit de la mise en prod. Ta Lanterne (son image), cinq clients qui commandent sans
# arrêt, et quatre incidents (contrat : docs/CONTRAT.md de ta Lanterne).
image="${MANOIR_IMAGE_LOCALE:-}"
if [ -z "$image" ]; then
  if [ -f Dockerfile ] && docker build -q -t manoir/lanterne-eleve . >/tmp/manoir-build.log 2>&1; then
    image=manoir/lanterne-eleve
  else
    tail -15 /tmp/manoir-build.log 2>/dev/null
  fi
fi
if [ -z "$image" ]; then
  ko "L'image de ta Lanterne se construit (Dockerfile) : sans elle, pas de Nuit"
  return 0
fi
echo "— La Nuit tombe sur ta Lanterne. Le récit, avec l'heure de chaque incident :"
while IFS=$'\t' read -r etat message; do
  if [ "$etat" = ok ]; then ok "$message"; else ko "$message"; fi
done < <(python3 "$MANOIR/banc/banc.py" nuit "$image")
