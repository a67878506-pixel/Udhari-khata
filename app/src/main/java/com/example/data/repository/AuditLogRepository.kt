package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AuditLog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AuditLogRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeAuditLogs(khataId: String): Flow<List<AuditLog>> {
        val path = "khatas/$khataId/auditLogs"
        return db.collection("khatas").document(khataId).collection("auditLogs")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(AuditLog::class.java) }
                    .sortedByDescending { it.createdAt }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }
}
