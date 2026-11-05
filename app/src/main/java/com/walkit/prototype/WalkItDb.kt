package com.walkit.prototype

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Hazard(val id: Long, val ownerId: Long, val owner: String, val segmentId: Long,
                  val segment: String, val title: String, val category: String,
                  val severity: Int, val details: String, val status: String)
data class Route(val name: String, val meters: Int, val hazards: Int, val barriers: Int)

class WalkItDb(context: Context) : SQLiteOpenHelper(context, "walkit.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, salt BLOB NOT NULL, password_hash BLOB NOT NULL)")
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
        listOf("North Walk", "Library Path", "Garden Lane", "South Walk").forEach { name ->
            db.execSQL("INSERT INTO pathway_segments(name) VALUES (?)", arrayOf(name))
        }
        db.execSQL("INSERT INTO walking_routes(name,distance_m) VALUES ('Library route',650),('Garden route',820)")
        db.execSQL("INSERT INTO route_segments(route_id,segment_id,sequence_no) VALUES (1,1,1),(1,2,2),(1,4,3),(2,1,1),(2,3,2),(2,4,3)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migration is needed for schema version 1. Future changes must preserve records.
        error("A database migration is required before opening version $newVersion")
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    fun register(username: String, password: String): Long {
        val normalized = username.trim().lowercase()
        require(normalized.matches(Regex("[a-z0-9_]{3,24}"))) { "Username needs 3–24 letters, digits, or underscores." }
        require(password.length in 12..128) { "Password needs 12–128 characters." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values = ContentValues().apply {
            put("username", normalized); put("display_name", normalized)
            put("salt", salt); put("password_hash", hash(password, salt))
        }
        return writableDatabase.insertOrThrow("users", null, values)
    }

    fun login(username: String, password: String): Long? {
        readableDatabase.rawQuery("SELECT id,salt,password_hash FROM users WHERE username=?", arrayOf(username.trim().lowercase())).use { c ->
            if (!c.moveToFirst()) return null
            val expected = c.getBlob(2)
            return if (java.security.MessageDigest.isEqual(expected, hash(password, c.getBlob(1)))) c.getLong(0) else null
        }
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
