package com.relationship.graph.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.relationship.graph.data.GraphData
import com.relationship.graph.data.RelationshipRepository
import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.local.GraphPositionEntity
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.TagEntity
import com.relationship.graph.data.local.PersonTagEntity
import java.io.File
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class BackupEnvelope(
    val formatVersion: Int,
    val schemaVersion: Int,
    val createdAt: Long,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val cipher: String,
    val iv: String,
    val payload: String,
)

private data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val people: List<PersonEntity>,
    val tags: List<TagEntity>,
    val personTags: List<PersonTagEntity>,
    val relationTypes: List<RelationTypeEntity>,
    val relationships: List<RelationshipEntity>,
    val graphPositions: List<GraphPositionEntity>?,
    val avatars: Map<String, String>,
    val settings: Map<String, String>,
)

class BackupManager(
    private val context: Context,
    private val repository: RelationshipRepository,
    private val gson: Gson = Gson(),
) {
    suspend fun export(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        require(password.length >= MIN_BACKUP_PASSWORD_LENGTH) {
            "备份密码至少需要 $MIN_BACKUP_PASSWORD_LENGTH 位"
        }
        val data = repository.getGraphData()
        val avatars = data.people.mapNotNull { person ->
            person.avatarPath
                ?.let(repository::readAvatar)
                ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                ?.let { person.id to it }
        }.toMap()
        val payload = BackupPayload(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            people = data.people,
            tags = data.tags,
            personTags = data.personTags,
            relationTypes = data.relationTypes,
            relationships = data.relationships,
            graphPositions = data.graphPositions,
            avatars = avatars,
            settings = emptyMap(),
        )
        val payloadBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val salt = ByteArray(SALT_LENGTH).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_LENGTH).also(SecureRandom()::nextBytes)
        val encrypted = encrypt(payloadBytes, password, salt, iv)
        val envelope = BackupEnvelope(
            formatVersion = BACKUP_FORMAT_VERSION,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
            kdf = KDF_ALGORITHM,
            iterations = PBKDF2_ITERATIONS,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            cipher = CIPHER_TRANSFORMATION,
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            payload = Base64.encodeToString(encrypted, Base64.NO_WRAP),
        )
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("无法创建备份文件")
        output.use { it.write(gson.toJson(envelope).toByteArray(Charsets.UTF_8)) }
    }

    suspend fun import(uri: Uri, password: String): GraphData = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取备份文件")
        val envelope: BackupEnvelope = runCatching {
            gson.fromJson(bytes.toString(Charsets.UTF_8), BackupEnvelope::class.java)
        }.getOrNull() ?: error("备份文件格式不正确")

        require(envelope.formatVersion == BACKUP_FORMAT_VERSION) { "不支持此备份文件版本" }
        require(envelope.schemaVersion <= CURRENT_SCHEMA_VERSION) { "备份来自更高版本，无法恢复" }
        require(envelope.kdf == KDF_ALGORITHM && envelope.cipher == CIPHER_TRANSFORMATION) {
            "备份文件使用了不支持的加密方式"
        }

        val salt = Base64.decode(envelope.salt, Base64.NO_WRAP)
        val iv = Base64.decode(envelope.iv, Base64.NO_WRAP)
        val encrypted = Base64.decode(envelope.payload, Base64.NO_WRAP)
        val decrypted = try {
            decrypt(encrypted, password, salt, iv, envelope.iterations)
        } catch (_: AEADBadTagException) {
            error("备份密码错误或文件已损坏")
        } catch (_: IllegalArgumentException) {
            error("备份文件已损坏")
        }

        val payload: BackupPayload = runCatching {
            gson.fromJson(decrypted.toString(Charsets.UTF_8), BackupPayload::class.java)
        }.getOrNull() ?: error("备份内容无法解析")
        runCatching { validate(payload) }.getOrElse {
            error(it.message ?: "备份内容校验失败")
        }

        payload.avatars.forEach { (personId, encoded) ->
            val bytesForAvatar = Base64.decode(encoded, Base64.NO_WRAP)
            repository.writeAvatar(repository.avatarFile(personId).absolutePath, bytesForAvatar)
        }
        val importedPeople = payload.people.map { person ->
            person.copy(
                avatarPath = if (payload.avatars.containsKey(person.id)) {
                    repository.avatarFile(person.id).absolutePath
                } else {
                    null
                },
            )
        }
        val personIds = importedPeople.map { it.id }.toSet()
        val importedPositions = payload.graphPositions.orEmpty()
            .filter { it.personId in personIds }
            .filter { it.mode in GraphMode.entries }
        GraphData(
            people = importedPeople,
            tags = payload.tags,
            personTags = payload.personTags,
            relationTypes = payload.relationTypes,
            relationships = payload.relationships,
            graphPositions = importedPositions,
        )
    }

    private fun validate(payload: BackupPayload) {
        require(payload.schemaVersion <= CURRENT_SCHEMA_VERSION) { "备份内容版本不兼容" }
        val personIds = payload.people.map { it.id }.toSet()
        require(personIds.size == payload.people.size) { "备份中存在重复人物" }
        val tagIds = payload.tags.map { it.id }.toSet()
        val typeIds = payload.relationTypes.map { it.id }.toSet()
        require(payload.relationships.all { it.fromPersonId in personIds && it.toPersonId in personIds }) {
            "备份中存在无效的人物关系"
        }
        require(payload.relationships.all { it.relationTypeId in typeIds }) {
            "备份中存在无效的关系类型"
        }
        require(payload.personTags.all { it.personId in personIds && it.tagId in tagIds }) {
            "备份中存在无效的人物标签"
        }
        require(payload.graphPositions.orEmpty().all { it.personId in personIds }) {
            "备份中存在无效的图谱位置"
        }
    }

    private fun encrypt(
        plainText: ByteArray,
        password: String,
        salt: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt, PBKDF2_ITERATIONS),
            GCMParameterSpec(128, iv),
        )
        return cipher.doFinal(plainText)
    }

    private fun decrypt(
        encrypted: ByteArray,
        password: String,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int,
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, salt, iterations),
            GCMParameterSpec(128, iv),
        )
        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    companion object {
        const val MIN_BACKUP_PASSWORD_LENGTH = 6
        const val BACKUP_FORMAT_VERSION = 1
        const val CURRENT_SCHEMA_VERSION = 2
        const val MIME_TYPE = "application/octet-stream"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
    }
}
