/*
 * qmgcore - core of the open-source encoder for Samsung ".qmg" (Qmage) boot animations
 *
 * Input : raw RGB565 little-endian frames (e.g. from ffmpeg -pix_fmt rgb565le)
 * Output: .qmg file (format version 15, 16-bit, animation, 12 fps playback by the device)
 *
 * The delta table the format needs is NOT bundled. It is read at run time from a
 * libQmageDecoder.so that you already have on your own phone/device
 * (exported symbol: Qmage_ori_deltaRGB_new).
 *
 * Build : clang -O2 -o qmgenc qmgenc.c        (or gcc)
 * Usage : qmgenc -w 720 -h 1600 -l libQmageDecoder.so -o out.qmg [-t 6] [-s 6] < frames.rgb565
 *
 * License: MIT
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <dlfcn.h>

typedef uint8_t u8; typedef uint16_t u16; typedef uint32_t u32; typedef int32_t i32;

/* ---------------- delta table ---------------- */
static i32 T[512];
static u16 exact_idx[65536];          /* delta(mod 2^16) -> cheapest table index (0 = none) */
static u8  CR[65536], CG[65536], CB[65536];

static int rd16(const u8 *p){ return p[0] | (p[1] << 8); }
static u32 rd32(const u8 *p){ return p[0] | (p[1] << 8) | (p[2] << 16) | ((u32)p[3] << 24); }
static uint64_t rd64(const u8 *p){ return (uint64_t)rd32(p) | ((uint64_t)rd32(p + 4) << 32); }

static int g_table_ok, g_tables_ready;
static int load_table(const char *path)
{
    FILE *f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "cannot open %s\n", path); return -1; }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    u8 *d = malloc(n);
    if (fread(d, 1, n, f) != (size_t)n) { fclose(f); free(d); return -1; }
    fclose(f);
    if (n < 64 || memcmp(d, "\x7f""ELF", 4) || d[4] != 2) { fprintf(stderr, "not an ELF64 file\n"); free(d); return -1; }
    uint64_t phoff = rd64(d + 0x20), shoff = rd64(d + 0x28);
    int phentsize = rd16(d + 0x36), phnum = rd16(d + 0x38), shentsize = rd16(d + 0x3a), shnum = rd16(d + 0x3c);
    /* find .dynsym (type 11) and its string table */
    uint64_t symoff = 0, symsz = 0, stroff = 0; 
    for (int i = 0; i < shnum; i++) {
        const u8 *sh = d + shoff + (uint64_t)i * shentsize;
        if (rd32(sh + 4) == 11) {
            symoff = rd64(sh + 0x18); symsz = rd64(sh + 0x20);
            const u8 *ss = d + shoff + (uint64_t)rd32(sh + 0x28) * shentsize;
            stroff = rd64(ss + 0x18);
        }
    }
    if (!symoff) { fprintf(stderr, "no .dynsym\n"); free(d); return -1; }
    uint64_t vaddr = 0;
    for (uint64_t o = 0; o + 24 <= symsz; o += 24) {
        const u8 *s = d + symoff + o;
        const char *name = (const char *)(d + stroff + rd32(s));
        if (!strcmp(name, "Qmage_ori_deltaRGB_new")) { vaddr = rd64(s + 8); break; }
    }
    if (!vaddr) { fprintf(stderr, "symbol Qmage_ori_deltaRGB_new not found in %s\n", path); free(d); return -1; }
    long fo = -1;
    for (int i = 0; i < phnum; i++) {
        const u8 *ph = d + phoff + (uint64_t)i * phentsize;
        if (rd32(ph) == 1) {
            uint64_t off = rd64(ph + 8), va = rd64(ph + 16), fsz = rd64(ph + 32);
            if (vaddr >= va && vaddr < va + fsz) { fo = (long)(vaddr - va + off); break; }
        }
    }
    if (fo < 0 || fo + 8 + 2048 > n) { fprintf(stderr, "bad table location\n"); free(d); return -1; }
    for (int i = 0; i < 512; i++) T[i] = (i32)rd32(d + fo + 8 + 4 * i);
    free(d);
    if (T[2] != -32 || T[3] != 32 || T[4] != -1 || T[5] != 1) { fprintf(stderr, "unexpected table contents\n"); return -1; }
    g_table_ok = 1;
    return 0;
}

