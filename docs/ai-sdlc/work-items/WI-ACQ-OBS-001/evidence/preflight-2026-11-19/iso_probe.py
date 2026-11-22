#!/usr/bin/env python3
"""Faithful port of /tmp/opencode/IsoProbe.java structural/canonical probe.

Runs against packed-fixtures.tsv generated from vectors/packed-vectors.json.
Disposable technical-preflight probe. NOT production code, NOT scheme conformance.
"""
import base64, hashlib, sys

F = {
    3: (6, False, False, "n"), 4: (12, False, False, "n"), 7: (10, False, False, "n"),
    11: (6, False, False, "n"), 22: (3, False, False, "n"), 25: (2, False, False, "n"),
    32: (11, True, False, "an"), 37: (12, False, False, "an"), 38: (6, False, False, "an"),
    39: (2, False, False, "n"), 41: (8, False, False, "an"), 42: (15, False, False, "an"),
    48: (999, False, True, "ext"), 49: (3, False, False, "n"),
}
REQ_0100 = {3, 4, 7, 11, 22, 25, 32, 37, 41, 42, 48, 49}
REQ_0110 = {3, 4, 7, 11, 22, 25, 32, 37, 39, 41, 42, 48, 49}
ORDER = ["v","op","aid","cid","tid","att","acq","mid","term","pay",
         "gen","fixture","cvm","crypto","risk","sent","prior","actor","stage","result","reason","evidence","at"]
ACTOR = {"ISSUER","NETWORK","PROCESSOR"}
STAGE = {"ISSUER_REPLIED","BEFORE_ISSUER","POSSIBLE_ISSUER","BEFORE_NETWORK"}
RESULT = {"APPROVED","DECLINED","PROCESSOR_REJECTED","NOT_CONFIRMED"}
REASON = {"APPROVED","DELIBERATE_DECLINE","FORMAT","DISALLOWED","UNAVAILABLE","UNCERTAIN"}


class Fail(Exception):
    def __init__(self, code, msg):
        super().__init__(msg); self.code = code; self.msg = msg


def require(b, code, msg):
    if not b: raise Fail(code, msg)


def decode_map(s):
    if not s: return {}
    m = {}
    for e in s.split(","):
        i = e.find(":")
        if i < 0: continue
        raw = e[i + 1:]
        pad = "=" * (-len(raw) % 4)
        m[e[:i]] = base64.urlsafe_b64decode(raw + pad).decode("utf-8")
    return m


