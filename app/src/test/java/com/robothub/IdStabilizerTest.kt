package com.robothub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdStabilizerTest {

    @Test
    fun emitsOnlyAfterThreeConsecutiveFrames() {
        val s = IdStabilizer(3)
        assertNull(s.update(2))
        assertNull(s.update(2))
        assertEquals(2, s.update(2))
    }

    @Test
    fun flickerDoesNotEmit() {
        val s = IdStabilizer(3)
        assertNull(s.update(2))
        assertNull(s.update(1))
        assertNull(s.update(2))
        assertNull(s.update(2))
    }

    @Test
    fun sameIdNotEmittedTwice() {
        val s = IdStabilizer(3)
        s.update(2)
        s.update(2)
        s.update(2)
        assertNull(s.update(2))
    }

    @Test
    fun lostMarkerAllowsReEmit() {
        val s = IdStabilizer(3)
        s.update(2)
        s.update(2)
        s.update(2)
        s.update(null)
        s.update(2)
        s.update(2)
        assertEquals(2, s.update(2))
    }
}