static void init_tables(void)
{
    for (int i = 255; i >= 2; i--) exact_idx[(u16)T[i]] = (u16)i;
    for (int v = 0; v < 65536; v++) {
        int r = (v >> 11) & 31, g = (v >> 5) & 63, b = v & 31;
        CR[v] = (r * 255 + 15) / 31; CG[v] = (g * 255 + 31) / 63; CB[v] = (b * 255 + 15) / 31;
    }
}
/* ---- O(1) best delta lookup: channel decomposition of the table + a 3D lookup over the needed (dr,dg,db) ---- */
#define LR 16
#define LG 32
#define LW (2 * LR + 1)
#define LH (2 * LG + 1)
static signed char tdr[256], tdg[256], tdb[256];
static u8 g_lut[LW * LH * LW];
static u32 g_lut_hash;
static void build_lut(void)
{
    u32 hsh = 2166136261u;
    for (int i = 2; i < 256; i++) hsh = (hsh ^ (u32)T[i]) * 16777619u;
    if (hsh == g_lut_hash) return;
    for (int i = 2; i < 256; i++) {
        int bestc = 1 << 30, br = 0, bg = 0, bb = 0;
        int tv = (u16)T[i];
        for (int r = -31; r <= 31; r++)
            for (int g = -63; g <= 63; g++) {
                int b = tv - r * 2048 - g * 32;
                b = ((b + 32768) & 65535) - 32768;
                if (b < -31 || b > 31) continue;
                int c = abs(r) + abs(g) + abs(b);
                if (c < bestc) { bestc = c; br = r; bg = g; bb = b; }
            }
        tdr[i] = (signed char)br; tdg[i] = (signed char)bg; tdb[i] = (signed char)bb;
    }
    for (int dr = -LR; dr <= LR; dr++)
        for (int dg = -LG; dg <= LG; dg++)
            for (int db = -LR; db <= LR; db++) {
                int be = 1 << 30, bi = 2;
                for (int i = 2; i < 256; i++) {
                    int e1 = abs(dr - tdr[i]) * 33, e2 = abs(dg - tdg[i]) * 16, e3 = abs(db - tdb[i]) * 33;
                    int e = e1 > e2 ? e1 : e2; if (e3 > e) e = e3;
                    if (e < be) { be = e; bi = i; }
                }
                g_lut[((dr + LR) * LH + (dg + LG)) * LW + (db + LR)] = (u8)bi;
            }
    g_lut_hash = hsh;
}

static inline int err16(u16 a, u16 b)
{
    int dr = abs((int)CR[a] - CR[b]), dg = abs((int)CG[a] - CG[b]), db = abs((int)CB[a] - CB[b]);
    int m = dr > dg ? dr : dg; return m > db ? m : db;
}

/* ---------------- bit writer ---------------- */
typedef struct { u8 *buf; size_t cap, nbits; } BW;
static void bw_put(BW *w, u32 v, int n)
{
    size_t need = ((w->nbits + (size_t)n) >> 3) + 2;
    if (need > w->cap) {
        size_t nc = w->cap ? w->cap * 2 : 4096; while (nc < need) nc *= 2;
        w->buf = realloc(w->buf, nc); memset(w->buf + w->cap, 0, nc - w->cap); w->cap = nc;
    }
    for (int i = n - 1; i >= 0; i--) {
        if ((v >> i) & 1) w->buf[w->nbits >> 3] |= 0x80 >> (w->nbits & 7);
        w->nbits++;
    }
}
static size_t bw_bytes(BW *w, int padbit)
{
    while (w->nbits & 7) { if (padbit) bw_put(w, 1, 1); else w->nbits++; }
    return w->nbits >> 3;
}
/* small growable byte buffer */
typedef struct { u8 *p; size_t n, cap; } BB;
static void bb_put(BB *b, const void *d, size_t n)
{
    if (b->n + n > b->cap) { b->cap = (b->n + n) * 2 + 1024; b->p = realloc(b->p, b->cap); }
    memcpy(b->p + b->n, d, n); b->n += n;
}
static void bb_u16(BB *b, u16 v) { u8 t[2] = { v & 255, v >> 8 }; bb_put(b, t, 2); }

