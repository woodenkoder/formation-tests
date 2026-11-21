"""Le faux prestataire de paiement du manoir. POST /paiements, et POST /mode {"mode": "ok" | "panne" | "lent"}."""
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ETAT = {"mode": "ok", "recus": []}
VERROU = threading.Lock()

def lire_corps(requete):
    """Le corps de la requête, avec Content-Length ou en morceaux (Transfer-Encoding: chunked)."""
    if "chunked" in (requete.headers.get("Transfer-Encoding") or "").lower():
        morceaux = b""
        while True:
            taille = int(requete.rfile.readline().strip().split(b";")[0] or b"0", 16)
            if taille == 0:
                requete.rfile.readline()
                return morceaux
            morceaux += requete.rfile.read(taille)
            requete.rfile.readline()
    return requete.rfile.read(int(requete.headers.get("Content-Length") or 0))



class Prestataire(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def repondre(self, code, corps):
        donnees = json.dumps(corps).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(donnees)))
        self.end_headers()
        self.wfile.write(donnees)

    def do_GET(self):
        with VERROU:
            self.repondre(200, {"mode": ETAT["mode"], "recus": list(ETAT["recus"])})

    def do_POST(self):
        corps = json.loads(lire_corps(self) or b"{}")
        if self.path == "/mode":
            ETAT["mode"] = corps.get("mode", "ok")
            return self.repondre(200, {"mode": ETAT["mode"]})
        mode = ETAT["mode"]
        if mode == "panne":
            return self.repondre(503, {"erreur": "prestataire indisponible"})
        if mode == "lent":
            time.sleep(15)
        with VERROU:
            ETAT["recus"].append(corps.get("commandeId"))
        self.repondre(200, {"statut": "ACCEPTE"})


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Prestataire).serve_forever()