def parse(s):
    if len(s) < 20: raise Fail("PARSER_TRUNCATED", "too short")
    mti = s[0:4]; pos = 4
    require(mti in ("0100", "0110"), "PARSER_MTI", f"mti={mti}")
    bm = s[pos:pos + 16]; pos += 16
    require(len(bm) == 16 and all(c in "0123456789ABCDEF" for c in bm), "PARSER_BITMAP", f"bitmap not uppercase hex: {bm}")
    bits = [False] * 65
    for i in range(64):
        v = int(bm[i // 4], 16)
        bits[i + 1] = ((v >> (3 - (i % 4))) & 1) == 1
    require(not bits[1], "PARSER_SECONDARY", "secondary bitmap present")
    out = {}
    for de in range(2, 65):
        if not bits[de]: continue
        sp = F.get(de)
        require(sp is not None, "PARSER_FORBIDDEN", f"DE{de} not in profile")
        length, ll, lll, kind = sp
        if ll:
            require(pos + 2 <= len(s), "PARSER_TRUNCATED", f"DE{de} prefix")
            l = int(s[pos:pos + 2]); pos += 2
            require(1 <= l <= length, "PARSER_LENGTH", f"DE{de} LL={l}"); length = l
        if lll:
            require(pos + 3 <= len(s), "PARSER_TRUNCATED", f"DE{de} prefix")
            l = int(s[pos:pos + 3]); pos += 3
            require(1 <= l <= length, "PARSER_LENGTH", f"DE{de} LLL={l}"); length = l
        require(pos + length <= len(s), "PARSER_TRUNCATED", f"DE{de} body")
        v = s[pos:pos + length]; pos += length
        if kind == "n":
            require(v.isdigit(), "PARSER_ALPHABET", f"DE{de} not numeric [{v}]")
        elif kind == "an":
            require(v.isalnum() and v == v.upper(), "PARSER_ALPHABET", f"DE{de} not uppercase AN [{v}]")
        out[str(de)] = v
    require(pos == len(s), "PARSER_END_BODY", f"trailing {len(s)-pos} bytes")
    req = REQ_0100 if mti == "0100" else REQ_0110
    for r in req:
        require(str(r) in out, "PARSER_REQUIRED", f"missing DE{r}")
    if mti == "0100":
        require("38" not in out and "39" not in out, "PARSER_FORBIDDEN", "DE38/39 on request")
    require("48" in out, "PARSER_REQUIRED", "missing DE48")
    return mti, out, out["48"]


def bounds(k, v):
    n = len(v)
    if k in ("aid", "cid", "tid", "att", "fixture", "evidence", "prior"):
        require(1 <= n <= 64, "SCHEMA_LENGTH", f"{k} len {n}")
    elif k in ("acq", "mid", "term", "pay"):
        require(1 <= n <= 128, "SCHEMA_LENGTH", f"{k} len {n}")
    elif k == "gen":
        require(v.isdigit() and v[0] != "0" and 1 <= len(v) <= 10, "SCHEMA_LENGTH", f"gen {v}")
        require(int(v) <= 2147483647, "SCHEMA_LENGTH", "gen range")
    elif k == "cvm":
        require(v in ("VERIFIED", "NOT_REQUIRED"), "SCHEMA_ENUM", "cvm")
    elif k == "crypto":
        require(v in ("0", "1"), "SCHEMA_ENUM", "crypto")
    elif k == "risk":
        require(v in ("PASS", "REJECT"), "SCHEMA_ENUM", "risk")
    elif k in ("sent", "at"):
        require(len(v) == 14 and v.isdigit(), "SCHEMA_LENGTH", f"{k} timestamp")
    elif k == "actor":
        require(v in ACTOR, "SCHEMA_ENUM", "actor")
    elif k == "stage":
        require(v in STAGE, "SCHEMA_ENUM", "stage")
    elif k == "result":
        require(v in RESULT, "SCHEMA_ENUM", "result")
    elif k == "reason":
        require(v in REASON, "SCHEMA_ENUM", "reason")


def canonical(mti, de48):
    kv = {}
    require(de48 is not None and de48 != "", "SCHEMA_CANONICAL", "empty DE48")
    require(len(de48) <= 999, "SCHEMA_CANONICAL", "DE48 >999")
    require(not de48.endswith(";"), "SCHEMA_CANONICAL", "terminal delimiter")
    last = -1
    for pair in de48.split(";"):
        require(pair != "", "SCHEMA_CANONICAL", "empty pair")
        eq = pair.find("=")
        require(eq > 0 and eq == pair.rfind("="), "SCHEMA_CANONICAL", f"bad pair {pair}")
        k, v = pair[:eq], pair[eq + 1:]
        require(v != "", "SCHEMA_CANONICAL", f"empty value {k}")
        require(k in ORDER, "SCHEMA_CANONICAL", f"unknown key {k}")
        idx = ORDER.index(k)
        require(idx > last, "SCHEMA_CANONICAL", f"out of order or duplicate {k}")
        last = idx
        require(all(c.isalnum() or c in "._:-" for c in v), "SCHEMA_CANONICAL", f"charset {k}")
        bounds(k, v)
        kv[k] = v
    require("v" in kv and "op" in kv, "SCHEMA_CANONICAL", "missing v/op")
    require(kv.get("v") == "1" and kv.get("op") == "A", "SCHEMA_ENUM", "v/op")
    resp = ["actor","stage","result","reason","evidence","at"]
    if mti == "0110":
        for r in resp: require(r in kv, "SCHEMA_CANONICAL", f"missing response key {r}")
    else:
        for r in resp: require(r not in kv, "SCHEMA_CANONICAL", f"response key on request {r}")
    require(len(kv) >= (20 if mti == "0110" else 14), "SCHEMA_CANONICAL", "unexpectedly few keys")
    return kv


def main(path):
    rows = [l for l in open(path, encoding="utf-8").read().splitlines() if l.strip()]
    n = 0; accept_ok = 0; parser_expected = 0; parser_detected = 0
    nonparser_expected = 0; problems = []; per = []
    for line in rows:
        p = line.split("|")
        vid, expected, first_fail = p[0], p[1], p[2]
        plen = int(p[3]); sha = p[4]; hx = p[5]
        exp_fields = decode_map(p[6]); exp_ext = decode_map(p[7])
        n += 1
        detected = None; detail = ""
        try:
            b = bytes.fromhex(hx)
            require(len(b) == plen, "INTEGRITY_LENGTH", f"hex length {len(b)} != {plen}")
            require(all(x < 0x80 for x in b), "PARSER_ASCII", "non-ASCII byte")
            act_sha = hashlib.sha256(b).hexdigest()
            require(act_sha == sha, "INTEGRITY_SHA256", f"sha mismatch {act_sha}")
            mti, fields, de48 = parse(b.decode("ascii"))
            kv = canonical(mti, de48)
            if vid.startswith("CAP-request-"):
                require(len(de48) <= 826, "ADMISSION_RESPONSE_CAPACITY", f"DE48 {len(de48)} > 826")
            if exp_fields:
                require(exp_fields == fields, "DECODED_MISMATCH", f"fields differ exp={exp_fields} act={fields}")
                for k, v in exp_ext.items():
                    require(v == kv.get(k), "EXT_MISMATCH", f"ext {k}")
            if first_fail == "":
                accept_ok += 1
                if vid.startswith("CAP-response-"):
                    require(len(fields["48"]) <= 999, "PARSER_LENGTH", "DE48 > 999")
            else:
                nonparser_expected += 1
        except Fail as f:
            detected = f.code; detail = f.msg
            if first_fail.startswith(("PARSER_", "SCHEMA_", "ADMISSION_")):
                parser_expected += 1
                if (f.code == first_fail
                        or (f.code.startswith("PARSER_") and first_fail.startswith("PARSER_"))
                        or (f.code.startswith("SCHEMA_") and first_fail.startswith("SCHEMA_"))
                        or (f.code.startswith("ADMISSION_") and first_fail.startswith("ADMISSION_"))):
                    parser_detected += 1
                else:
                    problems.append(f"{vid}: detected {f.code} ({detail}) but expected {first_fail}")
            elif first_fail == "":
                problems.append(f"{vid}: ACCEPT vector failed: {f.code} {detail}")
            else:
                nonparser_expected += 1
        status = "PASS"
        if first_fail == "" and detected is not None: status = "FAIL"
        if first_fail.startswith(("PARSER_", "SCHEMA_", "ADMISSION_")) and detected != first_fail:
            # allow family-level match as the Java probe did
            fam = detected.split("_")[0] if detected else "none"
            if not (detected and fam == first_fail.split("_")[0]): status = "FAIL"
        per.append((vid, expected, first_fail, detected, status, detail))

    print(f"fixtures={n}")
    print(f"ACCEPT parsed+decoded: {accept_ok}")
    print(f"structural-layer negatives detected: {parser_detected}/{parser_expected}")
    print(f"non-structural negatives deferred to business layers: {nonparser_expected}")
    print(f"problems={len(problems)}")
    for s in problems: print("  ! " + s)
    print("\n-- per-vector --")
    for vid, expected, ff, det, st, detail in per:
        print(f"{st}\t{vid}\t{expected}\texpectedFailure={ff or '-'}\tdetected={det or '-'}" + (f"\t{detail}" if detail else ""))
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