/* ---------------- encoder state ---------------- */
typedef struct { int type, idx; u16 rec; } Op;       /* type 0=same 1=delta 2=raw */
static int W, H, TOL = 6, STOL = 6;
static u16 *rec_buf; static u16 *rec;                /* rec = rec_buf + pad */
#define PAD (4096)

static inline int idx_n(int i) { return (31 - __builtin_clz(i)) - 1; }

#ifndef QMG_NO_CACHE
/* code_pixel() only depends on (nb, t, TOL, STOL): remember the answers. Flat anime colours repeat the same pairs a lot. */
#define CC_BITS 19
static u32 cc_key[1 << CC_BITS];
static u32 cc_val[1 << CC_BITS];          /* bit31 = valid, bits 24-25 type, bits 16-23 idx, bits 0-15 rec */
static int cc_tol = -99, cc_stol = -99;
static void cc_sync(int force)
{
    if (force || cc_tol != TOL || cc_stol != STOL) {
        memset(cc_val, 0, sizeof cc_val);
        cc_tol = TOL; cc_stol = STOL;
    }
}
#else
static void cc_sync(int force) { (void)force; }
#endif

static void code_pixel(u16 nb, u16 t, Op *o)
{
    if (err16(nb, t) <= STOL) { o->type = 0; o->rec = nb; return; }
#ifndef QMG_NO_CACHE
    u32 key = ((u32)nb << 16) | t;
    u32 hs = (key * 2654435761u) >> (32 - CC_BITS);
    if ((cc_val[hs] & 0x80000000u) && cc_key[hs] == key) {
        u32 v = cc_val[hs];
        o->type = (int)((v >> 24) & 3); o->idx = (int)((v >> 16) & 255); o->rec = (u16)v;
        return;
    }
#endif
    if (TOL == 0) {
        int i = exact_idx[(u16)(t - nb)];
        if (i) { o->type = 1; o->idx = i; o->rec = t; goto done; }
    } else {
        int bi = 0, be = 1 << 30; u16 br = 0;
        int dr = (t >> 11) - (nb >> 11), dg = ((t >> 5) & 63) - ((nb >> 5) & 63), db = (t & 31) - (nb & 31);
        if (dr >= -LR && dr <= LR && dg >= -LG && dg <= LG && db >= -LR && db <= LR) {
            int i = g_lut[((dr + LR) * LH + (dg + LG)) * LW + (db + LR)];
            u16 c = (u16)(nb + T[i]);
            int e = err16(c, t);
            if (e <= TOL) { bi = i; be = e; br = c; }
        }
        if (be > TOL) {                       /* rare: channel borrow or out of range, scan the whole table */
            be = 1 << 30;
            for (int i = 2; i < 256; i++) {
                u16 c = (u16)(nb + T[i]);
                int e = err16(c, t);
                if (e < be) { be = e; bi = i; br = c; if (e == 0) break; }
            }
        }
        if (be <= TOL) { o->type = 1; o->idx = bi; o->rec = br; goto done; }
    }
    o->type = 2; o->rec = t;
done:
#ifndef QMG_NO_CACHE
    cc_key[hs] = key;
    cc_val[hs] = 0x80000000u | ((u32)o->type << 24) | ((u32)(o->type == 1 ? o->idx : 0) << 16) | o->rec;
#endif
    return;
}

/* plan one 4x4 block with a given mode (0,1,2); returns bit cost, fills ops. Leaves rec modified.
 * Gives up early (returns a huge value) as soon as the cost can no longer beat `limit`. */
static int plan_mode(const u16 *tgt, int x, int y, int mode, Op *ops, int iskey, int limit)
{
    int nbo = mode == 0 ? -1 : (mode == 1 ? -W : -W - 1);
    int cost = iskey ? 18 : 2;
    for (int k = 0; k < 16; k++) {
        int py = y + (k >> 2), px = x + (k & 3);
        int p = py * W + px; u16 t = tgt[p];
        Op *o = &ops[k];
        if (p + nbo < 0) { o->type = 2; o->rec = t; }
        else code_pixel(rec[p + nbo], t, o);
        rec[p] = o->rec;
        if (o->type == 0) cost += iskey ? 0 : 1;
        else if (o->type == 1) cost += (iskey ? 0 : 1) + 3 + idx_n(o->idx) + 1;
        else cost += (iskey ? 0 : 1) + 3 + 16;
        if (cost >= limit) return 1 << 30;
    }
    return cost;
}

