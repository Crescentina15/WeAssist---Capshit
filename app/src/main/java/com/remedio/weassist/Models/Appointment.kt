package com.remedio.weassist.Models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Appointment(
    val appointmentId: String = "",
    val fullName: String = "",
    val date: String = "",
    val time: String = "",
    val problem: String = "",
    val lawyerId: String = "",
    val lawyerProfileImage: String? = null,
    val status: String? = null,
    var clientId: String = "",
    var secretaryId: String = ""
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



