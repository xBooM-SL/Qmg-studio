#ifndef QMGCORE_H
#define QMGCORE_H
#include <stdint.h>
int  qmg_init(const char *lib, int w, int h, int tol, int stol);  /* 0 = ok */
int  qmg_add_frame(const uint16_t *rgb565);                       /* frame size in bytes, <0 = error */
const uint16_t *qmg_last_recon(void);
long qmg_finish(const char *outpath);                             /* file size, -1 = error */
void qmg_free(void);
int  qmg_add_frame_budget(const uint16_t *rgb565, int budget, int maxtol);  /* size, <0 = error */
int  qmg_last_tol(void);
void qmg_convert(const int32_t *argb, uint16_t *out, int w, int h, int dither, int gain);
int  qmg_dec_open(int slot, const char *qmg, const char *lib);  /* frame count, <0 = error. slot 0..3 */
int  qmg_dec_info(int slot, int *w, int *h, int *nf);
int  qmg_dec_next(int slot, uint16_t *out);                     /* 0 ok, 1 end, -5 unsupported */
void qmg_dec_close(int slot);
void qmg_sys_verify(const char *syslib, const char *qmg, const char *ownlib, int *res);
#endif
