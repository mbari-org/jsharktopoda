#!/usr/bin/env python3
"""Send one JSON command to jsharktopoda over UDP and print the reply.

usage: udp_send.py '<json>' [port]      (default port 8800)
"""
import socket
import sys

msg = sys.argv[1]
port = int(sys.argv[2]) if len(sys.argv) > 2 else 8800
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.settimeout(2)
s.sendto(msg.encode("utf-8"), ("127.0.0.1", port))
try:
    print(s.recvfrom(65535)[0].decode())
except socket.timeout:
    print("(no reply)")
