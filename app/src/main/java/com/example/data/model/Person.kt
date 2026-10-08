package com.example.data.model

import com.google.firebase.Timestamp

data class Person(
    val personId: String = "",
    val khataId: String = "",
    val name: String = "",
    val phone: String? = null,
    val note: String? = null,
    val totalLena: Long = 0L,
    val totalDena: Long = 0L,
    val netBalance: Long = 0L,
    val archived: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
