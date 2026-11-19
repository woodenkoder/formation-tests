# Les tests du manoir

Le workflow de correction des combats GitHub de **PROD READY · La Maison hantée de la prod**, et nos tests
cachés.

Tu n'as rien à faire ici : ta Lanterne ([formation-projet](https://github.com/woodenkoder/formation-projet))
appelle ce workflow à chaque push sur une branche `exercice/…`.

## Comment ça marche

1. **Tests** : on trouve l'épreuve (`exercices/<épreuve>/manifest.json`), on copie nos tests dans ta Lanterne et on
   les lance avec le Maven de la machine GitHub. On vérifie dans les rapports que NOS tests ont vraiment tourné.
   Ce job n'a aucun droit de demander un jeton.
2. **Envoi** : un job qui n'exécute aucun code de ta Lanterne demande à GitHub un jeton OIDC (il prouve que c'est
   ce workflow, à cette version, sur une machine GitHub) et envoie le résultat au manoir.

Lire nos tests est permis : c'est une formation. Les contourner ne trompe que toi.

## Ajouter une épreuve

```text
exercices/<identifiant de l'épreuve>/
  manifest.json   { "titre", "type": "java", "branche": "exercice/…", "classes": [...], "minimumTests": n }
  tests/…         les classes de test, dans leurs packages
```

Les Lanternes appellent le workflow sur `main` : une épreuve poussée sur `main` est corrigée tout de suite, sans
toucher aux dépôts des élèves. Le manoir n'accepte que ce workflow, sur `main` (ou au tag `v1`, la première
version), de ce dépôt.