/* choose best mode for a block, apply reconstruction, return mode and ops */
static int plan_block(const u16 *tgt, int x, int y, Op *best_ops, int iskey)
{
    u16 save[16];
    for (int k = 0; k < 16; k++) save[k] = rec[(y + (k >> 2)) * W + x + (k & 3)];
    /* mode 3: replicate left pixel of each row */
    if (y * W + x - 1 >= 0) {
        int ok = 1;
        for (int r = 0; r < 4 && ok; r++) {
            u16 left = rec[(y + r) * W + x - 1];
            for (int c = 0; c < 4; c++) if (err16(left, tgt[(y + r) * W + x + c]) > STOL) { ok = 0; break; }
        }
        if (ok) {
            for (int r = 0; r < 4; r++) { u16 left = rec[(y + r) * W + x - 1]; for (int c = 0; c < 4; c++) rec[(y + r) * W + x + c] = left; }
            return 3;
        }
    }
    Op ops[16]; int bestc = 1 << 30, bestm = 0; u16 brec[16];
    for (int m = 0; m < 3; m++) {
        for (int k = 0; k < 16; k++) rec[(y + (k >> 2)) * W + x + (k & 3)] = save[k];
        int c = plan_mode(tgt, x, y, m, ops, iskey, bestc);
        if (c < bestc) {
            bestc = c; bestm = m; memcpy(best_ops, ops, sizeof ops);
            for (int k = 0; k < 16; k++) brec[k] = rec[(y + (k >> 2)) * W + x + (k & 3)];
            if (bestc <= 18) break;                      /* every pixel "same": nothing can be cheaper */
        }
    }
    for (int k = 0; k < 16; k++) rec[(y + (k >> 2)) * W + x + (k & 3)] = brec[k];
    return bestm;
}

static void put_header(u8 *h, int nframes, int idx, u32 lenfield, u32 off18, u32 off1c)
{
    memset(h, 0, 0x20);
    h[0] = 'Q'; h[1] = 'M'; h[2] = 0x0f; h[3] = 0; h[4] = 0x80; h[5] = 0;
    h[6] = W & 255; h[7] = W >> 8; h[8] = H & 255; h[9] = H >> 8; h[10] = 0; h[11] = 0x20;
    memcpy(h + 12, &lenfield, 4);
    h[16] = nframes & 255; h[17] = nframes >> 8; h[18] = idx & 255; h[19] = idx >> 8;
    memcpy(h + 0x18, &off18, 4); memcpy(h + 0x1c, &off1c, 4);
}

static void encode_key(const u16 *tgt, BB *out, int nframes)
{
    cc_sync(0);
    memset(rec_buf, 0, (size_t)(W * H + 2 * PAD) * 2);
    BW A = {0}, B = {0}; BB C = {0};
    Op ops[16];
    for (int y = 0; y < H; y += 4)
        for (int x = 0; x < W; x += 4) {
            int m = plan_block(tgt, x, y, ops, 1);
            bw_put(&A, m, 2);
            if (m == 3) continue;
            u16 mask = 0;
            for (int k = 0; k < 16; k++) if (ops[k].type == 0) mask |= 1 << k;
            bb_u16(&C, mask);
            for (int k = 0; k < 16; k++) {
                if (ops[k].type == 0) continue;
                if (ops[k].type == 2) { bw_put(&B, 7, 3); bb_u16(&C, ops[k].rec); }
                else { int n = idx_n(ops[k].idx); bw_put(&B, n, 3); bw_put(&A, ops[k].idx - (2 << n), n + 1); }
            }
        }
    size_t la = bw_bytes(&A, 1), lb = bw_bytes(&B, 0);
    size_t total = 0x20 + la + lb + C.n; size_t pad = (4 - (total & 3)) & 3;
    u8 h[0x20]; put_header(h, nframes, 1, (u32)((total + pad) / 4), (u32)(0x20 + la + pad), (u32)(0x20 + la + pad + lb));
    bb_put(out, h, 0x20); bb_put(out, A.buf, la);
    for (size_t i = 0; i < pad; i++) bb_put(out, "\xff", 1);
    bb_put(out, B.buf, lb); bb_put(out, C.p, C.n);
    free(A.buf); free(B.buf); free(C.p);
}

