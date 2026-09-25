package com.relationship.graph.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "'UNSPECIFIED'")
    val gender: Gender = Gender.UNSPECIFIED,
    val avatarPath: String? = null,
    val phone: String = "",
    val birthday: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class Gender {
    UNSPECIFIED,
    MALE,
    FEMALE,
}

enum class GraphMode {
    FAMILY,
    SOCIAL,
    ALL,
}

@Entity(
    tableName = "graph_positions",
    primaryKeys = ["personId", "mode"],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class GraphPositionEntity(
    val personId: String,
    val mode: GraphMode,
    val x: Float,
    val y: Float,
    val isManuallyPinned: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags", indices = [Index(value = ["normalizedName"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
)

@Entity(
    tableName = "person_tags",
    primaryKeys = ["personId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId"), Index("tagId")],
)
data class PersonTagEntity(
    val personId: String,
    val tagId: String,
)

enum class RelationCategory {
    FAMILY,
    SOCIAL,
    CUSTOM,
}

enum class RelationDirection {
    BIDIRECTIONAL,
    DIRECTED,
}

@Entity(
    tableName = "relation_types",
    indices = [Index(value = ["name"], unique = true)],
)
data class RelationTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val inverseName: String? = null,
    val category: RelationCategory,
    val direction: RelationDirection,
    val isBuiltIn: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isInferenceOnly: Boolean = false,
)

enum class RelationshipSource {
    MANUAL,
    CONFIRMED_INFERENCE,
}

@Entity(
    tableName = "relationships",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromPersonId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["toPersonId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RelationTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["relationTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("fromPersonId"),
        Index("toPersonId"),
        Index("relationTypeId"),
    ],
)
data class RelationshipEntity(
    @PrimaryKey val id: String,
    val fromPersonId: String,
    val toPersonId: String,
    val relationTypeId: String,
    @ColumnInfo(defaultValue = "'MANUAL'")
    val source: RelationshipSource = RelationshipSource.MANUAL,
    val labelOverride: String? = null,
    val inverseLabelOverride: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "inference_dismissals",
    primaryKeys = ["fromPersonId", "toPersonId", "ruleId"],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromPersonId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["toPersonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fromPersonId"), Index("toPersonId")],
)
data class InferenceDismissalEntity(
    val fromPersonId: String,
    val toPersonId: String,
    val ruleId: String,
    val evidenceFingerprint: String,
    val dismissedAt: Long = System.currentTimeMillis(),
)

enum class AgeComparison {
    FIRST_OLDER,
    SECOND_OLDER,
    SAME_AGE,
}

@Entity(
    tableName = "relative_age_orders",
    primaryKeys = ["firstPersonId", "secondPersonId"],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["firstPersonId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["secondPersonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("firstPersonId"), Index("secondPersonId")],
)
data class RelativeAgeOrderEntity(
    val firstPersonId: String,
    val secondPersonId: String,
    val comparison: AgeComparison,
    val updatedAt: Long = System.currentTimeMillis(),
)

object InferenceRelationTypeIds {
    const val GRANDPARENT = "preset_grandparent"
    const val AUNT_UNCLE = "preset_aunt_uncle"
    const val AUNT_UNCLE_IN_LAW = "preset_aunt_uncle_in_law"
    const val COUSIN = "preset_cousin"
    const val IN_LAW = "preset_in_law"
    const val SIBLING_IN_LAW = "preset_sibling_in_law"
    const val STEP_PARENT = "preset_step_parent"
}

object PresetRelationTypes {
    val all = listOf(
        RelationTypeEntity(
            id = "preset_parent_child",
            name = "父母",
            inverseName = "子女",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
        ),
        RelationTypeEntity(
            id = InferenceRelationTypeIds.GRANDPARENT,
            name = "祖父母",
            inverseName = "孙辈",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
            isInferenceOnly = true,
        ),
        RelationTypeEntity(
            id = InferenceRelationTypeIds.AUNT_UNCLE,
            name = "大爷/叔伯/舅姨",
            inverseName = "侄辈/外甥辈",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
            isInferenceOnly = true,
        ),
        RelationTypeEntity(
            id = InferenceRelationTypeIds.AUNT_UNCLE_IN_LAW,
            name = "大爷/叔伯/舅姨的配偶",
            inverseName = "侄辈/外甥辈",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
            isInferenceOnly = true,
        ),
        RelationTypeEntity(
            id = InferenceRelationTypeIds.COUSIN,
            name = "堂表亲",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
            isInferenceOnly = true,
        ),
        RelationTypeEntity(
            id = InferenceRelationTypeIds.IN_LAW,
            name = "姻亲长辈",
            inverseName = "姻亲晚辈",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
            isInferenceOnly = true,
        ),
        RelationTypeEntity(
            id = InferenceRelationTypeIds.SIBLING_IN_LAW,
            name = "兄弟姐妹的配偶",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
            isInferenceOnly = true,
        ),
        RelationTypeEntity(
            id = InferenceRelationTypeIds.STEP_PARENT,
            name = "继父母",
            inverseName = "继子女",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
            isInferenceOnly = true,
        ),
        RelationTypeEntity(
            id = "preset_spouse",
            name = "配偶/伴侣",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
        ),
        RelationTypeEntity(
            id = "preset_sibling",
            name = "兄弟姐妹",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
        ),
        RelationTypeEntity(
            id = "preset_friend",
            name = "朋友",
            category = RelationCategory.SOCIAL,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
        ),
        RelationTypeEntity(
            id = "preset_colleague",
            name = "同事",
            category = RelationCategory.SOCIAL,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
        ),
        RelationTypeEntity(
            id = "preset_classmate",
            name = "同学",
            category = RelationCategory.SOCIAL,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
        ),
        RelationTypeEntity(
            id = "preset_neighbor",
            name = "邻居",
            category = RelationCategory.SOCIAL,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
        ),
    )
}
