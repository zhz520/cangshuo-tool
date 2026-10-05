package com.cangshuo.toolbox.feature.qr.data

import com.cangshuo.toolbox.feature.qr.domain.QrExportSnapshot
import java.util.Locale

/**
 * Renders the module matrix as a dependency-free SVG. Pure Kotlin, so the output is unit-testable;
 * QR uses the module grid as its viewBox and linear symbols stretch their single row of bars.
 */
object QrSvgWriter {
    const val MAX_PATH_CHARS = 1_000_000

    fun write(snapshot: QrExportSnapshot): String {
        val matrix = snapshot.matrix
        val linear = snapshot.format.linear
        val width = snapshot.size.pixels
        val height = if (linear) width / 3 else width
        val path = StringBuilder()
        if (linear) {
            val bars = matrix.data.first()
            for (x in bars.indices) if (bars[x]) {
                path.append('M').append(x).append(" 0h1v1h-1z")
                if (path.length > MAX_PATH_CHARS) throw SvgLimitException()
            }
        } else {
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix.data[y][x]) {
                path.append('M').append(x).append(' ').append(y).append("h1v1h-1z")
                if (path.length > MAX_PATH_CHARS) throw SvgLimitException()
            }
        }
        val viewBox = if (linear) "0 0 ${matrix.width} 1" else "0 0 ${matrix.width} ${matrix.height}"
        val stretch = if (linear) " preserveAspectRatio=\"none\"" else ""
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" " +
            "viewBox=\"$viewBox\"$stretch shape-rendering=\"crispEdges\">" +
            "<rect width=\"100%\" height=\"100%\" fill=\"${hex(snapshot.colorStyle.lightColorArgb)}\"/>" +
            "<path d=\"$path\" fill=\"${hex(snapshot.colorStyle.darkColorArgb)}\"/></svg>\n"
    }

    private fun hex(argb: Long): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

    class SvgLimitException : Exception("SVG path budget exceeded")
}
