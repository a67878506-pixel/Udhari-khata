package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Person
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PersonRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance(db.app)

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePeople(khataId: String): Flow<List<Person>> {
        val path = "khatas/$khataId/people"
        return db.collection("khatas").document(khataId).collection("people")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Person::class.java) }
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePerson(khataId: String, personId: String): Flow<Person?> {
        val path = "khatas/$khataId/people/$personId"
        return db.collection("khatas").document(khataId).collection("people").document(personId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toObject(Person::class.java) else null
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun addPerson(
        khataId: String,
        name: String,
        phone: String?,
        note: String?,
        actorName: String
    ): Result<String> {
        val uid = requireUserId()
        val personId = "person_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val personRef = khataRef.collection("people").document(personId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val personData = mutableMapOf<String, Any>(
                "personId" to personId,
                "khataId" to khataId,
                "name" to name.trim(),
                "totalLena" to 0L,
                "totalDena" to 0L,
                "netBalance" to 0L,
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!phone.isNullOrBlank()) personData["phone"] = phone.trim()
            if (!note.isNullOrBlank()) personData["note"] = note.trim()

            val logData = mapOf(
                "logId" to logId,
                "khataId" to khataId,
                "personId" to personId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to "PERSON_ADDED",
                "description" to "$actorName added person \"$name\"",
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runTransaction { transaction ->
                val khataSnapshot = transaction.get(khataRef)
                val currentCount = (khataSnapshot.getLong("peopleCount") ?: 0L).toInt()
                transaction.set(personRef, personData)
                transaction.update(khataRef, mapOf(
                    "peopleCount" to currentCount + 1,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.set(logRef, logData)
            }.await()

            Result.success(personId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "khatas/$khataId/people/$personId")
            Result.failure(e)
        }
    }

    suspend fun updatePerson(
        khataId: String,
        personId: String,
        name: String,
        phone: String?,
        note: String?,
        actorName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val personRef = khataRef.collection("people").document(personId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val updateData = mutableMapOf<String, Any?>(
                "name" to name.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            updateData["phone"] = phone?.trim()?.ifEmpty { null }
            updateData["note"] = note?.trim()?.ifEmpty { null }

            val logData = mapOf(
                "logId" to logId,
                "khataId" to khataId,
                "personId" to personId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to "PERSON_EDITED",
                "description" to "$actorName updated details for \"$name\"",
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.update(personRef, updateData.filterValues { it != null })
                batch.set(logRef, logData)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "khatas/$khataId/people/$personId")
            Result.failure(e)
        }
    }

    suspend fun archivePerson(
        khataId: String,
        personId: String,
        personName: String,
        archived: Boolean,
        actorName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val personRef = khataRef.collection("people").document(personId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val actionName = if (archived) "PERSON_ARCHIVED" else "PERSON_RESTORED"
            val desc = if (archived) "$actorName archived person \"$personName\"" else "$actorName restored person \"$personName\""

            val logData = mapOf(
                "logId" to logId,
                "khataId" to khataId,
                "personId" to personId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to actionName,
                "description" to desc,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.update(personRef, mapOf(
                    "archived" to archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                batch.set(logRef, logData)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "khatas/$khataId/people/$personId")
            Result.failure(e)
        }
    }
}
