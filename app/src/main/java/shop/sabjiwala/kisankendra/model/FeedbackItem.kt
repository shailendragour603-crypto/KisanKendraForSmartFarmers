package shop.sabjiwala.kisankendra.model

import com.google.firebase.Timestamp

data class FeedbackItem(
    val id: String = "",
    val category: String = "",
    val message: String = "",
    val userId: String = "",
    val userName: String = "",
    val userContact: String = "",
    val userRole: String = "",
    val appVersionName: String = "",
    val appVersionCode: Long = 0,
    val androidVersion: String = "",
    val deviceModel: String = "",
    val timestamp: Timestamp? = null,
    val status: String = "pending" // pending, in_progress, resolved, closed
)
