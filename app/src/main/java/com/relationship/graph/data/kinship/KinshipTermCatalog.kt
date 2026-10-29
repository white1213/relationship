package com.relationship.graph.data.kinship

import com.relationship.graph.data.local.InferenceRelationTypeIds
import com.relationship.graph.data.local.RelationDirection

/**
 * 「一句话录入」与「详情页快建」共用的称谓目录。
 *
 * 优先级：现成 preset 类型 > 名字能命中 RelationshipSemantics 语义集合的自定义类型 >
 * 通用推理类型 + labelOverride（与 confirmInference 的既定约定一致）。
 */
enum class KinshipGroup(val title: String) {
    SPOUSE("配偶"),
    DIRECT("直系"),
    EXTENDED("旁系"),
    IN_LAW("姻亲"),
}

data class KinshipTermSpec(
    val term: String,
    val presetTypeId: String? = null,
    val customName: String? = null,
    val customInverse: String? = null,
    val customDirection: RelationDirection = RelationDirection.DIRECTED,
    // 主语（「A 是 B 的X」里的 A / 快建里的本人）是否为关系 from 一方。
    val subjectIsFrom: Boolean = true,
    // to 称呼 from 的具体称谓；from 称呼 to 用 inverseLabelOverride。
    val labelOverride: String? = null,
    val inverseLabelOverride: String? = null,
    val group: KinshipGroup = KinshipGroup.EXTENDED,
    val aliases: List<String> = emptyList(),
)

object KinshipTermCatalog {

