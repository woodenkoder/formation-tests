"""Un fichier YAML en JSON, sur la sortie standard. La clé « on » (lue True par YAML 1.1) reste « on »."""
import json
import sys

import yaml


def cles(valeur):
    if isinstance(valeur, dict):
        return {("on" if k is True else str(k)): cles(v) for k, v in valeur.items()}
    if isinstance(valeur, list):
        return [cles(v) for v in valeur]
    return valeur


with open(sys.argv[1], encoding="utf-8") as fichier:
    print(json.dumps(cles(yaml.safe_load(fichier)), default=str))
