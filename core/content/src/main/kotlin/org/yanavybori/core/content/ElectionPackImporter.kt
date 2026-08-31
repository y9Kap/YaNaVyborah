package org.yanavybori.core.content

import android.content.Context
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.yanavybori.core.common.ElectionPackRepository
import org.yanavybori.core.crypto.Sha256
import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistItem
import org.yanavybori.core.model.ComplaintTemplate
import org.yanavybori.core.model.ElectionPackContent
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.model.LawReference
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReferenceDocument
import org.yanavybori.core.model.Situation
import org.yanavybori.core.model.VotingDayDefinition

private fun String.isSafePackPath(): Boolean =
    isNotBlank() && !startsWith('/') && !contains("..") && !contains('\\')

interface ElectionPackSource {
    suspend fun read(path: String): ByteArray
}

class AssetElectionPackSource(
    context: Context,
    private val root: String,
) : ElectionPackSource {
    private val assets = context.applicationContext.assets

    override suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        require(path.isSafePackPath()) { "Недопустимый путь в Election Pack: $path" }
        try {
            assets.open("$root/$path").use { it.readBytes() }
        } catch (error: FileNotFoundException) {
            throw ElectionPackImportException.MissingFile(path, error)
        }
    }
}

sealed interface ElectionPackImportResult {
    data class Installed(val manifest: ElectionPackManifest) : ElectionPackImportResult
    data class AlreadyCurrent(val manifest: ElectionPackManifest) : ElectionPackImportResult
}

sealed class ElectionPackImportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidManifest(message: String, cause: Throwable? = null) : ElectionPackImportException(message, cause)
    class UnsupportedSchema(val actual: Int) : ElectionPackImportException(
        "Версия схемы Election Pack $actual не поддерживается",
    )
    class MissingFile(val path: String, cause: Throwable? = null) : ElectionPackImportException(
        "В Election Pack отсутствует файл $path",
        cause,
    )
    class HashMismatch(val path: String) : ElectionPackImportException(
        "Контрольная сумма файла $path не совпадает",
    )
    class InvalidContent(message: String, cause: Throwable? = null) : ElectionPackImportException(message, cause)
}

