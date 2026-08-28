package coredevices.ring.database.firestore.dao

import dev.gitlive.firebase.firestore.CollectionReference

actual suspend fun CollectionReference.count(): Int = get().documents.size
