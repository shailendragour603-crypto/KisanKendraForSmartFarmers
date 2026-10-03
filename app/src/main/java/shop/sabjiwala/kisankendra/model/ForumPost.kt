package shop.sabjiwala.kisankendra.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ForumPost(
    val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorLocation: String = "",
    val authorPhotoUrl: String = "",
    val category: String = "",
    val description: String = "",
    val photoUrl: String = "",
    val videoUrl: String = "",
    var likesCount: Int = 0,
    var repliesCount: Int = 0,
    var sharesCount: Int = 0,
    var likedBy: List<String> = emptyList(),
    val timestamp: Timestamp? = null
)
