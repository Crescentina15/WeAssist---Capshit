package com.remedio.weassist.Clients

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String? = null,
    val senderImageUrl: String? = null,
    val fileUrl: String? = null,          // URL of the uploaded file in Cloudinary
    val fileType: String? = null,         // Type of file (e.g., "image", "pdf", "video")
    val fileName: String? = null,         // Original filename
    val fileSize: Long? = null            // File size in bytes (optional)
) {
    // Helper function to check if this message contains a file
    fun isFileMessage(): Boolean {
        return !fileUrl.isNullOrEmpty()
    }

    // Helper function to get the appropriate display text for the message
    fun getDisplayText(): String {
        return if (isFileMessage()) {
            fileName ?: "File"
        } else {
            message
        }
    }

    // Helper function to get file extension
    fun getFileExtension(): String? {
        return fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            ?: fileUrl?.substringAfterLast('.', "")?.substringBefore('?')?.takeIf { it.isNotEmpty() && it.length <= 5 }
    }

    // Helper function to determine the correct MIME type based on all available info
    fun getMimeType(): String {
        val extension = getFileExtension()?.lowercase()

        return when {
            // First check explicit fileType
            fileType == "image" -> when (extension) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            fileType == "pdf" -> "application/pdf"
            fileType == "video" -> when (extension) {
                "avi" -> "video/x-msvideo"
                "wmv" -> "video/x-ms-wmv"
                "mov" -> "video/quicktime"
                else -> "video/mp4"
            }

            // Then check by extension
            extension == "pdf" -> "application/pdf"
            extension == "doc" -> "application/msword"
            extension == "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            extension == "xls" -> "application/vnd.ms-excel"
            extension == "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            extension == "txt" -> "text/plain"
            extension == "jpg" || extension == "jpeg" -> "image/jpeg"
            extension == "png" -> "image/png"
            extension == "gif" -> "image/gif"
            extension == "mp3" -> "audio/mpeg"
            extension == "wav" -> "audio/wav"
            extension == "mp4" -> "video/mp4"
            extension == "avi" -> "video/x-msvideo"
            extension == "zip" -> "application/zip"

            // Default catch-all
            else -> "*/*"
        }
    }
}