static void encode_delta(const u16 *tgt, const u16 *prev, BB *out, int nframes, int idx)
{
    cc_sync(0);
    memcpy(rec, prev, (size_t)W * H * 2);
    BW A = {0}; BB R = {0}; Op ops[16];
    for (int my = 0; my < H; my += 16) {
        int mh = H - my < 16 ? H - my : 16;
        for (int mx = 0; mx < W; mx += 16) {
            int mw = W - mx < 16 ? W - mx : 16;
            int same = 1;
            for (int yy = my; yy < my + mh && same; yy++)
                for (int xx = mx; xx < mx + mw; xx++) {
                    u16 a = tgt[yy * W + xx], b = prev[yy * W + xx];
                    if (a != b && err16(a, b) > STOL) { same = 0; break; }
                }
            if (same) { if (mw < 16 || mh < 16) bw_put(&A, 1, 1); else bw_put(&A, 3, 2); continue; }
            bw_put(&A, 0, 1);
            for (int sy = 0; sy < mh; sy += 4)
                for (int sx = 0; sx < mw; sx += 4) {
                    int m = plan_block(tgt, mx + sx, my + sy, ops, 0);
                    bw_put(&A, m, 2);
                    if (m == 3) continue;
                    for (int k = 0; k < 16; k++) {
                        if (ops[k].type == 0) bw_put(&A, 1, 1);
                        else if (ops[k].type == 2) { bw_put(&A, 0, 1); bw_put(&A, 7, 3); bb_u16(&R, ops[k].rec); }
                        else { int n = idx_n(ops[k].idx); bw_put(&A, 0, 1); bw_put(&A, n, 3); bw_put(&A, ops[k].idx - (2 << n), n + 1); }
                    }
                }
        }
    }
    size_t la = bw_bytes(&A, 1);
    size_t total = 0x20 + la + R.n; size_t pad = (4 - (total & 3)) & 3;
    u8 h[0x20]; put_header(h, nframes, idx, (u32)(total + pad), (u32)(0x20 + la), 0);
    bb_put(out, h, 0x20); bb_put(out, A.buf, la); bb_put(out, R.p, R.n);
    for (size_t i = 0; i < pad; i++) bb_put(out, "\0", 1);
    free(A.buf); free(R.p);
}

/* ---------------- public API (used by the CLI and by the Android JNI wrapper) ---------------- */
static BB *g_frames; static int g_nf, g_cap;
static u16 *g_cur, *g_prev;

void qmg_free(void)
{
    for (int i = 0; i < g_nf; i++) free(g_frames[i].p);
    free(g_frames); g_frames = NULL; g_nf = g_cap = 0;
    free(g_cur); free(g_prev); free(rec_buf); g_cur = g_prev = NULL; rec_buf = NULL; rec = NULL;
}

int qmg_init(const char *lib, int w, int h, int tol, int stol)
{
    qmg_free();
    if (w <= 0 || h <= 0 || (w & 3) || (h & 3)) return -2;
    W = w; H = h; TOL = tol; STOL = stol < 0 ? tol : stol;
    if (load_table(lib)) return -1;
    init_tables();
    build_lut();
    cc_sync(1);
    g_tables_ready = 1;
    rec_buf = calloc((size_t)W * H + 2 * PAD, 2); rec = rec_buf + PAD;
    g_cur = malloc((size_t)W * H * 2); g_prev = malloc((size_t)W * H * 2);
    return (rec_buf && g_cur && g_prev) ? 0 : -3;
}

