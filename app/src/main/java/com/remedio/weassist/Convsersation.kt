    package com.remedio.weassist

    data class Conversation(
        val conversationId: String = "",
        val secretaryId: String = "",
        val secretaryName: String = "",
        val lastMessage: String = "",
        val unreadCount: Int = 0, // 🔹 This is INT
        val clientId: String = "", // 🔹 This is STRING
        val clientName: String = ""
    )


