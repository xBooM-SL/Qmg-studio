/*
 * qmgenc CLI
 *   build : clang -O2 -o qmgenc tools/qmgenc_cli.c app/src/main/cpp/qmgcore.c -Iapp/src/main/cpp
 *   encode: qmgenc -w 720 -h 1600 -l libQmageDecoder.so -o out.qmg [-t tol] [-s skiptol] [-b bytes_per_frame] [-i frames.rgb565] [-r recon.rgb565]
 *   decode: qmgenc -d in.qmg -l libQmageDecoder.so -o frames.rgb565        (raw RGB565 little endian, all frames)
 *   info  : qmgenc -d in.qmg -l libQmageDecoder.so                         (prints size / frame count)
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "qmgcore.h"
int main(int argc, char **argv)
{
    const char *lib = "libQmageDecoder.so", *outp = NULL, *inp = NULL, *reconp = NULL, *decp = NULL;
    int W = 0, H = 0, tol = 6, stol = -1, budget = 0;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "-w") && i + 1 < argc) W = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-h") && i + 1 < argc) H = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-l") && i + 1 < argc) lib = argv[++i];
        else if (!strcmp(argv[i], "-o") && i + 1 < argc) outp = argv[++i];
        else if (!strcmp(argv[i], "-i") && i + 1 < argc) inp = argv[++i];
        else if (!strcmp(argv[i], "-t") && i + 1 < argc) tol = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-s") && i + 1 < argc) stol = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-b") && i + 1 < argc) budget = atoi(argv[++i]);
        else if (!strcmp(argv[i], "-r") && i + 1 < argc) reconp = argv[++i];
        else if (!strcmp(argv[i], "-d") && i + 1 < argc) decp = argv[++i];
        else { fprintf(stderr, "see the header of tools/qmgenc_cli.c for usage\n"); return 1; }
    }
    if (decp) {
        int nf = qmg_dec_open(0, decp, lib);
        if (nf < 0) { fprintf(stderr, "cannot open %s (%d)\n", decp, nf); return 1; }
        int w, h, n; qmg_dec_info(0, &w, &h, &n);
        fprintf(stderr, "%dx%d, %d frames\n", w, h, n);
        if (!outp) return 0;
        FILE *o = fopen(outp, "wb"); if (!o) { perror("output"); return 1; }
        uint16_t *px = malloc((size_t)w * h * 2); int k = 0, r;
        while ((r = qmg_dec_next(0, px)) == 0) { fwrite(px, 2, (size_t)w * h, o); k++; }
        if (r < 0) fprintf(stderr, "decode stopped after %d frames: error %d\n", k, r);
        fclose(o); qmg_dec_close(0); return r < 0;
    }
    if (!outp) outp = "out.qmg";
    int rc = qmg_init(lib, W, H, tol, stol);
    if (rc) { fprintf(stderr, "init failed (%d): check -w/-h (multiples of 4) and the library path\n", rc); return 1; }
    FILE *in = inp ? fopen(inp, "rb") : stdin; if (!in) { perror("input"); return 1; }
    FILE *rf = reconp ? fopen(reconp, "wb") : NULL;
    size_t fsz = (size_t)W * H; uint16_t *px = malloc(fsz * 2); int n = 0;
    while (fread(px, 2, fsz, in) == fsz) {
        int b = budget > 0 ? qmg_add_frame_budget(px, n == 0 ? budget * 4 : budget, 40) : qmg_add_frame(px);
        if (b < 0) { fprintf(stderr, "encode error\n"); return 1; }
        if (rf) fwrite(qmg_last_recon(), 2, fsz, rf);
        fprintf(stderr, "frame %d: %d bytes\n", ++n, b);
    }
    long tot = qmg_finish(outp);
    if (tot < 0) { fprintf(stderr, "nothing written\n"); return 1; }
    fprintf(stderr, "wrote %s: %d frames, %ld bytes\n", outp, n, tot);
    qmg_free(); return 0;
}