    val all: List<KinshipTermSpec> = listOf(
        // 配偶
        spec("丈夫", "preset_spouse", aliases = listOf("老公"), group = KinshipGroup.SPOUSE),
        spec("妻子", "preset_spouse", aliases = listOf("老婆"), group = KinshipGroup.SPOUSE),
        spec("配偶", "preset_spouse", aliases = listOf("伴侣", "爱人"), group = KinshipGroup.SPOUSE),
        // 直系
        spec("爸爸", "preset_parent_child", aliases = listOf("父亲", "爹", "老爸"), group = KinshipGroup.DIRECT),
        spec("妈妈", "preset_parent_child", aliases = listOf("母亲", "娘", "老妈"), group = KinshipGroup.DIRECT),
        spec("儿子", "preset_parent_child", subjectIsFrom = false, group = KinshipGroup.DIRECT),
        spec("女儿", "preset_parent_child", subjectIsFrom = false, group = KinshipGroup.DIRECT),
        spec(
            "爷爷",
            InferenceRelationTypeIds.GRANDPARENT,
            labelOverride = "爷爷",
            inverseLabelOverride = "孙子/孙女",
            group = KinshipGroup.DIRECT,
        ),
        spec(
            "奶奶",
            InferenceRelationTypeIds.GRANDPARENT,
            labelOverride = "奶奶",
            inverseLabelOverride = "孙子/孙女",
            group = KinshipGroup.DIRECT,
        ),
        spec(
            "外公",
            InferenceRelationTypeIds.GRANDPARENT,
            labelOverride = "外公",
            inverseLabelOverride = "外孙/外孙女",
            group = KinshipGroup.DIRECT,
        ),
        spec(
            "外婆",
            InferenceRelationTypeIds.GRANDPARENT,
            labelOverride = "外婆",
            inverseLabelOverride = "外孙/外孙女",
            group = KinshipGroup.DIRECT,
        ),
        spec(
            "孙子",
            InferenceRelationTypeIds.GRANDPARENT,
            subjectIsFrom = false,
            labelOverride = "爷爷/奶奶",
            inverseLabelOverride = "孙子",
            group = KinshipGroup.DIRECT,
        ),
        spec(
            "孙女",
            InferenceRelationTypeIds.GRANDPARENT,
            subjectIsFrom = false,
            labelOverride = "爷爷/奶奶",
            inverseLabelOverride = "孙女",
            group = KinshipGroup.DIRECT,
        ),
        spec(
            "外孙",
            InferenceRelationTypeIds.GRANDPARENT,
            subjectIsFrom = false,
            labelOverride = "外公/外婆",
            inverseLabelOverride = "外孙",
            group = KinshipGroup.DIRECT,
        ),
        spec(
            "外孙女",
            InferenceRelationTypeIds.GRANDPARENT,
            subjectIsFrom = false,
            labelOverride = "外公/外婆",
            inverseLabelOverride = "外孙女",
            group = KinshipGroup.DIRECT,
        ),
        // 旁系
        spec(
            "哥哥",
            customName = "哥哥",
            customInverse = "弟弟/妹妹",
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "弟弟",
            customName = "弟弟",
            customInverse = "哥哥/姐姐",
            group = KinshipGroup.EXTENDED,
        ),
        spec("姐姐", "preset_older_sister", group = KinshipGroup.EXTENDED),
        spec(
            "妹妹",
            customName = "妹妹",
            customInverse = "哥哥/姐姐",
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "大爷",
            InferenceRelationTypeIds.AUNT_UNCLE,
            labelOverride = "大爷",
            inverseLabelOverride = "侄子/侄女",
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "叔叔",
            InferenceRelationTypeIds.AUNT_UNCLE,
            labelOverride = "叔叔",
            inverseLabelOverride = "侄子/侄女",
            group = KinshipGroup.EXTENDED,
        ),
        spec("姑姑", "preset_aunt", aliases = listOf("姑母"), group = KinshipGroup.EXTENDED),
        spec(
            "舅舅",
            InferenceRelationTypeIds.AUNT_UNCLE,
            labelOverride = "舅舅",
            inverseLabelOverride = "外甥/外甥女",
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "姨妈",
            InferenceRelationTypeIds.AUNT_UNCLE,
            labelOverride = "姨妈",
            inverseLabelOverride = "外甥/外甥女",
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "堂兄弟姐妹",
            "preset_tang_cousin",
            aliases = listOf("堂哥", "堂弟", "堂姐", "堂妹", "堂兄弟", "堂姐妹"),
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "表兄弟姐妹",
            "preset_biao_cousin",
            aliases = listOf("表哥", "表弟", "表姐", "表妹", "表兄弟", "表姐妹"),
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "侄子",
            InferenceRelationTypeIds.AUNT_UNCLE,
            subjectIsFrom = false,
            inverseLabelOverride = "侄子",
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "侄女",
            InferenceRelationTypeIds.AUNT_UNCLE,
            subjectIsFrom = false,
            inverseLabelOverride = "侄女",
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "外甥",
            InferenceRelationTypeIds.AUNT_UNCLE,
            subjectIsFrom = false,
            inverseLabelOverride = "外甥",
            group = KinshipGroup.EXTENDED,
        ),
        spec(
            "外甥女",
            InferenceRelationTypeIds.AUNT_UNCLE,
            subjectIsFrom = false,
            inverseLabelOverride = "外甥女",
            group = KinshipGroup.EXTENDED,
        ),
        // 姻亲
        spec(
            "嫂子",
            customName = "嫂子",
            customInverse = "小叔子/小姑子",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "弟媳",
            customName = "弟媳",
            customInverse = "大伯子/大姑子",
            group = KinshipGroup.IN_LAW,
        ),
        spec("姐夫", "preset_brother_in_law", group = KinshipGroup.IN_LAW),
        spec(
            "妹夫",
            customName = "妹夫",
            customInverse = "大舅子/大姨子",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "大娘",
            InferenceRelationTypeIds.AUNT_UNCLE_IN_LAW,
            labelOverride = "大娘",
            inverseLabelOverride = "侄子/侄女",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "婶婶",
            InferenceRelationTypeIds.AUNT_UNCLE_IN_LAW,
            labelOverride = "婶婶",
            inverseLabelOverride = "侄子/侄女",
            group = KinshipGroup.IN_LAW,
        ),
        spec("姑父", "preset_aunt_husband", group = KinshipGroup.IN_LAW),
        spec(
            "舅妈",
            InferenceRelationTypeIds.AUNT_UNCLE_IN_LAW,
            labelOverride = "舅妈",
            inverseLabelOverride = "外甥/外甥女",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "姨父",
            InferenceRelationTypeIds.AUNT_UNCLE_IN_LAW,
            labelOverride = "姨父",
            inverseLabelOverride = "外甥/外甥女",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "公公",
            InferenceRelationTypeIds.IN_LAW,
            labelOverride = "公公",
            inverseLabelOverride = "儿媳",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "婆婆",
            InferenceRelationTypeIds.IN_LAW,
            labelOverride = "婆婆",
            inverseLabelOverride = "儿媳",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "岳父",
            InferenceRelationTypeIds.IN_LAW,
            labelOverride = "岳父",
            inverseLabelOverride = "女婿",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "岳母",
            InferenceRelationTypeIds.IN_LAW,
            labelOverride = "岳母",
            inverseLabelOverride = "女婿",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "儿媳",
            InferenceRelationTypeIds.IN_LAW,
            subjectIsFrom = false,
            labelOverride = "公公/婆婆",
            inverseLabelOverride = "儿媳",
            group = KinshipGroup.IN_LAW,
        ),
        spec(
            "女婿",
            InferenceRelationTypeIds.IN_LAW,
            subjectIsFrom = false,
            labelOverride = "岳父/岳母",
            inverseLabelOverride = "女婿",
            group = KinshipGroup.IN_LAW,
        ),
    )

