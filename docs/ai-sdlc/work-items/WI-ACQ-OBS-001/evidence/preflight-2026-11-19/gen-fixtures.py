import json, base64, sys
src = "/home/pazfernando/projects/acquiring-simulator/docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json"
out = "/tmp/opencode/packed-fixtures.tsv"
b64 = lambda s: base64.urlsafe_b64encode(str(s).encode()).decode().rstrip("=")
lines = []
for v in json.load(open(src))["vectors"]:
    ef = v.get("expectedDecodedFields") or {}
    ex = v.get("extension") or {}
    def enc(d): return ",".join(f"{k}:{b64(val)}" for k, val in d.items())
    lines.append("|".join([
        v["id"], v["expected"], v.get("expectedFirstFailure") or "", str(v["length"]),
        v["sha256"], v["hex"], enc(ef), enc(ex),
    ]))
open(out, "w").write("\n".join(lines) + "\n")
keys = sorted({k for v in json.load(open(src))["vectors"] for k in (v.get("expectedDecodedFields") or {})})
print("wrote", len(lines), "fixtures; decoded-field keys:", keys)
