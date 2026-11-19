package com.walkit.prototype.data

import android.content.Context
import android.net.Uri
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
    companion object { const val AUTH_CALLBACK="walkit://auth/callback" }
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
        val response=request("POST","/auth/v1/signup?redirect_to=${URLEncoder.encode(AUTH_CALLBACK,"UTF-8")}",body,false)
        return if(response.optString("access_token").isNotEmpty()) { saveSession(response); true } else false
    }

    fun signIn(email: String,password: String): String {
        val response=request("POST","/auth/v1/token?grant_type=password",
            JSONObject().put("email",email.trim()).put("password",password),false)
        saveSession(response)
        return userId() ?: error("The server did not return a user ID.")
    }

    fun oauthUrl(provider: String): String {
        require(provider=="google" || provider=="apple") { "Unsupported sign-in provider." }
        return "$base/auth/v1/authorize?provider=$provider&redirect_to=${URLEncoder.encode(AUTH_CALLBACK,"UTF-8")}"
    }

    fun providerEnabled(provider: String): Boolean =
        request("GET","/auth/v1/settings",null,false).optJSONObject("external")?.optBoolean(provider) == true

    fun acceptAuthRedirect(uri: Uri) {
        require(uri.scheme=="walkit" && uri.host=="auth" && uri.path=="/callback") { "Invalid sign-in return link." }
        val params=Uri.parse("https://walkit.invalid/?${uri.fragment.orEmpty()}")
        val redirectError=params.getQueryParameter("error_description") ?: uri.getQueryParameter("error_description")
        if(!redirectError.isNullOrBlank()) throw IllegalArgumentException(redirectError)
        val access=params.getQueryParameter("access_token") ?: error("The sign-in link did not include a session. Check the Supabase redirect settings.")
        val refresh=params.getQueryParameter("refresh_token") ?: error("The sign-in link did not include a refresh token.")
        val userRequest=Request.Builder().url("$base/auth/v1/user")
            .header("apikey",key).header("Authorization","Bearer $access").get().build()
        val user=http.newCall(userRequest).execute().use { response ->
            if(!response.isSuccessful) error("The sign-in link could not be verified (${response.code}).")
            JSONObject(response.body?.string().orEmpty())
        }
        saveSession(JSONObject().put("user",user).put("access_token",access)
            .put("refresh_token",refresh).put("expires_in",params.getQueryParameter("expires_in")?.toLongOrNull() ?: 3600))
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
        val rows=runCatching { requestArray("GET","/rest/v1/profiles?id=eq.$id&select=id,username,display_name,bio,avatar_path",null,true) }
            .getOrElse { requestArray("GET","/rest/v1/profiles?id=eq.$id&select=id,username,display_name,bio",null,true) }
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

    fun forumPosts(): JSONArray = runCatching { requestArray("GET",
        "/rest/v1/forum_feed?select=id,author_id,username,display_name,title,body,created_at,reply_count,image_path,score,my_vote&order=created_at.desc&limit=100",null,true) }
        .getOrElse { requestArray("GET","/rest/v1/forum_feed?select=id,author_id,username,display_name,title,body,created_at,reply_count&order=created_at.desc&limit=100",null,true) }

    fun forumReplies(postId: Long): JSONArray = requestArray("GET",
        "/rest/v1/forum_thread_replies?post_id=eq.$postId&select=id,post_id,author_id,username,display_name,body,created_at&order=created_at.asc&limit=200",null,true)

    fun friendships(): JSONArray {
        val id=userId() ?: error("Log in to see your friends.")
        return requestArray("GET","/rest/v1/friendships?or=(requester_id.eq.$id,addressee_id.eq.$id)"+
            "&select=id,status,requester_id,addressee_id,requester:profiles!friendships_requester_id_fkey(username,display_name),addressee:profiles!friendships_addressee_id_fkey(username,display_name)&order=created_at.desc",null,true)
    }

    fun sendFriendRequest(username: String) {
        val self=userId() ?: error("Log in to add friends.")
        val name=username.trim().removePrefix("@").lowercase()
        require(name.matches(Regex("[a-z0-9_]{3,24}"))) { "Enter a valid username." }
        val person=requestArray("GET","/rest/v1/profiles?username=eq.${URLEncoder.encode(name,"UTF-8")}&select=id",null,true)
            .optJSONObject(0) ?: error("No WalkIt user has that username.")
        val other=person.getString("id")
        require(other!=self) { "You cannot add yourself." }
        val relationships=friendships()
        val existing=(0 until relationships.length()).map { relationships.getJSONObject(it) }
            .firstOrNull { it.optString("requester_id")==other || it.optString("addressee_id")==other }
        if(existing!=null) {
            if(existing.optString("status")=="accepted") error("You are already friends.")
            if(existing.optString("addressee_id")==self) { acceptFriendRequest(existing.getString("id")); return }
            error("Friend request already sent.")
        }
        request("POST","/rest/v1/friendships",JSONObject().put("requester_id",self)
            .put("addressee_id",other).put("status","pending"),true)
    }

    fun acceptFriendRequest(id: String) {
        require(id.matches(Regex("[0-9a-f-]{36}"))) { "Invalid friend request." }
        request("PATCH","/rest/v1/friendships?id=eq.$id",JSONObject().put("status","accepted"),true)
    }

    fun removeFriendship(id: String) {
        require(id.matches(Regex("[0-9a-f-]{36}"))) { "Invalid friend request." }
        request("DELETE","/rest/v1/friendships?id=eq.$id",null,true)
    }

    fun createForumPost(title: String,body: String,imageFile: File?=null) {
        require(title.trim().length in 5..100) { "Title needs 5–100 characters." }
        require(body.trim().length in 10..3000) { "Post needs 10–3000 characters." }
        val id=userId() ?: error("Log in to post in the forum.")
        val imagePath=imageFile?.let {
            val path="$id/post-${java.util.UUID.randomUUID()}.jpg"
            uploadImage("forum-photos",path,it,1600)
            path
        }
        val payload=JSONObject().put("author_id",id).put("title",title.trim()).put("body",body.trim())
        if(imagePath!=null) payload.put("image_path",imagePath)
        requestArray("POST","/rest/v1/forum_posts",payload,true)
    }

    fun voteForumPost(postId: Long,value: Int) {
        require(postId>0 && value in -1..1) { "Invalid vote." }
        val id=userId() ?: error("Log in to vote.")
        if(value==0) rawRequest("DELETE","/rest/v1/forum_votes?post_id=eq.$postId&voter_id=eq.$id",null,true,false)
        else rawRequest("POST","/rest/v1/forum_votes?on_conflict=post_id,voter_id",
            JSONObject().put("post_id",postId).put("voter_id",id).put("value",value),true,false,"resolution=merge-duplicates")
    }

    fun deleteForumPost(postId: Long) {
        require(postId>0) { "Choose a forum post." }
        val id=userId() ?: error("Log in to remove your post.")
        val removed=requestArray("DELETE","/rest/v1/forum_posts?id=eq.$postId&author_id=eq.$id&select=id",null,true,true)
        require(removed.length()==1) { "Only the post owner can remove this post." }
    }

    fun downloadForumPhoto(path: String): ByteArray {
        val request=Request.Builder().url("$base/storage/v1/object/forum-photos/${encodePath(path)}")
            .header("apikey",key).header("Authorization","Bearer ${token()}").get().build()
        return http.newCall(request).execute().use { response ->
            if(!response.isSuccessful) error("Could not load a forum photo (${response.code}).")
            response.body?.bytes() ?: error("The forum photo is empty.")
        }
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

    fun mapComments(reportId: String): JSONArray = runCatching { requestArray("GET",
        "/rest/v1/map_comments?report_id=eq.$reportId&select=id,owner_id,parent_id,body,anonymous,attachment_path,created_at,display_name,username,avatar_path&order=created_at.asc&limit=200",null,true) }
        .getOrElse { requestArray("GET",
            "/rest/v1/map_comments?report_id=eq.$reportId&select=id,owner_id,body,anonymous,attachment_path,created_at,display_name,username&order=created_at.asc&limit=200",null,true) }

    fun reportReactions(reportId: String): JSONArray = requestArray("GET",
        "/rest/v1/report_reactions?report_id=eq.$reportId&select=owner_id,kind",null,true)

    fun commentReactions(reportId: String): JSONArray = requestArray("GET",
        "/rest/v1/map_comment_reactions?report_id=eq.$reportId&select=comment_id,owner_id,kind",null,true)

    fun setReportReaction(reportId: String,kind: String?) = setReaction("report_reactions","report_id",reportId,kind)
    fun setCommentReaction(commentId: String,kind: String?) = setReaction("comment_reactions","comment_id",commentId,kind)
    private fun setReaction(table: String,idField: String,targetId: String,kind: String?) {
        val id=userId() ?: error("Log in to react.")
        require(kind==null || kind in setOf("like","love","care","haha","wow","sad","angry")) { "Invalid reaction." }
        if(kind==null) rawRequest("DELETE","/rest/v1/$table?$idField=eq.$targetId&owner_id=eq.$id",null,true,false)
        else rawRequest("POST","/rest/v1/$table?on_conflict=$idField,owner_id",
            JSONObject().put(idField,targetId).put("owner_id",id).put("kind",kind),true,false,
            "resolution=merge-duplicates")
    }

    fun addReportPhoto(reportId: String,position: Int,storagePath: String) {
        val id=userId() ?: error("Log in to share a report photo.")
        rawRequest("POST","/rest/v1/report_photos?on_conflict=report_id,position",
            JSONObject().put("report_id",reportId).put("owner_id",id)
                .put("position",position).put("storage_path",storagePath),true,false,
            "resolution=ignore-duplicates")
    }

    fun postMapComment(reportId: String,body: String,anonymous: Boolean,attachmentPath: String?,parentId: String?=null): String {
        val id=userId() ?: error("Log in to comment.")
        val payload=JSONObject().put("report_id",reportId).put("owner_id",id)
                .put("body",body.trim()).put("anonymous",anonymous)
                .put("attachment_path",attachmentPath ?: JSONObject.NULL)
        if(parentId!=null) payload.put("parent_id",parentId)
        val response=requestArray("POST","/rest/v1/comments",payload,true,true)
        return response.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            ?: error("The server did not return a comment ID.")
    }

    fun uploadPhoto(storagePath: String,file: File) = uploadImage("report-photos",storagePath,file,1600)
    fun uploadAvatar(file: File): String {
        val id=userId() ?: error("Log in to change your photo.")
        val path="$id/avatar-${System.currentTimeMillis()}.jpg"
        uploadImage("profile-photos",path,file,512)
        request("PATCH","/rest/v1/profiles?id=eq.$id",JSONObject().put("avatar_path",path),true)
        return path
    }
    private fun uploadImage(bucket: String,storagePath: String,file: File,maxSize: Int) {
        require(file.exists()) { "A photo on this device is missing." }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.absolutePath,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0) { "Could not read a photo." }
        var sample=1
        while(bounds.outWidth/sample>maxSize || bounds.outHeight/sample>maxSize) sample*=2
        val bitmap=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample })
            ?: error("Could not read a photo.")
        val bytes=ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG,82,output)
            bitmap.recycle()
            output.toByteArray()
        }
        val request=Request.Builder().url("$base/storage/v1/object/$bucket/${encodePath(storagePath)}")
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
    fun downloadAvatar(storagePath: String): ByteArray {
        val request=Request.Builder().url("$base/storage/v1/object/profile-photos/${encodePath(storagePath)}")
            .header("apikey",key).header("Authorization","Bearer ${token()}").get().build()
        return http.newCall(request).execute().use { response ->
            if(!response.isSuccessful) error("Could not load a profile photo (${response.code}).")
            response.body?.bytes() ?: error("The profile photo is empty.")
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
