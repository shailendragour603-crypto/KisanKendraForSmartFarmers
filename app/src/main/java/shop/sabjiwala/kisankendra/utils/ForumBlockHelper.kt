package shop.sabjiwala.kisankendra.utils

import android.content.Context
import androidx.core.content.edit

object ForumBlockHelper {

    private const val PREFS_BLOCK = "forum_block_prefs"
    private const val KEY_BLOCKED_USERS = "blocked_users_set"

    fun blockUser(context: Context, userId: String) {
        if (userId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_BLOCK, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(KEY_BLOCKED_USERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(userId)
        prefs.edit {
            putStringSet(KEY_BLOCKED_USERS, currentSet)
        }
    }

    fun isUserBlocked(context: Context, userId: String): Boolean {
        if (userId.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_BLOCK, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(KEY_BLOCKED_USERS, emptySet()) ?: emptySet()
        return currentSet.contains(userId)
    }

    fun getBlockedUsers(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_BLOCK, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BLOCKED_USERS, emptySet()) ?: emptySet()
    }
}
