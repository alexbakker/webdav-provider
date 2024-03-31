import subprocess
import sys

from flask import Flask

app = Flask(__name__)

@app.get("/health")
def health():
    return "OK"

@app.post("/reset/<container>")
def reset_container(container: str):
    subprocess.check_call(["/init.sh", f"/dav/{container}"], stdout=sys.stdout, stderr=sys.stderr)
    if container == "nextcloud":
        subprocess.check_call(["nc", "nextcloud", "1337"], stdout=sys.stdout, stderr=sys.stderr)
    return "OK"
