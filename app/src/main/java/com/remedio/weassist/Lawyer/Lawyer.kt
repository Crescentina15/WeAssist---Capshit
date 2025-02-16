package com.remedio.weassist.Lawyer

data class Lawyer(
    val id: String = "",
    val name: String = "",
    val specialization: String = "",
    val lawFirm: String = "",
    val licenseNumber: String = "",
    val experience: String = "",
    val lawSchool: String? = null,  // Nullable for missing fields
    val graduationYear: String? = null,
    val certifications: String? = null,
    val jurisdiction: String? = null,
    val employer: String? = null,
    val bio: String? = null,
    val rate: String? = null,
    val profileImage: String? = null,
    val contact: Contact? = null // Contact can be null in Firebase
) {
    constructor() : this("", "", "", "", "", "", null, null, null, null, null, null, null, null, null)
}

data class Contact(
    val phone: String = "",
    val email: String = "",
    val address: String = ""
) {
    constructor() : this("", "", "")
}
data class Availability(
    val date: String = "",
    val startTime: String = "",
    val endTime: String = ""
) {
    constructor() : this("", "", "")
}
