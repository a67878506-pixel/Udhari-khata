package com.example.data.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val userId: String = "",
    val normalizedUserId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
