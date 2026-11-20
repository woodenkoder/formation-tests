// Quelle épreuve corriger ? Lit la branche (ou l'identifiant) passée par le dépôt de l'élève et trouve
// l'épreuve correspondante dans exercices/*/manifest.json. Écrit « id=… » pour $GITHUB_OUTPUT.
// Échoue si rien ne correspond : on ne corrige jamais une épreuve sans ses tests.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const demande = String(process.argv[2] ?? '').trim();
const racine = join(import.meta.dirname, '..', 'exercices');
const exercices = readdirSync(racine).filter((id) => existsSync(join(racine, id, 'manifest.json')));

const trouve = exercices.find((id) => {
  const manifest = JSON.parse(readFileSync(join(racine, id, 'manifest.json'), 'utf8'));
  // L'identifiant, la branche de l'épreuve, ou son code court (lancé à la main : Actions → Manoir → Run workflow).
  return id === demande || manifest.branche === demande || manifest.code === demande;
});
if (!trouve) {
  console.error(`::error::Aucune épreuve du manoir pour « ${demande} ». Pousse sur une branche exercice/… de ta Lanterne.`);
  process.exit(1);
}
console.log(`id=${trouve}`);