class ElectionPackImporter(
    private val repository: ElectionPackRepository,
    private val json: Json = Json { ignoreUnknownKeys = false; explicitNulls = false },
) {
    suspend fun import(source: ElectionPackSource): ElectionPackImportResult {
        val manifest = parseManifest(source.read(MANIFEST_PATH))
        validateManifest(manifest)
        val current = repository.activeManifest()
        if (current?.id == manifest.id && current.contentVersion >= manifest.contentVersion) {
            return ElectionPackImportResult.AlreadyCurrent(current)
        }

        val verifiedFiles = manifest.files.associate { file ->
            require(file.path.isSafePackPath()) { "Недопустимый путь ${file.path}" }
            val bytes = source.read(file.path)
            val actualHash = Sha256.digest(bytes)
            if (!actualHash.equals(file.sha256, ignoreCase = true) ||
                manifest.hashes[file.path]?.equals(actualHash, ignoreCase = true) == false
            ) {
                throw ElectionPackImportException.HashMismatch(file.path)
            }
            file.path to bytes
        }
        val content = try {
            val referenceDocuments = verifiedFiles
                .decodeRequired<List<ReferenceDocument>>(REFERENCE_DOCUMENTS_PATH)
                .map { document ->
                    if (document.mimeType.startsWith("text/")) {
                        document.copy(content = verifiedFiles[document.contentPath]?.decodeToString().orEmpty())
                    } else {
                        document
                    }
                }
            ElectionPackContent(
                manifest = manifest,
                votingDays = verifiedFiles.decodeRequired(VOTING_DAYS_PATH),
                checklistDefinitions = verifiedFiles.decodeRequired(CHECKLIST_DEFINITIONS_PATH),
                checklistItems = verifiedFiles.decodeRequired(CHECKLIST_ITEMS_PATH),
                situations = verifiedFiles.decodeRequired(SITUATIONS_PATH),
                lawReferences = verifiedFiles.decodeRequired(LAWS_PATH),
                complaintTemplates = verifiedFiles.decodeRequired(COMPLAINT_TEMPLATES_PATH),
                reconciliationDefinitions = verifiedFiles.decodeRequired(RECONCILIATIONS_PATH),
                referenceDocuments = referenceDocuments,
            )
        } catch (error: ElectionPackImportException) {
            throw error
        } catch (error: SerializationException) {
            throw ElectionPackImportException.InvalidContent("Некорректный JSON в Election Pack", error)
        } catch (error: IllegalArgumentException) {
            throw ElectionPackImportException.InvalidContent(error.message ?: "Некорректное содержимое", error)
        }
        try {
            validateReferences(content)
        } catch (error: IllegalArgumentException) {
            throw ElectionPackImportException.InvalidContent(error.message ?: "Некорректные ссылки в пакете", error)
        }
        repository.replaceAtomically(content)
        return ElectionPackImportResult.Installed(manifest)
    }

    private fun parseManifest(bytes: ByteArray): ElectionPackManifest = try {
        json.decodeFromString(bytes.decodeToString())
    } catch (error: Exception) {
        throw ElectionPackImportException.InvalidManifest("Не удалось прочитать manifest.json", error)
    }

    private fun validateManifest(manifest: ElectionPackManifest) {
        if (manifest.schemaVersion !in MIN_SUPPORTED_SCHEMA_VERSION..SUPPORTED_SCHEMA_VERSION) {
            throw ElectionPackImportException.UnsupportedSchema(manifest.schemaVersion)
        }
        if (manifest.id.isBlank() || manifest.name.isBlank() || manifest.publisher.isBlank()) {
            throw ElectionPackImportException.InvalidManifest("id, name и publisher обязательны")
        }
        if (manifest.contentVersion < 1 || manifest.files.isEmpty()) {
            throw ElectionPackImportException.InvalidManifest("contentVersion и список files некорректны")
        }
        val paths = manifest.files.map { it.path }
        if (paths.size != paths.distinct().size) {
            throw ElectionPackImportException.InvalidManifest("Пути файлов в manifest должны быть уникальны")
        }
        if (!REQUIRED_PATHS.all(paths::contains)) {
            throw ElectionPackImportException.InvalidManifest("Election Pack не содержит обязательные разделы")
        }
        if (manifest.files.any { manifest.hashes[it.path] != null &&
                !manifest.hashes.getValue(it.path).equals(it.sha256, ignoreCase = true) }) {
            throw ElectionPackImportException.InvalidManifest("Поля files и hashes противоречат друг другу")
        }
        if (manifest.files.any { it.path !in manifest.hashes }) {
            throw ElectionPackImportException.InvalidManifest("Для каждого файла требуется запись в hashes")
        }
        if (manifest.emergencyContacts.map { it.id }.distinct().size != manifest.emergencyContacts.size) {
            throw ElectionPackImportException.InvalidManifest("Идентификаторы экстренных контактов должны быть уникальны")
        }
        if (manifest.emergencyContacts.any { it.id.isBlank() || it.title.isBlank() || it.phone.isBlank() }) {
            throw ElectionPackImportException.InvalidManifest("У экстренного контакта обязательны id, title и phone")
        }
    }

    private fun validateReferences(content: ElectionPackContent) {
        val packId = content.manifest.id
        require(content.votingDays.isNotEmpty()) { "Election Pack не содержит дней голосования" }
        require(content.votingDays.all { it.packId == packId }) { "День ссылается на другой Election Pack" }
        require(content.checklistDefinitions.all { it.packId == packId }) { "Чек-лист ссылается на другой пакет" }
        require(content.situations.all { it.packId == packId }) { "Ситуация ссылается на другой пакет" }
        require(content.lawReferences.all { it.packId == packId }) { "Справка ссылается на другой пакет" }
        require(content.complaintTemplates.all { it.packId == packId }) { "Шаблон ссылается на другой пакет" }
        require(content.reconciliationDefinitions.all { it.packId == packId }) { "Сверка ссылается на другой пакет" }
        require(content.referenceDocuments.all { it.packId == packId }) { "Документ ссылается на другой пакет" }
        require(content.checklistItems.map { it.id }.distinct().size == content.checklistItems.size) {
            "Идентификаторы пунктов чек-листа должны быть уникальны"
        }
        require(content.checklistDefinitions.map { it.id }.distinct().size == content.checklistDefinitions.size) {
            "Идентификаторы чек-листов должны быть уникальны"
        }
        val definitionIds = content.checklistDefinitions.map { it.id }.toSet()
        require(content.checklistItems.all { it.definitionId in definitionIds }) {
            "Пункт ссылается на неизвестный чек-лист"
        }
        val itemIds = content.checklistItems.map { it.id }.toSet()
        require(content.checklistDefinitions.flatMap { it.itemIds }.all(itemIds::contains)) {
            "Определение чек-листа содержит неизвестный пункт"
        }
        val lawIds = content.lawReferences.map { it.id }.toSet()
        require(content.checklistItems.flatMap { it.lawReferenceIds }.all(lawIds::contains)) {
            "Пункт чек-листа содержит неизвестную правовую ссылку"
        }
        require(content.situations.flatMap { it.lawReferenceIds }.all(lawIds::contains)) {
            "Ситуация содержит неизвестную правовую ссылку"
        }
        require(content.complaintTemplates.flatMap { it.lawReferenceIds }.all(lawIds::contains)) {
            "Шаблон жалобы содержит неизвестную правовую ссылку"
        }
        val complaintTemplateIds = content.complaintTemplates.map { it.id }.toSet()
        require(content.situations.mapNotNull { it.complaintTemplateId }.all(complaintTemplateIds::contains)) {
            "Ситуация содержит неизвестный шаблон жалобы"
        }
        val situationIds = content.situations.map { it.id }.toSet()
        require(content.situations.mapNotNull { it.parentId }.all(situationIds::contains)) {
            "Ситуация содержит неизвестного родителя"
        }
        val referenceDocumentIds = content.referenceDocuments.map { it.id }.toSet()
        require(content.situations.flatMap { it.referenceDocumentIds }.all(referenceDocumentIds::contains)) {
            "Ситуация содержит неизвестную ссылку на пример документа"
        }
        val declaredPaths = content.manifest.files.map { it.path }.toSet()
        require(content.referenceDocuments.all { it.contentPath in declaredPaths }) {
            "Справочный документ ссылается на отсутствующий файл"
        }
        content.reconciliationDefinitions.forEach { definition ->
            val fieldIds = definition.fields.map { it.id }.toSet()
            require(fieldIds.size == definition.fields.size) {
                "Форма ${definition.id} содержит повторяющиеся поля"
            }
            require(definition.rules.all { rule ->
                rule.inputIds.all(fieldIds::contains) &&
                    rule.comparisonInputIds.all(fieldIds::contains) &&
                    rule.targetInputId?.let(fieldIds::contains) != false &&
                    rule.previousInputId?.let(fieldIds::contains) != false
            }) {
                "Правило формы ${definition.id} ссылается на неизвестное поле"
            }
        }
    }

    private inline fun <reified T> Map<String, ByteArray>.decodeRequired(path: String): T {
        val bytes = this[path] ?: throw ElectionPackImportException.MissingFile(path)
        return json.decodeFromString(bytes.decodeToString())
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 2
        private const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        const val MANIFEST_PATH = "manifest.json"
        const val VOTING_DAYS_PATH = "voting_days.json"
        const val CHECKLIST_DEFINITIONS_PATH = "checklists/definitions.json"
        const val CHECKLIST_ITEMS_PATH = "checklists/items.json"
        const val SITUATIONS_PATH = "situations/situations.json"
        const val LAWS_PATH = "laws/laws.json"
        const val COMPLAINT_TEMPLATES_PATH = "complaint_templates/templates.json"
        const val RECONCILIATIONS_PATH = "reconciliation_rules/forms.json"
        const val REFERENCE_DOCUMENTS_PATH = "reference_documents/documents.json"
        val REQUIRED_PATHS = setOf(
            VOTING_DAYS_PATH,
            CHECKLIST_DEFINITIONS_PATH,
            CHECKLIST_ITEMS_PATH,
            SITUATIONS_PATH,
            LAWS_PATH,
            COMPLAINT_TEMPLATES_PATH,
            RECONCILIATIONS_PATH,
            REFERENCE_DOCUMENTS_PATH,
        )
    }
}
