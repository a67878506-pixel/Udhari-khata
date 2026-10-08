package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Khata
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

class KhataRepository(private val db: FirebaseFirestore) {
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

    fun observeUserKhatas(uid: String): Flow<List<Khata>> {
        val path = "khatas"
        return db.collection("khatas")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Khata::class.java) }
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeKhata(khataId: String): Flow<Khata?> {
        val path = "khatas/$khataId"
        return db.collection("khatas").document(khataId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toObject(Khata::class.java) else null
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun createKhata(name: String, actorName: String): Result<String> {
        val uid = requireUserId()
        val khataId = "khata_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val khataData = mapOf(
                "khataId" to khataId,
                "name" to name.trim(),
                "ownerUid" to uid,
                "memberUids" to listOf(uid),
                "totalLena" to 0L,
                "totalDena" to 0L,
                "netBalance" to 0L,
                "peopleCount" to 0,
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val logData = mapOf(
                "logId" to logId,
                "khataId" to khataId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to "KHATA_CREATED",
                "description" to "$actorName created Khata \"$name\"",
                "createdAt" to FieldValue.serverTimestamp()
            )

            khataRef.set(khataData).await()
            try {
                logRef.set(logData).await()
            } catch (e: Exception) {
                // Non-fatal if audit log fails
            }

            Result.success(khataId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "khatas/$khataId")
            Result.failure(e)
        }
    }

    suspend fun updateKhataName(khataId: String, newName: String, actorName: String): Result<Unit> {
        val uid = requireUserId()
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val logData = mapOf(
                "logId" to logId,
                "khataId" to khataId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to "KHATA_RENAMED",
                "description" to "$actorName renamed Khata to \"$newName\"",
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.update(khataRef, mapOf(
                    "name" to newName.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                batch.set(logRef, logData)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "khatas/$khataId")
            Result.failure(e)
        }
    }

    suspend fun archiveKhata(khataId: String, archived: Boolean, actorName: String): Result<Unit> {
        val uid = requireUserId()
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val actionName = if (archived) "KHATA_ARCHIVED" else "KHATA_RESTORED"
            val desc = if (archived) "$actorName archived this Khata" else "$actorName restored this Khata"

            val logData = mapOf(
                "logId" to logId,
                "khataId" to khataId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to actionName,
                "description" to desc,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.update(khataRef, mapOf(
                    "archived" to archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                batch.set(logRef, logData)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "khatas/$khataId")
            Result.failure(e)
        }
    }
}
