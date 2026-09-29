package com.relationship.graph.data.gedcom

import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.GraphData
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.local.PersonEntity
import java.time.LocalDate

/** 把本地 GraphData 导出为 GEDCOM 5.5.1 文本；非核心关系用 ASSO 表达。 */
object GedcomExporter {

    private val MONTHS = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )

    fun export(data: GraphData): String {
        val xrefByPerson = data.people
            .mapIndexed { index, person -> person.id to "I${index + 1}" }
            .toMap()
        val tagById = data.tags.associateBy { it.id }
        val typeById = data.relationTypes.associateBy { it.id }
        val tagsByPerson = data.personTags.groupBy { it.personId }

        // 非核心关系（兄弟姐妹/堂表/社交/自定义）→ ASSO 附加行。
        val assoLinesByPerson = mutableMapOf<String, MutableList<String>>()
        data.relationships.forEach { relationship ->
            val type = typeById[relationship.relationTypeId] ?: return@forEach
            val kind = RelationshipSemantics.kind(type)
            if (kind == FamilyRelationKind.PARENT_CHILD || kind == FamilyRelationKind.SPOUSE) {
                return@forEach
            }
            val fromXref = xrefByPerson[relationship.fromPersonId] ?: return@forEach
            val toXref = xrefByPerson[relationship.toPersonId] ?: return@forEach
            val block = buildString {
                append("1 ASSO @$toXref@")
                appendLine()
                append("2 TYPE ${type.name}")
                if (relationship.note.isNotBlank()) {
                    appendLine()
                    append("2 NOTE ${relationship.note}")
                }
            }
            assoLinesByPerson.getOrPut(relationship.fromPersonId) { mutableListOf() } += block
        }

        val sb = StringBuilder()
        sb.appendLine("0 HEAD")
        sb.appendLine("1 SOUR RelationshipGraph")
        sb.appendLine("2 NAME 关系图谱")
        sb.appendLine("1 GEDC")
        sb.appendLine("2 VERS 5.5.1")
        sb.appendLine("2 FORM LINEAGE-LINKED")
        sb.appendLine("1 CHAR UTF-8")
        sb.appendLine("1 DATE ${gedcomDate(LocalDate.now().toString())}")

        data.people.forEach { person ->
            val xref = xrefByPerson.getValue(person.id)
            sb.appendLine("0 @$xref@ INDI")
            sb.appendLine("1 NAME ${person.name}")
            sb.appendLine("1 SEX ${when (person.gender.name) {
                "MALE" -> "M"
                "FEMALE" -> "F"
                else -> "U"
            }}")
            if (person.birthday.isNotBlank()) {
                sb.appendLine("1 BIRT")
                sb.appendLine("2 DATE ${gedcomDate(person.birthday)}")
                if (person.usesLunarBirthday) {
                    sb.appendLine("2 NOTE 农历生日：${lunarBirthdayText(person)}")
                }
            } else if (person.usesLunarBirthday) {
                sb.appendLine("1 BIRT")
                sb.appendLine("2 NOTE 农历生日：${lunarBirthdayText(person)}")
            }
            if (person.deceased) {
                sb.appendLine("1 DEAT Y")
                val death = person.deathDate
                if (!death.isNullOrBlank()) {
                    sb.appendLine("2 DATE ${gedcomDate(death)}")
                }
                if (person.usesLunarDeathDay) {
                    sb.appendLine("2 NOTE 农历忌日：${lunarDeathText(person)}")
                }
            }
            person.notes.takeIf(String::isNotBlank)?.let { notes ->
                val lines = notes.lines()
                sb.appendLine("1 NOTE ${lines.first()}")
                lines.drop(1).forEach { line -> sb.appendLine("2 CONT $line") }
            }
            tagsByPerson[person.id].orEmpty()
                .mapNotNull { tagById[it.tagId]?.name }
                .takeIf(List<*>::isNotEmpty)
                ?.let { names -> sb.appendLine("1 NOTE 标签：${names.joinToString("、")}") }
            assoLinesByPerson[person.id].orEmpty().forEach { block ->
                sb.appendLine(block)
            }
        }

        buildFamilies(data).forEachIndexed { index, draft ->
            sb.appendLine("0 @F${index + 1}@ FAM")
            draft.parents.forEach { parentId ->
                val person = data.people.firstOrNull { it.id == parentId } ?: return@forEach
                sb.appendLine(
                    "1 ${if (person.gender.name == "FEMALE") "WIFE" else "HUSB"} " +
                        "@${xrefByPerson[parentId]}@",
                )
            }
            draft.children.forEach { childId ->
                sb.appendLine("1 CHIL @${xrefByPerson[childId]}@")
            }
        }

        sb.appendLine("0 TRLR")
        return sb.toString()
    }

    data class FamilyDraft(val parents: List<String>, val children: List<String>)

    private fun buildFamilies(data: GraphData): List<FamilyDraft> {
        val typeById = data.relationTypes.associateBy { it.id }
        val childrenByParent = mutableMapOf<String, MutableSet<String>>()
        val spousePairs = mutableSetOf<List<String>>()
        data.relationships.forEach { relationship ->
            val type = typeById[relationship.relationTypeId]
            when (RelationshipSemantics.kind(type)) {
                FamilyRelationKind.PARENT_CHILD -> {
                    val edge = RelationshipSemantics.parentChildEdge(relationship, type)
                        ?: return@forEach
                    childrenByParent.getOrPut(edge.parentPersonId) { mutableSetOf() } += edge.childPersonId
                }
                FamilyRelationKind.SPOUSE -> spousePairs += listOf(
                    relationship.fromPersonId,
                    relationship.toPersonId,
                ).sorted()
                else -> Unit
            }
        }

        val drafts = mutableListOf<FamilyDraft>()
        val handledByParent = mutableMapOf<String, MutableSet<String>>()
        spousePairs.forEach { pair ->
            val (a, b) = pair
            val shared = (childrenByParent[a].orEmpty() intersect childrenByParent[b].orEmpty()).sorted()
            drafts += FamilyDraft(parents = listOf(a, b), children = shared)
            shared.forEach { child ->
                handledByParent.getOrPut(a) { mutableSetOf() } += child
                handledByParent.getOrPut(b) { mutableSetOf() } += child
            }
            if (shared.isEmpty()) {
                drafts += FamilyDraft(parents = listOf(a, b), children = emptyList())
            }
        }
        childrenByParent.forEach { (parent, children) ->
            val remaining = children.filterNot { it in handledByParent[parent].orEmpty() }
            if (remaining.isNotEmpty()) {
                drafts += FamilyDraft(parents = listOf(parent), children = remaining.sorted())
            }
        }
        return drafts
    }

    private fun lunarBirthdayText(person: PersonEntity): String = buildString {
        if (person.isLeapMonth == true) append("闰")
        append("${person.lunarMonth}月${person.lunarDay}日")
    }

    private fun lunarDeathText(person: PersonEntity): String = buildString {
        if (person.isLeapDeathMonth == true) append("闰")
        append("${person.lunarDeathMonth}月${person.lunarDeathDay}日")
    }

    /** ISO 日期 → GEDCOM 风格（16 AUG 1990）；仅年份或无法解析时原样输出。 */
    internal fun gedcomDate(iso: String): String {
        val parsed = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
        return "%02d %s %04d".format(
            parsed.dayOfMonth,
            MONTHS[parsed.monthValue - 1],
            parsed.year,
        )
    }
}
