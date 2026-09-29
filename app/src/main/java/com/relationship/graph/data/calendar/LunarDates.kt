package com.relationship.graph.data.calendar

import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.LocalDate
import kotlin.math.abs

/**
 * 公历 ↔ 农历换算的薄封装，基于 cn.6tail:lunar（纯本地计算，1900–2100）。
 * 项目内的农历字段统一用「正数月 + isLeapMonth」表达，lib 内部用负数月表示闰月。
 */
object LunarDates {

    /** 农历日期 → 公历；无法换算（超出范围/日期不存在）返回 null。 */
    fun toSolar(
        lunarYear: Int,
        lunarMonth: Int,
        lunarDay: Int,
        isLeapMonth: Boolean,
    ): LocalDate? {
        // lib 的可靠换算范围是 1900–2100，越界时它不报错而是给出错值，这里显式拦截。
        if (lunarYear !in 1900..2100) return null
        return runCatching {
            val lunar = Lunar.fromYmd(
                lunarYear,
                if (isLeapMonth) -lunarMonth else lunarMonth,
                lunarDay,
            )
            val solar = lunar.solar
            LocalDate.of(solar.year, solar.month, solar.day)
        }.getOrNull()
    }

    /** 公历 → 农历；换算失败返回 null。 */
    fun toLunar(date: LocalDate): LunarDate? = runCatching {
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        LunarDate(
            year = lunar.year,
            month = abs(lunar.month),
            day = lunar.day,
            isLeapMonth = lunar.month < 0,
        )
    }.getOrNull()

    data class LunarDate(
        val year: Int,
        val month: Int,
        val day: Int,
        val isLeapMonth: Boolean,
    )
}
