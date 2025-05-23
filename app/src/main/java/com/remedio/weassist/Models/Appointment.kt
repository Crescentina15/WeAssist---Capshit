package com.remedio.weassist.Models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Appointment(
    var appointmentId: String = "",
    var fullName: String = "",
    var lawyerName: String = "",
    var date: String = "",
    val time: String = "",
    val problem: String = "",
    var lawyerId: String = "",
    var lawyerProfileImage: String? = null,
    var status: String? = "pending", // Default to pending
    var clientId: String = "",
    var secretaryId: String = "",
    var attachments: List<String> = emptyList(), // Add this line
    var sessionStarted: Boolean = false
    //val clientUid: String = ""
    //var problemDescription: String = "" // Add this property
) : Parcelable

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



