package com.walkit.prototype

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Hazard(val id: Long, val ownerId: Long, val owner: String, val segmentId: Long,
                  val segment: String, val title: String, val category: String,
                  val severity: Int, val details: String, val status: String)
data class Route(val name: String, val meters: Int, val hazards: Int, val barriers: Int)
data class PhotoReport(val id: Long, val description: String, val time: String, val location: String, val photos: List<String>, val cloudId: String? = null, val ownerId: Long? = null, val ownerCloudId: String? = null)
data class PhotoComment(val id: Long, val reportId: Long, val text: String, val anonymous: Boolean, val attachmentPath: String?, val createdAt: Long, val authorName: String = "WalkIt user", val cloudId: String? = null, val parentId: String? = null, val avatarPath: String? = null, val ownerCloudId: String? = null)
data class UserProfile(
    val id: Long,
    val username: String,
    val displayName: String,
    val bio: String,
    val reportCount: Int,
    val commentCount: Int
)
data class LeaderboardEntry(val userId: Long, val displayName: String, val username: String, val reports: Int, val comments: Int, val points: Int)

class WalkItDb(context: Context, databaseName: String = "walkit.db") : SQLiteOpenHelper(context, databaseName, null, 6) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, bio TEXT NOT NULL DEFAULT '', salt BLOB NOT NULL, password_hash BLOB NOT NULL, cloud_id TEXT UNIQUE)")
        db.execSQL("CREATE TABLE pathway_segments (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE walking_routes (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, distance_m INTEGER NOT NULL CHECK(distance_m > 0))")
        db.execSQL("CREATE TABLE route_segments (route_id INTEGER NOT NULL REFERENCES walking_routes(id), segment_id INTEGER NOT NULL REFERENCES pathway_segments(id), sequence_no INTEGER NOT NULL, PRIMARY KEY(route_id, segment_id), UNIQUE(route_id, sequence_no))")
        db.execSQL("CREATE TABLE hazard_reports (id INTEGER PRIMARY KEY, owner_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, segment_id INTEGER NOT NULL REFERENCES pathway_segments(id), title TEXT NOT NULL, details TEXT NOT NULL, category TEXT NOT NULL, severity INTEGER NOT NULL CHECK(severity BETWEEN 1 AND 3), status TEXT NOT NULL CHECK(status IN ('Active','Resolved')), observed_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX reports_segment_status ON hazard_reports(segment_id,status)")
        db.execSQL("CREATE INDEX reports_owner ON hazard_reports(owner_id)")
        db.execSQL("CREATE TABLE badges (id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE, threshold INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE user_badges (user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, badge_id INTEGER NOT NULL REFERENCES badges(id), PRIMARY KEY(user_id,badge_id))")
        db.execSQL("CREATE TABLE saved_routes (user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, route_id INTEGER NOT NULL REFERENCES walking_routes(id), PRIMARY KEY(user_id,route_id))")
        db.execSQL("CREATE TABLE user_preferences (user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, route_preference TEXT NOT NULL)")
        createPhotoTables(db)
        createPhotoDiscussionTable(db)
        listOf("North Walk", "Library Path", "Garden Lane", "South Walk").forEach { name ->
            db.execSQL("INSERT INTO pathway_segments(name) VALUES (?)", arrayOf(name))
        }
        db.execSQL("INSERT INTO walking_routes(name,distance_m) VALUES ('Library route',650),('Garden route',820)")
        db.execSQL("INSERT INTO route_segments(route_id,segment_id,sequence_no) VALUES (1,1,1),(1,2,2),(1,4,3),(2,1,1),(2,3,2),(2,4,3)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createPhotoTables(db)
        if (oldVersion < 3) createPhotoDiscussionTable(db)
        if (oldVersion < 4) {
            // Keep existing accounts and reports when adding profile information.
            db.execSQL("ALTER TABLE users ADD COLUMN bio TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE users ADD COLUMN cloud_id TEXT")
            db.execSQL("CREATE UNIQUE INDEX users_cloud_id ON users(cloud_id)")
            if(!hasColumn(db,"photo_reports","cloud_id")) db.execSQL("ALTER TABLE photo_reports ADD COLUMN cloud_id TEXT")
        }
        if (oldVersion < 6) {
            if(!hasColumn(db,"photo_items","cloud_path")) db.execSQL("ALTER TABLE photo_items ADD COLUMN cloud_path TEXT")
            if(!hasColumn(db,"photo_comments","cloud_id")) db.execSQL("ALTER TABLE photo_comments ADD COLUMN cloud_id TEXT")
        }
    }

    private fun hasColumn(db: SQLiteDatabase,table: String,column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)",null).use { cursor ->
            while(cursor.moveToNext()) if(cursor.getString(1)==column) return true
        }
        return false
    }

    private fun createPhotoTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS photo_reports (id INTEGER PRIMARY KEY, owner_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, description TEXT NOT NULL, captured_time TEXT NOT NULL, location TEXT NOT NULL, created_at INTEGER NOT NULL, cloud_id TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS photo_items (id INTEGER PRIMARY KEY, report_id INTEGER NOT NULL REFERENCES photo_reports(id) ON DELETE CASCADE, local_path TEXT NOT NULL, cloud_path TEXT)")
    }

    private fun createPhotoDiscussionTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS photo_comments (id INTEGER PRIMARY KEY, report_id INTEGER NOT NULL REFERENCES photo_reports(id) ON DELETE CASCADE, owner_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, body TEXT NOT NULL, anonymous INTEGER NOT NULL DEFAULT 0, attachment_path TEXT, created_at INTEGER NOT NULL, cloud_id TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS photo_comments_report ON photo_comments(report_id,created_at)")
    }

    fun addPhotoComment(reportId: Long, ownerId: Long, body: String, anonymous: Boolean, attachmentPath: String?) {
        require(body.trim().isNotEmpty() || attachmentPath != null) { "Write a comment or attach a photo." }
        writableDatabase.insertOrThrow("photo_comments",null,ContentValues().apply {
            put("report_id",reportId); put("owner_id",ownerId); put("body",body.trim())
            put("anonymous",if(anonymous) 1 else 0); put("attachment_path",attachmentPath)
            put("created_at",System.currentTimeMillis())
        })
    }

    fun photoComments(reportId: Long): List<PhotoComment> = buildList {
        readableDatabase.rawQuery("SELECT id,report_id,body,anonymous,attachment_path,created_at FROM photo_comments WHERE report_id=? ORDER BY created_at",arrayOf(reportId.toString())).use { c ->
            while(c.moveToNext()) add(PhotoComment(c.getLong(0),c.getLong(1),c.getString(2),c.getInt(3)==1,c.getString(4),c.getLong(5)))
        }
    }

    fun savePhotoReport(ownerId: Long, description: String, time: String, location: String, photoPaths: List<String>): Long {
        require(description.trim().isNotEmpty()) { "Add a description for the photo." }
        require(photoPaths.isNotEmpty()) { "Add at least one photo." }
        val db=writableDatabase
        db.beginTransaction()
        try {
            val values=ContentValues().apply {
                put("owner_id",ownerId); put("description",description.trim()); put("captured_time",time.trim())
                put("location",location.trim()); put("created_at",System.currentTimeMillis())
            }
            val id=db.insertOrThrow("photo_reports",null,values)
            photoPaths.forEach { path -> db.insertOrThrow("photo_items",null,ContentValues().apply { put("report_id",id); put("local_path",path) }) }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun ensureCloudUser(cloudId: String, displayName: String): Long {
        readableDatabase.rawQuery("SELECT id FROM users WHERE cloud_id=?",arrayOf(cloudId)).use { cursor ->
            if(cursor.moveToFirst()) return cursor.getLong(0)
        }
        val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
        return writableDatabase.insertOrThrow("users",null,ContentValues().apply {
            put("username","cloud_"+cloudId.replace("-","").take(18))
            put("display_name",displayName)
            put("cloud_id",cloudId)
            put("salt",salt)
            put("password_hash",hash(java.util.UUID.randomUUID().toString(),salt))
        })
    }

    data class PendingCloudReport(val id: Long,val description: String,val category: String,val latitude: Double,val longitude: Double)

    fun pendingCloudReports(ownerId: Long): List<PendingCloudReport> = buildList {
        readableDatabase.rawQuery("SELECT id,description,location FROM photo_reports WHERE owner_id=? AND cloud_id IS NULL ORDER BY id",arrayOf(ownerId.toString())).use { cursor ->
            while(cursor.moveToNext()) {
                val coordinates=cursor.getString(2).split(",").mapNotNull { it.trim().toDoubleOrNull() }
                if(coordinates.size==2) {
                    val description=cursor.getString(1)
                    add(PendingCloudReport(cursor.getLong(0),description,description.substringBefore("·").trim(),coordinates[0],coordinates[1]))
                }
            }
        }
    }

    fun markCloudReportSynced(localId: Long,cloudId: String) {
        writableDatabase.update("photo_reports",ContentValues().apply { put("cloud_id",cloudId) },"id=?",arrayOf(localId.toString()))
    }

    data class PendingCloudPhoto(val itemId: Long,val reportCloudId: String,val position: Int,val path: String)
    fun pendingCloudPhotos(ownerId: Long): List<PendingCloudPhoto> = buildList {
        readableDatabase.rawQuery("SELECT p.id,p.cloud_id,i.id,i.local_path,i.cloud_path FROM photo_reports p JOIN photo_items i ON i.report_id=p.id WHERE p.owner_id=? AND p.cloud_id IS NOT NULL ORDER BY p.id,i.id",arrayOf(ownerId.toString())).use { cursor ->
            var reportId=-1L
            var position=0
            while(cursor.moveToNext()) {
                val current=cursor.getLong(0)
                if(current!=reportId) { reportId=current; position=0 }
                if(cursor.isNull(4)) add(PendingCloudPhoto(cursor.getLong(2),cursor.getString(1),position,cursor.getString(3)))
                position++
            }
        }
    }

    fun markCloudPhotoSynced(itemId: Long,storagePath: String) {
        writableDatabase.update("photo_items",ContentValues().apply { put("cloud_path",storagePath) },"id=?",arrayOf(itemId.toString()))
    }

    data class PendingCloudComment(val id: Long,val reportCloudId: String,val body: String,val anonymous: Boolean,val attachmentPath: String?)
    fun pendingCloudComments(ownerId: Long): List<PendingCloudComment> = buildList {
        readableDatabase.rawQuery("SELECT c.id,p.cloud_id,c.body,c.anonymous,c.attachment_path FROM photo_comments c JOIN photo_reports p ON p.id=c.report_id WHERE c.owner_id=? AND p.cloud_id IS NOT NULL AND c.cloud_id IS NULL ORDER BY c.id",arrayOf(ownerId.toString())).use { cursor ->
            while(cursor.moveToNext()) add(PendingCloudComment(cursor.getLong(0),cursor.getString(1),cursor.getString(2),cursor.getInt(3)==1,cursor.getString(4)))
        }
    }

    fun markCloudCommentSynced(localId: Long,cloudId: String) {
        writableDatabase.update("photo_comments",ContentValues().apply { put("cloud_id",cloudId) },"id=?",arrayOf(localId.toString()))
    }

    fun pendingPhotoComments(reportId: Long): List<PhotoComment> = buildList {
        readableDatabase.rawQuery("SELECT id,report_id,body,anonymous,attachment_path,created_at FROM photo_comments WHERE report_id=? AND cloud_id IS NULL ORDER BY created_at",arrayOf(reportId.toString())).use { c ->
            while(c.moveToNext()) add(PhotoComment(c.getLong(0),c.getLong(1),c.getString(2),c.getInt(3)==1,c.getString(4),c.getLong(5),"You · uploading"))
        }
    }

    fun cloudReportIds(): Set<String> = buildSet {
        readableDatabase.rawQuery("SELECT cloud_id FROM photo_reports WHERE cloud_id IS NOT NULL",null).use { c ->
            while(c.moveToNext()) add(c.getString(0))
        }
    }

    fun photoReports(): List<PhotoReport> = buildList {
        readableDatabase.rawQuery("SELECT id,description,captured_time,location,cloud_id,owner_id FROM photo_reports ORDER BY id DESC",null).use { c ->
            while (c.moveToNext()) {
                val id=c.getLong(0)
                val photos=buildList {
                    readableDatabase.rawQuery("SELECT local_path FROM photo_items WHERE report_id=? ORDER BY id",arrayOf(id.toString())).use { p ->
                        while (p.moveToNext()) add(p.getString(0))
                    }
                }
                add(PhotoReport(id,c.getString(1),c.getString(2),c.getString(3),photos,c.getString(4),c.getLong(5)))
            }
        }
    }

    fun deletePhotoReport(id: Long,ownerId: Long): Boolean = writableDatabase.delete(
        "photo_reports","id=? AND owner_id=?",arrayOf(id.toString(),ownerId.toString()))==1

    fun deleteLegacyPhotoReports(cutoffMillis: Long): Int = writableDatabase.delete(
        "photo_reports","created_at < ?",arrayOf(cutoffMillis.toString()))

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    fun ensureGuestUser(): Long {
        readableDatabase.rawQuery("SELECT id FROM users WHERE username='guest'", null).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("username", "guest"); put("display_name", "Guest")
            put("salt", salt); put("password_hash", hash(java.util.UUID.randomUUID().toString(), salt))
        }
        return writableDatabase.insertOrThrow("users", null, values)
    }

    fun register(username: String, password: String, displayName: String = username.trim()): Long {
        val normalized = username.trim().lowercase(Locale.ROOT)
        require(normalized.matches(Regex("[a-z0-9_]{3,24}"))) { "Username needs 3–24 letters, digits, or underscores." }
        require(normalized != "guest") { "Please choose a different username." }
        require(password.length in 12..128) { "Password needs 12–128 characters." }
        require(displayName.trim().length in 1..60) { "Name needs 1–60 characters." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("username", normalized); put("display_name", displayName.trim())
            put("salt", salt); put("password_hash", hash(password, salt))
        }
        return try {
            writableDatabase.insertOrThrow("users", null, values)
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            throw IllegalArgumentException("That username is already taken.")
        }
    }

    fun login(username: String, password: String): Long? {
        val normalized = username.trim().lowercase(Locale.ROOT)
        if (normalized == "guest" || password.length !in 12..128) return null
        readableDatabase.rawQuery("SELECT id,salt,password_hash FROM users WHERE username=?", arrayOf(normalized)).use { c ->
            if (!c.moveToFirst()) return null
            val expected = c.getBlob(2)
            return if (java.security.MessageDigest.isEqual(expected, hash(password, c.getBlob(1)))) c.getLong(0) else null
        }
    }

    fun profile(userId: Long): UserProfile? {
        val sql = """
            SELECT u.id, u.username, u.display_name, u.bio,
                (SELECT COUNT(*) FROM photo_reports WHERE owner_id=u.id) +
                (SELECT COUNT(*) FROM hazard_reports WHERE owner_id=u.id),
                (SELECT COUNT(*) FROM photo_comments WHERE owner_id=u.id)
            FROM users u WHERE u.id=? AND u.username<>'guest'
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(userId.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return UserProfile(
                cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getInt(4), cursor.getInt(5)
            )
        }
    }

    fun leaderboard(): List<LeaderboardEntry> = buildList {
        val sql = """
            SELECT u.id,u.display_name,u.username,
              (SELECT COUNT(*) FROM photo_reports WHERE owner_id=u.id) +
              (SELECT COUNT(*) FROM hazard_reports WHERE owner_id=u.id) AS reports,
              (SELECT COUNT(*) FROM photo_comments WHERE owner_id=u.id) AS comments
            FROM users u WHERE u.username<>'guest'
            ORDER BY reports DESC, comments DESC, u.display_name COLLATE NOCASE
        """.trimIndent()
        readableDatabase.rawQuery(sql,null).use { cursor ->
            while(cursor.moveToNext()) {
                val reports=cursor.getInt(3)
                val comments=cursor.getInt(4)
                add(LeaderboardEntry(cursor.getLong(0),cursor.getString(1),cursor.getString(2),reports,comments,reports*10+comments*2))
            }
        }
    }.sortedWith(compareByDescending<LeaderboardEntry> { it.points }.thenBy { it.displayName.lowercase(Locale.ROOT) })

    fun updateProfile(userId: Long, displayName: String, bio: String) {
        require(displayName.trim().length in 1..60) { "Name needs 1–60 characters." }
        require(bio.trim().length <= 300) { "Bio must be 300 characters or fewer." }
        val values = ContentValues().apply {
            put("display_name", displayName.trim())
            put("bio", bio.trim())
        }
        val updated = writableDatabase.update(
            "users", values, "id=? AND username<>'guest'", arrayOf(userId.toString())
        )
        check(updated == 1) { "Account no longer exists. Please log in again." }
    }

    fun changePassword(userId: Long, currentPassword: String, newPassword: String) {
        require(newPassword.length in 12..128) { "Password needs 12–128 characters." }
        require(currentPassword != newPassword) { "Choose a different new password." }
        val user = profile(userId) ?: throw IllegalArgumentException("Account no longer exists.")
        require(login(user.username, currentPassword) == userId) { "Current password is incorrect." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("salt", salt)
            put("password_hash", hash(newPassword, salt))
        }
        check(writableDatabase.update("users", values, "id=?", arrayOf(userId.toString())) == 1)
    }

    fun segments(): List<Pair<Long, String>> = buildList {
        readableDatabase.rawQuery("SELECT id,name FROM pathway_segments ORDER BY id", null).use { c ->
            while (c.moveToNext()) add(c.getLong(0) to c.getString(1))
        }
    }

    fun reports(): List<Hazard> = buildList {
        readableDatabase.rawQuery("SELECT h.id,h.owner_id,u.username,h.segment_id,s.name,h.title,h.category,h.severity,h.details,h.status FROM hazard_reports h JOIN users u ON u.id=h.owner_id JOIN pathway_segments s ON s.id=h.segment_id ORDER BY h.id DESC", null).use { c ->
            while (c.moveToNext()) add(Hazard(c.getLong(0),c.getLong(1),c.getString(2),c.getLong(3),c.getString(4),c.getString(5),c.getString(6),c.getInt(7),c.getString(8),c.getString(9)))
        }
    }

    fun addReport(ownerId: Long, segmentId: Long, title: String, details: String, category: String, severity: Int) {
        require(title.trim().length in 5..80) { "Title needs 5–80 characters." }
        require(details.trim().length in 10..500) { "Details need 10–500 characters." }
        require(category in listOf("Obstruction", "Damaged Pavement", "Accessibility Barrier", "Other"))
        require(severity in 1..3)
        val values = ContentValues().apply {
            put("owner_id",ownerId); put("segment_id",segmentId); put("title",title.trim())
            put("details",details.trim()); put("category",category); put("severity",severity)
            put("status","Active"); put("observed_at",System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("hazard_reports",null,values)
    }

    fun resolveReport(id: Long, ownerId: Long): Boolean = writableDatabase.update("hazard_reports", ContentValues().apply { put("status","Resolved") }, "id=? AND owner_id=?", arrayOf(id.toString(),ownerId.toString())) == 1
    fun deleteReport(id: Long, ownerId: Long): Boolean = writableDatabase.delete("hazard_reports", "id=? AND owner_id=?", arrayOf(id.toString(),ownerId.toString())) == 1

    fun routes(): List<Route> = buildList {
        readableDatabase.rawQuery("SELECT r.name,r.distance_m,COUNT(DISTINCT CASE WHEN h.status='Active' THEN h.id END),COUNT(DISTINCT CASE WHEN h.status='Active' AND h.category='Accessibility Barrier' THEN h.id END) FROM walking_routes r LEFT JOIN route_segments rs ON rs.route_id=r.id LEFT JOIN hazard_reports h ON h.segment_id=rs.segment_id GROUP BY r.id ORDER BY r.distance_m,r.id",null).use { c ->
            while (c.moveToNext()) add(Route(c.getString(0),c.getInt(1),c.getInt(2),c.getInt(3)))
        }
    }
}
