package com.example.data.model

import com.google.firebase.Timestamp

enum class PartnerRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED;

    fun toFirestoreValue(): String = name.lowercase()

    companion object {
        fun fromFirestoreValue(value: String?): PartnerRequestStatus {
            return when (value?.lowercase()) {
                "accepted" -> ACCEPTED
                "rejected" -> REJECTED
                "cancelled" -> CANCELLED
                else -> PENDING
            }
        }
    }
}

data class PartnerRequest(
    val requestId: String = "",
    val khataId: String = "",
    val khataName: String = "",
    val senderUid: String = "",
    val senderUserId: String = "",
    val senderName: String = "",
    val receiverUid: String = "",
    val receiverUserId: String = "",
    val status: String = "pending",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
