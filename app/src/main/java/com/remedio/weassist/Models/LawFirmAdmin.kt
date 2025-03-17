package com.remedio.weassist.Models

data class LawFirmAdmin(
    val uid: String = "",
    val email: String = "",
    val firmDescription: String = "",
    val firmType: String = "",
    val lawFirm: String = "",
    val licenseNumber: String = "",
    val officeAddress: String = "",  // This is what you want to use as the lawyer location
    val operatingHours: String = "",
    val phoneNumber: String = "",
    val profilePicture: String = "",
    val specialization: String = "",
    val website: String = ""
)
