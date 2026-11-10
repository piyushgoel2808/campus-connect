#!/usr/bin/env python3
"""
GradBot Local AI Authentication Proxy
=====================================
A lightweight, zero-dependency reverse proxy that protects local Ollama
by requiring a shared secret token in incoming HTTP requests (e.g., from Render).

Usage:
  python3 scripts/ai_proxy.py --port 11435 --secret <YOUR_SECRET_KEY>
"""

import argparse
import hmac
import json
import os
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import datetime
from urllib.parse import urlsplit

# Global configuration initialized in main()
TARGET_HOST = "http://127.0.0.1:11434"
QDRANT_HOST = "http://127.0.0.1:6333"
AUTH_HEADER = "X-GradBot-Key"
AUTH_SECRET = ""
MAX_CONTENT_LENGTH = 25 * 1024 * 1024  # 25 MB request body cap
ALLOWED_OLLAMA_PREFIXES = (
    "/api/tags",
    "/api/chat",
    "/api/embeddings",
    "/api/generate",
    "/api/show",
    "/api/version",
)


class AuthenticatedProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):
        # Clean timestamped log format
        sys.stderr.write(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {self.address_string()} - {format % args}\n")

    def do_OPTIONS(self):
        """Handle CORS pre-flight requests gracefully."""
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", f"Content-Type, {AUTH_HEADER}")
        self.end_headers()

    def do_GET(self):
        # Health check endpoint reports status of both Ollama and Qdrant
        if self.path in ("/", "/health"):
            ollama_ok = False
            try:
                with urllib.request.urlopen(f"{TARGET_HOST}/api/tags", timeout=2) as r:
                    ollama_ok = (r.status == 200)
            except Exception:
                pass

            qdrant_ok = False
            try:
                with urllib.request.urlopen(f"{QDRANT_HOST}/readyz", timeout=2) as r:
                    qdrant_ok = (r.status == 200)
            except Exception:
                pass

            payload = json.dumps({
                "status": "ONLINE" if (ollama_ok or qdrant_ok) else "OFFLINE",
                "service": "GradBot-AI-Secure-Proxy",
                "ollama": "ONLINE" if ollama_ok else "OFFLINE",
                "qdrant": "ONLINE" if qdrant_ok else "OFFLINE",
                "timestamp": datetime.now().isoformat()
            }).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            self.wfile.flush()
            return

        self._proxy_request("GET")

    def do_POST(self):
        self._proxy_request("POST")

    def do_PUT(self):
        self._proxy_request("PUT")

    def do_DELETE(self):
        self._proxy_request("DELETE")

    def _send_json_error(self, status_code, payload_dict):
        err_payload = json.dumps(payload_dict).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(err_payload)))
        self.end_headers()
        self.wfile.write(err_payload)
        self.wfile.flush()

    def _proxy_request(self, method):
        # 1. Enforce Shared Secret Authentication in constant time
        provided_token = self.headers.get(AUTH_HEADER) or ""
        if not AUTH_SECRET or not provided_token or not hmac.compare_digest(provided_token, AUTH_SECRET):
            self._send_json_error(401, {
                "error": "Unauthorized: invalid or missing security token.",
                "hint": f"Provide '{AUTH_HEADER}' header matching your runner secret."
            })
            return

        # 2. Validate and bound Request Body size
        raw_cl = self.headers.get("Content-Length", "0")
        try:
            content_length = int(raw_cl)
        except ValueError:
            self._send_json_error(400, {"error": "Invalid Content-Length header."})
            return

        if content_length < 0:
            self._send_json_error(400, {"error": "Negative Content-Length header."})
            return
        if content_length > MAX_CONTENT_LENGTH:
            self._send_json_error(413, {"error": f"Request body exceeds maximum {MAX_CONTENT_LENGTH} bytes."})
            return

        body = self.rfile.read(content_length) if content_length > 0 else None

        # 3. Multiplex Upstream Request (strictly allowlisted paths for Qdrant vs Ollama)
        path_only = urlsplit(self.path).path
        if ".." in path_only.split("/"):
            self._send_json_error(400, {"error": "Invalid request path."})
            return

        if self.path.startswith("/qdrant/"):
            upstream_name = "Qdrant"
            upstream_path = self.path[len("/qdrant"):]
            target_url = f"{QDRANT_HOST}{upstream_path}"
        elif path_only == "/collections" or path_only.startswith("/collections/") or path_only == "/aliases" or path_only.startswith("/aliases/"):
            upstream_name = "Qdrant"
            target_url = f"{QDRANT_HOST}{self.path}"
        elif any(path_only == prefix or path_only.startswith(prefix + "/") for prefix in ALLOWED_OLLAMA_PREFIXES):
            upstream_name = "Ollama"
            target_url = f"{TARGET_HOST}{self.path}"
        else:
            self._send_json_error(403, {"error": f"Forbidden path '{path_only}': not in Ollama/Qdrant allowlist."})
            return

        req = urllib.request.Request(target_url, data=body, method=method)

        for key, value in self.headers.items():
            if key.lower() not in ("host", "content-length", AUTH_HEADER.lower()):
                req.add_header(key, value)

        # 4. Execute Request to upstream
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                self.send_response(resp.status)
                raw_content_length = resp.headers.get("Content-Length")
                is_chunked = resp.headers.get("Transfer-Encoding", "").lower() == "chunked"

                for header, val in resp.getheaders():
                    if header.lower() not in ("connection", "transfer-encoding", "content-length"):
                        self.send_header(header, val)

                if is_chunked or raw_content_length is None:
                    # Stream chunks immediately for real-time tokens/generation
                    self.send_header("Transfer-Encoding", "chunked")
                    self.end_headers()
                    while True:
                        chunk = resp.read(4096)
                        if not chunk:
                            break
                        self.wfile.write(f"{len(chunk):X}\r\n".encode("ascii") + chunk + b"\r\n")
                        self.wfile.flush()
                    self.wfile.write(b"0\r\n\r\n")
                    self.wfile.flush()
                else:
                    self.send_header("Content-Length", raw_content_length)
                    self.end_headers()
                    while True:
                        chunk = resp.read(8192)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                    self.wfile.flush()

        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            for header, val in e.headers.items():
                if header.lower() not in ("connection", "transfer-encoding", "content-length"):
                    self.send_header(header, val)
            error_data = e.read()
            self.send_header("Content-Length", str(len(error_data)))
            self.end_headers()
            self.wfile.write(error_data)

        except Exception as e:
            self._send_json_error(502, {"error": f"Failed to reach local {upstream_name}: {str(e)}"})


