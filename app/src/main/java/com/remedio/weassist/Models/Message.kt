package com.remedio.weassist.Clients

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String? = null, // Add this field to hold sender name
    val senderImageUrl: String? = null  // Add this field

)