/* px: W*H RGB565 pixels. returns encoded size of this frame in bytes, or <0 on error */
int qmg_add_frame(const u16 *px)
{
    if (!rec_buf) return -1;
    if (g_nf == g_cap) { g_cap = g_cap ? g_cap * 2 : 64; g_frames = realloc(g_frames, g_cap * sizeof(BB)); }
    memset(&g_frames[g_nf], 0, sizeof(BB));
    size_t fsz = (size_t)W * H;
    memcpy(g_cur, px, fsz * 2);
    if (g_nf == 0) encode_key(g_cur, &g_frames[g_nf], 0);
    else encode_delta(g_cur, g_prev, &g_frames[g_nf], 0, g_nf + 1);
    memcpy(g_prev, rec, fsz * 2);
    return (int)g_frames[g_nf++].n;
}

/* last reconstructed frame (what the device will show), W*H pixels */
const u16 *qmg_last_recon(void) { return g_prev; }

/* writes the .qmg file. returns its size or -1 */
long qmg_finish(const char *outpath)
{
    if (g_nf == 0) return -1;
    FILE *o = fopen(outpath, "wb"); if (!o) return -1;
    long tot = 0;
    for (int i = 0; i < g_nf; i++) {
        g_frames[i].p[16] = g_nf & 255; g_frames[i].p[17] = g_nf >> 8;
        fwrite(g_frames[i].p, 1, g_frames[i].n, o); tot += (long)g_frames[i].n;
    }
    fclose(o);
    return tot;
}

/* ---------------- rate control: fit a frame into a byte budget by raising the tolerance ---------------- */
static int g_last_tol;
int qmg_last_tol(void) { return g_last_tol; }

/* encodes the next frame with the smallest tolerance (>= base) whose size fits `budget` bytes (or maxtol). */
int qmg_add_frame_budget(const u16 *px, int budget, int maxtol)
{
    if (!rec_buf) return -1;
    int base = TOL, basest = STOL, tol = base, n;
    if (g_nf == g_cap) { g_cap = g_cap ? g_cap * 2 : 64; g_frames = realloc(g_frames, g_cap * sizeof(BB)); }
    size_t fsz = (size_t)W * H;
    memcpy(g_cur, px, fsz * 2);
    for (;;) {
        TOL = tol; STOL = tol;
        memset(&g_frames[g_nf], 0, sizeof(BB));
        if (g_nf == 0) encode_key(g_cur, &g_frames[g_nf], 0);
        else encode_delta(g_cur, g_prev, &g_frames[g_nf], 0, g_nf + 1);
        n = (int)g_frames[g_nf].n;
        if (n <= budget || tol >= maxtol) break;
        free(g_frames[g_nf].p);
        tol += tol < 8 ? 2 : 3; if (tol > maxtol) tol = maxtol;
    }
    g_last_tol = tol; TOL = base; STOL = basest;
    memcpy(g_prev, rec, fsz * 2);
    g_nf++;
    return n;
}

/* ---------------- RGB -> RGB565 conversion with optional ordered dither and brightness gain ---------------- */
void qmg_convert(const int32_t *argb, u16 *out, int w, int h, int dither, int gain)
{
    static const int bayer[16] = { 0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5 };
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            u32 c = (u32)argb[y * w + x];
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if (gain < 256) { r = r * gain >> 8; g = g * gain >> 8; b = b * gain >> 8; }
            if (dither) {
                int t = bayer[(y & 3) * 4 + (x & 3)] * 2 + 1 - 16;      /* -15..15 */
                r += t >> 2; g += t >> 3; b += t >> 2;
                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);
            }
            out[y * w + x] = (u16)(((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3));
        }
}

/* ---------------- decoder (keyframe + skip/intra delta frames, as produced by this encoder and by Samsung's own files) ---------------- */
typedef struct { const u8 *p; size_t nbytes, pos; } BR;
static u32 br_get(BR *b, int n)
{
    u32 v = 0;
    while (n--) {
        size_t by = b->pos >> 3;
        int bit = by < b->nbytes ? (b->p[by] >> (7 - (b->pos & 7))) & 1 : 0;
        v = (v << 1) | bit; b->pos++;
    }
    return v;
}
typedef struct { u8 *d; size_t n, off; int w, h, nf, idx; u16 *img; int base; } Dec;
#define QMG_SLOTS 4
static Dec g_decs[QMG_SLOTS];   /* independent decoders: preview player, GIF export, single-frame preview ... */

