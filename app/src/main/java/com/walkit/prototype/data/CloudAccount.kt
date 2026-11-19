package com.walkit.prototype.data

import android.content.Context
import com.walkit.prototype.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Auth and profile requests use only the public project key and the user's token. */
class CloudAccount(context: Context) {
    private val prefs=context.getSharedPreferences("walkit_cloud_session",Context.MODE_PRIVATE)
    private val base=BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key=BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val configured: Boolean get() = base.startsWith("https://") && key.startsWith("sb_publishable_")
    fun userId(): String? = prefs.getString("user_id",null)

    fun signUp(email: String,password: String,username: String,displayName: String): Boolean {
        require(email.contains('@') && email.contains('.')) { "Enter a valid email address." }
        require(username.matches(Regex("[a-z0-9_]{3,24}"))) { "Username needs 3–24 lowercase letters, digits, or underscores." }
        require(password.length in 12..128) { "Password needs 12–128 characters." }
        require(displayName.trim().length in 1..60) { "Name needs 1–60 characters." }
        val body=JSONObject().put("email",email.trim()).put("password",password)
            .put("data",JSONObject().put("username",username).put("display_name",displayName.trim()))
        val response=request("POST","/auth/v1/signup",body,false)
        return if(response.optString("access_token").isNotEmpty()) { saveSession(response); true } else false
    }

    fun signIn(email: String,password: String): String {
        val response=request("POST","/auth/v1/token?grant_type=password",
            JSONObject().put("email",email.trim()).put("password",password),false)
        saveSession(response)
        return userId() ?: error("The server did not return a user ID.")
    }

    fun restore(): String? {
        if(prefs.getString("refresh_token",null).isNullOrBlank()) return null
        return try { token(); userId() } catch (_: Exception) { null }
    }

    fun signOut() {
        try { request("POST","/auth/v1/logout",JSONObject(),true) } catch (_: Exception) {}
        prefs.edit().clear().apply()
    }

    fun profile(): JSONObject {
        val id=userId() ?: error("Please log in again.")
        val rows=requestArray("GET","/rest/v1/profiles?id=eq.$id&select=id,username,display_name,bio",null,true)
        return rows.optJSONObject(0) ?: error("Your profile is unavailable. Ask the team to apply the profile trigger.")
    }

    fun updateProfile(displayName: String,bio: String) {
        require(displayName.trim().length in 1..60) { "Name needs 1–60 characters." }
        require(bio.trim().length<=300) { "Bio must be 300 characters or fewer." }
        val id=userId() ?: error("Please log in again.")
        request("PATCH","/rest/v1/profiles?id=eq.$id",
            JSONObject().put("display_name",displayName.trim()).put("bio",bio.trim()),true)
    }

    fun leaderboard(): JSONArray = requestArray("GET",
        "/rest/v1/leaderboard?select=id,username,display_name,reports,comments,points&order=points.desc,display_name.asc",null,true)

    fun postReport(description: String,category: String,latitude: Double,longitude: Double): String {
        val id=userId() ?: error("Please log in again.")
        val response=requestArray("POST","/rest/v1/reports",
            JSONObject().put("owner_id",id).put("description",description).put("category",category)
                .put("latitude",latitude).put("longitude",longitude),true,true)
        return response.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            ?: error("The server did not return a report ID.")
    }

    @Synchronized private fun token(): String {
        val access=prefs.getString("access_token",null)
        if(!access.isNullOrBlank() && prefs.getLong("expires_at",0)>System.currentTimeMillis()+30_000) return access
        val refresh=prefs.getString("refresh_token",null) ?: error("Please log in again.")
        saveSession(request("POST","/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token",refresh),false))
        return prefs.getString("access_token",null) ?: error("Please log in again.")
    }

    private fun saveSession(response: JSONObject) {
        val user=response.optJSONObject("user") ?: error("The server did not return a user.")
        val id=user.optString("id").takeIf { it.isNotBlank() } ?: error("The server did not return a user ID.")
        prefs.edit().putString("user_id",id)
            .putString("access_token",response.getString("access_token"))
            .putString("refresh_token",response.getString("refresh_token"))
            .putLong("expires_at",System.currentTimeMillis()+response.optLong("expires_in",3600)*1000L)
            .apply()
    }

    private fun requestArray(method: String,path: String,body: JSONObject?,auth: Boolean,returnRows: Boolean=false): JSONArray {
        val text=rawRequest(method,path,body,auth,returnRows)
        return JSONArray(text.ifBlank { "[]" })
    }

    private fun request(method: String,path: String,body: JSONObject?,auth: Boolean): JSONObject {
        val text=rawRequest(method,path,body,auth,false)
        return JSONObject(text.ifBlank { "{}" })
    }

    private fun rawRequest(method: String,path: String,body: JSONObject?,auth: Boolean,returnRows: Boolean): String {
        check(configured) { "Supabase is not configured." }
        val connection=(URL(base+path).openConnection() as HttpURLConnection).apply {
            requestMethod=method
            connectTimeout=15_000
            readTimeout=20_000
            setRequestProperty("apikey",key)
            setRequestProperty("Content-Type","application/json")
            if(auth) setRequestProperty("Authorization","Bearer ${token()}")
            if(returnRows) setRequestProperty("Prefer","return=representation")
            if(body!=null) {
                doOutput=true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val text=(if(connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if(connection.responseCode !in 200..299) {
                val error=runCatching { JSONObject(text) }.getOrNull()
                throw IllegalArgumentException(error?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: error?.optString("error_description")?.takeIf { it.isNotBlank() }
                    ?: error?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Supabase request failed (${connection.responseCode}).")
            }
            text
        } finally { connection.disconnect() }
    }
}
