"""Le bilan d'un combat, mis en scène : le monstre de la salle, ses points de vie, chaque vérification en clair.

Écrit dans le log et dans le résumé du run GitHub ($GITHUB_STEP_SUMMARY), que l'élève voit en premier.
  python3 bilan.py <manifest.json> --java <rapports surefire TEST-*.xml>…
  python3 bilan.py <manifest.json> --lignes <fichier de lignes « ok|ko<TAB>message »>
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ICI = os.path.dirname(os.path.abspath(__file__))


# Les identifiants Java n'ont pas d'accents : on les remet pour l'affichage.
ACCENTS = {'a': 'à', 'acces': 'accès', 'activees': 'activées', 'actives': 'activés', 'appele': 'appelé', 'appliquees': 'appliquées', 'apres': 'après', 'arreter': 'arrêter', 'autorisee': 'autorisée', 'caracteres': 'caractères', 'configuree': 'configurée', 'connecte': 'connecté', 'credite': 'crédité', 'creee': 'créée', 'creer': 'créer', 'debit': 'débit', 'decrit': 'décrit', 'delai': 'délai', 'demandee': 'demandée', 'demarre': 'démarre', 'demarrer': 'démarrer', 'dependance': 'dépendance', 'dependances': 'dépendances', 'depreciee': 'dépréciée', 'dernieres': 'dernières', 'desactive': 'désactivé', 'differentes': 'différentes', 'documentees': 'documentées', 'donnee': 'donnée', 'echec': 'échec', 'echecs': 'échecs', 'echoue': 'échoue', 'enregistre': 'enregistré', 'etat': 'état', 'evenement': 'événement', 'exigees': 'exigées', 'expediee': 'expédiée', 'expose': 'exposé', 'francais': 'français', 'identite': 'identité', 'immediate': 'immédiate', 'installes': 'installés', 'integration': 'intégration', 'journee': 'journée', 'limitee': 'limitée', 'marquee': 'marquée', 'meme': 'même', 'modifie': 'modifié', 'parametre': 'paramètre', 'personnelle': 'personnelle', 'preparer': 'préparer', 'quantite': 'quantité', 'quatrieme': 'quatrième', 'recoit': 'reçoit', 'recu': 'reçu', 'reference': 'référence', 'refuse': 'refusé', 'refusee': 'refusée', 'refusees': 'refusées', 'regroupee': 'regroupée', 'renvoie': 'renvoie', 'repond': 'répond', 'reponse': 'réponse', 'requete': 'requête', 'requetes': 'requêtes', 'retrait': 'retrait', 'reussi': 'réussi', 'reveler': 'révéler', 'scenarios': 'scénarios', 'signe': 'signé', 'teste': 'teste', 'triees': 'triées', 'unitaires': 'unitaires', 'utilise': 'utilise', 'validee': 'validée', 'verifiee': 'vérifiée', 'annulee': 'annulée', 'annule': 'annulé', 'inattendue': 'inattendue', 'fidelite': 'fidélité', 'attrape': 'attrape', 'creux': 'creux', 'fixe': 'fixe', 'ignore': 'ignoré', 'envoyer': 'envoyer', 'cle': 'clé', 'ttc': 'TTC', 'api': 'API', 'jwt': 'JWT'}


def lisible(nom):
    """apres_10_echecs_le_transporteur_n_est_plus_appele → « Après 10 échecs le transporteur n'est plus appelé »."""
    nom = re.sub(r"\(.*\)$", "", nom)
    texte = " ".join(ACCENTS.get(mot, mot) for mot in nom.split("_") if mot).strip()
    for mot in ("n", "l", "d", "qu", "s", "j", "c"):
        texte = re.sub(rf"\b{mot} (?=[aeiouhéèêàâîôûy])", f"{mot}'", texte)
    return texte[:1].upper() + texte[1:]


