package com.relationship.graph.data.kinship

/** 一句话录入 / 快建提交的结果：重复时携带已有关系 id，供 UI 跳转编辑。 */
sealed interface KinshipCommitResult {
    data object Success : KinshipCommitResult
    data class Duplicate(val relationshipId: String, val message: String) : KinshipCommitResult
    data class Failure(val message: String) : KinshipCommitResult
}
