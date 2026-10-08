package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.PartnerRequest
import com.example.data.model.PartnerRequestStatus
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

class PartnerRepository(private val db: FirebaseFirestore) {
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

    fun observeIncomingRequests(receiverUid: String): Flow<List<PartnerRequest>> {
        val path = "partnerRequests"
        return db.collection("partnerRequests")
            .whereEqualTo("receiverUid", receiverUid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(PartnerRequest::class.java) }
                    .filter { it.status == "pending" }
                    .sortedByDescending { it.createdAt }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeOutgoingRequests(senderUid: String): Flow<List<PartnerRequest>> {
        val path = "partnerRequests"
        return db.collection("partnerRequests")
            .whereEqualTo("senderUid", senderUid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(PartnerRequest::class.java) }
                    .sortedByDescending { it.createdAt }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun sendPartnerRequest(
        khataId: String,
        khataName: String,
        senderUserId: String,
        senderName: String,
        targetHandle: String
    ): Result<String> {
        val uid = requireUserId()
        val normalized = targetHandle.trim().lowercase().removePrefix("@")
        val requestId = "req_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val userHandleDoc = db.collection("userIds").document(normalized).get().await()
            if (!userHandleDoc.exists()) {
                return Result.failure(IllegalArgumentException("No user found with handle @$normalized"))
            }

            val targetUid = userHandleDoc.getString("uid") ?: ""
            val targetUserId = userHandleDoc.getString("userId") ?: normalized

            if (targetUid == uid) {
                return Result.failure(IllegalArgumentException("You cannot invite yourself"))
            }

            // Check if already a member
            val khataDoc = db.collection("khatas").document(khataId).get().await()
            val members = (khataDoc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            if (members.contains(targetUid)) {
                return Result.failure(IllegalArgumentException("@$targetUserId is already a partner in this Khata"))
            }

            val requestRef = db.collection("partnerRequests").document(requestId)
            val requestData = mapOf(
                "requestId" to requestId,
                "khataId" to khataId,
                "khataName" to khataName.trim(),
                "senderUid" to uid,
                "senderUserId" to senderUserId.trim(),
                "senderName" to senderName.trim(),
                "receiverUid" to targetUid,
                "receiverUserId" to targetUserId,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            requestRef.set(requestData).await()
            Result.success(requestId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "partnerRequests/$requestId")
            Result.failure(e)
        }
    }

    suspend fun respondToPartnerRequest(
        request: PartnerRequest,
        accept: Boolean,
        receiverName: String
    ): Result<Unit> {
        val uid = requireUserId()
        if (request.receiverUid != uid) {
            return Result.failure(IllegalStateException("You are not the recipient of this request"))
        }

        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            val reqRef = db.collection("partnerRequests").document(request.requestId)
            val khataRef = db.collection("khatas").document(request.khataId)
            val logRef = khataRef.collection("auditLogs").document(logId)

            if (accept) {
                db.runTransaction { transaction ->
                    val khataSnap = transaction.get(khataRef)
                    val currentMembers = (khataSnap.get("memberUids") as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
                        ?: mutableListOf()

                    if (!currentMembers.contains(uid)) {
                        currentMembers.add(uid)
                    }

                    transaction.update(khataRef, mapOf(
                        "memberUids" to currentMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))

                    transaction.update(reqRef, mapOf(
                        "status" to "accepted",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))

                    val logData = mapOf(
                        "logId" to logId,
                        "khataId" to request.khataId,
                        "actorUid" to uid,
                        "actorName" to receiverName.trim(),
                        "action" to "PARTNER_JOINED",
                        "description" to "$receiverName joined as partner via invitation",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    transaction.set(logRef, logData)
                }.await()
            } else {
                reqRef.update(
                    mapOf(
                        "status" to "rejected",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "partnerRequests/${request.requestId}")
            Result.failure(e)
        }
    }
}
