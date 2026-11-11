#!/usr/bin/env python3
"""Serve the game locally with caching disabled, so edits show up on reload.

    python3 tools/serve.py          # http://localhost:8765
    python3 tools/serve.py 9000     # another port
"""
import functools, http.server, os, sys

class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    handler = functools.partial(NoCacheHandler, directory=root)
    with http.server.ThreadingHTTPServer(("127.0.0.1", port), handler) as httpd:
        print(f"Encircle: http://localhost:{port}  (tests: http://localhost:{port}/tests/)")
        httpd.serve_forever()

if __name__ == "__main__":
    main()
