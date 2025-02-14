package com.remedio.weassist.Lawyer

data class Lawyer(
    val id: String = "",
    val name: String = "",
    val specialization: String = "",
    val lawFirm: String = "",
    val licenseNumber: String = "",
    val experience: String = "",
    val contact: Contact = Contact()
)

data class Contact(
    val phone: String = "",
    val email: String = "",
    val address: String = ""
)
