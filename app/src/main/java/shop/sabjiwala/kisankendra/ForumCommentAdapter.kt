package shop.sabjiwala.kisankendra

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.model.ForumComment
import shop.sabjiwala.kisankendra.utils.ForumBlockHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper

class ForumCommentAdapter(
    private var comments: List<ForumComment>,
    private val isAdmin: Boolean = false,
    private val currentUserId: String = "",
    private val onDeleteClick: ((ForumComment) -> Unit)? = null,
    private val onReportClick: ((ForumComment) -> Unit)? = null,
    private val onLikeClick: ((ForumComment) -> Unit)? = null,
    private val onReplyClick: ((ForumComment) -> Unit)? = null
) : RecyclerView.Adapter<ForumCommentAdapter.ViewHolder>() {

    private val localComments = mutableListOf<ForumComment>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rootLayout: View = view.findViewById(R.id.layoutCommentRoot)
        val ivAuthorAvatar: ImageView = view.findViewById(R.id.ivCommentAuthorAvatar)
        val tvAuthor: TextView = view.findViewById(R.id.tvCommentAuthor)
        val tvTime: TextView = view.findViewById(R.id.tvCommentTime)
        val tvReplyTag: TextView = view.findViewById(R.id.tvReplyTag)
        val tvText: TextView = view.findViewById(R.id.tvCommentText)
        val btnLike: ImageView = view.findViewById(R.id.btnLikeComment)
        val tvLikesCount: TextView = view.findViewById(R.id.tvCommentLikesCount)
        val btnReply: TextView = view.findViewById(R.id.btnReplyComment)
        val btnTranslate: TextView = view.findViewById(R.id.btnTranslateComment)
        val btnDelete: TextView = view.findViewById(R.id.btnDeleteComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_forum_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = comments[position]
        val context = holder.itemView.context

        // Hide comment if authored by a blocked user
        if (ForumBlockHelper.isUserBlocked(context, comment.userId)) {
            holder.itemView.visibility = View.GONE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Indent reply comments for Instagram-style nested thread structure
        val isReply = comment.replyToAuthor.isNotBlank() || comment.parentCommentId.isNotBlank()
        val startPadding = if (isReply) (36 * context.resources.displayMetrics.density).toInt() else (12 * context.resources.displayMetrics.density).toInt()
        val verticalPadding = (8 * context.resources.displayMetrics.density).toInt()
        val endPadding = (12 * context.resources.displayMetrics.density).toInt()
        holder.rootLayout.setPadding(startPadding, verticalPadding, endPadding, verticalPadding)

        holder.tvAuthor.text = comment.authorName.ifBlank { "Farmer" }
        holder.tvText.text = comment.text
        holder.tvTime.text = formatTimeAgo(comment.timestamp)

        if (comment.replyToAuthor.isNotBlank()) {
            holder.tvReplyTag.visibility = View.VISIBLE
            holder.tvReplyTag.text = "Replying to @${comment.replyToAuthor}"
        } else {
            holder.tvReplyTag.visibility = View.GONE
        }

        var isTranslated = false
        var translatedTextCache = ""

        holder.btnTranslate.text = "See translation"
        holder.btnTranslate.setOnClickListener {
            if (isTranslated) {
                holder.tvText.text = comment.text
                holder.btnTranslate.text = "See translation"
                isTranslated = false
            } else {
                if (translatedTextCache.isNotBlank()) {
                    holder.tvText.text = translatedTextCache
                    holder.btnTranslate.text = "See original"
                    isTranslated = true
                } else {
                    val activeLang = LocaleHelper.getPersistedLanguage(context)
                    CoroutineScope(Dispatchers.Main).launch {
                        val trans = TranslationHelper.translateText(context, comment.text, activeLang)
                        translatedTextCache = trans
                        holder.tvText.text = trans
                        holder.btnTranslate.text = "See original"
                        isTranslated = true
                    }
                }
            }
        }

        if (comment.authorPhotoUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(comment.authorPhotoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(holder.ivAuthorAvatar)
        } else {
            Glide.with(holder.itemView.context)
                .load(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(holder.ivAuthorAvatar)
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: currentUserId
        val hasLiked = currentUid.isNotBlank() && comment.likedBy.contains(currentUid)

        if (hasLiked) {
            holder.btnLike.setImageResource(R.drawable.ic_comment_heart_liked)
            holder.tvLikesCount.text = "${comment.likesCount}"
            holder.tvLikesCount.setTextColor(Color.parseColor("#E53935"))
        } else {
            holder.btnLike.setImageResource(R.drawable.ic_comment_heart)
            holder.tvLikesCount.text = if (comment.likesCount > 0) "${comment.likesCount}" else ""
            holder.tvLikesCount.setTextColor(Color.parseColor("#8E8E93"))
        }

        val onLikeClickAction = View.OnClickListener { onLikeClick?.invoke(comment) }
        holder.btnLike.setOnClickListener(onLikeClickAction)
        holder.tvLikesCount.setOnClickListener(onLikeClickAction)

        holder.btnReply.setOnClickListener { onReplyClick?.invoke(comment) }

        if (currentUid.isNotBlank() && comment.userId == currentUid) {
            val openSettingsListener = View.OnClickListener {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }
            holder.ivAuthorAvatar.setOnClickListener(openSettingsListener)
            holder.tvAuthor.setOnClickListener(openSettingsListener)
        } else {
            holder.ivAuthorAvatar.setOnClickListener(null)
            holder.tvAuthor.setOnClickListener(null)
        }

        // Show delete option for Admin or comment author
        if (isAdmin || (currentUserId.isNotBlank() && currentUserId == comment.userId)) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener { onDeleteClick?.invoke(comment) }
        } else {
            holder.btnDelete.visibility = View.GONE
        }

        holder.itemView.setOnLongClickListener {
            onReportClick?.invoke(comment)
            true
        }
    }

    private fun formatTimeAgo(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: return "Just now"
        val diffMs = System.currentTimeMillis() - date.time
        if (diffMs < 0) return "Just now"
        val minutes = diffMs / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7

        return when {
            weeks > 0 -> "${weeks}w"
            days > 0 -> "${days}d"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "Just now"
        }
    }

    override fun getItemCount(): Int = comments.size

    fun updateData(newComments: List<ForumComment>) {
        val mergedMap = LinkedHashMap<String, ForumComment>()
        for (c in localComments) {
            val key = if (c.id.isNotBlank()) c.id else "${c.authorName}_${c.text}"
            mergedMap[key] = c
        }
        for (c in newComments) {
            val key = if (c.id.isNotBlank()) c.id else "${c.authorName}_${c.text}"
            mergedMap[key] = c
        }
        this.comments = mergedMap.values.toList().sortedBy { it.timestamp?.toDate()?.time ?: 0L }
        notifyDataSetChanged()
    }

    fun addComment(comment: ForumComment) {
        if (!localComments.contains(comment)) {
            localComments.add(comment)
        }
        val mergedMap = LinkedHashMap<String, ForumComment>()
        for (c in this.comments) {
            val key = if (c.id.isNotBlank()) c.id else "${c.authorName}_${c.text}"
            mergedMap[key] = c
        }
        val newKey = if (comment.id.isNotBlank()) comment.id else "${comment.authorName}_${comment.text}"
        mergedMap[newKey] = comment
        this.comments = mergedMap.values.toList().sortedBy { it.timestamp?.toDate()?.time ?: 0L }
        notifyDataSetChanged()
    }

    fun removeComment(comment: ForumComment) {
        localComments.remove(comment)
        val mutable = this.comments.toMutableList()
        mutable.remove(comment)
        this.comments = mutable
        notifyDataSetChanged()
    }
}
