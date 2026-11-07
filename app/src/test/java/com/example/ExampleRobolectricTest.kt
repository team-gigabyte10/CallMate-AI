package com.example

import com.example.core.util.TimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun testSameDayCalculation() {
        val now = System.currentTimeMillis()
        assertTrue(TimeUtils.isSameDay(now, now + 3600000L))
        assertFalse(TimeUtils.isSameDay(now, now + (25 * 3600000L)))
    }
}
