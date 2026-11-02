package com.relationship.graph.data.kinship

import com.relationship.graph.data.local.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceholderResolverTest {

    private val uncleSpec = KinshipTermCatalog.lookup("舅舅")!!

    private fun person(id: String, name: String, placeholder: Boolean = false) = PersonEntity(
        id = id,
        name = name,
        isPlaceholder = placeholder,
    )

    @Test
    fun findsPlaceholderWithExactGeneratedName() {
        val people = listOf(
            person("wang", "老王"),
            person("ph", "老王的舅舅", placeholder = true),
        )

        assertEquals("ph", PlaceholderResolver.findFillTarget(people, "老王", uncleSpec)?.id)
    }

    @Test
    fun ignoresRealPersonWithSameName() {
        val people = listOf(
            person("wang", "老王"),
            person("real", "老王的舅舅"),
        )

        assertNull(PlaceholderResolver.findFillTarget(people, "老王", uncleSpec))
    }

    @Test
    fun multipleSamePlaceholdersFallBackToNull() {
        val people = listOf(
            person("ph1", "老王的舅舅", placeholder = true),
            person("ph2", "老王的舅舅", placeholder = true),
        )

        assertNull(PlaceholderResolver.findFillTarget(people, "老王", uncleSpec))
    }

    @Test
    fun differentTermDoesNotMatch() {
        val people = listOf(
            person("ph", "老王的叔叔", placeholder = true),
        )

        assertNull(PlaceholderResolver.findFillTarget(people, "老王", uncleSpec))
    }

    @Test
    fun placeholderNameIsDerivedFromRelativeAndTerm() {
        assertEquals("老王的舅舅", PlaceholderResolver.placeholderName("老王", "舅舅"))
        assertEquals("小明的表哥", PlaceholderResolver.placeholderName(" 小明 ", "表哥"))
    }
}
