package com.example.ohrana

import java.util.UUID

data class Employee(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String
)
