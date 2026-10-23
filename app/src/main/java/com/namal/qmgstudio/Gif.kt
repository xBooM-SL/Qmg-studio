package com.namal.qmgstudio

import android.graphics.Bitmap
import java.io.File
import java.io.OutputStream

/** Minimal GIF89a writer (adaptive 256 colour palette via median cut, LZW) used for "QMG -> GIF". */
object Gif {
    private class Sub(val out: OutputStream) {
        val buf = ByteArray(255)
        var n = 0
        fun put(b: Int) { buf[n++] = b.toByte(); if (n == 255) flush() }
        fun flush() { if (n > 0) { out.write(n); out.write(buf, 0, n); n = 0 } }
    }

    private fun lzw(idx: ByteArray, out: OutputStream) {
        out.write(8)
        val sb = Sub(out)
        var nbits = 9
        var maxcode = 511
        var free = 258
        var clear = false
        var bitbuf = 0
        var bitcnt = 0
        val hk = IntArray(8192)
        val hv = IntArray(8192)
        hk.fill(-1)

        fun output(code: Int) {
            bitbuf = bitbuf or (code shl bitcnt)
            bitcnt += nbits
            while (bitcnt >= 8) {
                sb.put(bitbuf and 255)
                bitbuf = bitbuf ushr 8
                bitcnt -= 8
            }
            if (free > maxcode || clear) {
                if (clear) {
                    nbits = 9; maxcode = 511; clear = false
                } else {
                    nbits++
                    maxcode = if (nbits == 12) 4096 else (1 shl nbits) - 1
                }
            }
        }

        output(256)
        var ent = idx[0].toInt() and 255
        for (i in 1 until idx.size) {
            val c = idx[i].toInt() and 255
            val key = (ent shl 8) or c
            var h = (key * -1640531535) ushr 19
            var found = -1
            while (hk[h] != -1) {
                if (hk[h] == key) { found = hv[h]; break }
                h = (h + 1) and 8191
            }
            if (found >= 0) { ent = found; continue }
            output(ent)
            ent = c
            if (free < 4096) {
                hk[h] = key; hv[h] = free; free++
            } else {
                hk.fill(-1); free = 258; clear = true
                output(256)
            }
        }
        output(ent)
        output(257)
        if (bitcnt > 0) sb.put(bitbuf and 255)
        sb.flush()
        out.write(0)
    }

    private fun bin(c: Int): Int =
        (((c shr 19) and 31) shl 10) or (((c shr 11) and 31) shl 5) or ((c shr 3) and 31)

    /** median cut over the 15-bit colour histogram. Returns RGB palette bytes (768) and fills lut with palette index per bin. */
    private fun palette(hist: IntArray, lut: IntArray): ByteArray {
        val bins = ArrayList<Int>()
        for (i in hist.indices) if (hist[i] > 0) bins.add(i)
        if (bins.isEmpty()) bins.add(0)
        val boxes = ArrayList<List<Int>>()
        boxes.add(bins)
        while (boxes.size < 256) {
            var bi = -1
            var best = -1L
            for (k in boxes.indices) {
                val b = boxes[k]
                if (b.size < 2) continue
                var cnt = 0L
                for (x in b) cnt += hist[x]
                if (cnt > best) { best = cnt; bi = k }
            }
            if (bi < 0) break
            val b = boxes[bi]
            var rmin = 31; var rmax = 0; var gmin = 31; var gmax = 0; var bmin = 31; var bmax = 0
            for (x in b) {
                val r = (x shr 10) and 31; val g = (x shr 5) and 31; val bl = x and 31
                if (r < rmin) rmin = r; if (r > rmax) rmax = r
                if (g < gmin) gmin = g; if (g > gmax) gmax = g
                if (bl < bmin) bmin = bl; if (bl > bmax) bmax = bl
            }
            val shift = if (rmax - rmin >= gmax - gmin && rmax - rmin >= bmax - bmin) 10
            else if (gmax - gmin >= bmax - bmin) 5 else 0
            val sorted = b.sortedBy { (it shr shift) and 31 }
            var total = 0L
            for (x in sorted) total += hist[x]
            var acc = 0L
            var cut = 1
            for (j in sorted.indices) {
                acc += hist[sorted[j]]
                if (acc * 2 >= total) { cut = j + 1; break }
            }
            cut = cut.coerceIn(1, sorted.size - 1)
            boxes[bi] = sorted.subList(0, cut)
            boxes.add(sorted.subList(cut, sorted.size))
        }
        val pal = ByteArray(768)
        for (k in boxes.indices) {
            var sr = 0L; var sg = 0L; var sbl = 0L; var cnt = 0L
            for (x in boxes[k]) {
                val w = hist[x].toLong().coerceAtLeast(1L)
                sr += ((x shr 10) and 31) * w; sg += ((x shr 5) and 31) * w; sbl += (x and 31) * w; cnt += w
                lut[x] = k
            }
            val r = (sr / cnt * 255 / 31).toInt(); val g = (sg / cnt * 255 / 31).toInt(); val bl = (sbl / cnt * 255 / 31).toInt()
            pal[k * 3] = r.toByte(); pal[k * 3 + 1] = g.toByte(); pal[k * 3 + 2] = bl.toByte()
        }
        return pal
    }

