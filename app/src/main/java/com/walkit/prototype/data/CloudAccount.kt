package com.walkit.prototype.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.walkit.prototype.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder

/** Auth and profile requests use only the public project key and the user's token. */
class CloudAccount(context: Context) {
    private val prefs=context.getSharedPreferences("walkit_cloud_session",Context.MODE_PRIVATE)
    private val base=BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key=BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()

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

    fun forumPosts(): JSONArray = requestArray("GET",
        "/rest/v1/forum_feed?select=id,author_id,username,display_name,title,body,created_at,reply_count&order=created_at.desc&limit=100",null,true)

    fun forumReplies(postId: Long): JSONArray = requestArray("GET",
        "/rest/v1/forum_thread_replies?post_id=eq.$postId&select=id,post_id,author_id,username,display_name,body,created_at&order=created_at.asc&limit=200",null,true)

    fun createForumPost(title: String,body: String) {
        require(title.trim().length in 5..100) { "Title needs 5–100 characters." }
        require(body.trim().length in 10..3000) { "Post needs 10–3000 characters." }
        val id=userId() ?: error("Log in to post in the forum.")
        requestArray("POST","/rest/v1/forum_posts",
            JSONObject().put("author_id",id).put("title",title.trim()).put("body",body.trim()),true)
    }

    fun createForumReply(postId: Long,body: String) {
        require(postId>0) { "Choose a forum post." }
        require(body.trim().length in 1..1000) { "Reply needs 1–1000 characters." }
        val id=userId() ?: error("Log in to reply.")
        requestArray("POST","/rest/v1/forum_replies",
            JSONObject().put("post_id",postId).put("author_id",id).put("body",body.trim()),true)
    }

    fun postReport(description: String,category: String,latitude: Double,longitude: Double): String {
        val id=userId() ?: error("Please log in again.")
        val response=requestArray("POST","/rest/v1/reports",
            JSONObject().put("owner_id",id).put("description",description).put("category",category)
                .put("latitude",latitude).put("longitude",longitude),true,true)
        return response.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            ?: error("The server did not return a report ID.")
    }

    fun mapReports(): JSONArray = requestArray("GET",
        "/rest/v1/reports?select=id,owner_id,description,category,latitude,longitude,created_at&order=created_at.desc&limit=100",null,true)

    fun reportPhotos(): JSONArray = requestArray("GET",
        "/rest/v1/report_photos?select=report_id,storage_path,position&order=position.asc&limit=1000",null,true)

    fun mapComments(reportId: String): JSONArray = requestArray("GET",
        "/rest/v1/map_comments?report_id=eq.$reportId&select=id,owner_id,body,anonymous,attachment_path,created_at,display_name,username&order=created_at.asc&limit=200",null,true)

    fun addReportPhoto(reportId: String,position: Int,storagePath: String) {
        val id=userId() ?: error("Log in to share a report photo.")
        rawRequest("POST","/rest/v1/report_photos?on_conflict=report_id,position",
            JSONObject().put("report_id",reportId).put("owner_id",id)
                .put("position",position).put("storage_path",storagePath),true,false,
            "resolution=ignore-duplicates")
    }

    fun postMapComment(reportId: String,body: String,anonymous: Boolean,attachmentPath: String?): String {
        val id=userId() ?: error("Log in to comment.")
        val response=requestArray("POST","/rest/v1/comments",
            JSONObject().put("report_id",reportId).put("owner_id",id)
                .put("body",body.trim()).put("anonymous",anonymous)
                .put("attachment_path",attachmentPath ?: JSONObject.NULL),true,true)
        return response.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            ?: error("The server did not return a comment ID.")
    }

    fun uploadPhoto(storagePath: String,file: File) {
        require(file.exists()) { "A photo on this device is missing." }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.absolutePath,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0) { "Could not read a photo." }
        var sample=1
        while(bounds.outWidth/sample>1600 || bounds.outHeight/sample>1600) sample*=2
        val bitmap=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample })
            ?: error("Could not read a photo.")
        val bytes=ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG,82,output)
            bitmap.recycle()
            output.toByteArray()
        }
        val request=Request.Builder().url("$base/storage/v1/object/report-photos/${encodePath(storagePath)}")
            .header("apikey",key).header("Authorization","Bearer ${token()}")
            .post(bytes.toRequestBody("image/jpeg".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            if(!response.isSuccessful && response.code!=409)
                error("Photo upload failed (${response.code}): ${response.body?.string().orEmpty().take(160)}")
        }
    }

    fun downloadPhoto(storagePath: String): ByteArray {
        val request=Request.Builder().url("$base/storage/v1/object/report-photos/${encodePath(storagePath)}")
            .header("apikey",key).header("Authorization","Bearer ${token()}").get().build()
        return http.newCall(request).execute().use { response ->
            if(!response.isSuccessful) error("Could not load a shared photo (${response.code}).")
            response.body?.bytes() ?: error("The shared photo is empty.")
        }
    }

    private fun encodePath(path: String): String = path.split('/').joinToString("/") { URLEncoder.encode(it,"UTF-8") }

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

    private fun rawRequest(method: String,path: String,body: JSONObject?,auth: Boolean,returnRows: Boolean,prefer: String?=null): String {
        check(configured) { "Supabase is not configured." }
        val request=Request.Builder().url(base+path).header("apikey",key).apply {
            if(auth) header("Authorization","Bearer ${token()}")
            if(returnRows) header("Prefer","return=representation")
            if(prefer!=null) header("Prefer",prefer)
            method(method,body?.toString()?.toRequestBody("application/json".toMediaType()))
        }.build()
        return http.newCall(request).execute().use { response ->
            val text=response.body?.string().orEmpty()
            if(!response.isSuccessful) {
                val error=runCatching { JSONObject(text) }.getOrNull()
                throw IllegalArgumentException(error?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: error?.optString("error_description")?.takeIf { it.isNotBlank() }
                    ?: error?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Supabase request failed (${response.code}).")
            }
            text
        }
    }
}
