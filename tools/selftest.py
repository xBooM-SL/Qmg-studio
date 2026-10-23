#!/usr/bin/env python3
"""Round-trip self test (python3 stdlib only).

  python3 tools/selftest.py /path/to/libQmageDecoder.so

Builds tools/qmgenc, encodes synthetic frames at several sizes/tolerances, decodes them again and checks that the
decoder output is identical to what the encoder reconstructed (so the file shows exactly what the encoder intended)."""
import os, random, struct, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.dirname(here)
lib = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/libQmageDecoder.so")
exe = os.path.join(tempfile.mkdtemp(), "qmgenc")
subprocess.check_call(["cc", "-O2", "-o", exe, os.path.join(here, "qmgenc_cli.c"),
                       os.path.join(root, "app/src/main/cpp/qmgcore.c"), "-I" + os.path.join(root, "app/src/main/cpp"), "-ldl"])

def frames(w, h, n, seed):
    rnd = random.Random(seed)
    out = []
    for f in range(n):
        px = []
        for y in range(h):
            for x in range(w):
                r = ((x * 3 + f * 5) // 8) & 31
                g = ((y * 2 + x + f * 3) // 4) & 63
                b = ((x + y + f * 7) // 6) & 31
                if rnd.random() < 0.02:
                    g = rnd.randrange(64)
                px.append((r << 11) | (g << 5) | b)
        out.append(struct.pack("<%dH" % len(px), *px))
    return b"".join(out)

ok = True
for (w, h) in ((64, 48), (100, 52), (36, 100), (132, 76)):
    for tol in (0, 3, 8):
        d = tempfile.mkdtemp()
        raw = os.path.join(d, "in.rgb565"); open(raw, "wb").write(frames(w, h, 4, w * h + tol))
        q, rec, dec = (os.path.join(d, n) for n in ("o.qmg", "rec", "dec"))
        subprocess.check_call([exe, "-w", str(w), "-h", str(h), "-l", lib, "-o", q, "-t", str(tol), "-i", raw, "-r", rec],
                              stderr=subprocess.DEVNULL)
        subprocess.check_call([exe, "-d", q, "-l", lib, "-o", dec], stderr=subprocess.DEVNULL)
        same = open(rec, "rb").read() == open(dec, "rb").read()
        print("%4dx%-4d tol %2d  %6d bytes  %s" % (w, h, tol, os.path.getsize(q), "OK" if same else "MISMATCH"))
        ok = ok and same
print("ALL OK" if ok else "FAILED")
sys.exit(0 if ok else 1)
