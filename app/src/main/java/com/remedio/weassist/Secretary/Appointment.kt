package com.remedio.weassist.Secretary

data class Appointment(
    val appointmentId: String? = null,
    val fullName: String? = null,
    val date: String? = null,
    val time: String? = null,
    val lawyerId: String? = null,
    val lawyerProfileImage: String? = null // Optional field for lawyer's profile image URL
)

