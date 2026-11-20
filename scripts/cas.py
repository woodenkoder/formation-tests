"""Les cas des rapports Surefire, une ligne par test : « ok|ko<TAB>vérification lisible (— pourquoi) »."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bilan import depuis_surefire  # noqa: E402

for ok, message, raison in depuis_surefire(sys.argv[1:]):
    print(("ok" if ok else "ko") + "\t" + message + (f" — {raison}" if raison else ""))
