package dev.gitlive.firebase.firestore

import dev.gitlive.firebase.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

val Firebase.firestore: FirebaseFirestore
    get() = FirebaseFirestore()

class FirebaseFirestore {
    var settings: FirebaseFirestoreSettings? = null

    fun collection(path: String): CollectionReference = CollectionReference(path)
    fun document(path: String): DocumentReference = DocumentReference(path.substringAfterLast('/'), path)
    fun batch(): WriteBatch = WriteBatch()
}

class FirebaseFirestoreSettings {
    var cacheSettings: Any? = null

    companion object {
        const val CACHE_SIZE_UNLIMITED: Long = -1L
    }
}

class PersistentCacheSettings {
    var sizeBytes: Long = FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED
}

fun firestoreSettings(block: FirebaseFirestoreSettings.() -> Unit): FirebaseFirestoreSettings =
    FirebaseFirestoreSettings().apply(block)

fun persistentCacheSettings(block: PersistentCacheSettings.() -> Unit): PersistentCacheSettings =
    PersistentCacheSettings().apply(block)

open class Query(
    protected val path: String,
) {
    val snapshots: Flow<QuerySnapshot>
        get() = flowOf(QuerySnapshot())

    fun snapshots(includeMetadataChanges: Boolean = false): Flow<QuerySnapshot> = flowOf(QuerySnapshot())
    suspend fun get(source: Source = Source.DEFAULT): QuerySnapshot = QuerySnapshot()
    fun where(block: QueryScope.() -> Any?): Query = this
    fun orderBy(field: String, direction: Direction = Direction.ASCENDING): Query = this
    fun limit(limit: Int): Query = this
    fun startAfter(snapshot: DocumentSnapshot): Query = this
}

class CollectionReference(path: String) : Query(path) {
    fun document(id: String): DocumentReference = DocumentReference(id, "$path/$id")
    suspend fun add(data: Any?): DocumentReference = document("fdroid-local-document")
}

class DocumentReference(
    val id: String,
    private val path: String,
) {
    val snapshots: Flow<DocumentSnapshot>
        get() = flowOf(DocumentSnapshot(reference = this, exists = false))

    fun collection(path: String): CollectionReference = CollectionReference("${this.path}/$path")
    suspend fun get(): DocumentSnapshot = DocumentSnapshot(reference = this, exists = false)
    suspend fun set(data: Any?) = Unit
    suspend fun update(data: Map<String, Any?>) = Unit
    suspend fun update(vararg fieldsAndValues: Pair<String, Any?>) = Unit
    suspend fun delete() = Unit
}

class WriteBatch {
    fun set(document: DocumentReference, data: Any?) = Unit
    fun delete(document: DocumentReference) = Unit
    suspend fun commit() = Unit
}

class DocumentSnapshot(
    val reference: DocumentReference = DocumentReference("fdroid-local-document", "fdroid-local-document"),
    val id: String = reference.id,
    val exists: Boolean = false,
) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> data(): T = null as T
}

class QuerySnapshot(
    val documents: List<DocumentSnapshot> = emptyList(),
    val documentChanges: List<DocumentChange> = emptyList(),
    val metadata: SnapshotMetadata = SnapshotMetadata(),
)

class DocumentChange(
    val document: DocumentSnapshot,
    val type: ChangeType,
)

class SnapshotMetadata(
    val isFromCache: Boolean = false,
)

class QueryScope {
    infix fun String.equalTo(value: Any?): Any? = null
    infix fun FieldPath.equalTo(value: Any?): Any? = null
    infix fun String.greaterThan(value: Any?): Any? = null
    infix fun FieldPath.greaterThan(value: Any?): Any? = null
}

class FieldPath(vararg val fields: String)

enum class Direction {
    ASCENDING,
    DESCENDING,
}

enum class Source {
    DEFAULT,
    CACHE,
    SERVER,
}

enum class ChangeType {
    ADDED,
    MODIFIED,
    REMOVED,
}

enum class FirestoreExceptionCode {
    UNAVAILABLE,
    DEADLINE_EXCEEDED,
    UNKNOWN,
}

class FirebaseFirestoreException(
    val firestoreCode: FirestoreExceptionCode = FirestoreExceptionCode.UNKNOWN,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

val FirebaseFirestoreException.code: FirestoreExceptionCode
    get() = firestoreCode

@Serializable
data class Timestamp(
    val seconds: Long,
    val nanoseconds: Int,
) {
    companion object {
        fun fromMilliseconds(milliseconds: Double): Timestamp {
            val seconds = (milliseconds / 1000).toLong()
            val nanos = ((milliseconds - seconds * 1000) * 1_000_000).toInt()
            return Timestamp(seconds, nanos)
        }
    }
}

fun fromMilliseconds(milliseconds: Double): Timestamp = Timestamp.fromMilliseconds(milliseconds)

object BaseTimestampSerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.gitlive.firebase.firestore.Timestamp", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Any? = null
    override fun serialize(encoder: Encoder, value: Any?) = Unit
}
