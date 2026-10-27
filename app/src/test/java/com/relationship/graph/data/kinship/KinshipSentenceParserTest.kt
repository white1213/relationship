package com.relationship.graph.data.kinship

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KinshipSentenceParserTest {

    private fun parseTerm(text: String): String {
        val result = KinshipSentenceParser.parse(text)
        assertTrue("应解析成功: $text -> $result", result is KinshipParseResult.Success)
        return (result as KinshipParseResult.Success).spec.term
    }

    @Test
    fun simpleSentenceResolvesTerm() {
        val result = KinshipSentenceParser.parse("老王是小明的舅舅")

        assertTrue(result is KinshipParseResult.Success)
        result as KinshipParseResult.Success
        assertEquals("老王", result.subjectName)
        assertEquals("小明", result.relativeName)
        assertEquals("舅舅", result.spec.term)
        assertTrue(result.spec.subjectIsFrom)
    }

    @Test
    fun rankingPrefixIsStripped() {
        assertEquals("舅舅", parseTerm("老王是小明的三舅"))
        assertEquals("姑姑", parseTerm("老王是小明的小姑"))
        assertEquals("姨妈", parseTerm("老王是小明的大姨"))
        assertEquals("叔叔", parseTerm("老王是小明的四叔"))
    }

    @Test
    fun twoStepChainResolves() {
        assertEquals("舅舅", parseTerm("老王是小明的妈妈的哥哥"))
        assertEquals("姑姑", parseTerm("老王是小明的爸爸的姐姐"))
        assertEquals("奶奶", parseTerm("老王是小明的爸爸的妈妈"))
        assertEquals("嫂子", parseTerm("老王是小明的哥哥的妻子"))
    }

    @Test
    fun threeStepChainResolvesToCousin() {
        val result = KinshipSentenceParser.parse("小刚是小明的爸爸的姐姐的儿子")
        assertTrue(result is KinshipParseResult.Success)
        assertEquals("表兄弟姐妹", (result as KinshipParseResult.Success).spec.term)

        assertEquals("堂兄弟姐妹", parseTerm("小刚是小明的大爷的儿子"))
    }

    @Test
    fun juniorTermStoresSubjectAsToSide() {
        val result = KinshipSentenceParser.parse("老王是小明的侄子")
        assertTrue(result is KinshipParseResult.Success)
        result as KinshipParseResult.Success
        assertFalse(result.spec.subjectIsFrom)
        assertEquals("侄子", result.spec.inverseLabelOverride)
    }

    @Test
    fun spouseTermUsesSpouseType() {
        val result = KinshipSentenceParser.parse("老张是小丽的老公")
        assertTrue(result is KinshipParseResult.Success)
        assertEquals("preset_spouse", (result as KinshipParseResult.Success).spec.presetTypeId)
    }

    @Test
    fun malformedSentencesFailWithMessage() {
        val noShi = KinshipSentenceParser.parse("老王小明的舅舅")
        assertTrue(noShi is KinshipParseResult.Failure)

        val noDe = KinshipSentenceParser.parse("老王是舅舅")
        assertTrue(noDe is KinshipParseResult.Failure)

        val unknownTerm = KinshipSentenceParser.parse("老王是小明的邻居的表叔")
        assertTrue(unknownTerm is KinshipParseResult.Failure)
        assertTrue((unknownTerm as KinshipParseResult.Failure).message.contains("邻居"))

        val samePerson = KinshipSentenceParser.parse("小明是小明的哥哥")
        assertTrue(samePerson is KinshipParseResult.Failure)

        val unsupported = KinshipSentenceParser.parse("老王是小明的爷爷的儿子")
        assertTrue(unsupported is KinshipParseResult.Failure)
    }

    @Test
    fun catalogLookupNormalizesSingleCharTerms() {
        assertEquals("舅舅", KinshipTermCatalog.lookup("舅")?.term)
        assertEquals("姑姑", KinshipTermCatalog.lookup("三姑")?.term)
        assertEquals("大爷", KinshipTermCatalog.lookup("伯")?.term)
        assertNull(KinshipTermCatalog.lookup("邻居"))
    }

    @Test
    fun panelGroupsCoverAllTermsExactlyOnce() {
        val grouped = KinshipTermCatalog.panelGroups.flatMap { it.second }
        assertEquals(KinshipTermCatalog.all.size, grouped.size)
    }
}
