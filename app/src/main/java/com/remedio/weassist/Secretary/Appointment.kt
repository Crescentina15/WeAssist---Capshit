package com.remedio.weassist.Secretary

data class Secretary(
    val name: String? = null,
    val lawFirm: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null
)

data class Lawyer(
    val name: String? = null,
    val lawFirm: String? = null,
    val licenseNumber: String? = null,
    val experience: String? = null,
    val id: String? = null // We'll store the UID here for clarity
)

data class Appointment(
    val appointmentId: String? = null,
    val fullName: String? = null,
    val date: String? = null,
    val time: String? = null,
    val lawyerId: String? = null,
    val problem: String? = null,
    val lawyerProfileImage: String? = null
)

