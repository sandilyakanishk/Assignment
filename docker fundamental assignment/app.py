# =============================================================
#  backend/app.py  —  Flask REST API
#  Author  : Kanishk Sandilya
#  Connects to: PostgreSQL (via DATABASE_URL) + Redis (cache)
# =============================================================

import os
import json
import redis
import psycopg2
from flask import Flask, jsonify, request
from datetime import datetime

app = Flask(__name__)

# ── Database connection ───────────────────────────────────────
def get_db():
    return psycopg2.connect(os.environ["DATABASE_URL"])

# ── Redis connection ──────────────────────────────────────────
cache = redis.from_url(os.environ.get("REDIS_URL", "redis://redis:6379/0"))

# ── Routes ────────────────────────────────────────────────────

@app.route("/health")
def health():
    """Health check — Docker Compose uses this to know we are ready"""
    return jsonify({"status": "ok", "service": "backend", "time": str(datetime.utcnow())})

@app.route("/users", methods=["GET"])
def get_users():
    """
    Get all users.
    Checks Redis cache first — falls back to Postgres if cache miss.
    This is the core pattern: Cache-Aside.
    """
    cached = cache.get("users:all")
    if cached:
        return jsonify({"source": "cache", "data": json.loads(cached)})

    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT id, name, email FROM users ORDER BY id;")
    rows = [{"id": r[0], "name": r[1], "email": r[2]} for r in cur.fetchall()]
    conn.close()

    cache.setex("users:all", 60, json.dumps(rows))   # Cache for 60 seconds
    return jsonify({"source": "database", "data": rows})

@app.route("/users", methods=["POST"])
def create_user():
    """Create a new user and invalidate the cache"""
    data = request.get_json()
    conn = get_db()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO users (name, email) VALUES (%s, %s) RETURNING id;",
        (data["name"], data["email"])
    )
    new_id = cur.fetchone()[0]
    conn.commit()
    conn.close()
    cache.delete("users:all")                        # Invalidate cache
    return jsonify({"id": new_id, "message": "User created"}), 201

@app.route("/info")
def info():
    """Returns info about this container — useful for learning"""
    import socket
    return jsonify({
        "hostname": socket.gethostname(),            # Container ID
        "database_url": os.environ.get("DATABASE_URL", "not set"),
        "redis_url": os.environ.get("REDIS_URL", "not set"),
        "author": "Kanishk Sandilya"
    })

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
