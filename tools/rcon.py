"""Send commands to the dev server over RCON.

usage: python tools/rcon.py "seafarers test float 0 120 0" ["another command" ...]
Reads port/password from run/server/server.properties (or the file in RCON_PROPS).
"""
import os
import pathlib
import socket
import struct
import sys

PROPS = pathlib.Path(os.environ.get("RCON_PROPS") or pathlib.Path(__file__).resolve().parent.parent / "run" / "server" / "server.properties")


def props():
    out = {}
    for line in PROPS.read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def packet(req_id, kind, body):
    data = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def read(sock):
    length = struct.unpack("<i", sock.recv(4))[0]
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    req_id, _ = struct.unpack("<ii", data[:8])
    return req_id, data[8:-2].decode("utf-8", "replace")


def main():
    p = props()
    with socket.create_connection(("127.0.0.1", int(p.get("rcon.port", 25575))), timeout=10) as s:
        s.sendall(packet(1, 3, p["rcon.password"]))
        if read(s)[0] == -1:
            sys.exit("rcon auth failed")
        for i, cmd in enumerate(sys.argv[1:], start=2):
            s.sendall(packet(i, 2, cmd))
            print(f"> {cmd}\n{read(s)[1]}")


if __name__ == "__main__":
    main()
