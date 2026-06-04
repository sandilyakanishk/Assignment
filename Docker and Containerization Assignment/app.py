# ============================================================
# app.py — Sample Flask Application
# Author : Kanishk Sandilya
# This is the app we are containerizing with Docker
# ============================================================

from flask import Flask, jsonify
import os
import socket

app = Flask(__name__)

@app.route("/")
def home():
    """Main route — returns a welcome message"""
    return jsonify({
        "message": "Hello from Docker!",
        "author": "Kanishk Sandilya",
        "hostname": socket.gethostname(),      # Container's hostname
        "environment": os.getenv("FLASK_ENV", "development")
    })

@app.route("/health")
def health():
    """Health check endpoint — used by Docker HEALTHCHECK"""
    return jsonify({"status": "healthy"}), 200

@app.route("/info")
def info():
    """Returns container/environment info"""
    return jsonify({
        "python_version": os.sys.version,
        "container_id": socket.gethostname(),
        "port": os.getenv("PORT", "5000")
    })

if __name__ == "__main__":
    port = int(os.getenv("PORT", 5000))
    app.run(host="0.0.0.0", port=port)
