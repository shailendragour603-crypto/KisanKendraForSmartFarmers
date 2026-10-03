package shop.sabjiwala.kisankendra.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class NewsItem(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val timestamp: Timestamp? = null,
    val isPinned: Boolean = false,
    var likesCount: Int = 0,
    var commentsCount: Int = 0,
    var sharesCount: Int = 0,
    var likedBy: List<String> = emptyList()
)
