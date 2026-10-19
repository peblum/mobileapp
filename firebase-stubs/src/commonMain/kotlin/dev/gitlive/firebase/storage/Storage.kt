package dev.gitlive.firebase.storage

import dev.gitlive.firebase.Firebase

val Firebase.storage: FirebaseStorage
    get() = FirebaseStorage()

class FirebaseStorage {
    fun reference(path: String): StorageReference = StorageReference(path)
}

class StorageReference(
    private val path: String,
) {
    suspend fun getDownloadUrl(): String = error("Firebase Storage is disabled in the F-Droid build")
    suspend fun getMetadata(): FirebaseStorageMetadata? = null
    suspend fun putFile(file: File, metadata: FirebaseStorageMetadata? = null) = Unit
    suspend fun delete() = Unit
}

class File(
    val nativeFile: Any?,
)

data class FirebaseStorageMetadata(
    val contentType: String? = null,
    val customMetadata: Map<String, String>? = null,
)
