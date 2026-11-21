"""La fausse IA du manoir (API compatible OpenAI) : un modèle « détourné ».

Quand on lui propose des outils, elle les appelle tous, avec les identifiants d'une AUTRE cliente (la cible,
réglée par POST /cible). Quand elle reçoit le résultat des outils, elle le recopie mot pour mot dans sa réponse :
tout ce que les outils renvoient finit chez le client.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CIBLE = {"email": "victime@exemple.test", "commandeId": 1}
APPELS = {"n": 0}

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



def argument(nom, schema):
    t = schema.get("type")
    if t in ("integer", "number"):
        return CIBLE["commandeId"]
    if t == "boolean":
        return True
    if t == "array":
        return [CIBLE["email"]]
    return CIBLE["email"]


def reponse(message, fin):
    return {"id": "chatcmpl-manoir", "object": "chat.completion", "created": 0, "model": "manoir",
            "choices": [{"index": 0, "message": message, "finish_reason": fin}],
            "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}


class Oracle(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def envoyer(self, corps):
        donnees = json.dumps(corps).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(donnees)))
        self.end_headers()
        self.wfile.write(donnees)

    def do_POST(self):
        demande = json.loads(lire_corps(self) or b"{}")
        if self.path == "/cible":
            CIBLE.update(demande)
            return self.envoyer({"ok": True})
        APPELS["n"] += 1
        messages = demande.get("messages", [])
        resultats = [str(m.get("content")) for m in messages if m.get("role") == "tool"]
        if resultats:
            texte = "Voici ce que j'ai trouvé : " + " | ".join(resultats)
            return self.envoyer(reponse({"role": "assistant", "content": texte}, "stop"))
        outils = demande.get("tools") or []
        if outils:
            appels = []
            for i, o in enumerate(outils):
                f = o.get("function", {})
                proprietes = (f.get("parameters") or {}).get("properties") or {}
                args = {n: argument(n, s) for n, s in proprietes.items()}
                appels.append({"id": f"appel_{i}", "type": "function",
                               "function": {"name": f.get("name"), "arguments": json.dumps(args)}})
            return self.envoyer(reponse({"role": "assistant", "content": None, "tool_calls": appels}, "tool_calls"))
        self.envoyer(reponse({"role": "assistant", "content": "Je n'ai aucun outil pour regarder tes commandes."}, "stop"))

    def do_GET(self):
        self.envoyer({"appels": APPELS["n"], "cible": CIBLE})


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Oracle).serve_forever()
