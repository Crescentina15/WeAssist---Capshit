package com.remedio.weassist.Models

data class Consultation(
    val clientName: String = "",
    val consultationTime: String = "",
    var notes: String = "",
    val lawyerId: String = "",
    val consultationDate: String = "",
    val consultationType: String = "",
    val status: String = "",
    val problem: String = "",
    val appointmentId: String = "" // Ensure this is here
)
