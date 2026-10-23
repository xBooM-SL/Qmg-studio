#!/bin/sh
# usage: sh tools/video2qmg.sh input.mp4 output.qmg [tol]
# tol: 0 = lossless (huge), 8 = good, 16 = small file
# env:  QMG_W / QMG_H (default 720x1600, multiples of 4), QMG_LIB (path to libQmageDecoder.so)
# needs: ffmpeg and the ./qmgenc binary (see README)
IN="$1"; OUT="${2:-out.qmg}"; TOL="${3:-8}"
LIB="${QMG_LIB:-$HOME/libQmageDecoder.so}"
W="${QMG_W:-720}"; H="${QMG_H:-1600}"
ffmpeg -v error -y -i "$IN" -an \
  -vf "fps=12,scale=${W}:${H}:force_original_aspect_ratio=increase,crop=${W}:${H}" \
  -pix_fmt rgb565le -f rawvideo - | ./qmgenc -w "$W" -h "$H" -l "$LIB" -o "$OUT" -t "$TOL"