def pourquoi(message):
    """Le message utile d'un échec : la description [entre crochets] des assertions, sinon la première ligne."""
    if not message:
        return ""
    entre_crochets = re.search(r"\[([^\]]{8,})\]", message)
    ligne = entre_crochets.group(1) if entre_crochets else message.strip().splitlines()[0]
    return ligne[:220]


def depuis_surefire(rapports):
    resultats = []
    for rapport in rapports:
        for cas in ET.parse(rapport).getroot().iter("testcase"):
            echec = cas.find("failure")
            if echec is None:
                echec = cas.find("error")
            if cas.find("skipped") is not None:
                resultats.append((False, lisible(cas.get("name", "")), "ignoré : un test du manoir ne s'ignore pas"))
            elif echec is not None:
                resultats.append((False, lisible(cas.get("name", "")), pourquoi(echec.get("message") or echec.text)))
            else:
                resultats.append((True, lisible(cas.get("name", "")), ""))
    return resultats


def depuis_lignes(fichier):
    resultats = []
    with open(fichier, encoding="utf-8") as f:
        for ligne in f:
            etat, _, message = ligne.rstrip("\n").partition("\t")
            resultats.append((etat == "ok", message, ""))
    return resultats


def main():
    manifest = json.load(open(sys.argv[1], encoding="utf-8"))
    mode, fichiers = sys.argv[2], sys.argv[3:]
    resultats = depuis_surefire(fichiers) if mode == "--java" else depuis_lignes(fichiers[0])
    salles = json.load(open(os.path.join(ICI, "salles.json"), encoding="utf-8"))
    salle = salles.get("m" + manifest.get("numero", "00.0").split(".")[0], {})
    monstre = salle.get("monstre", "Le monstre")
    total = len(resultats)
    coups = sum(1 for ok, _, _ in resultats if ok)
    restants = total - coups
    barre = "🟥" * restants + "⬛" * coups

    titre = f"⚔ Combat {manifest.get('numero', '')} · {manifest.get('titre', '')}"
    if total and restants == 0:
        verdict = f"☠ **{monstre} est vaincu.** {coups}/{total} coups portés. Le résultat part au manoir."
    elif total:
        verdict = (f"🛡 **{monstre} résiste encore** : {restants} point(s) de vie sur {total}. "
                   "Lis les ✖ ci-dessous, corrige, puis pousse à nouveau.")
    else:
        verdict = f"🛡 **{monstre} n'a même pas été touché** : aucun test du manoir n'a tourné (lis le log)."

    # Le log, pour qui le lit en entier.
    print()
    print(titre)
    print(f"{monstre} · PV {barre} ({restants}/{total})")
    for ok, message, raison in resultats:
        print(f"  {'✔' if ok else '✖'} {message}" + (f"\n      → {raison}" if raison else ""))
    print(verdict.replace("**", ""))

    # Le bilan pour le manoir (envoyé avec le résultat) : coups portés, total, au plus 5 vérifications ratées.
    sortie = os.environ.get("MANOIR_BILAN")
    if sortie and total:
        echecs = [message[:120] for ok, message, _ in resultats if not ok][:5]
        with open(sortie, "w", encoding="utf-8") as f:
            json.dump({"score": coups, "total": total, "echecs": echecs}, f, ensure_ascii=False)

    # Le résumé du run, en haut de la page.
    resume = os.environ.get("GITHUB_STEP_SUMMARY")
    if resume:
        with open(resume, "a", encoding="utf-8") as f:
            f.write(f"## {titre}\n\n**{salle.get('salle', '')}** · {monstre}  \n")
            f.write(f"Points de vie : {barre} {restants}/{total}\n\n{verdict}\n\n")
            if resultats:
                f.write("| | Vérification | Pourquoi |\n|---|---|---|\n")
                for ok, message, raison in resultats:
                    f.write(f"| {'✔' if ok else '✖'} | {message} | {raison.replace('|', '/')} |\n")
                f.write("\n")


if __name__ == "__main__":
    main()
