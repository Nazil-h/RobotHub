package com.robothub

class IdStabilizer(private val requiredFrames: Int = 3) {

    private var candidate: Int? = null
    private var count = 0
    private var lastEmitted: Int? = null

    // Dipanggil sekali per frame. Mengembalikan ID jika baru saja stabil.
    fun update(seenId: Int?): Int? {
        if (seenId == null) {
            reset()
            return null
        }
        if (seenId == candidate) {
            count++
        } else {
            candidate = seenId
            count = 1
        }
        if (count >= requiredFrames && seenId != lastEmitted) {
            lastEmitted = seenId
            return seenId
        }
        return null
    }

    private fun reset() {
        candidate = null
        count = 0
        lastEmitted = null
    }
}