package com.relationship.graph.data.kinship

import com.relationship.graph.data.local.PersonEntity

/**
 * 占位人物的识别与智能补全：
 * 一句话里出现「张伟是老王的舅舅」时，如果库里已有名叫「老王的舅舅」的占位人物，
 * 就用它承载这条关系并把名字改成张伟，而不是再建一个重复的人。
 *
 * 只按占位名精确匹配，不按关系类型匹配——「大爷/叔叔/舅舅」共用同一种推理类型，
 * 按类型匹配会把不同的人错填成同一个。
 */
object PlaceholderResolver {

    fun placeholderName(relativeName: String, term: String): String =
        KinshipSentenceParser.placeholderNameOf(relativeName, term)

    /** 找到可以承载这条关系的占位人物；0 个或多个同名占位时返回 null，回退到普通新建流程。 */
    fun findFillTarget(
        people: List<PersonEntity>,
        relativeName: String,
        spec: KinshipTermSpec,
    ): PersonEntity? {
        val target = placeholderName(relativeName, spec.term)
        return people.filter { it.isPlaceholder && it.name == target }.singleOrNull()
    }
}
