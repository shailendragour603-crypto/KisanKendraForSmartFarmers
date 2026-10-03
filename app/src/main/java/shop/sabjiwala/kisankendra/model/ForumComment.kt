package shop.sabjiwala.kisankendra.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ForumComment(
    val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val text: String = "",
    var likesCount: Int = 0,
    var likedBy: List<String> = emptyList(),
    val replyToAuthor: String = "",
    val parentCommentId: String = "",
    val timestamp: Timestamp? = null
)
