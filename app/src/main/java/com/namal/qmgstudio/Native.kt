package com.namal.qmgstudio

object Native {
    init { System.loadLibrary("qmgnative") }

    // encoder
    external fun nativeInit(lib: String, w: Int, h: Int, tol: Int): Int
    external fun nativeAddFrame(px: ShortArray): Int
    external fun nativeAddFrameBudget(px: ShortArray, budget: Int, maxTol: Int): Int
    external fun nativeLastTol(): Int
    external fun nativeFinish(out: String): Long
    external fun nativeFree()

    // ARGB -> RGB565 (optional ordered dither, gain 0..256 for fades)
    external fun nativeConvert(argb: IntArray, out: ShortArray, w: Int, h: Int, dither: Boolean, gain: Int)

    // decoder (preview / export)
    external fun nativeDecOpen(slot: Int, path: String, lib: String): Int
    external fun nativeDecInfo(slot: Int): IntArray
    external fun nativeDecNext(slot: Int, out: ShortArray): Int
    external fun nativeDecClose(slot: Int)

    // self-test against the phone's own Samsung decoder: [status, matched, checked]
    external fun nativeVerify(sysLib: String, qmg: String, ownLib: String): IntArray
}