def main():
    global TARGET_HOST, QDRANT_HOST, AUTH_HEADER, AUTH_SECRET

    parser = argparse.ArgumentParser(description="GradBot Secure Ollama & Qdrant Proxy")
    parser.add_argument("--bind", default=os.getenv("PROXY_BIND_HOST", "0.0.0.0"), help="Host address to bind to (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=int(os.getenv("PROXY_PORT", "11435")), help="Port to listen on (default: 11435)")
    parser.add_argument("--target", default=os.getenv("TARGET_HOST", os.getenv("OLLAMA_HOST", "http://127.0.0.1:11434")), help="Ollama endpoint")
    parser.add_argument("--qdrant-target", default=os.getenv("QDRANT_HOST", "http://127.0.0.1:6333"), help="Qdrant endpoint")
    parser.add_argument("--secret", default=os.getenv("GRADBOT_SECRET_KEY", os.getenv("AUTH_SECRET", "")), help="Required secret token")
    parser.add_argument("--header", default=os.getenv("AUTH_HEADER", "X-GradBot-Key"), help="Header name (default: X-GradBot-Key)")

    args = parser.parse_args()

    TARGET_HOST = args.target.rstrip("/")
    QDRANT_HOST = args.qdrant_target.rstrip("/")
    AUTH_HEADER = args.header
    AUTH_SECRET = args.secret

    if not AUTH_SECRET:
        sys.stderr.write("❌ ERROR: A security secret must be provided via --secret or GRADBOT_SECRET_KEY!\n")
        sys.exit(1)

    server = ThreadingHTTPServer((args.bind, args.port), AuthenticatedProxyHandler)
    print(f"🔒 GradBot Auth Proxy listening on http://{args.bind}:{args.port}")
    print(f"👉 Forwarding Ollama calls to {TARGET_HOST}")
    print(f"👉 Forwarding Qdrant calls to {QDRANT_HOST}")
    print(f"🛡️  Enforcing header '{AUTH_HEADER}'\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n🛑 Stopping Auth Proxy...")
        server.server_close()


if __name__ == "__main__":
    main()
