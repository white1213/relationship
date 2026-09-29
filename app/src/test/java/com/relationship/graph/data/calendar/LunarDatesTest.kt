package com.relationship.graph.data.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LunarDatesTest {

    @Test
    fun `lunar new year 2023 converts to solar`() {
        val solar = LunarDates.toSolar(2023, 1, 1, isLeapMonth = false)
        assertEquals(LocalDate.of(2023, 1, 22), solar)
    }

    @Test
    fun `leap month lunar date converts to solar`() {
        // 2020 年闰四月初一 = 公历 2020-05-23。
        val solar = LunarDates.toSolar(2020, 4, 1, isLeapMonth = true)
        assertEquals(LocalDate.of(2020, 5, 23), solar)
    }

    @Test
    fun `non leap month in leap year uses normal month`() {
        // 2020 年四月初一（非闰四月）= 公历 2020-04-23。
        val solar = LunarDates.toSolar(2020, 4, 1, isLeapMonth = false)
        assertEquals(LocalDate.of(2020, 4, 23), solar)
    }

    @Test
    fun `solar converts back to lunar`() {
        val lunar = LunarDates.toLunar(LocalDate.of(2023, 1, 22))
        assertNotNull(lunar)
        assertEquals(2023, lunar!!.year)
        assertEquals(1, lunar.month)
        assertEquals(1, lunar.day)
        assertEquals(false, lunar.isLeapMonth)
    }

    @Test
    fun `out of range lunar date returns null`() {
        assertNull(LunarDates.toSolar(1800, 1, 1, isLeapMonth = false))
    }
}