static void dec_close(Dec *D)
{
    free(D->d); free(D->img); memset(D, 0, sizeof *D);
}

static int dec_open(Dec *D, const char *path, const char *lib)
{
    dec_close(D);
    if (!g_table_ok && load_table(lib)) return -1;
    FILE *f = fopen(path, "rb"); if (!f) return -2;
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    if (n < 0x20) { fclose(f); return -3; }
    D->d = malloc(n); D->n = n;
    if (fread(D->d, 1, n, f) != (size_t)n) { fclose(f); dec_close(D); return -2; }
    fclose(f);
    if (D->d[0] != 'Q' || D->d[1] != 'M') { dec_close(D); return -3; }
    D->w = rd16(D->d + 6); D->h = rd16(D->d + 8); D->nf = rd16(D->d + 16);
    if (D->w <= 0 || D->h <= 0 || (D->w & 3) || (D->h & 3)) { dec_close(D); return -3; }
    D->base = D->w + 4;
    D->img = calloc((size_t)D->base + (size_t)D->w * D->h, 2);
    if (!D->img) { dec_close(D); return -4; }
    return D->nf;
}



#define DEC_NB(p, off) ((p) + (off) < 0 ? 0 : 1)
/* decodes the next frame into out (w*h RGB565). returns 0 ok, 1 = no more frames, <0 error (-5 = unsupported feature) */
static int dec_next(Dec *D, u16 *out)
{
    if (!D->d) return -1;
    if (D->off >= D->n || D->idx >= D->nf) return 1;
    const u8 *fr = D->d + D->off; size_t avail = D->n - D->off;
    if (avail < 0x20) return -3;
    int W_ = D->w, H_ = D->h, base = D->base;
    u32 c = rd32(fr + 12); int gidx = rd16(fr + 0x12);
    size_t flen = (D->idx == 0 || gidx <= 1) ? (size_t)c * 4 : (size_t)c;
    if (flen < 0x20 || flen > avail) return -3;
    u16 *img = D->img;
    int offs[3] = { -1, -W_, -W_ - 1 };
    if (D->idx == 0) {
        u32 boff = rd32(fr + 0x18), coff = rd32(fr + 0x1c);
        if (boff < 0x20 || coff < boff || coff > flen) return -3;
        BR A = { fr + 0x20, boff - 0x20, 0 }, B = { fr + boff, coff - boff, 0 };
        size_t cp = coff;
        for (int y = 0; y < H_; y += 4)
            for (int x = 0; x < W_; x += 4) {
                int m = br_get(&A, 2);
                if (m == 3) {
                    for (int r = 0; r < 4; r++) {
                        int p = base + (y + r) * W_ + x; u16 left = img[p - 1];
                        for (int k = 0; k < 4; k++) img[p + k] = left;
                    }
                    continue;
                }
                if (cp + 2 > flen) return -3;
                u16 mask = rd16(fr + cp); cp += 2;
                for (int k = 0; k < 16; k++) {
                    int p = base + (y + (k >> 2)) * W_ + x + (k & 3);
                    if ((mask >> k) & 1) img[p] = img[p + offs[m]];
                    else {
                        int n = br_get(&B, 3);
                        if (n == 7) { if (cp + 2 > flen) return -3; img[p] = rd16(fr + cp); cp += 2; }
                        else { int v = br_get(&A, n + 1); img[p] = (u16)(T[(2 << n) + v] + img[p + offs[m]]); }
                    }
                }
            }
    } else {
        u32 rawoff = rd32(fr + 0x18);
        if (rawoff < 0x20 || rawoff > flen) return -3;
        BR A = { fr + 0x20, rawoff - 0x20, 0 };
        size_t rp = rawoff;
        for (int my = 0; my < H_; my += 16) {
            int mh = H_ - my < 16 ? H_ - my : 16;
            for (int mx = 0; mx < W_; mx += 16) {
                int mw = W_ - mx < 16 ? W_ - mx : 16;
                int partial = (mw < 16 || mh < 16);
                if (br_get(&A, 1)) {
                    if (partial) continue;                       /* '1' = skip for partial macroblocks */
                    if (br_get(&A, 1)) continue;                 /* '11' = skip */
                    return -5;                                   /* '10x': block copy / motion types are not supported here */
                }
                for (int sy = 0; sy < mh; sy += 4)
                    for (int sx = 0; sx < mw; sx += 4) {
                        int m = br_get(&A, 2), x = mx + sx, y = my + sy;
                        if (m == 3) {
                            for (int r = 0; r < 4; r++) {
                                int p = base + (y + r) * W_ + x; u16 left = img[p - 1];
                                for (int k = 0; k < 4; k++) img[p + k] = left;
                            }
                            continue;
                        }
                        for (int k = 0; k < 16; k++) {
                            int p = base + (y + (k >> 2)) * W_ + x + (k & 3);
                            if (br_get(&A, 1)) img[p] = img[p + offs[m]];
                            else {
                                int n = br_get(&A, 3);
                                if (n == 7) { if (rp + 2 > flen) return -3; img[p] = rd16(fr + rp); rp += 2; }
                                else { int v = br_get(&A, n + 1); img[p] = (u16)(T[(2 << n) + v] + img[p + offs[m]]); }
                            }
                        }
                    }
            }
        }
    }
    D->off += flen; D->idx++;
    if (out) memcpy(out, img + base, (size_t)W_ * H_ * 2);
    return 0;
}

