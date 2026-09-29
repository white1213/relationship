package com.relationship.graph.data.inference

import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KinshipValidatorTest {

    private val relationTypes = PresetRelationTypes.all

    private fun type(id: String) = relationTypes.first { it.id == id }

    private fun person(id: String, gender: Gender = Gender.UNSPECIFIED) =
        PersonEntity(id = id, name = id, gender = gender)

    private fun peopleById(vararg people: PersonEntity) = people.associateBy { it.id }

    private fun relationship(
        id: String,
        from: String,
        to: String,
        typeId: String,
    ) = RelationshipEntity(
        id = id,
        fromPersonId = from,
        toPersonId = to,
        relationTypeId = typeId,
    )

    private fun validate(
        existing: List<RelationshipEntity>,
        candidate: RelationshipEntity,
        vararg people: PersonEntity,
    ): KinshipValidator.Result = KinshipValidator.validate(
        existingRelationships = existing,
        relationTypes = relationTypes,
        peopleById = peopleById(*people),
        candidate = candidate,
    )

    @Test
    fun `simple parent child is allowed`() {
        val result = validate(
            existing = emptyList(),
            candidate = relationship("r", "p", "c", "preset_parent_child"),
            person("p"), person("c"),
        )
        assertTrue(result.ok)
    }

    @Test
    fun `parent child cycle is blocked`() {
        // 现有：p 是 c 的父亲。候选：c 是 p 的父亲 → 环。
        val result = validate(
            existing = listOf(relationship("r1", "p", "c", "preset_parent_child")),
            candidate = relationship("r2", "c", "p", "preset_parent_child"),
            person("p"), person("c"),
        )
        assertFalse(result.ok)
        assertEquals(KinshipValidator.ConflictType.CYCLE, result.conflicts.first().type)
    }

    @Test
    fun `grandparent grandchild cycle via three hops is blocked`() {
        val existing = listOf(
            relationship("r1", "a", "b", "preset_parent_child"),
            relationship("r2", "b", "c", "preset_parent_child"),
        )
        val result = validate(
            existing = existing,
            candidate = relationship("r3", "c", "a", "preset_parent_child"),
            person("a"), person("b"), person("c"),
        )
        assertFalse(result.ok)
        assertEquals(KinshipValidator.ConflictType.CYCLE, result.conflicts.first().type)
    }

    @Test
    fun `ancestor marriage is blocked`() {
        // 现有：p 是 c 的父亲。候选：p 与 c 结为配偶 → 拦截。
        val result = validate(
            existing = listOf(relationship("r1", "p", "c", "preset_parent_child")),
            candidate = relationship("r2", "p", "c", "preset_spouse"),
            person("p", Gender.MALE), person("c", Gender.FEMALE),
        )
        assertFalse(result.ok)
        assertEquals(
            KinshipValidator.ConflictType.ANCESTOR_MARRIAGE,
            result.conflicts.first().type,
        )
    }

    @Test
    fun `unrelated spouse marriage is allowed`() {
        val result = validate(
            existing = listOf(relationship("r1", "p", "c", "preset_parent_child")),
            candidate = relationship("r2", "p", "x", "preset_spouse"),
            person("p"), person("c"), person("x"),
        )
        assertTrue(result.ok)
    }

    @Test
    fun `generation mismatch through mixed relations is blocked`() {
        // 现有：a 与 b 同辈（兄弟），b 是 c 的父亲。
        // 候选：a 是 c 的儿子 → 矛盾（c 与 b 同辈则 a 应与 b 同辈，而候选要求 a 低一辈）。
        val existing = listOf(
            relationship("r1", "a", "b", "preset_sibling"),
            relationship("r2", "b", "c", "preset_parent_child"),
        )
        val result = validate(
            existing = existing,
            candidate = relationship("r3", "c", "a", "preset_parent_child"),
            person("a"), person("b"), person("c"),
        )
        assertFalse(result.ok)
        assertEquals(
            KinshipValidator.ConflictType.GENERATION_MISMATCH,
            result.conflicts.first().type,
        )
    }

    @Test
    fun `consistent cousin chain is allowed`() {
        // 祖父 G 有两个儿子 P 和 U；P 的孩子 C1 与 U 的孩子 C2 是堂兄弟。
        val existing = listOf(
            relationship("r1", "g", "p", "preset_parent_child"),
            relationship("r2", "g", "u", "preset_parent_child"),
            relationship("r3", "p", "c1", "preset_parent_child"),
            relationship("r4", "u", "c2", "preset_parent_child"),
            relationship("r5", "c1", "c2", "preset_tang_cousin"),
        )
        // 候选：g 是 c1 的祖父类型边之外，再给 c2 添加姑姑（aunt）＝ u 的姐妹 s。
        val result = validate(
            existing = existing,
            candidate = relationship("r6", "s", "c2", "preset_parent_child"),
            person("g"), person("p"), person("u"), person("c1"), person("c2"), person("s"),
        )
        assertTrue(result.ok)
    }

    @Test
    fun `editing a relationship excludes its own old edges`() {
        // 修改已有父子关系为反向：不应因自身旧边而误判成环。
        val existing = listOf(relationship("r1", "p", "c", "preset_parent_child"))
        val result = validate(
            existing = existing,
            candidate = relationship("r1", "c", "p", "preset_parent_child"),
            person("p"), person("c"),
        )
        assertTrue(result.ok)
    }

    @Test
    fun `self parent child is blocked`() {
        val result = validate(
            existing = emptyList(),
            candidate = relationship("r", "x", "x", "preset_parent_child"),
            person("x"),
        )
        assertFalse(result.ok)
    }

    @Test
    fun `social relations impose no generation constraint`() {
        val existing = listOf(
            relationship("r1", "a", "b", "preset_friend"),
            relationship("r2", "b", "c", "preset_parent_child"),
        )
        val result = validate(
            existing = existing,
            candidate = relationship("r3", "c", "a", "preset_friend"),
            person("a"), person("b"), person("c"),
        )
        assertTrue(result.ok)
    }
}