    /** 快建面板的分组展示顺序。 */
    val panelGroups: List<Pair<KinshipGroup, List<KinshipTermSpec>>> =
        KinshipGroup.entries.map { group -> group to all.filter { it.group == group } }

    private val specsByTerm: Map<String, KinshipTermSpec> = buildMap {
        all.forEach { entry ->
            put(entry.term, entry)
            entry.aliases.forEach { alias -> putIfAbsent(alias, entry) }
        }
    }

    /** 归一化后查找：处理「三舅」「老姨」这类排行前缀和「舅」「姑」单字称谓。 */
    fun lookup(term: String): KinshipTermSpec? {
        val text = term.trim()
        if (text.isEmpty()) return null
        if (text in specsByTerm) return specsByTerm[text]
        val stripped = text.dropWhile { it in RANKING_PREFIX_CHARS }
        return SINGLE_CHAR_ALIASES[stripped]?.let { specsByTerm[it] }
    }

    private val RANKING_PREFIX_CHARS = "大小老幺么一二三四五六七八九十"

    private val SINGLE_CHAR_ALIASES = mapOf(
        "舅" to "舅舅",
        "姑" to "姑姑",
        "姨" to "姨妈",
        "叔" to "叔叔",
        "伯" to "大爷",
        "爸" to "爸爸",
        "妈" to "妈妈",
        "爹" to "爸爸",
        "娘" to "妈妈",
        "哥" to "哥哥",
        "姐" to "姐姐",
        "弟" to "弟弟",
        "妹" to "妹妹",
    )

    private fun spec(
        term: String,
        typeId: String? = null,
        customName: String? = null,
        customInverse: String? = null,
        subjectIsFrom: Boolean = true,
        labelOverride: String? = null,
        inverseLabelOverride: String? = null,
        aliases: List<String> = emptyList(),
        group: KinshipGroup,
    ) = KinshipTermSpec(
        term = term,
        presetTypeId = typeId,
        customName = customName,
        customInverse = customInverse,
        subjectIsFrom = subjectIsFrom,
        labelOverride = labelOverride,
        inverseLabelOverride = inverseLabelOverride,
        group = group,
        aliases = aliases,
    )
}

sealed interface KinshipParseResult {
    data class Success(
        val subjectName: String,
        val relativeName: String,
        val spec: KinshipTermSpec,
    ) : KinshipParseResult

    data class Failure(val message: String) : KinshipParseResult
}

/**
 * 解析「A 是 B 的称谓」与「A 是 B 的妈妈的哥哥」这类句子，纯本地、离线可用。
 *
 * 句式边界（均为「安全失败」：明确报错，不会错误绑定）：
 * - 只支持「A 是 B 的…」结构，人名中不能含「是」或「的」；
 * - 组合链中的每一步都必须在词表内；「爷爷的儿子」这类多解链条不支持。
 */
object KinshipSentenceParser {

