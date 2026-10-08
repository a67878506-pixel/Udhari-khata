package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
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

class TransactionRepository(private val db: FirebaseFirestore) {
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

    fun observeTransactions(khataId: String, personId: String? = null): Flow<List<Transaction>> {
        val path = "khatas/$khataId/transactions"
        val query = if (personId != null) {
            db.collection("khatas").document(khataId).collection("transactions")
                .whereEqualTo("personId", personId)
        } else {
            db.collection("khatas").document(khataId).collection("transactions")
        }

        return query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Transaction::class.java) }
                    .filter { it.deletedAt == null }
                    .sortedByDescending { it.createdAt }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun addTransaction(
        khataId: String,
        personId: String,
        personName: String,
        amount: Long,
        type: TransactionType,
        note: String?,
        actorName: String
    ): Result<String> {
        val uid = requireUserId()
        val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val personRef = khataRef.collection("people").document(personId)
            val txRef = khataRef.collection("transactions").document(txId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val txData = mutableMapOf<String, Any>(
                "transactionId" to txId,
                "khataId" to khataId,
                "personId" to personId,
                "amount" to amount,
                "type" to type.name,
                "createdBy" to uid,
                "createdByName" to actorName.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!note.isNullOrBlank()) txData["note"] = note.trim()

            val actionDesc = "$actorName added ₹$amount ${type.name} for $personName" +
                    if (!note.isNullOrBlank()) " ($note)" else ""

            val logData = mutableMapOf<String, Any>(
                "logId" to logId,
                "khataId" to khataId,
                "personId" to personId,
                "transactionId" to txId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to "TRANSACTION_ADDED",
                "amount" to amount,
                "description" to actionDesc,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runTransaction { transaction ->
                val personSnap = transaction.get(personRef)
                val khataSnap = transaction.get(khataRef)

                val pLena = personSnap.getLong("totalLena") ?: 0L
                val pDena = personSnap.getLong("totalDena") ?: 0L

                val kLena = khataSnap.getLong("totalLena") ?: 0L
                val kDena = khataSnap.getLong("totalDena") ?: 0L

                val (newPLena, newPDena) = if (type == TransactionType.LENA) {
                    (pLena + amount) to pDena
                } else {
                    pLena to (pDena + amount)
                }

                val (newKLena, newKDena) = if (type == TransactionType.LENA) {
                    (kLena + amount) to kDena
                } else {
                    kLena to (kDena + amount)
                }

                transaction.set(txRef, txData)
                transaction.update(personRef, mapOf(
                    "totalLena" to newPLena,
                    "totalDena" to newPDena,
                    "netBalance" to (newPLena - newPDena),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.update(khataRef, mapOf(
                    "totalLena" to newKLena,
                    "totalDena" to newKDena,
                    "netBalance" to (newKLena - newKDena),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.set(logRef, logData)
            }.await()

            Result.success(txId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "khatas/$khataId/transactions/$txId")
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        khataId: String,
        personId: String,
        personName: String,
        transactionId: String,
        oldAmount: Long,
        oldType: TransactionType,
        newAmount: Long,
        newType: TransactionType,
        newNote: String?,
        actorName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val personRef = khataRef.collection("people").document(personId)
            val txRef = khataRef.collection("transactions").document(transactionId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val updateTxMap = mutableMapOf<String, Any?>(
                "amount" to newAmount,
                "type" to newType.name,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            updateTxMap["note"] = newNote?.trim()?.ifEmpty { null }

            val actionDesc = "$actorName updated transaction for $personName to ₹$newAmount ${newType.name}" +
                    if (!newNote.isNullOrBlank()) " ($newNote)" else ""

            val logData = mapOf(
                "logId" to logId,
                "khataId" to khataId,
                "personId" to personId,
                "transactionId" to transactionId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to "TRANSACTION_UPDATED",
                "amount" to newAmount,
                "description" to actionDesc,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runTransaction { transaction ->
                val personSnap = transaction.get(personRef)
                val khataSnap = transaction.get(khataRef)

                var pLena = personSnap.getLong("totalLena") ?: 0L
                var pDena = personSnap.getLong("totalDena") ?: 0L
                var kLena = khataSnap.getLong("totalLena") ?: 0L
                var kDena = khataSnap.getLong("totalDena") ?: 0L

                // Reverse old transaction
                if (oldType == TransactionType.LENA) {
                    pLena -= oldAmount
                    kLena -= oldAmount
                } else {
                    pDena -= oldAmount
                    kDena -= oldAmount
                }

                // Apply new transaction
                if (newType == TransactionType.LENA) {
                    pLena += newAmount
                    kLena += newAmount
                } else {
                    pDena += newAmount
                    kDena += newAmount
                }

                transaction.update(txRef, updateTxMap.filterValues { it != null })
                transaction.update(personRef, mapOf(
                    "totalLena" to pLena,
                    "totalDena" to pDena,
                    "netBalance" to (pLena - pDena),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.update(khataRef, mapOf(
                    "totalLena" to kLena,
                    "totalDena" to kDena,
                    "netBalance" to (kLena - kDena),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.set(logRef, logData)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "khatas/$khataId/transactions/$transactionId")
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(
        khataId: String,
        personId: String,
        personName: String,
        transactionId: String,
        amount: Long,
        type: TransactionType,
        actorName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val khataRef = db.collection("khatas").document(khataId)
            val personRef = khataRef.collection("people").document(personId)
            val txRef = khataRef.collection("transactions").document(transactionId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            val logData = mapOf(
                "logId" to logId,
                "khataId" to khataId,
                "personId" to personId,
                "transactionId" to transactionId,
                "actorUid" to uid,
                "actorName" to actorName.trim(),
                "action" to "TRANSACTION_DELETED",
                "amount" to amount,
                "description" to "$actorName deleted ₹$amount ${type.name} transaction of $personName",
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runTransaction { transaction ->
                val personSnap = transaction.get(personRef)
                val khataSnap = transaction.get(khataRef)

                var pLena = personSnap.getLong("totalLena") ?: 0L
                var pDena = personSnap.getLong("totalDena") ?: 0L
                var kLena = khataSnap.getLong("totalLena") ?: 0L
                var kDena = khataSnap.getLong("totalDena") ?: 0L

                if (type == TransactionType.LENA) {
                    pLena = (pLena - amount).coerceAtLeast(0L)
                    kLena = (kLena - amount).coerceAtLeast(0L)
                } else {
                    pDena = (pDena - amount).coerceAtLeast(0L)
                    kDena = (kDena - amount).coerceAtLeast(0L)
                }

                // Soft-delete transaction document
                transaction.update(txRef, mapOf(
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.update(personRef, mapOf(
                    "totalLena" to pLena,
                    "totalDena" to pDena,
                    "netBalance" to (pLena - pDena),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.update(khataRef, mapOf(
                    "totalLena" to kLena,
                    "totalDena" to kDena,
                    "netBalance" to (kLena - kDena),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.set(logRef, logData)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "khatas/$khataId/transactions/$transactionId")
            Result.failure(e)
        }
    }
}
