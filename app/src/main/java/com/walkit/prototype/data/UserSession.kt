package com.walkit.prototype.data

import android.content.Context

class UserSession(context: Context, preferencesName: String = "walkit_session") {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun userId(): Long? = preferences.getLong("user_id", -1L).takeIf { it > 0 }

    fun signIn(userId: Long) {
        // Store the account ID, never the password.
        preferences.edit().putLong("user_id", userId).apply()
    }

    fun signOut() {
        preferences.edit().remove("user_id").apply()
    }
}
