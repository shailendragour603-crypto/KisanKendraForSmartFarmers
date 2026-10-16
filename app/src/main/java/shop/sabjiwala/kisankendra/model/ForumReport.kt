package shop.sabjiwala.kisankendra.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ForumReport(
    val id: String = "",
    val postId: String = "",
    val authorUid: String = "",
    val reporterUid: String = "",
    val reason: String = "",
    val postSnippet: String = "",
    val timestamp: Timestamp? = null
)

data class ReportedPostItem(
    val report: ForumReport,
    val post: ForumPost?
)