    fun parse(raw: String): KinshipParseResult {
        val text = raw.trim().trimEnd('.', '。', '，', ',', '!', '！', '?', '？', '、', ' ')
        if (text.isBlank()) return KinshipParseResult.Failure("请输入一句话")
        if (!text.contains("是")) {
            return KinshipParseResult.Failure("请用「某人 是 某人 的称谓」的格式")
        }
        val parts = text.split("是", limit = 2)
        val subjectName = parts[0].trim()
        val rest = parts[1].trim()
        if (subjectName.isBlank() || !rest.contains("的")) {
            return KinshipParseResult.Failure("没认出人名，例如「老王 是 小明 的舅舅」")
        }
        val segments = rest.split("的").map(String::trim)
        val relativeName = segments.first()
        val chain = segments.drop(1)
        if (relativeName.isBlank() || chain.isEmpty() || chain.any(String::isBlank)) {
            return KinshipParseResult.Failure("没认出称谓，例如「老王 是 小明 的舅舅」")
        }
        if (subjectName == relativeName) {
            return KinshipParseResult.Failure("两个人不能是同一位")
        }

        val steps = chain.map { step ->
            KinshipTermCatalog.lookup(step)
                ?: return KinshipParseResult.Failure("暂不认识称谓「$step」")
        }

        var composed = steps.first().term
        for (next in steps.drop(1)) {
            val combined = COMPOSE[composed to next.term] ?: return KinshipParseResult.Failure(
                "「${steps.first().term}的${chain.drop(1).joinToString("的")}」暂不支持，请换成具体称谓",
            )
            composed = combined
        }
        val spec = KinshipTermCatalog.lookup(composed)
            ?: return KinshipParseResult.Failure("暂不认识称谓「$composed」")
        return KinshipParseResult.Success(
            subjectName = subjectName,
            relativeName = relativeName,
            spec = spec,
        )
    }

    // 两两组合表：subject 的「X的Y」= ？ 例如「妈妈的哥哥」= 舅舅。
    private val COMPOSE: Map<Pair<String, String>, String> = mapOf(
        "爸爸" to "爸爸" to "爷爷",
        "爸爸" to "妈妈" to "奶奶",
        "妈妈" to "爸爸" to "外公",
        "妈妈" to "妈妈" to "外婆",
        "爸爸" to "哥哥" to "大爷",
        "爸爸" to "弟弟" to "叔叔",
        "爸爸" to "姐姐" to "姑姑",
        "爸爸" to "妹妹" to "姑姑",
        "妈妈" to "哥哥" to "舅舅",
        "妈妈" to "弟弟" to "舅舅",
        "妈妈" to "姐姐" to "姨妈",
        "妈妈" to "妹妹" to "姨妈",
        "哥哥" to "儿子" to "侄子",
        "哥哥" to "女儿" to "侄女",
        "弟弟" to "儿子" to "侄子",
        "弟弟" to "女儿" to "侄女",
        "姐姐" to "儿子" to "外甥",
        "姐姐" to "女儿" to "外甥女",
        "妹妹" to "儿子" to "外甥",
        "妹妹" to "女儿" to "外甥女",
        "儿子" to "儿子" to "孙子",
        "儿子" to "女儿" to "孙女",
        "女儿" to "儿子" to "外孙",
        "女儿" to "女儿" to "外孙女",
        "哥哥" to "妻子" to "嫂子",
        "弟弟" to "妻子" to "弟媳",
        "姐姐" to "丈夫" to "姐夫",
        "妹妹" to "丈夫" to "妹夫",
        "儿子" to "妻子" to "儿媳",
        "女儿" to "丈夫" to "女婿",
        "丈夫" to "爸爸" to "公公",
        "丈夫" to "妈妈" to "婆婆",
        "妻子" to "爸爸" to "岳父",
        "妻子" to "妈妈" to "岳母",
        "大爷" to "儿子" to "堂兄弟",
        "大爷" to "女儿" to "堂姐妹",
        "叔叔" to "儿子" to "堂兄弟",
        "叔叔" to "女儿" to "堂姐妹",
        "姑姑" to "儿子" to "表兄弟",
        "姑姑" to "女儿" to "表姐妹",
        "舅舅" to "儿子" to "表兄弟",
        "舅舅" to "女儿" to "表姐妹",
        "姨妈" to "儿子" to "表兄弟",
        "姨妈" to "女儿" to "表姐妹",
    )
}
