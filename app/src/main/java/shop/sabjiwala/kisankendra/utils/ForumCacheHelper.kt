package shop.sabjiwala.kisankendra.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import shop.sabjiwala.kisankendra.model.ForumComment
import shop.sabjiwala.kisankendra.model.ForumPost

object ForumCacheHelper {
    private const val PREFS_NAME = "forum_local_cache"
    private const val KEY_POSTS_LIST = "cached_forum_posts_json"
    private const val KEY_COMMENTS_PREFIX = "cached_comments_"
    private const val KEY_REPLIES_PREFIX = "cached_replies_count_"
    private const val KEY_LIKES_PREFIX = "cached_likes_count_"

    private val gson = Gson()

    fun savePostsLocally(context: Context, posts: List<ForumPost>) {
        if (posts.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(posts)
        prefs.edit().putString(KEY_POSTS_LIST, json).apply()
    }

    fun getLocalPosts(context: Context): List<ForumPost> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_POSTS_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ForumPost>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLocalComment(context: Context, postId: String, comment: ForumComment) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_COMMENTS_PREFIX + postId
        val json = prefs.getString(key, null)
        val list = if (json != null) {
            try {
                val type = object : TypeToken<MutableList<ForumComment>>() {}.type
                gson.fromJson<MutableList<ForumComment>>(json, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        val commentKey = if (comment.id.isNotBlank()) comment.id else "${comment.authorName}_${comment.text}"
        if (list.none { (if (it.id.isNotBlank()) it.id else "${it.authorName}_${it.text}") == commentKey }) {
            list.add(comment)
            prefs.edit().putString(key, gson.toJson(list)).apply()
        }

        val currentCount = getLocalReplyCount(context, postId, 0)
        saveLocalReplyCount(context, postId, currentCount + 1)
    }

    fun getLocalComments(context: Context, postId: String): List<ForumComment> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_COMMENTS_PREFIX + postId
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ForumComment>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLocalReplyCount(context: Context, postId: String, count: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_REPLIES_PREFIX + postId, count).apply()
    }

    fun getLocalReplyCount(context: Context, postId: String, defaultCount: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getInt(KEY_REPLIES_PREFIX + postId, -1)
        return if (cached != -1) maxOf(cached, defaultCount) else defaultCount
    }

    fun saveLocalLikeCount(context: Context, postId: String, count: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LIKES_PREFIX + postId, count).apply()
    }

    fun getLocalLikeCount(context: Context, postId: String, defaultCount: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getInt(KEY_LIKES_PREFIX + postId, -1)
        return if (cached != -1) maxOf(cached, defaultCount) else defaultCount
    }

    fun saveUserLikedStatus(context: Context, itemId: String, userId: String, isLiked: Boolean) {
        if (itemId.isBlank() || userId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_liked_${itemId}_$userId", isLiked).apply()
    }

    fun isUserLikedStatus(context: Context, itemId: String, userId: String, defaultStatus: Boolean): Boolean {
        if (itemId.isBlank() || userId.isBlank()) return defaultStatus
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains("user_liked_${itemId}_$userId")) return defaultStatus
        return prefs.getBoolean("user_liked_${itemId}_$userId", defaultStatus)
    }
}
