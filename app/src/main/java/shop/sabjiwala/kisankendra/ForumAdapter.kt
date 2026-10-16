package shop.sabjiwala.kisankendra

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.databinding.ItemInfeedAdBinding
import shop.sabjiwala.kisankendra.model.ForumPost
import shop.sabjiwala.kisankendra.utils.ForumBlockHelper
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.MediaUtils
import shop.sabjiwala.kisankendra.utils.TranslationHelper
import java.text.SimpleDateFormat
import java.util.Locale

class ForumAdapter(
    private var posts: List<ForumPost>,
    private val onLikeClick: (ForumPost) -> Unit,
    private val onReplyClick: (ForumPost) -> Unit,
    private val onShareClick: (ForumPost) -> Unit,
    private val onMediaClick: ((url: String, isVideo: Boolean) -> Unit)? = null,
    private val onOptionsClick: ((ForumPost, View) -> Unit)? = null,
    private val onAudioToggleClick: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_POST = 0
        private const val TYPE_AD = 1
    }

    class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAuthorAvatar: ImageView = view.findViewById(R.id.ivAuthorAvatar)
        val tvAuthorName: TextView = view.findViewById(R.id.tvAuthorName)
        val tvPostTime: TextView = view.findViewById(R.id.tvPostTime)
        val tvCategoryBadge: TextView = view.findViewById(R.id.tvForumCategoryBadge)
        val btnMoreOptions: ImageButton = view.findViewById(R.id.btnPostMoreOptions)
        val tvDesc: TextView = view.findViewById(R.id.tvQuestionDesc)
        val ivPhoto: ImageView = view.findViewById(R.id.ivQuestionPhoto)
        val layoutVideoContainer: FrameLayout = view.findViewById(R.id.layoutVideoContainer)
        val vvInFeedVideo: VideoView = view.findViewById(R.id.vvInFeedVideo)
        val ivVideoThumbnail: ImageView = view.findViewById(R.id.ivQuestionVideoThumbnail)
        val btnPlayVideo: MaterialButton = view.findViewById(R.id.btnPlayVideoFeed)
        val btnAudioToggle: ImageButton = view.findViewById(R.id.btnFeedAudioToggle)
        val btnLike: MaterialButton = view.findViewById(R.id.btnLikePost)
        val btnReplies: MaterialButton = view.findViewById(R.id.btnReplies)
        val btnShare: MaterialButton = view.findViewById(R.id.btnSharePost)
    }

    class AdViewHolder(val binding: ItemInfeedAdBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if ((position + 1) % 6 == 0) TYPE_AD else TYPE_POST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_AD) {
            val binding = ItemInfeedAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AdViewHolder(binding)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_forum_post, parent, false)
            PostViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentPos = if (holder.adapterPosition != RecyclerView.NO_POSITION) holder.adapterPosition else position

        if (getItemViewType(currentPos) == TYPE_AD) {
            val adHolder = holder as AdViewHolder
            val context = holder.itemView.context
            try {
                val adLoader = AdLoader.Builder(context, "ca-app-pub-3054731531860427/8792970999")
                    .forNativeAd { nativeAd ->
                        adHolder.binding.myTemplate.setNativeAd(nativeAd)
                        adHolder.itemView.visibility = View.VISIBLE
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            adHolder.itemView.visibility = View.GONE
                        }
                    })
                    .build()
                adLoader.loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
                adHolder.itemView.visibility = View.GONE
            }
            return
        }

        val realIndex = currentPos - (currentPos / 6)
        if (realIndex !in posts.indices) return

        val item = posts[realIndex]
        val postHolder = holder as PostViewHolder
        val context = holder.itemView.context

        // Hide post if authored by a blocked user
        if (ForumBlockHelper.isUserBlocked(context, item.userId)) {
            postHolder.itemView.visibility = View.GONE
            val lp = postHolder.itemView.layoutParams
            if (lp != null) {
                lp.width = 0
                lp.height = 0
                postHolder.itemView.layoutParams = lp
            } else {
                postHolder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            }
            return
        } else {
            postHolder.itemView.visibility = View.VISIBLE
            val lp = postHolder.itemView.layoutParams
            if (lp != null && (lp.width == 0 || lp.height == 0)) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                postHolder.itemView.layoutParams = lp
            }
        }

        val author = item.authorName.ifBlank { "Farmer" }
        val location = item.authorLocation.ifBlank { "India" }
        postHolder.tvAuthorName.text = "$author • $location"

        if (item.authorPhotoUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(item.authorPhotoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(postHolder.ivAuthorAvatar)
        } else {
            Glide.with(holder.itemView.context)
                .load(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(postHolder.ivAuthorAvatar)
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (!currentUid.isNullOrBlank() && item.userId == currentUid) {
            val openSettingsListener = View.OnClickListener {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }
            postHolder.ivAuthorAvatar.setOnClickListener(openSettingsListener)
            postHolder.tvAuthorName.setOnClickListener(openSettingsListener)
        } else {
            postHolder.ivAuthorAvatar.setOnClickListener(null)
            postHolder.tvAuthorName.setOnClickListener(null)
        }

        val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val timeStr = item.timestamp?.toDate()?.let { dateFormat.format(it) } ?: "Recently"
        postHolder.tvPostTime.text = timeStr

        postHolder.tvCategoryBadge.text = item.category.ifBlank { "General" }
        postHolder.tvDesc.text = item.description

        // Dynamic AI Translation for non-English active languages
        val activeLang = LocaleHelper.getPersistedLanguage(context)
        if (activeLang != "en") {
            CoroutineScope(Dispatchers.Main).launch {
                val translatedDesc = TranslationHelper.translateText(context, item.description, activeLang)
                postHolder.tvDesc.text = translatedDesc
            }
        }

        // Image Attachment Handling
        if (item.photoUrl.isNotBlank()) {
            postHolder.ivPhoto.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(item.photoUrl)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(postHolder.ivPhoto)

            postHolder.ivPhoto.setOnClickListener {
                onMediaClick?.invoke(item.photoUrl, false)
            }
        } else {
            postHolder.ivPhoto.visibility = View.GONE
        }

        // Video Attachment Handling
        if (item.videoUrl.isNotBlank()) {
            postHolder.layoutVideoContainer.visibility = View.VISIBLE
            postHolder.ivVideoThumbnail.visibility = View.VISIBLE
            postHolder.btnPlayVideo.visibility = View.VISIBLE

            if (ForumFragment.isSessionAudioMuted) {
                postHolder.btnAudioToggle.setImageResource(R.drawable.ic_volume_off)
            } else {
                postHolder.btnAudioToggle.setImageResource(R.drawable.ic_volume_up)
            }

            postHolder.btnAudioToggle.setOnClickListener {
                onAudioToggleClick?.invoke()
            }

            Glide.with(holder.itemView.context)
                .asBitmap()
                .load(if (item.photoUrl.isNotBlank()) item.photoUrl else item.videoUrl)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(postHolder.ivVideoThumbnail)

            val openVideoListener = View.OnClickListener {
                onMediaClick?.invoke(item.videoUrl, true)
            }
            postHolder.btnPlayVideo.setOnClickListener(openVideoListener)
            postHolder.layoutVideoContainer.setOnClickListener(openVideoListener)
        } else {
            postHolder.layoutVideoContainer.visibility = View.GONE
        }

        val hasLiked = !currentUid.isNullOrBlank() && item.likedBy.contains(currentUid)
        val defaultColor = postHolder.btnReplies.currentTextColor
        if (hasLiked) {
            postHolder.btnLike.text = if (item.likesCount > 0) "${item.likesCount}" else ""
            postHolder.btnLike.setIconResource(R.drawable.ic_feed_heart_liked)
            postHolder.btnLike.setTextColor(defaultColor)
            postHolder.btnLike.strokeColor = ColorStateList.valueOf(defaultColor)
        } else {
            postHolder.btnLike.text = if (item.likesCount > 0) "${item.likesCount}" else ""
            postHolder.btnLike.setIconResource(R.drawable.ic_feed_heart)
            postHolder.btnLike.setTextColor(defaultColor)
            postHolder.btnLike.strokeColor = ColorStateList.valueOf(defaultColor)
        }

        postHolder.btnReplies.text = if (item.repliesCount > 0) "${item.repliesCount}" else ""
        postHolder.btnShare.text = if (item.sharesCount > 0) "${item.sharesCount}" else ""

        postHolder.btnLike.setOnClickListener {
            onLikeClick(item)
        }
        postHolder.btnReplies.setOnClickListener { onReplyClick(item) }
        postHolder.btnShare.setOnClickListener { onShareClick(item) }
        postHolder.btnMoreOptions.setOnClickListener { v -> onOptionsClick?.invoke(item, v) }
    }

    override fun getItemCount(): Int {
        if (posts.isEmpty()) return 0
        val totalAds = posts.size / 5
        return posts.size + totalAds
    }

    fun getItemAt(position: Int): ForumPost? {
        if (posts.isEmpty()) return null
        val realIndex = position - (position / 6)
        return posts.getOrNull(realIndex)
    }

    fun updateData(newPosts: List<ForumPost>) {
        this.posts = newPosts
        notifyDataSetChanged()
    }

    fun notifyItemLiked(postId: String) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index != -1) {
            val totalAdsBefore = index / 5
            val adapterPosition = index + totalAdsBefore
            notifyItemChanged(adapterPosition)
        }
    }
}