/* public decoder API: slot = independent decoder instance (0..3) */
static Dec *slot_of(int slot) { return (slot >= 0 && slot < QMG_SLOTS) ? &g_decs[slot] : NULL; }
int qmg_dec_open(int slot, const char *path, const char *lib) { Dec *D = slot_of(slot); return D ? dec_open(D, path, lib) : -9; }
int qmg_dec_info(int slot, int *w, int *h, int *nf)
{
    Dec *D = slot_of(slot);
    if (!D || !D->d) { *w = *h = *nf = 0; return -1; }
    *w = D->w; *h = D->h; *nf = D->nf; return 0;
}
int qmg_dec_next(int slot, u16 *out) { Dec *D = slot_of(slot); return D ? dec_next(D, out) : -9; }
void qmg_dec_close(int slot) { Dec *D = slot_of(slot); if (D) dec_close(D); }

/* ---------------- optional self-test against the phone's own Samsung decoder (experimental) ----------------
 * Uses its own private decoder instance, so it never disturbs the preview player.
 * res[0] = status (0 ok, <0 problem), res[1] = frames that matched, res[2] = frames checked */
typedef void *(*create_fn)(const void *buf, int type, int len, int flag);
typedef int (*decode_fn)(void *ctx, void *out);
void qmg_sys_verify(const char *syslib, const char *qmgpath, const char *ownlib, int *res)
{
    Dec vd; memset(&vd, 0, sizeof vd); Dec *D = &vd;
    res[0] = 0; res[1] = 0; res[2] = 0;
    int nf = dec_open(D, qmgpath, ownlib);
    if (nf <= 0) { res[0] = -1; return; }
    void *h = dlopen(syslib, RTLD_NOW);
    if (!h) { res[0] = -10; dec_close(D); return; }
    create_fn create = (create_fn)dlsym(h, "QmageDecCommon_CreateAniInfo");
    decode_fn decode = (decode_fn)dlsym(h, "QmageDecodeAniFrame");
    if (!create || !decode) { res[0] = -11; dec_close(D); return; }
    size_t fsz = (size_t)D->w * D->h;
    void *ctx = create(D->d, 1, (int)D->n, 0);
    if (!ctx) { res[0] = -12; dec_close(D); return; }
    u8 *sysout = calloc(fsz * 4 + 4096, 1);
    u16 *mine = malloc(fsz * 2);
    for (int i = 0; i < nf; i++) {
        int r = dec_next(D, mine);
        if (r != 0) { res[0] = r == 1 ? -3 : r; break; }
        int d = decode(ctx, sysout);
        if (d < 0) { res[0] = -13; break; }
        res[2]++;
        if (!memcmp(mine, sysout, fsz * 2)) res[1]++;
    }
    free(sysout); free(mine); dec_close(D);
}
