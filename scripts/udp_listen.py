#!/usr/bin/env python3
"""Pretend to be the remote app: print every command jsharktopoda sends and reply ok.

usage: udp_listen.py [port]      (default port 9999; send `connect` with this port first)
"""
import json
import socket
import sys

port = int(sys.argv[1]) if len(sys.argv) > 1 else 9999
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("127.0.0.1", port))
print(f"listening on {port}", flush=True)
while True:
    data, addr = s.recvfrom(65535)
    text = data.decode()
    print("<-", text, flush=True)
    try:
        cmd = json.loads(text).get("command", "")
    except Exception:
        cmd = ""
    s.sendto(json.dumps({"response": cmd, "status": "ok"}).encode(), addr)
