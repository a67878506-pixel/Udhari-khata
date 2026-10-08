package com.example.data.model

import com.google.firebase.Timestamp

data class Khata(
    val khataId: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val totalLena: Long = 0L,
    val totalDena: Long = 0L,
    val netBalance: Long = 0L,
    val peopleCount: Int = 0,
    val archived: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
