package com.relationship.graph.ui

import com.relationship.graph.data.calendar.LunarDates
import com.relationship.graph.data.local.PersonEntity
import java.time.LocalDate

/** 农历月日的展示文案，编辑器与详情页共用。 */
fun lunarMonthLabel(month: Int): String = when (month) {
    12 -> "腊月"
    11 -> "冬月"
    else -> "${month}月"
}

fun lunarDayLabel(day: Int): String = when (day) {
    10 -> "初十"
    20 -> "二十"
    30 -> "三十"
    in 1..9 -> "初${chineseDigit(day)}"
    in 11..19 -> "十${chineseDigit(day - 10)}"
    in 21..29 -> "廿${chineseDigit(day - 20)}"
    else -> "$day"
}

private fun chineseDigit(value: Int): String = when (value) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "七"
    8 -> "八"
    9 -> "九"
    else -> "$value"
}

/** 生日的完整展示：农历标注 + 公历日期互为补充；两者皆无时返回 null。 */
fun birthdayDisplayLine(person: PersonEntity): String? {
    val solarPart = person.birthday.takeIf(String::isNotBlank)
    return when {
        person.usesLunarBirthday -> buildString {
            append("农历")
            if (person.isLeapMonth == true) append("闰")
            append(lunarMonthLabel(person.lunarMonth ?: 1))
            append(lunarDayLabel(person.lunarDay ?: 1))
            if (solarPart != null) append("（公历 $solarPart）")
        }
        solarPart != null -> buildString {
            append(solarPart)
            toLunarSuffix(solarPart)?.let { append("（$it）") }
        }
        else -> null
    }
}

/** 忌日的完整展示；未标记已故或无日期返回 null。 */
fun deathDayDisplayLine(person: PersonEntity): String? {
    if (!person.deceased) return null
    val solarPart = person.deathDate?.takeIf(String::isNotBlank)
    return when {
        person.usesLunarDeathDay -> buildString {
            append("农历")
            if (person.isLeapDeathMonth == true) append("闰")
            append(lunarMonthLabel(person.lunarDeathMonth ?: 1))
            append(lunarDayLabel(person.lunarDeathDay ?: 1))
            if (solarPart != null) append("（公历 $solarPart）")
        }
        solarPart != null -> buildString {
            append(solarPart)
            toLunarSuffix(solarPart)?.let { append("（$it）") }
        }
        else -> "已故"
    }
}

private fun toLunarSuffix(isoDate: String): String? {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return null
    val lunar = LunarDates.toLunar(date) ?: return null
    return buildString {
        append("农历")
        if (lunar.isLeapMonth) append("闰")
        append(lunarMonthLabel(lunar.month))
        append(lunarDayLabel(lunar.day))
    }
}
