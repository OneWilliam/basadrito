package org.basadrito.app.data.intranet.dto

import kotlinx.serialization.Serializable

@Serializable
data class StudentProfile(
    val fullName: String = "",
    val code: String = "",
    val career: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = ""
)
