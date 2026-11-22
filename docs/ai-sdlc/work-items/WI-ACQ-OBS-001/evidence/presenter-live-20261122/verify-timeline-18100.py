#!/usr/bin/env python3
"""Loopback timeline verification for the presenter relay on 18100 (WI-ACQ-OBS-001 T6 slice).

Steps: GET index -> POST session -> POST one authorization -> HTTP catch-up events
-> WebSocket upgrade, subscribe ACK, sync inventory. No AWS call, no payment outside
the local relay. Prints a JSON summary and a non-zero exit when any assertion fails.
"""
import base64
import hashlib
import json
import os
import socket
import struct
import sys
import urllib.request
import urllib.error

PORT = int(os.environ.get("RELAY_PORT", "18100"))
BASE = f"http://127.0.0.1:{PORT}"
ORIGIN = f"http://127.0.0.1:{PORT}"
ACCESS_CODE = os.environ.get("DEMO_ACCESS_CODE", "DEMO-ACCESS-CODE-1234")
ENV = "local-demo"

result = {"port": PORT, "checks": [], "failures": []}


def check(name, ok, detail=""):
    result["checks"].append({"name": name, "ok": bool(ok), "detail": detail})
    if not ok:
        result["failures"].append(name)


def request(method, path, body=None, cookie=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Accept", "application/json")
    req.add_header("Origin", ORIGIN)
    if method != "GET":
        req.add_header("X-Presenter-Request", "1")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if cookie:
        req.add_header("Cookie", cookie)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, dict(r.headers), r.read(), r.headers.get_all("Set-Cookie") or []
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read(), e.headers.get_all("Set-Cookie") or []


status, _, body, _ = request("GET", "/")
check("index.html served", status == 200 and b"<!doctype html" in body.lower(), f"status={status}")

status, _, body, set_cookies = request("POST", "/presenter/v1/session", {"accessCode": ACCESS_CODE})
session = json.loads(body) if body else {}
check("session accepted", status == 200 and session.get("environmentId") == ENV, f"status={status} body={body[:200]}")
cookie = None
for header in set_cookies:
    first = header.split(";", 1)[0]
    if first.startswith("PRESENTER_SESSION="):
        cookie = first
check("session cookie issued", cookie is not None, str(set_cookies)[:200])

ctx = session.get("context") or {}
submit = {
    "contractVersion": "1",
    "operation": "A",
    "correlationId": "CORR-VERIFY-" + base64.b16encode(os.urandom(6)).decode(),
    "acquirerRef": ctx.get("acquirerRef"),
    "merchantRef": ctx.get("merchantRef"),
    "terminalRef": ctx.get("terminalRef"),
    "paymentRef": "PAY-VERIFY-" + base64.b16encode(os.urandom(6)).decode(),
    "credentialFixtureRef": "SIM-APP-001",
    "amount": "12.34",
    "currency": "USD",
    "entryMode": "simulated-contact",
    "cvm": "simulated-verified",
    "cryptogramPresent": False,
}
status, _, body, _ = request("POST", "/console/v1/authorizations", submit, cookie)
c3 = json.loads(body) if body else {}
envelope = c3.get("envelope", c3)
transaction_id = envelope.get("transactionId") or c3.get("transactionId")
check("authorization accepted", status == 200 and bool(transaction_id), f"status={status} body={body[:300]}")

events = []
if transaction_id:
    status, _, body, _ = request(
        "GET", f"/console/v1/transactions/{transaction_id}/events", None, cookie)
    payload = json.loads(body) if body else {}
    events = payload.get("events") or payload.get("items") or []
    sequences = [e.get("sourceSequence") for e in events]
    check("catch-up returns ordered rows", status == 200 and events != [] and sequences == sorted(sequences),
          f"status={status} rows={len(events)} seqs={sequences[:8]}")

# --- WebSocket: handshake, ready, subscribe, sync (RFC 6455 client, masked frames) ---
def ws_handshake():
    key = base64.b64encode(os.urandom(16)).decode()
    sock = socket.create_connection(("127.0.0.1", PORT), timeout=10)
    req = (
        f"GET /presenter/v1/live HTTP/1.1\r\n"
        f"Host: 127.0.0.1:{PORT}\r\n"
        f"Origin: {ORIGIN}\r\n"
        f"Cookie: {cookie}\r\n"
        f"Upgrade: websocket\r\n"
        f"Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        f"Sec-WebSocket-Version: 13\r\n\r\n"
    )
    sock.sendall(req.encode())
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = sock.recv(4096)
        if not chunk:
            break
        buf += chunk
    head, _, rest = buf.partition(b"\r\n\r\n")
    return sock, head.decode(errors="replace"), rest


def send_text(sock, obj):
    payload = json.dumps(obj).encode()
    mask = os.urandom(4)
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    header = bytearray([0x81])
    n = len(payload)
    if n < 126:
        header.append(0x80 | n)
    elif n < 65536:
        header.append(0x80 | 126)
        header += struct.pack(">H", n)
    else:
        header.append(0x80 | 127)
        header += struct.pack(">Q", n)
    sock.sendall(bytes(header) + mask + masked)


def recv_frame(sock, pending):
    buf = pending
    while True:
        if len(buf) >= 2:
            b1, b2 = buf[0], buf[1]
            length = b2 & 0x7F
            offset = 2
            if length == 126:
                if len(buf) < 4:
                    buf += sock.recv(4096); continue
                length = struct.unpack(">H", buf[2:4])[0]; offset = 4
            elif length == 127:
                if len(buf) < 10:
                    buf += sock.recv(4096); continue
                length = struct.unpack(">Q", buf[2:10])[0]; offset = 10
            if len(buf) < offset + length:
                buf += sock.recv(4096); continue
            payload = buf[offset:offset + length]
            rest = buf[offset + length:]
            opcode = b1 & 0x0F
            if opcode == 0x9:  # ping -> pong
                sock.sendall(bytes([0x8A, 0x80]) + os.urandom(4))
                buf = rest; continue
            return opcode, payload, rest
        chunk = sock.recv(4096)
        if not chunk:
            return None, b"", b""
        buf += chunk


ws = None
try:
    sock, head, rest = ws_handshake()
    ws = sock
    check("websocket upgrade 101", head.startswith("HTTP/1.1 101"), head.splitlines()[0] if head else "no response")
    opcode, payload, rest = recv_frame(sock, rest)
    ready = json.loads(payload) if payload else {}
    check("ready frame", ready.get("action") == "ready" and ready.get("environmentId") == ENV, str(ready)[:200])

    if ready.get("action") == "ready" and transaction_id:
        send_text(sock, {
            "version": 1,
            "action": "subscribe",
            "requestId": "REQ-VERIFY-" + base64.b16encode(os.urandom(6)).decode(),
            "subscriptionId": "SUB-VERIFY-" + base64.b16encode(os.urandom(8)).decode(),
            "environmentId": ENV,
            "context": {"acquirerRef": ctx.get("acquirerRef"), "merchantRef": ctx.get("merchantRef")},
            "transactionId": transaction_id,
        })
        deadline_frames = []
        for _ in range(6):
            opcode, payload, rest = recv_frame(sock, rest)
            if opcode is None:
                break
            frame = json.loads(payload) if payload else {}
            deadline_frames.append(frame)
            if frame.get("action") in ("subscribed", "subscription_rejected"):
                break
        ack = next((f for f in deadline_frames if f.get("action") == "subscribed"), None)
        rejected = next((f for f in deadline_frames if f.get("action") == "subscription_rejected"), None)
        check("subscribe ACK", ack is not None, json.dumps(rejected or deadline_frames)[:300])

        send_text(sock, {"version": 1, "action": "sync"})
        sync = None
        for _ in range(6):
            opcode, payload, rest = recv_frame(sock, rest)
            if opcode is None:
                break
            frame = json.loads(payload) if payload else {}
            if frame.get("action") == "sync":
                sync = frame
                break
        inventory = (sync or {}).get("subscriptions") or []
        check("sync inventory",
              sync is not None and any(i.get("transactionId") == transaction_id for i in inventory),
              json.dumps(sync)[:300])
finally:
    if ws is not None:
        try:
            ws.sendall(bytes([0x88, 0x82]) + os.urandom(4) + struct.pack(">H", 1000))
        except Exception:
            pass
        try:
            ws.close()
        except Exception:
            pass

result["transactionId"] = transaction_id
result["eventRows"] = len(events)
result["ok"] = not result["failures"]
print(json.dumps(result, indent=2))
sys.exit(0 if result["ok"] else 1)
