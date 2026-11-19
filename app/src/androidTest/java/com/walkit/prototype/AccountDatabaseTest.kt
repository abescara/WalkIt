package com.walkit.prototype

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.walkit.prototype.data.UserSession
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@RunWith(AndroidJUnit4::class)
class AccountDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: WalkItDb
    private val password = "a-long-test-password"

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_SESSION, Context.MODE_PRIVATE).edit().clear().commit()
        db = WalkItDb(context, TEST_DATABASE)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_SESSION, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun registrationNormalizesUsernamesAndRejectsDuplicates() {
        val id = db.register("  Walker_01  ", password, "  Sam  ")
        assertEquals(id, db.login("WALKER_01", password))
        assertEquals("Sam", db.profile(id)?.displayName)
        assertEquals("walker_01", db.profile(id)?.username)
        assertNull(db.login("walker_01", "wrong-password"))
        assertNull(db.login("missing_user", password))
        expectInvalid { db.register("walker_01", password) }
        expectInvalid { db.register("guest", password) }
        expectInvalid { db.register("ab", password) }
        expectInvalid { db.register("another_user", "short") }
        expectInvalid { db.register("another_user", password, " ") }
        expectInvalid { db.register("another_user", "x".repeat(129)) }
    }

    @Test
    fun normalizationDoesNotDependOnDeviceLanguage() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val id = db.register("WALKIT", password)
            assertEquals(id, db.login("walkit", password))
            assertEquals("walkit", db.profile(id)?.username)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun profileChangesPersistWithoutChangingAnotherAccount() {
        val first = db.register("first_walker", password)
        val second = db.register("second_walker", password)
        db.updateProfile(first, "  Sam Arias  ", "  Walking around campus.  ")
        expectInvalid { db.updateProfile(first, "", "") }
        expectInvalid { db.updateProfile(first, "Sam", "x".repeat(301)) }
        db.close()
        db = WalkItDb(context, TEST_DATABASE)
        val profile = requireNotNull(db.profile(first))
        assertEquals("Sam Arias", profile.displayName)
        assertEquals("Walking around campus.", profile.bio)
        assertEquals("second_walker", db.profile(second)?.displayName)
        assertEquals("", db.profile(second)?.bio)
    }

    @Test
    fun passwordChangeChecksCurrentPasswordAndReplacesHash() {
        val id = db.register("test_walker", password)
        val other = db.register("other_walker", password)
        val before = storedCredentials(id)
        assertEquals(16, before.first.size)
        assertEquals(32, before.second.size)
        assertFalse(MessageDigest.isEqual(before.first, storedCredentials(other).first))
        expectInvalid { db.changePassword(id, "wrong-password", "a-new-test-password") }
        expectInvalid { db.changePassword(id, password, "short") }
        expectInvalid { db.changePassword(id, password, password) }
        assertEquals(id, db.login("test_walker", password))
        db.changePassword(id, password, "a-new-test-password")
        val after = storedCredentials(id)
        assertFalse(MessageDigest.isEqual(before.first, after.first))
        assertFalse(MessageDigest.isEqual(before.second, after.second))
        assertNull(db.login("test_walker", password))
        assertEquals(id, db.login("test_walker", "a-new-test-password"))
        assertEquals(other, db.login("other_walker", password))
    }

    @Test
    fun reportAndCommentCountsBelongToTheirAccount() {
        val first = db.register("first_walker", password)
        val second = db.register("second_walker", password)
        val report = db.savePhotoReport(first, "Uneven pavement", "10:00", "14.60,120.98", listOf("test-photo.jpg"))
        db.addReport(first, db.segments().first().first, "Blocked path", "Boxes block the walkway.", "Obstruction", 2)
        db.savePhotoReport(second, "Other report", "11:00", "14.60,120.98", listOf("other-photo.jpg"))
        db.addPhotoComment(report, first, "Still blocked.", false, null)
        db.addPhotoComment(report, second, "I saw it too.", true, null)
        assertEquals(2, db.profile(first)?.reportCount)
        assertEquals(1, db.profile(second)?.reportCount)
        assertEquals(1, db.profile(first)?.commentCount)
        assertEquals(1, db.profile(second)?.commentCount)
    }

    @Test
    fun leaderboardRanksAccountsByEarnedPointsAndExcludesGuest() {
        val first = db.register("first_walker", password, "First")
        val second = db.register("second_walker", password, "Second")
        db.ensureGuestUser()
        val report = db.savePhotoReport(first, "Uneven sidewalk", "10:00", "14.60,120.98", listOf("photo.jpg"))
        db.savePhotoReport(second, "Blocked path", "11:00", "14.60,120.98", listOf("other.jpg"))
        db.addPhotoComment(report, first, "Still blocked", false, null)
        val ranking = db.leaderboard()
        assertEquals(listOf(first, second), ranking.map { it.userId })
        assertEquals(listOf(12, 10), ranking.map { it.points })
    }

    @Test
    fun sessionSurvivesNewInstanceAndLogoutClearsIt() {
        val id = db.register("test_walker", password)
        val session = UserSession(context, TEST_SESSION)
        assertNull(session.userId())
        session.signIn(id)
        assertEquals(id, UserSession(context, TEST_SESSION).userId())
        session.signOut()
        assertNull(UserSession(context, TEST_SESSION).userId())
        assertEquals(id, db.login("test_walker", password))
    }

    @Test
    fun versionThreeUpgradeKeepsAccountsPasswordsAndReports() {
        db.close()
        val salt = ByteArray(16) { it.toByte() }
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val hash = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val legacy = object : SQLiteOpenHelper(context, TEST_DATABASE, null, 3) {
            override fun onCreate(database: SQLiteDatabase) {
                database.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, salt BLOB NOT NULL, password_hash BLOB NOT NULL)")
                database.execSQL("CREATE TABLE photo_reports (id INTEGER PRIMARY KEY, owner_id INTEGER NOT NULL REFERENCES users(id), description TEXT NOT NULL, captured_time TEXT NOT NULL, location TEXT NOT NULL, created_at INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE photo_items (id INTEGER PRIMARY KEY, report_id INTEGER NOT NULL REFERENCES photo_reports(id), local_path TEXT NOT NULL)")
                database.execSQL("CREATE TABLE photo_comments (id INTEGER PRIMARY KEY, report_id INTEGER NOT NULL REFERENCES photo_reports(id), owner_id INTEGER NOT NULL REFERENCES users(id), body TEXT NOT NULL, anonymous INTEGER NOT NULL, attachment_path TEXT, created_at INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE hazard_reports (id INTEGER PRIMARY KEY, owner_id INTEGER NOT NULL REFERENCES users(id))")
            }
            override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        legacy.writableDatabase.apply {
            execSQL("INSERT INTO users VALUES (1, 'legacy_walker', 'Original Name', ?, ?)", arrayOf(salt, hash))
            execSQL("INSERT INTO users VALUES (2, 'guest', 'Guest', ?, ?)", arrayOf(salt, hash))
            execSQL("INSERT INTO photo_reports VALUES (1, 2, 'Guest report', '09:00', '14.60,120.98', 1)")
            execSQL("INSERT INTO photo_items VALUES (1, 1, 'legacy-photo.jpg')")
        }
        legacy.close()
        db = WalkItDb(context, TEST_DATABASE)
        assertEquals(1L, db.login("legacy_walker", password))
        assertEquals("Original Name", db.profile(1)?.displayName)
        assertEquals("", db.profile(1)?.bio)
        assertNull(db.profile(2))
        assertNull(db.login("guest", password))
        assertEquals("Guest report", db.photoReports().single().description)
        assertEquals(listOf("legacy-photo.jpg"), db.photoReports().single().photos)
        assertEquals(4, db.readableDatabase.version)
    }

    private fun storedCredentials(id: Long): Pair<ByteArray, ByteArray> {
        db.readableDatabase.rawQuery("SELECT salt,password_hash FROM users WHERE id=?", arrayOf(id.toString())).use {
            check(it.moveToFirst())
            return it.getBlob(0) to it.getBlob(1)
        }
    }

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
            fail("Expected input to be rejected")
        } catch (_: IllegalArgumentException) {
            // Validation should leave stored data unchanged.
        }
    }

    companion object {
        private const val TEST_DATABASE = "walkit_accounts_test.db"
        private const val TEST_SESSION = "walkit_accounts_test_session"
    }
}
