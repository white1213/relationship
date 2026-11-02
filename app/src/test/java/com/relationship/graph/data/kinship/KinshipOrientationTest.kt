package com.relationship.graph.data.kinship

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归测试：新建的人（快建/占位）必须站在「称谓所指」的位置。
 * 历史 bug：快建选「儿子」建出来的是对方的爸爸（方向整体反转）。
 */
class KinshipOrientationTest {

    private val new = "NEW"
    private val anchor = "ANCHOR"

    private fun orientationOf(term: String): Pair<String, String> =
        KinshipTermCatalog.lookup(term)!!.orientation(new, anchor)

    @Test
    fun juniorTermsPutNewPersonAsChildSide() {
        // 父母子女类 from 是父母：新人是儿子时 from 必须是「本人」。
        listOf("儿子", "女儿").forEach { term ->
            assertEquals("term=$term", anchor to new, orientationOf(term))
        }
        // 姻亲晚辈同理：长辈在前。
        listOf("儿媳", "女婿").forEach { term ->
            assertEquals("term=$term", anchor to new, orientationOf(term))
        }
        // 侄辈：叔伯一方在前。
        listOf("侄子", "侄女", "外甥", "外甥女").forEach { term ->
            assertEquals("term=$term", anchor to new, orientationOf(term))
        }
        listOf("孙子", "孙女", "外孙", "外孙女").forEach { term ->
            assertEquals("term=$term", anchor to new, orientationOf(term))
        }
    }

    @Test
    fun seniorTermsPutNewPersonFirst() {
        listOf(
            "爸爸", "妈妈", "爷爷", "奶奶", "外公", "外婆",
            "舅舅", "姑姑", "叔叔", "大爷", "姨妈",
            "哥哥", "弟弟", "姐姐", "妹妹",
            "嫂子", "弟媳", "姐夫", "妹夫",
            "大娘", "婶婶", "姑父", "舅妈", "姨父",
            "公公", "婆婆", "岳父", "岳母",
        ).forEach { term ->
            assertEquals("term=$term", new to anchor, orientationOf(term))
        }
    }

    @Test
    fun existenceSentencePlaceholderStandsInTermPosition() {
        // 「老王有个舅舅」：占位人就是舅舅 → from=占位。
        val uncle = KinshipSentenceParser.parse("老王有个舅舅") as KinshipParseResult.Success
        assertEquals(
            uncle.subjectName to "老王",
            uncle.spec.orientation(uncle.subjectName, uncle.relativeName),
        )

        // 「老王有个儿子」：占位人是儿子 → from=老王。
        val son = KinshipSentenceParser.parse("老王有个儿子") as KinshipParseResult.Success
        assertEquals(
            "老王" to son.subjectName,
            son.spec.orientation(son.subjectName, son.relativeName),
        )
    }
}
