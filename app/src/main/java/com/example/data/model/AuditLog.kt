package com.example.data.model

import com.google.firebase.Timestamp

data class AuditLog(
    val logId: String = "",
    val khataId: String = "",
    val personId: String? = null,
    val transactionId: String? = null,
    val actorUid: String = "",
    val actorName: String = "",
    val action: String = "",
    val amount: Long? = null,
    val description: String = "",
    val createdAt: Timestamp? = null
)
