package com.cangshuo.toolbox.feature.qr.data

/**
 * Tracks the payloads collected during one camera session.
 *
 * The analyzer runs on a single CameraX executor, so the set is only touched from that thread.
 * Identical payloads are ignored and the session stops growing at [MAX_RESULTS] to bound memory.
 */
class QrCameraCollector(private val limit: Int = MAX_RESULTS) {
    private val seen = LinkedHashSet<String>()

    val size: Int get() = seen.size
    val isFull: Boolean get() = seen.size >= limit

    /** Returns true when [text] was added; false for duplicates or a full session. */
    fun add(text: String): Boolean {
        if (text.isEmpty() || seen.size >= limit) return false
        return seen.add(text)
    }

    companion object {
        const val MAX_RESULTS = 50
    }
}
