import subprocess

from flask import Flask

app = Flask(__name__)

@app.get("/health")
def health():
    return "OK"

@app.post("/reset/<container>")
def reset_container(container: str):
    subprocess.check_output(["/init.sh", f"/dav/{container}"]) 
    return "OK"
