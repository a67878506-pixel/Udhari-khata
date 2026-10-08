package com.example.data.model

import com.google.firebase.Timestamp

enum class TransactionType {
    LENA,
    DENA
}

data class Transaction(
    val transactionId: String = "",
    val khataId: String = "",
    val personId: String = "",
    val amount: Long = 0L,
    val type: String = TransactionType.LENA.name,
    val note: String? = null,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Timestamp? = null,
    val updatedBy: String? = null,
    val updatedAt: Timestamp? = null,
    val deletedAt: Timestamp? = null
)
