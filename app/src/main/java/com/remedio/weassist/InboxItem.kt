package com.remedio.weassist

data class InboxItem(
    val chatPartnerId: String,  // 🆕 Generic ID (can be a client or secretary)
    val chatPartnerName: String,  // 🆕 Generic name (can be a client or secretary)
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int
)