    private fun nearest(pal: ByteArray, b: Int): Int {
        val r = ((b shr 10) and 31) * 255 / 31
        val g = ((b shr 5) and 31) * 255 / 31
        val bl = (b and 31) * 255 / 31
        var best = 0
        var bd = Int.MAX_VALUE
        for (k in 0 until 256) {
            val dr = r - (pal[k * 3].toInt() and 255)
            val dg = g - (pal[k * 3 + 1].toInt() and 255)
            val db = bl - (pal[k * 3 + 2].toInt() and 255)
            val d = dr * dr + dg * dg + db * db
            if (d < bd) { bd = d; best = k }
        }
        return best
    }

    private fun le16(o: OutputStream, v: Int) { o.write(v and 255); o.write((v shr 8) and 255) }

    /** Exports a .qmg as an animated GIF (max 360 px wide). Returns null on success or an error message. */
    fun export(qmg: File, lib: String, out: OutputStream, onProgress: (Int, Int) -> Unit): String? =
        synchronized(GifReader.lock) {
            val n = GifReader.open(qmg.path, lib)
            if (n <= 0) return@synchronized "Cannot open this .qmg file"
            val w = GifReader.w
            val h = GifReader.h
            val gw = minOf(w, 360)
            val gh = maxOf(1, h * gw / w)
            val ints = IntArray(gw * gh)
            val hist = IntArray(32768)
            val step = maxOf(1, n / 10)
            var i = 0
            while (true) {
                val b = GifReader.next() ?: break
                if (i % step == 0) {
                    val s = Bitmap.createScaledBitmap(b, gw, gh, true)
                    s.getPixels(ints, 0, gw, 0, 0, gw, gh)
                    for (c in ints) hist[bin(c)]++
                    if (s !== b) s.recycle()
                }
                b.recycle()
                i++
            }
            val rc = GifReader.lastRc
            GifReader.close()
            if (rc < 0) return@synchronized (if (rc == -5) "This file uses features the built-in decoder does not support yet" else "Decode error ($rc)")

            val lut = IntArray(32768)
            lut.fill(-1)
            val pal = palette(hist, lut)

            val n2 = GifReader.open(qmg.path, lib)
            if (n2 <= 0) return@synchronized "Cannot reopen the file"
            out.write("GIF89a".toByteArray())
            le16(out, gw); le16(out, gh)
            out.write(0xF7); out.write(0); out.write(0)
            out.write(pal)
            out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
            out.write("NETSCAPE2.0".toByteArray())
            out.write(byteArrayOf(3, 1, 0, 0, 0))
            val idx = ByteArray(gw * gh)
            var f = 0
            while (true) {
                val b = GifReader.next() ?: break
                val s = Bitmap.createScaledBitmap(b, gw, gh, true)
                s.getPixels(ints, 0, gw, 0, 0, gw, gh)
                for (k in ints.indices) {
                    val bn = bin(ints[k])
                    var p = lut[bn]
                    if (p < 0) { p = nearest(pal, bn); lut[bn] = p }
                    idx[k] = p.toByte()
                }
                if (s !== b) s.recycle()
                b.recycle()
                out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0x04))
                le16(out, 8)
                out.write(0); out.write(0)
                out.write(0x2C)
                le16(out, 0); le16(out, 0); le16(out, gw); le16(out, gh)
                out.write(0)
                lzw(idx, out)
                f++
                onProgress(f, n)
            }
            GifReader.close()
            out.write(0x3B)
            out.flush()
            null
        }
}
