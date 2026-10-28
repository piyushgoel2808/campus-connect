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
import json
import os
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import datetime

# Global configuration initialized in main()
TARGET_HOST = "http://127.0.0.1:11434"
AUTH_HEADER = "X-GradBot-Key"
AUTH_SECRET = ""


class AuthenticatedProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):
        # Clean timestamped log format
        sys.stderr.write(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {self.address_string()} - {format % args}\n")

    def do_OPTIONS(self):
        """Handle CORS pre-flight requests gracefully."""
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", f"Content-Type, {AUTH_HEADER}")
        self.end_headers()

    def do_GET(self):
        # Health check endpoint does not leak model data
        if self.path in ("/", "/health"):
            payload = json.dumps({
                "status": "ONLINE",
                "service": "GradBot-AI-Secure-Proxy",
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

    def _proxy_request(self, method):
        # 1. Enforce Shared Secret Authentication
        provided_token = self.headers.get(AUTH_HEADER)
        if not AUTH_SECRET or provided_token != AUTH_SECRET:
            err_payload = json.dumps({
                "error": "Unauthorized: invalid or missing security token.",
                "hint": f"Provide '{AUTH_HEADER}' header matching your runner secret."
            }).encode("utf-8")
            self.send_response(401)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(err_payload)))
            self.end_headers()
            self.wfile.write(err_payload)
            self.wfile.flush()
            return

        # 2. Read Request Body (if any)
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else None

        # 3. Construct Upstream Request to Local Ollama
        target_url = f"{TARGET_HOST}{self.path}"
        req = urllib.request.Request(target_url, data=body, method=method)

        for key, value in self.headers.items():
            if key.lower() not in ("host", "content-length", AUTH_HEADER.lower()):
                req.add_header(key, value)

        # 4. Execute Request to Ollama
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
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            msg = json.dumps({"error": f"Failed to reach local Ollama: {str(e)}"}).encode("utf-8")
            self.send_header("Content-Length", str(len(msg)))
            self.end_headers()
            self.wfile.write(msg)
            self.wfile.flush()


def main():
    global TARGET_HOST, AUTH_HEADER, AUTH_SECRET

    parser = argparse.ArgumentParser(description="GradBot Secure Ollama Proxy")
    parser.add_argument("--port", type=int, default=11435, help="Port to listen on (default: 11435)")
    parser.add_argument("--target", default="http://127.0.0.1:11434", help="Local Ollama endpoint")
    parser.add_argument("--secret", default=os.getenv("GRADBOT_SECRET_KEY", ""), help="Required secret token")
    parser.add_argument("--header", default="X-GradBot-Key", help="Header name (default: X-GradBot-Key)")

    args = parser.parse_args()

    TARGET_HOST = args.target.rstrip("/")
    AUTH_HEADER = args.header
    AUTH_SECRET = args.secret

    if not AUTH_SECRET:
        sys.stderr.write("❌ ERROR: A security secret must be provided via --secret or GRADBOT_SECRET_KEY!\n")
        sys.exit(1)

    server = ThreadingHTTPServer(("127.0.0.1", args.port), AuthenticatedProxyHandler)
    print(f"🔒 GradBot Auth Proxy listening on http://127.0.0.1:{args.port}")
    print(f"👉 Forwarding verified calls to {TARGET_HOST}")
    print(f"🛡️  Enforcing header '{AUTH_HEADER}'\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n🛑 Stopping Auth Proxy...")
        server.server_close()


if __name__ == "__main__":
    main()
