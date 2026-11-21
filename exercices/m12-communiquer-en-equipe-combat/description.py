"""Vérifie la description d'une PR selon le modèle de l'équipe. Une ligne « ok|ko<TAB>message » par vérification."""
import re
import sys

texte = open(sys.argv[1], encoding="utf-8").read().replace("\r\n", "\n")


def section(titre):
    m = re.search(rf"^#{{2,3}}\s*{titre}\s*$(.*?)(?=^#{{1,3}}\s|\Z)", texte, re.M | re.S | re.I)
    return None if m is None else re.sub(r"<!--.*?-->", "", m.group(1), flags=re.S).strip()


def dire(ok, message):
    print(("ok" if ok else "ko") + "\t" + message)


SECTIONS = ("Contexte", "Changements", "Comment tester", "Risques")
contenus = {t: section(t) for t in SECTIONS}
for t in SECTIONS:
    dire(bool(contenus[t]), f"La section « {t} » est là, et remplie")
dire("<!--" not in texte, "Aucun commentaire du modèle n'est laissé tel quel")

contexte = contenus["Contexte"] or ""
lien = re.search(r"\[[^\]]*\]\([^)]*LAN-42[^)]*\)|https?://\S*LAN-42", contexte, re.I)
dire(bool(lien), "Le contexte contient le lien vers le ticket LAN-42")

etapes = re.findall(r"^\s*(?:\d+[.)]|[-*])\s+\S", contenus["Comment tester"] or "", re.M)
dire(len(etapes) >= 2, "« Comment tester » donne au moins deux étapes")

risques = contenus["Risques"] or ""
mots = len(re.findall(r"\w+", risques))
if re.search(r"aucun risque", risques, re.I):
    dire(mots >= 8, "« Aucun risque identifié » est justifié")
else:
    dire(mots >= 5, "Les risques sont décrits")
