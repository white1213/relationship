package com.relationship.graph.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarPath: String? = null,
    val phone: String = "",
    val birthday: String = "",
    val address: String = "",
    val notes: String = "",
    val graphX: Float = 0f,
    val graphY: Float = 0f,
    val positionInitialized: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
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
)

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
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

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
