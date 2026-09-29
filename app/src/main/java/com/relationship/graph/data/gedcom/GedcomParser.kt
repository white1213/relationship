package com.relationship.graph.data.gedcom

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 最小可用的 GEDCOM 5.5.1 读取器：INDI/FAM/ASSO + BIRT/DEAT 日期 + NOTE。
 * 编码按 BOM/严格 UTF-8/GBK 兜底判断；不支持 ANSEL。
 */
object GedcomParser {

    data class Individual(
        val xref: String,
        val name: String,
        val sex: String,
        val birthDate: String,
        val deathDate: String,
        val notes: List<String>,
    )

    data class Family(
        val xref: String,
        val husband: String?,
        val wife: String?,
        val children: List<String>,
    )

    data class Association(
        val fromXref: String,
        val toXref: String,
        val type: String,
        val note: String,
    )

    data class Document(
        val individuals: List<Individual>,
        val families: List<Family>,
        val associations: List<Association>,
    )

    class GedcomException(message: String) : Exception(message)

    private class IndividualBuilder(val xref: String) {
        var name: String = ""
        var sex: String = "U"
        var birthDate: String = ""
        var deathDate: String = ""
        val notes = mutableListOf<String>()

        fun build() = Individual(xref, name, sex, birthDate, deathDate, notes.toList())
    }

    private class FamilyBuilder(val xref: String) {
        var husband: String? = null
        var wife: String? = null
        val children = mutableListOf<String>()

        fun build() = Family(xref, husband, wife, children.toList())
    }

    fun parse(stream: InputStream): Document {
        val raw = stream.readBytes()
        val text = decode(raw)
        val lines = text.lines().map(String::trimEnd).filter(String::isNotBlank)
        if (lines.none { it.startsWith("0 ") }) {
            throw GedcomException("不是有效的 GEDCOM 文件")
        }

        val individuals = mutableListOf<Individual>()
        val families = mutableListOf<Family>()
        val associations = mutableListOf<Association>()

        var individual: IndividualBuilder? = null
        var family: FamilyBuilder? = null
        var eventTag: String? = null
        var eventDate: String? = null
        var assoXref: String? = null
        var assoType = ""
        var assoNote = ""

        fun commitEvent() {
            when (eventTag) {
                "BIRT" -> individual?.birthDate = normalizeDate(eventDate)
                "DEAT" -> individual?.deathDate = normalizeDate(eventDate)
            }
            eventTag = null
            eventDate = null
        }

        fun commitAsso() {
            val from = individual?.xref
            val to = assoXref
            if (from != null && to != null) {
                associations.add(Association(from, to, assoType, assoNote))
            }
            assoXref = null
            assoType = ""
            assoNote = ""
        }

        fun commitIndividual() {
            commitEvent()
            commitAsso()
            individual?.let { if (it.name.isNotBlank() || it.notes.isNotEmpty()) {
                individuals.add(it.build())
            } }
            individual = null
        }

        fun commitFamily() {
            family?.let { families.add(it.build()) }
            family = null
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            index++
            val match = LINE_REGEX.find(line) ?: continue
            val level = match.groupValues[1].toInt()
            val xref = match.groupValues[2]
            val tag = match.groupValues[3]
            val value = match.groupValues[4]

            if (level == 0) {
                commitAsso()
                commitIndividual()
                commitFamily()
                when {
                    tag.equals("INDI", true) -> individual = IndividualBuilder(xref.trim('@'))
                    tag.equals("FAM", true) -> family = FamilyBuilder(xref.trim('@'))
                }
                continue
            }

            val builder = individual
            if (builder != null && family == null) {
                when {
                    level == 1 -> {
                        // 新的一级标签意味着上一个事件/ASSO 结束。
                        commitEvent()
                        if (assoXref != null) commitAsso()
                        when {
                            tag.equals("NAME", true) -> {
                                val (parsedName, consumed) = readWithContinuation(lines, index, value)
                                builder.name = parsedName.replace("/", "").trim()
                                index += consumed
                            }
                            tag.equals("SEX", true) -> builder.sex = value.trim().uppercase()
                            tag.equals("BIRT", true) || tag.equals("DEAT", true) ->
                                eventTag = tag.uppercase()
                            tag.equals("NOTE", true) -> {
                                val (note, consumed) = readWithContinuation(lines, index, value)
                                if (note.isNotBlank()) builder.notes.add(note.trim())
                                index += consumed
                            }
                            tag.equals("ASSO", true) -> assoXref = value.trim('@')
                        }
                    }
                    level == 2 && tag.equals("DATE", true) -> eventDate = value.trim()
                    level == 2 && tag.equals("TYPE", true) && assoXref != null -> assoType = value.trim()
                    level == 2 && tag.equals("NOTE", true) && assoXref != null -> assoNote = value.trim()
                }
                continue
            }

            val familyBuilder = family
            if (familyBuilder != null) {
                when (tag.uppercase()) {
                    "HUSB" -> familyBuilder.husband = value.trim('@')
                    "WIFE" -> familyBuilder.wife = value.trim('@')
                    "CHIL" -> familyBuilder.children.add(value.trim('@'))
                }
            }
        }
        commitAsso()
        commitIndividual()
        commitFamily()
        return Document(individuals, families, associations)
    }

    /** 读取 1 CONT/1 CONC 续行，返回拼接文本与消耗的行数。 */
    private fun readWithContinuation(lines: List<String>, startIndex: Int, firstValue: String): Pair<String, Int> {
        var text = firstValue
        var consumed = 0
        var i = startIndex + 1
        while (i < lines.size) {
            val match = LINE_REGEX.find(lines[i]) ?: break
            val level = match.groupValues[1].toInt()
            val tag = match.groupValues[3]
            val value = match.groupValues[4]
            if (level != 1) break
            when {
                tag.equals("CONC", true) -> { text += value; consumed++; i++ }
                tag.equals("CONT", true) -> { text += "\n$value"; consumed++; i++ }
                else -> break
            }
        }
        return text to consumed
    }

    private val LINE_REGEX = Regex("^(\\d+)(?:\\s+(@[^@]+@))?(?:\\s+(_?\\w+))?(?:\\s+(.*))?$")

    private fun decode(raw: ByteArray): String {
        if (raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()) {
            return String(raw, 3, raw.size - 3, Charsets.UTF_8)
        }
        val utf8Strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { utf8Strict.decode(ByteBuffer.wrap(raw)).toString() }
            .getOrElse { String(raw, Charset.forName("GBK")) }
    }

    /** GEDCOM 日期（16 AUG 1990 / AUG 1990 / 1990 / ABT 1900）→ 尽量归一为 YYYY[-MM[-DD]]。 */
    internal fun normalizeDate(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val tokens = raw.trim().uppercase().split(Regex("[\\s,]+"))
        val months = mapOf(
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
            "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
        )
        val year = tokens.firstOrNull { it.length == 4 && it.all(Char::isDigit) }
        val month = tokens.firstNotNullOfOrNull { months[it] }
        val day = tokens.firstOrNull {
            (it.toIntOrNull() ?: 0) in 1..31 && it.length <= 2 && it.all(Char::isDigit)
        }
        return buildString {
            if (year != null) append(year)
            if (month != null) { if (isNotEmpty()) append("-"); append("%02d".format(month)) }
            if (day != null && year != null) { append("-"); append("%02d".format(day.toInt())) }
            if (isEmpty()) append(raw.trim())
        }
    }
}
