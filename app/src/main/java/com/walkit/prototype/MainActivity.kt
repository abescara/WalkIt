package com.walkit.prototype

import android.app.AlertDialog
import android.app.Dialog
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Build
import android.os.Looper
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.widget.*
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.location.Geocoder
import org.json.JSONObject
import com.walkit.prototype.data.UserSession
import com.walkit.prototype.data.CloudAccount

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var db: WalkItDb
    private lateinit var session: UserSession
    private lateinit var cloud: CloudAccount
    private var cloudProfile: JSONObject? = null
    private lateinit var screen: FrameLayout
    private var userId: Long? = null
    private var mapView: MapView? = null
    private var currentMap: org.maplibre.android.maps.MapLibreMap? = null
    private var currentStyleReady = false
    private var selectedTab = Tab.MAP
    private val capturedPhotos = mutableListOf<String>()
    private val photoDir by lazy { File(filesDir, "walkit-photos").apply { mkdirs() } }
    private var photoDialog: Dialog? = null
    private var currentWebView: WebView? = null
    private var currentWebScreen: String? = null
    private var transitionFrame: Bitmap? = null
    private var transitionCover: ImageView? = null
    private var returnToReportAfterPicker = false
    private var darkThemeEnabled = false
    private var lastSubmittedPhotos = emptyList<String>()
    private var lastSubmittedDescription = ""
    private var lastSubmittedLocation = ""
    private var forwardingMapGesture = false
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) worker.execute {
            val imported=uris.mapNotNull { uri ->
                try {
                    val file=File(photoDir,"walkit_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
                    contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
                    file.absolutePath.takeIf { file.exists() && file.length()>0 }
                } catch (_: Exception) { null }
            }
            runOnUiThread {
                capturedPhotos.addAll(imported)
                if(currentWebScreen=="Report") currentWebView?.evaluateJavascript("window.walkItRefreshDraft && window.walkItRefreshDraft()",null)
                else if(currentWebScreen=="Camera") finishCamera(true)
                else showPhotoLocationPicker()
            }
        }
    }
    private var selectedDiscussionPhoto: String? = null
    private var discussionPhotoPreview: ImageView? = null
    private val discussionPhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) worker.execute {
            val path=try {
                val file=File(photoDir,"discussion_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
                file.absolutePath.takeIf { file.exists() && file.length()>0 }
            } catch (_: Exception) { null }
            runOnUiThread {
                selectedDiscussionPhoto=path
                discussionPhotoPreview?.setImageBitmap(path?.let { loadPhotoPreview(it,dp(96)) })
            }
        }
    }
    private var cameraController: LifecycleCameraController? = null
    private var cameraModeBatch = false
    private var cameraUsesFrontLens = false
    private var cameraFlashEnabled = false
    private var cameraUiDone: TextView? = null
    private var cameraUiShutter: View? = null
    private var captureInProgress = false
    private var locationManager: LocationManager? = null
    private var pendingLocationListener: LocationListener? = null
    private var lastUserLocation: LatLng? = null
    private var userLocationMarker: org.maplibre.android.annotations.Marker? = null
    private val photoMarkerDetails = mutableMapOf<Long, PhotoMarkerDetail>()
    private var mapTarget = LatLng(12.8797,121.7740)
    private var mapZoom = 5.5
    private var startupLocationRequested = false
    private var waitingForLocationSettings = false
    private var pickingPhotoLocation = false
    private var routePlannerVisible = false
    private var pickingRoutePoint: String? = null
    private var routeStartPoint: LatLng? = null
    private var routeEndPoint: LatLng? = null
    private var routePanelView: View? = null
    private var routeOriginField: EditText? = null
    private var routeDestinationField: EditText? = null
    private var routeLine: Polyline? = null
    private val routeMarkers = mutableListOf<org.maplibre.android.annotations.Marker>()

    private enum class Tab { MAP, COMMUNITY, FORUMS, ROUTES, REWARDS, PROFILE }
    private data class PhotoMarkerDetail(val report: PhotoReport, val photos: List<String>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = WalkItDb(this)
        session=UserSession(this)
        cloud=CloudAccount(this)
        darkThemeEnabled=getSharedPreferences("walkit-ui",MODE_PRIVATE).getBoolean("dark_theme",false)
        MapLibre.getInstance(this)
        window.statusBarColor = Color.rgb(248, 250, 252)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        screen = FrameLayout(this)
        setContentView(screen)
        runDb({ cloud.restore()?.let { cloud.profile() } }) { profile ->
            if(profile==null) showAuthScreen("Login")
            else {
                cloudProfile=profile
                runDb({ db.ensureCloudUser(profile.getString("id"),profile.getString("display_name")) }) { id ->
                    userId=id; showTab(Tab.MAP); syncCloudReports(id)
                }
            }
        }
    }

    private fun <T> runDb(task: () -> T, done: (T) -> Unit) {
        worker.execute {
            try { val result=task(); runOnUiThread { done(result) } }
            catch (e: Exception) { runOnUiThread { toast(e.message ?: "Something went wrong") } }
        }
    }

    private fun toast(message: String) = Toast.makeText(this,message,Toast.LENGTH_LONG).show()

    private fun showTab(tab: Tab) {
        if(userId==null) { showAuthScreen("Login"); return }
        captureTransitionFrame()
        selectedTab=tab
        if (tab!=Tab.MAP || currentMap!=null) {
            currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
            disposeMap()
        }
        clearFrontendView()
        screen.removeAllViews()
        when (tab) {
            Tab.MAP -> showMapScreen()
            Tab.COMMUNITY, Tab.FORUMS -> showWebScreen("Community")
            Tab.ROUTES -> showMapScreen(routeMode=true)
            Tab.REWARDS -> showWebScreen("Rewards")
            Tab.PROFILE -> showWebScreen("Profile")
        }
        showTransitionFrame()
    }

    private fun showEmptyScreen(title: String) {
        showWebScreen(when(title.lowercase()) { "forums", "community" -> "Community"; "routes" -> "Routes"; "rewards" -> "Rewards"; else -> "Profile" })
    }

    private fun showWebScreen(page: String) {
        captureTransitionFrame()
        if(currentMap!=null) {
            currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
            disposeMap()
        }
        clearFrontendView()
        screen.removeAllViews()
        pickingPhotoLocation=false
        selectedTab=when(page) {
            "Community", "Friends" -> Tab.COMMUNITY
            "Routes" -> Tab.ROUTES
            "Rewards", "Leaderboard", "Leaderboard_Nationwide" -> Tab.REWARDS
            "Profile", "Edit_Profile", "Notifications" -> Tab.PROFILE
            else -> Tab.MAP
        }
        screen.setBackgroundColor(if(darkThemeEnabled) Color.rgb(24,24,24) else Color.WHITE)
        val web=makeFrontendWebView(page)
        screen.addView(web,FrameLayout.LayoutParams(-1,-1))
        showTransitionFrame()
    }

    private fun showAuthScreen(page: String) {
        currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
        disposeMap()
        clearFrontendView()
        screen.removeAllViews()
        screen.setBackgroundColor(Color.WHITE)
        screen.addView(makeFrontendWebView(page),FrameLayout.LayoutParams(-1,-1))
    }

    private fun makeFrontendWebView(page: String): WebView {
        val darkPage="${page}_Dark"
        val assetPage=if(darkThemeEnabled && assetExists("walkit/screens/$darkPage.html")) darkPage else page
        val web=WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled=false
            isHorizontalScrollBarEnabled=false
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.allowFileAccess=true
            settings.allowContentAccess=false
            settings.allowFileAccessFromFileURLs=true
            settings.allowUniversalAccessFromFileURLs=false
            settings.javaScriptCanOpenWindowsAutomatically=false
            settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient=WebChromeClient()
            addJavascriptInterface(FrontendBridge(),"WalkIt")
            webViewClient=object: WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest): Boolean {
                    return !request.url.toString().startsWith("file:///android_asset/walkit/")
                }
                override fun onPageFinished(view: WebView,url: String) {
                    if(currentWebView===view) {
                        view.evaluateJavascript("window.walkItRefreshDraft && window.walkItRefreshDraft()",null)
                        view.evaluateJavascript("window.walkItRefreshPreview && window.walkItRefreshPreview()",null)
                        view.postDelayed({ dismissTransitionFrame() },350)
                    }
                }
            }
        }
        currentWebView=web
        currentWebScreen=page
        web.loadUrl("file:///android_asset/walkit/screens/$assetPage.html")
        return web
    }

    private fun assetExists(path: String): Boolean = try { assets.open(path).use { true } } catch (_: Exception) { false }

    private fun clearFrontendView() {
        currentWebView?.apply {
            stopLoading()
            (parent as? android.view.ViewGroup)?.removeView(this)
            removeJavascriptInterface("WalkIt")
            destroy()
        }
        currentWebView=null
        currentWebScreen=null
    }

    private fun captureTransitionFrame() {
        if(transitionFrame!=null || screen.width<=0 || screen.height<=0 || screen.childCount==0) return
        try {
            transitionFrame=Bitmap.createBitmap(screen.width,screen.height,Bitmap.Config.RGB_565).also {
                screen.draw(Canvas(it))
            }
        } catch (_: Exception) { transitionFrame=null }
    }

    private fun showTransitionFrame() {
        if(transitionCover?.parent===screen) return
        transitionCover=null
        val frame=transitionFrame ?: return
        transitionCover=ImageView(this).apply {
            setImageBitmap(frame)
            scaleType=ImageView.ScaleType.FIT_XY
            isClickable=true
        }
        screen.addView(transitionCover,FrameLayout.LayoutParams(-1,-1))
        screen.postDelayed({ dismissTransitionFrame() },1500)
    }

    private fun dismissTransitionFrame() {
        val cover=transitionCover ?: return
        cover.animate().alpha(0f).setDuration(150).withEndAction {
            screen.removeView(cover)
            if(transitionCover===cover) transitionCover=null
            transitionFrame?.recycle(); transitionFrame=null
        }.start()
    }

    private fun forwardMapGesture(web: WebView,map: MapView,event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            val scale=web.width/390f
            val designY=event.y/scale
            forwardingMapGesture=designY in 158f..(if(currentWebScreen=="Routes") 460f else 738f)
        }
        if(!forwardingMapGesture) return false
        val copy=MotionEvent.obtain(event)
        map.dispatchTouchEvent(copy)
        copy.recycle()
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) forwardingMapGesture=false
        return true
    }

    inner class FrontendBridge {
        @JavascriptInterface fun login(email: String,password: String) {
            worker.execute {
                val result=runCatching {
                    cloud.signIn(email,password)
                    val profile=cloud.profile()
                    db.ensureCloudUser(profile.getString("id"),profile.getString("display_name")) to profile
                }
                runOnUiThread {
                    result.fold({ (id,profile) -> cloudProfile=profile; userId=id; showTab(Tab.MAP); syncCloudReports(id) },
                        { error -> accountError(error.message ?: "Could not sign in.") })
                }
            }
        }
        @JavascriptInterface fun register(email: String,username: String,password: String,displayName: String) {
            worker.execute {
                val result=runCatching {
                    if(cloud.signUp(email,password,username.trim().lowercase(Locale.ROOT),displayName)) {
                        val profile=cloud.profile()
                        db.ensureCloudUser(profile.getString("id"),profile.getString("display_name")) to profile
                    } else null
                }
                runOnUiThread {
                    result.fold({ account ->
                        if(account==null) accountError("Check your email to confirm the account, then log in.")
                        else { cloudProfile=account.second; userId=account.first; showTab(Tab.MAP) }
                    },
                        { error -> accountError(error.message ?: "Could not create account.") })
                }
            }
        }
        @JavascriptInterface fun logout() = runOnUiThread {
            userId=null; cloudProfile=null; capturedPhotos.clear(); showAuthScreen("Login")
            worker.execute { cloud.signOut() }
        }
        @JavascriptInterface fun getProfile(): String {
            val profile=cloudProfile ?: return "{}"
            val ranked=runCatching { cloud.leaderboard() }.getOrNull()?.let { rows ->
                (0 until rows.length()).mapNotNull(rows::optJSONObject)
            }.orEmpty()
            val rank=ranked.indexOfFirst { it.optString("id")==profile.optString("id") }
            val entry=ranked.getOrNull(rank)
            return JSONObject().put("username",profile.optString("username"))
                .put("displayName",profile.optString("display_name")).put("bio",profile.optString("bio"))
                .put("reports",entry?.optInt("reports") ?: 0).put("comments",entry?.optInt("comments") ?: 0)
                .put("points",entry?.optInt("points") ?: 0).put("rank",if(rank>=0) rank+1 else 0).toString()
        }
        @JavascriptInterface fun saveProfile(displayName: String,bio: String) {
            val id=userId ?: return
            worker.execute {
                val result=runCatching {
                    cloud.updateProfile(displayName,bio)
                    db.updateProfile(id,displayName,bio)
                    cloudProfile=cloud.profile()
                }
                runOnUiThread { result.fold({ accountError("Profile saved.") },{ accountError(it.message ?: "Could not save profile.") }) }
            }
        }
        @JavascriptInterface fun getLeaderboard(): String = org.json.JSONArray().apply {
            val rows=runCatching { cloud.leaderboard() }.getOrNull() ?: org.json.JSONArray()
            for(index in 0 until rows.length()) {
                val entry=rows.getJSONObject(index)
                put(JSONObject().put("rank",index+1).put("userId",entry.optString("id"))
                    .put("displayName",entry.optString("display_name")).put("username",entry.optString("username"))
                    .put("reports",entry.optInt("reports")).put("comments",entry.optInt("comments"))
                    .put("points",entry.optInt("points")))
            }
        }.toString()
        @JavascriptInterface fun getCurrentUserId(): String = cloud.userId() ?: ""
        @JavascriptInterface fun getRewards(): String = runCatching {
            val id=cloud.userId() ?: error("Log in to view rewards.")
            val rows=cloud.leaderboard()
            val entry=(0 until rows.length()).mapNotNull(rows::optJSONObject)
                .firstOrNull { it.optString("id")==id }
            JSONObject().put("reports",entry?.optInt("reports") ?: 0)
                .put("points",entry?.optInt("points") ?: 0)
        }.getOrElse { JSONObject().put("error",it.message ?: "Rewards are unavailable right now.") }.toString()
        @JavascriptInterface fun navigate(destination: String) = runOnUiThread {
            when(destination.lowercase()) {
                "map", "main" -> {
                    if(currentWebScreen=="Report") discardReportDraft()
                    showTab(Tab.MAP)
                }
                "community", "forums" -> showWebScreen("Community")
                "routes" -> showTab(Tab.ROUTES)
                "rewards" -> showWebScreen("Rewards")
                "profile" -> showWebScreen("Profile")
                "friends" -> showWebScreen("Friends")
                "notifications" -> showWebScreen("Notifications")
                "edit_profile" -> showWebScreen("Edit_Profile")
                "leaderboard" -> showWebScreen("Leaderboard")
                "leaderboard_nationwide" -> showWebScreen("Leaderboard_Nationwide")
                "login" -> showAuthScreen("Login")
                "signup" -> showAuthScreen("Signup")
            }
        }
        @JavascriptInterface fun openCamera() = runOnUiThread { requestCamera() }
        @JavascriptInterface fun closeCamera() = runOnUiThread { finishCamera(false) }
        @JavascriptInterface fun captureCameraPhoto() = runOnUiThread {
            val done=cameraUiDone; val shutter=cameraUiShutter
            if(done!=null && shutter!=null && !captureInProgress) capturePhoto(done,shutter)
        }
        @JavascriptInterface fun finishCamera() = runOnUiThread { finishCamera(true) }
        @JavascriptInterface fun openCameraGallery() = runOnUiThread { photoPicker.launch(arrayOf("image/*")) }
        @JavascriptInterface fun setCameraBatchMode(enabled: Boolean) = runOnUiThread { cameraModeBatch=enabled }
        @JavascriptInterface fun getCameraPhotoCount(): Int = capturedPhotos.size
        @JavascriptInterface fun isCameraBatchMode(): Boolean = cameraModeBatch
        @JavascriptInterface fun toggleCameraFlash() = runOnUiThread {
            val controller=cameraController ?: return@runOnUiThread
            cameraFlashEnabled=!cameraFlashEnabled
            try { controller.cameraControl?.enableTorch(cameraFlashEnabled) }
            catch (_: Exception) { cameraFlashEnabled=false; toast("Flash is not available on this camera.") }
            currentWebView?.evaluateJavascript("window.walkItSetFlash && window.walkItSetFlash($cameraFlashEnabled)",null)
        }
        @JavascriptInterface fun flipCamera() = runOnUiThread {
            val controller=cameraController ?: return@runOnUiThread
            cameraUsesFrontLens=!cameraUsesFrontLens
            try {
                controller.cameraSelector=if(cameraUsesFrontLens) androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA else androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            } catch (_: Exception) {
                cameraUsesFrontLens=!cameraUsesFrontLens
                toast("Could not switch cameras on this device.")
            }
        }
        @JavascriptInterface fun addReportPhotos() = runOnUiThread { photoPicker.launch(arrayOf("image/*")) }
        @JavascriptInterface fun changeReportLocation() = runOnUiThread {
            returnToReportAfterPicker=true
            showPhotoLocationPicker()
        }
        @JavascriptInterface fun openRoutePlanner() = runOnUiThread {
            routePlannerVisible=true
            showTab(Tab.MAP)
        }
        @JavascriptInterface fun planRoute(origin: String,destination: String) = runOnUiThread {
            val start=EditText(this@MainActivity).apply { setText(origin) }
            val end=EditText(this@MainActivity).apply { setText(destination) }
            requestWalkingRoute(start,end,TextView(this@MainActivity))
        }
        @JavascriptInterface fun getRouteOrigin(): String = if(lastUserLocation!=null) "Current location" else ""
        @JavascriptInterface fun pickRoutePoint(which: String) = runOnUiThread {
            if(which!="start" && which!="end") return@runOnUiThread
            pickingRoutePoint=which
            showPhotoLocationPicker()
        }
        @JavascriptInterface fun getPinnedRoutePoint(which: String): String =
            if((if(which=="start") routeStartPoint else routeEndPoint)!=null)
                if(which=="start") "Pinned start on map" else "Pinned destination on map"
            else ""
        @JavascriptInterface fun searchLocation() = runOnUiThread { showCoordinateSearch() }
        @JavascriptInterface fun showMessage(message: String) = runOnUiThread { toast(message) }
        @JavascriptInterface fun setDarkMode(enabled: Boolean) = runOnUiThread {
            darkThemeEnabled=enabled
            getSharedPreferences("walkit-ui",MODE_PRIVATE).edit().putBoolean("dark_theme",enabled).apply()
            currentWebScreen?.let { page ->
                if(page=="Main" && currentMap!=null) {
                    val newPage=if(enabled && assetExists("walkit/screens/Main_Dark.html")) "Main_Dark" else "Main"
                    currentWebView?.loadUrl("file:///android_asset/walkit/screens/$newPage.html")
                } else if(page=="Routes") showTab(Tab.ROUTES)
                else showWebScreen(page)
            }
        }
        @JavascriptInterface fun getDraftPhotos(): String = org.json.JSONArray(capturedPhotos).toString()
        @JavascriptInterface fun getSubmittedPhotos(): String = org.json.JSONArray(lastSubmittedPhotos).toString()
        @JavascriptInterface fun getDraftCoordinates(): String = formatCoordinate(mapTarget)
        @JavascriptInterface fun getSubmittedDescription(): String = lastSubmittedDescription
        @JavascriptInterface fun getSubmittedLocation(): String = lastSubmittedLocation
        @JavascriptInterface fun submitReport(category: String,details: String) = runOnUiThread {
            if(details.isBlank() || capturedPhotos.isEmpty()) { toast("Add a photo and short description first."); return@runOnUiThread }
            val owner=userId ?: return@runOnUiThread
            val target=mapTarget
            val location=formatCoordinate(target)
            val description="$category · ${details.trim()}"
            val time=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date())
            val paths=capturedPhotos.toList()
            runDb({ db.savePhotoReport(owner,description,time,location,paths) }) {
                lastSubmittedPhotos=paths
                lastSubmittedDescription=description
                lastSubmittedLocation=location
                capturedPhotos.clear()
                showWebScreen("Report_Preview")
                syncCloudReports(owner)
            }
        }
    }

    private fun accountError(message: String) {
        currentWebView?.evaluateJavascript("window.walkItAccountMessage && window.walkItAccountMessage(${JSONObject.quote(message)})",null)
    }

    private fun syncCloudReports(owner: Long) {
        worker.execute {
            val result=runCatching {
                db.pendingCloudReports(owner).forEach { report ->
                    val remoteId=cloud.postReport(report.description,report.category,report.latitude,report.longitude)
                    db.markCloudReportSynced(report.id,remoteId)
                }
            }
            if(result.isFailure) runOnUiThread { toast("Report saved on this device. Cloud sync will retry after your next login.") }
        }
    }

    private fun showCoordinateSearch() {
        val field=EditText(this).apply {
            hint="Search a place or address in the Philippines"
            setSingleLine(true)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Find a map location")
            .setView(field)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Go") { _,_ ->
                val query=field.text.toString().trim()
                if(query.isBlank()) return@setPositiveButton
                worker.execute {
                    val point=resolvePlace(query)
                    runOnUiThread {
                        if(point==null) toast("Place not found in the Philippines. Try a more specific address.")
                        else {
                            mapTarget=point; mapZoom=16.0
                            currentMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point,16.0),650)
                        }
                    }
                }
            }.show()
    }

    private fun discardReportDraft() {
        currentWebView?.evaluateJavascript("window.walkItClearDraft && window.walkItClearDraft()",null)
        capturedPhotos.clear()
    }

    @Deprecated("Use the app's in-screen navigation")
    override fun onBackPressed() {
        when {
            pickingPhotoLocation -> {
                if(pickingRoutePoint!=null) {
                    pickingRoutePoint=null
                    pickingPhotoLocation=false
                    showTab(Tab.ROUTES)
                } else if(returnToReportAfterPicker) {
                    returnToReportAfterPicker=false
                    pickingPhotoLocation=false
                    showWebScreen("Report")
                } else {
                    capturedPhotos.clear()
                    pickingPhotoLocation=false
                    showTab(Tab.MAP)
                }
            }
            currentWebScreen=="Friends" || currentWebScreen=="Notifications" -> showWebScreen("Community")
            currentWebScreen=="Edit_Profile" -> showWebScreen("Profile")
            currentWebScreen=="Leaderboard" || currentWebScreen=="Leaderboard_Nationwide" -> showWebScreen("Rewards")
            currentWebScreen=="Report_Preview" -> showTab(Tab.MAP)
            currentWebScreen!=null && currentWebScreen!="Main" -> {
                if(currentWebScreen=="Report") discardReportDraft()
                showTab(Tab.MAP)
            }
            else -> super.onBackPressed()
        }
    }

    private fun showPhotoLocationPicker() {
        currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
        if(!returnToReportAfterPicker && lastUserLocation!=null && pickingRoutePoint==null) { mapTarget=lastUserLocation!!; mapZoom=16.0 }
        pickingPhotoLocation=true
        routePlannerVisible=false
        disposeMap()
        clearFrontendView()
        screen.removeAllViews()
        selectedTab=Tab.MAP
        showMapScreen(photoLocationPicking=true)
    }

    private fun confirmPhotoLocation() {
        currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
        if(mapTarget.latitude !in 4.0..21.8 || mapTarget.longitude !in 116.0..127.0) {
            toast("Choose a point in the Philippines.")
            return
        }
        pickingPhotoLocation=false
        pickingRoutePoint?.let { which ->
            if(which=="start") routeStartPoint=mapTarget else routeEndPoint=mapTarget
            pickingRoutePoint=null
            showTab(Tab.ROUTES)
            return
        }
        if(returnToReportAfterPicker) returnToReportAfterPicker=false
        showWebScreen("Report")
    }

    private fun showMapScreen(photoLocationPicking: Boolean = false, routeMode: Boolean = false) {
        screen.setBackgroundColor(Color.WHITE)
        val map=MapView(this)
        mapView=map
        screen.addView(map,FrameLayout.LayoutParams(-1,-1))

        if(photoLocationPicking) {
            val locate=TextView(this).apply {
                text="◎"; textSize=30f; gravity=Gravity.CENTER; contentDescription="Show my location"
                setTextColor(Color.rgb(37,99,235)); background=rounded(Color.WHITE,32,0x22000000)
                elevation=8f; setOnClickListener { showMyLocation() }
            }
            screen.addView(locate,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.END or Gravity.TOP).apply {
                rightMargin=dp(18); topMargin=dp(26)
            })
            val centerPin=ImageView(this).apply {
                setImageBitmap(makeMapPinIcon()); contentDescription="Selected photo location"
                elevation=12f; translationY=-dp(27).toFloat()
            }
            screen.addView(centerPin,FrameLayout.LayoutParams(dp(44),dp(54),Gravity.CENTER))
            val hint=TextView(this).apply {
                text=if(pickingRoutePoint==null) "Move the map to choose where this photo was taken" else "Move the map to choose your route ${pickingRoutePoint}"
                textSize=14f; gravity=Gravity.CENTER; setTextColor(Color.rgb(31,41,55))
                setPadding(dp(16),dp(11),dp(16),dp(11)); background=rounded(Color.WHITE,28,0x22000000); elevation=8f
            }
            screen.addView(hint,FrameLayout.LayoutParams(-1,-2,Gravity.TOP).apply { setMargins(dp(18),dp(26),dp(82),0) })
            val actions=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(8),dp(8),dp(8),dp(8)); background=rounded(Color.WHITE,20,0x22000000); elevation=10f
            }
            val cancel=TextView(this).apply {
                text="Cancel"; textSize=15f; gravity=Gravity.CENTER; setTextColor(Color.rgb(75,85,99))
                setOnClickListener {
                    pickingPhotoLocation=false
                    if(pickingRoutePoint!=null) {
                        pickingRoutePoint=null
                        showTab(Tab.ROUTES)
                    } else if(returnToReportAfterPicker) {
                        returnToReportAfterPicker=false
                        showWebScreen("Report")
                    } else {
                        capturedPhotos.clear()
                        showTab(Tab.MAP)
                    }
                }
            }
            val confirm=TextView(this).apply {
                text="Use this location"; textSize=15f; gravity=Gravity.CENTER
                setTextColor(Color.WHITE); setTypeface(null,android.graphics.Typeface.BOLD)
                background=rounded(Color.rgb(37,99,235),14); setOnClickListener { confirmPhotoLocation() }
            }
            actions.addView(cancel,LinearLayout.LayoutParams(0,dp(48),1f))
            actions.addView(confirm,LinearLayout.LayoutParams(0,dp(48),2f))
            screen.addView(actions,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { setMargins(dp(18),0,dp(18),dp(22)) })
        } else {
            val web=makeFrontendWebView(if(routeMode) "Routes" else "Main")
            web.setBackgroundColor(Color.TRANSPARENT)
            web.setOnTouchListener { _, event -> forwardMapGesture(web,map,event) }
            screen.addView(web,FrameLayout.LayoutParams(-1,-1))

        }

        map.onCreate(null)
        map.onStart()
        map.onResume()
        map.getMapAsync { maplibreMap ->
            if(mapView!==map) return@getMapAsync
            currentMap=maplibreMap
            maplibreMap.setStyle("https://styles.maptoolkit.org/street.json") {
                if(mapView!==map) return@setStyle
                currentStyleReady=true
                maplibreMap.uiSettings.isCompassEnabled=false
                maplibreMap.setLatLngBoundsForCameraTarget(LatLngBounds.from(21.5,127.0,4.0,116.0))
                maplibreMap.cameraPosition=CameraPosition.Builder().target(mapTarget).zoom(mapZoom).build()
                lastUserLocation?.let { addUserLocationMarker(it) }
                photoMarkerDetails.clear()
                maplibreMap.setOnMarkerClickListener { marker ->
                    photoMarkerDetails[marker.id]?.let { showPhotoDetails(it) }
                    true
                }
                runDb({ db.photoReports() }) { reports -> reports.forEach { addPhotoMarkers(it) } }
            }
        }
        if(photoLocationPicking && lastUserLocation==null) showMyLocation()
        if(!photoLocationPicking && !routeMode && !startupLocationRequested) {
            startupLocationRequested=true
            showMyLocation()
        }
    }

    private fun addBottomNavigation() {
        val bar=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(dp(10),dp(6),dp(10),dp(6))
            background=rounded(Color.WHITE,20,0x22000000); elevation=12f
        }
        listOf(Tab.MAP to "⌖\nMap",Tab.FORUMS to "☷\nForums",Tab.PROFILE to "●\nProfile").forEach { (tab,labelText) ->
            val title=labelText.substringAfter('\n')
            val item=TextView(this).apply {
                text=labelText
                textSize=12f; gravity=Gravity.CENTER; minHeight=dp(48)
                setTextColor(if(tab==selectedTab) Color.rgb(37,99,235) else Color.rgb(75,85,99))
                contentDescription=title; setOnClickListener { showTab(tab) }
            }
            bar.addView(item,LinearLayout.LayoutParams(0,dp(48),1f))
        }
        val params=FrameLayout.LayoutParams(-1,dp(60),Gravity.BOTTOM).apply { setMargins(dp(16),0,dp(16),dp(12)) }
        screen.addView(bar,params)
    }

    private fun showRoutePlannerPanel() {
        routePanelView?.let { screen.removeView(it) }
        routePanelView=null
        if(!routePlannerVisible || pickingPhotoLocation) return
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(14),dp(16),dp(16))
            background=rounded(Color.WHITE,20,0x22000000)
            elevation=14f
        }
        val heading=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        heading.addView(TextView(this).apply {
            text="Plan a route"; textSize=18f; setTextColor(Color.rgb(17,24,39)); setTypeface(null,android.graphics.Typeface.BOLD)
        },LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(TextView(this).apply {
            text="×"; textSize=26f; gravity=Gravity.CENTER; setTextColor(Color.rgb(107,114,128))
            setOnClickListener { routePlannerVisible=false; showRoutePlannerPanel() }
        },LinearLayout.LayoutParams(dp(36),dp(36)))
        panel.addView(heading)

        val defaultStart=lastUserLocation ?: currentMap?.cameraPosition?.target ?: mapTarget
        val start=EditText(this).apply {
            hint="Point A · latitude, longitude"; textSize=14f; isSingleLine=true
            setText("${"%.6f".format(Locale.US,defaultStart.latitude)}, ${"%.6f".format(Locale.US,defaultStart.longitude)}")
            setPadding(dp(12),dp(10),dp(12),dp(10)); background=rounded(Color.rgb(249,250,251),12,Color.rgb(229,231,235))
        }
        val end=EditText(this).apply {
            hint="Point B · latitude, longitude"; textSize=14f; isSingleLine=true
            setPadding(dp(12),dp(10),dp(12),dp(10)); background=rounded(Color.rgb(249,250,251),12,Color.rgb(229,231,235))
        }
        routeOriginField=start
        routeDestinationField=end
        panel.addView(start,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
        val setStart=TextView(this).apply {
            text="Set A to map center"; textSize=12f; setTextColor(Color.rgb(37,99,235)); gravity=Gravity.END
            setOnClickListener { currentMap?.cameraPosition?.target?.let { start.setText(formatCoordinate(it)) } }
        }
        panel.addView(setStart,LinearLayout.LayoutParams(-1,dp(30)))
        panel.addView(end,LinearLayout.LayoutParams(-1,-2))
        val setEnd=TextView(this).apply {
            text="Set B to map center"; textSize=12f; setTextColor(Color.rgb(37,99,235)); gravity=Gravity.END
            setOnClickListener { currentMap?.cameraPosition?.target?.let { end.setText(formatCoordinate(it)) } }
        }
        panel.addView(setEnd,LinearLayout.LayoutParams(-1,dp(30)))
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val useLocation=TextView(this).apply {
            text="My location"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.rgb(37,99,235))
            setPadding(dp(8),dp(10),dp(8),dp(10)); setOnClickListener { showMyLocation() }
        }
        val findRoute=TextView(this).apply {
            text="Find route"; textSize=14f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            setTypeface(null,android.graphics.Typeface.BOLD); background=rounded(Color.rgb(37,99,235),12)
            setPadding(dp(14),dp(11),dp(14),dp(11)); setOnClickListener { requestWalkingRoute(start,end,this) }
        }
        actions.addView(useLocation,LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(findRoute)
        panel.addView(actions)
        routePanelView=panel
        screen.addView(panel,FrameLayout.LayoutParams(-1,-2,Gravity.TOP or Gravity.END).apply {
            setMargins(dp(18),dp(154),dp(18),0)
        })
    }

    private fun formatCoordinate(point: LatLng) = "${"%.6f".format(Locale.US,point.latitude)}, ${"%.6f".format(Locale.US,point.longitude)}"

    private fun resolvePlace(query: String): LatLng? {
        val coordinates=query.split(",").map { it.trim().toDoubleOrNull() }
        if(coordinates.size==2 && coordinates[0]!=null && coordinates[1]!=null) {
            val lat=coordinates[0]!!; val lon=coordinates[1]!!
            return if(lat in 4.0..21.8 && lon in 116.0..127.0) LatLng(lat,lon) else null
        }
        try {
            val result=Geocoder(this,Locale("en","PH")).getFromLocationName(query,1)?.firstOrNull()
            if(result!=null && result.latitude in 4.0..21.8 && result.longitude in 116.0..127.0)
                return LatLng(result.latitude,result.longitude)
        } catch (_: Exception) {}
        return try {
            val url="https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=ph&q=${URLEncoder.encode(query,"UTF-8")}"
            val connection=(URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout=10_000; readTimeout=15_000
                setRequestProperty("User-Agent","WalkItPrototype/1.0 (student accessibility map)")
            }
            val body=try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
            val item=org.json.JSONArray(body).optJSONObject(0) ?: return null
            val lat=item.optString("lat").toDoubleOrNull() ?: return null
            val lon=item.optString("lon").toDoubleOrNull() ?: return null
            if(lat in 4.0..21.8 && lon in 116.0..127.0) LatLng(lat,lon) else null
        } catch (_: Exception) { null }
    }

    private fun requestWalkingRoute(start: EditText,end: EditText,button: TextView) {
        val startQuery=start.text.toString().trim()
        val endQuery=end.text.toString().trim()
        if(startQuery.isBlank() || endQuery.isBlank()) { routeError("Enter a start and destination address."); return }
        button.isEnabled=false
        button.text="Finding…"
        worker.execute {
            try {
                val from=when(startQuery) {
                    "Pinned start on map" -> routeStartPoint
                    "Current location" -> lastUserLocation
                    else -> resolvePlace(startQuery)
                }
                val to=if(endQuery=="Pinned destination on map") routeEndPoint else resolvePlace(endQuery)
                if(from==null || to==null) error("Start or destination could not be found in the Philippines")
                val pair="${from.longitude},${from.latitude}|${to.longitude},${to.latitude}"
                val query=URLEncoder.encode(pair,"UTF-8")
                val connection=(URL("https://brouter.de/brouter?lonlats=$query&profile=hiking-mountain&alternativeidx=0&format=geojson").openConnection() as HttpURLConnection).apply {
                    connectTimeout=15_000; readTimeout=25_000; requestMethod="GET"
                }
                val response=try {
                    if(connection.responseCode !in 200..299) error("Route service returned ${connection.responseCode}")
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally { connection.disconnect() }
                val features=JSONObject(response).getJSONArray("features")
                val coordinates=features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                val points=(0 until coordinates.length()).map { index ->
                    val coordinate=coordinates.getJSONArray(index)
                    LatLng(coordinate.getDouble(1),coordinate.getDouble(0))
                }
                if(points.size<2) error("No route was found")
                runOnUiThread {
                    button.isEnabled=true; button.text="Find route"
                    displayRoute(points,from,to)
                    val length=points.zipWithNext().sumOf { (a,b) ->
                        val result=FloatArray(1)
                        Location.distanceBetween(a.latitude,a.longitude,b.latitude,b.longitude,result)
                        result[0].toDouble()
                    }
                    val minutes=kotlin.math.max(1,(length/1.2/60).toInt())
                    val summary="Walking route · ${minutes} min · ${"%.1f".format(Locale.US,length/1000)} km"
                    currentWebView?.evaluateJavascript("window.walkItRouteReady && window.walkItRouteReady(${JSONObject.quote(summary)},'')",null)
                    runDb({
                        db.photoReports().firstOrNull { report ->
                            val parts=report.location.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                            if(parts.size!=2) false else points.any { point ->
                                val meters=FloatArray(1)
                                Location.distanceBetween(parts[0],parts[1],point.latitude,point.longitude,meters)
                                meters[0]<35f
                            }
                        }
                    }) { obstacle ->
                        if(obstacle!=null && currentWebScreen=="Routes") {
                            val warning="Possible obstacle along this route: ${obstacle.description}"
                            currentWebView?.evaluateJavascript("window.walkItRouteReady && window.walkItRouteReady(${JSONObject.quote(summary)},${JSONObject.quote(warning)})",null)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    button.isEnabled=true; button.text="Find route"
                    toast("Could not find a walking route: ${e.message ?: "try again"}")
                    routeError("Could not find a walking route. Check your connection and coordinates.")
                }
            }
        }
    }

    private fun routeError(message: String) {
        currentWebView?.evaluateJavascript("window.walkItRouteError && window.walkItRouteError(${JSONObject.quote(message)})",null)
    }

    private fun displayRoute(points: List<LatLng>,from: LatLng,to: LatLng) {
        val map=currentMap ?: return
        routeLine?.let { map.removePolyline(it) }
        routeMarkers.forEach { map.removeMarker(it) }
        routeMarkers.clear()
        val lineOptions=PolylineOptions().add(points.first()).color(Color.rgb(37,99,235)).width(6f)
        points.drop(1).forEach { lineOptions.add(it) }
        routeLine=map.addPolyline(lineOptions)
        routeMarkers.add(map.addMarker(MarkerOptions().position(from).title("Point A")))
        routeMarkers.add(map.addMarker(MarkerOptions().position(to).title("Point B")))
        val bounds=LatLngBounds.Builder().include(from).include(to).build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,dp(64)),800)
        toast("Walking route ready.")
    }

    private fun requestCamera() {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) {
            capturedPhotos.clear(); cameraModeBatch=false; showCameraScreen()
        } else requestPermissions(arrayOf(Manifest.permission.CAMERA),REQUEST_CAMERA_PERMISSION)
    }

    @Deprecated("Camera permission callback")
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==REQUEST_CAMERA_PERMISSION) {
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) {
                capturedPhotos.clear(); cameraModeBatch=false; showCameraScreen()
            } else toast("Camera access is needed to take a photo.")
        } else if(requestCode==REQUEST_LOCATION_PERMISSION) {
            if(grantResults.any { it==PackageManager.PERMISSION_GRANTED }) showMyLocation()
            else toast("Allow location access to show your position on the map.")
        }
    }

    private fun showCameraScreen() {
        captureInProgress=false
        cameraUsesFrontLens=false
        cameraFlashEnabled=false
        currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
        disposeMap(); clearFrontendView(); screen.removeAllViews(); screen.setBackgroundColor(Color.BLACK)
        window.statusBarColor=Color.BLACK; window.navigationBarColor=Color.BLACK
        window.decorView.systemUiVisibility=0
        val preview=PreviewView(this).apply { scaleType=PreviewView.ScaleType.FILL_CENTER }
        screen.addView(preview,FrameLayout.LayoutParams(-1,-1))
        val controller=LifecycleCameraController(this).apply {
            imageCaptureMode=ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            bindToLifecycle(this@MainActivity)
        }
        preview.controller=controller; cameraController=controller
        val done=TextView(this).apply {
            visibility=View.GONE
        }
        val shutter=View(this).apply { visibility=View.GONE }
        cameraUiDone=done; cameraUiShutter=shutter
        screen.addView(done); screen.addView(shutter)
        val cameraUi=makeFrontendWebView("Camera")
        cameraUi.setBackgroundColor(Color.TRANSPARENT)
        screen.addView(cameraUi,FrameLayout.LayoutParams(-1,-1))
    }

    private fun updateCameraMode(single: TextView,batch: TextView,done: TextView) {
        single.setTextColor(if(cameraModeBatch) Color.WHITE else Color.rgb(17,24,39))
        single.background=if(cameraModeBatch) null else rounded(Color.WHITE,24)
        batch.setTextColor(if(cameraModeBatch) Color.rgb(17,24,39) else Color.WHITE)
        batch.background=if(cameraModeBatch) rounded(Color.WHITE,24) else null
        done.visibility=if(cameraModeBatch && capturedPhotos.isNotEmpty()) View.VISIBLE else View.GONE
        done.text="Done ${capturedPhotos.size}"
    }

    private fun capturePhoto(done: TextView,shutter: View) {
        val controller=cameraController ?: return
        captureInProgress=true
        shutter.isEnabled=false
        shutter.alpha=0.55f
        val file=File(photoDir,"walkit_${System.currentTimeMillis()}.jpg")
        val output=ImageCapture.OutputFileOptions.Builder(file).build()
        controller.takePicture(output,ContextCompat.getMainExecutor(this),object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                captureInProgress=false
                shutter.isEnabled=true
                shutter.alpha=1f
                if(!file.exists() || file.length()==0L) {
                    file.delete(); toast("The camera returned an empty photo. Please try again."); return
                }
                capturedPhotos.add(file.absolutePath)
                if(cameraModeBatch) {
                    done.text="Done ${capturedPhotos.size}"; done.visibility=View.VISIBLE
                    currentWebView?.evaluateJavascript("window.walkItUpdateCameraState && window.walkItUpdateCameraState(${capturedPhotos.size},true)",null)
                    toast("Photo ${capturedPhotos.size} added")
                } else finishCamera(true)
            }
            override fun onError(exception: ImageCaptureException) {
                captureInProgress=false
                shutter.isEnabled=true
                shutter.alpha=1f
                file.delete()
                toast("Could not capture photo: ${exception.message ?: "please try again"}")
            }
        })
    }

    private fun showMyLocation() {
        val fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine && !coarse) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION),REQUEST_LOCATION_PERMISSION)
            return
        }
        val manager=getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager=manager
        val provider=when {
            fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if(provider==null) {
            AlertDialog.Builder(this).setMessage("Turn on device location to center the map on you.")
                .setNegativeButton("Later",null)
                .setPositiveButton("Open settings") { _,_ ->
                    waitingForLocationSettings=true
                    startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.show()
            return
        }
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) {
            try {
                manager.getCurrentLocation(provider,null,mainExecutor) { location ->
                    if(location==null) toast("Could not get your location. Check that Location is turned on.")
                    else displayUserLocation(location)
                }
            } catch (_: SecurityException) { toast("Allow location access to show your position.") }
        } else {
            var gotLocation=false
            try {
                val cached=manager.getLastKnownLocation(provider)
                if(cached!=null) { displayUserLocation(cached); gotLocation=true }
                val listener=object: LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        pendingLocationListener=null
                        displayUserLocation(location)
                    }
                }
                pendingLocationListener=listener
                manager.requestLocationUpdates(provider,0L,0f,listener,Looper.getMainLooper())
                screen.postDelayed({
                    pendingLocationListener?.let { manager.removeUpdates(it); pendingLocationListener=null }
                    if(!gotLocation) toast("Could not get your location. Check that Location is turned on.")
                },12_000L)
            } catch (_: SecurityException) { toast("Allow location access to show your position.") }
        }
    }

    private fun displayUserLocation(location: Location) {
        if(location.latitude !in 4.0..21.8 || location.longitude !in 116.0..127.0) {
            toast("Your current location is outside the Philippines map.")
            return
        }
        val point=LatLng(location.latitude,location.longitude)
        lastUserLocation=point
        mapTarget=point
        mapZoom=16.0
        routeOriginField?.setText(formatCoordinate(point))
        addUserLocationMarker(point)
        currentMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point,16.0),650)
    }

    private fun addUserLocationMarker(point: LatLng) {
        val map=currentMap ?: return
        if(!currentStyleReady) return
        userLocationMarker?.let { map.removeMarker(it) }
        val size=dp(24)
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=0x443B82F6
        canvas.drawCircle(size/2f,size/2f,size/2f,paint)
        paint.color=Color.WHITE
        canvas.drawCircle(size/2f,size/2f,dp(8).toFloat(),paint)
        paint.color=Color.rgb(37,99,235)
        canvas.drawCircle(size/2f,size/2f,dp(6).toFloat(),paint)
        val icon=IconFactory.getInstance(this).fromBitmap(bitmap)
        userLocationMarker=map.addMarker(MarkerOptions().position(point).title("You are here").icon(icon))
    }

    private fun finishCamera(keepPhotos: Boolean) {
        cameraController?.unbind(); cameraController=null
        cameraUiDone=null; cameraUiShutter=null
        cameraFlashEnabled=false; cameraUsesFrontLens=false
        window.statusBarColor=Color.rgb(248,250,252); window.navigationBarColor=Color.WHITE
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if(keepPhotos && capturedPhotos.isNotEmpty()) showPhotoLocationPicker()
        else { capturedPhotos.clear(); showTab(Tab.MAP) }
    }

    private fun showPhotoForm() {
        if(capturedPhotos.isEmpty()) return
        val sheet=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(20))
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadii=floatArrayOf(dp(28).toFloat(),dp(28).toFloat(),dp(28).toFloat(),dp(28).toFloat(),0f,0f,0f,0f)
            }
        }
        val handle=View(this).apply { background=rounded(Color.rgb(209,213,219),8) }
        sheet.addView(handle,LinearLayout.LayoutParams(dp(42),dp(5)).apply { gravity=Gravity.CENTER_HORIZONTAL; bottomMargin=dp(14) })
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val title=TextView(this).apply { text="New map report"; textSize=21f; setTextColor(Color.rgb(17,24,39)); setTypeface(null,android.graphics.Typeface.BOLD) }
        val count=TextView(this).apply { text="${capturedPhotos.size} photo${if(capturedPhotos.size==1) "" else "s"}"; textSize=13f; setTextColor(Color.rgb(75,85,99)); gravity=Gravity.END }
        header.addView(title,LinearLayout.LayoutParams(0,-2,1f)); header.addView(count)
        sheet.addView(header)
        sheet.addView(TextView(this).apply { text="Add a clear description and where you found it."; textSize=14f; setTextColor(Color.rgb(107,114,128)); setPadding(0,dp(4),0,dp(12)) })

        val photoStrip=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val photoScroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(photoStrip) }
        fun fillPhotoStrip() {
            photoStrip.removeAllViews()
            capturedPhotos.forEach { path ->
                val tile=ImageView(this).apply {
                    setImageBitmap(BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=8 }))
                    scaleType=ImageView.ScaleType.CENTER_CROP; background=rounded(Color.rgb(229,231,235),14); clipToOutline=true
                }
                photoStrip.addView(tile,LinearLayout.LayoutParams(dp(82),dp(82)).apply { rightMargin=dp(8) })
            }
            val add=TextView(this).apply {
                text="+\nAdd more"; gravity=Gravity.CENTER; textSize=13f; setTextColor(Color.rgb(37,99,235))
                background=rounded(Color.rgb(239,246,255),14,Color.rgb(191,219,254)); setOnClickListener {
                    photoDialog?.dismiss(); photoPicker.launch(arrayOf("image/*"))
                }
            }
            photoStrip.addView(add,LinearLayout.LayoutParams(dp(82),dp(82)))
        }
        fillPhotoStrip()
        sheet.addView(photoScroll,LinearLayout.LayoutParams(-1,dp(88)).apply { bottomMargin=dp(14) })

        fun input(hint: String,multiline: Boolean=false)=EditText(this).apply {
            this.hint=hint; textSize=15f; setTextColor(Color.rgb(31,41,55)); setHintTextColor(Color.rgb(156,163,175))
            setPadding(dp(14),dp(12),dp(14),dp(12)); background=rounded(Color.rgb(249,250,251),14,Color.rgb(229,231,235))
            if(multiline) { minLines=2; gravity=Gravity.TOP; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
            else setSingleLine(true)
        }
        val description=input("What did you notice?")
        val time=input("Date and time")
        time.setText(SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date()))
        val target=mapTarget
        sheet.addView(description,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        val metadata=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        metadata.addView(time,LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(6) })
        val location=TextView(this).apply {
            text="📍  ${"%.5f".format(Locale.US,target.latitude)}, ${"%.5f".format(Locale.US,target.longitude)}"
            textSize=13f; gravity=Gravity.CENTER_VERTICAL; setTextColor(Color.rgb(55,65,81))
            setPadding(dp(10),dp(12),dp(8),dp(12)); background=rounded(Color.rgb(249,250,251),14,Color.rgb(229,231,235))
        }
        metadata.addView(location,LinearLayout.LayoutParams(0,-2,1.25f).apply { leftMargin=dp(6) })
        sheet.addView(metadata)
        val changeLocation=TextView(this).apply {
            text="Change map pin"; textSize=13f; setTextColor(Color.rgb(37,99,235)); gravity=Gravity.END
            setPadding(0,dp(8),dp(4),0)
            setOnClickListener { photoDialog?.dismiss(); showPhotoLocationPicker() }
        }
        sheet.addView(changeLocation,LinearLayout.LayoutParams(-1,-2))
        val submit=TextView(this).apply {
            text="Submit report"; textSize=16f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            setTypeface(null,android.graphics.Typeface.BOLD); background=rounded(Color.rgb(37,99,235),16)
            setPadding(0,dp(14),0,dp(14)); elevation=3f
        }
        sheet.addView(submit,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14) })
        val dialog=Dialog(this)
        photoDialog=dialog
        dialog.setContentView(sheet)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1,-2); setGravity(Gravity.BOTTOM); addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes=attributes.apply { dimAmount=0.42f }
        }
        submit.setOnClickListener {
            val lat=target.latitude; val lon=target.longitude
            if(description.text.toString().isBlank()) { description.error="Add a description"; return@setOnClickListener }
            if(time.text.toString().isBlank()) { time.error="Add a time"; return@setOnClickListener }
            if(lat !in 4.0..21.8 || lon !in 116.0..127.0) { toast("Choose a location in the Philippines."); return@setOnClickListener }
            val owner=userId ?: return@setOnClickListener
            val paths=capturedPhotos.toList(); submit.isEnabled=false; submit.text="Submitting…"
            runDb({ db.savePhotoReport(owner,description.text.toString(),time.text.toString(),"$lat, $lon",paths) }) { id ->
                val report=PhotoReport(id,description.text.toString(),time.text.toString(),"$lat, $lon",paths)
                mapTarget=LatLng(lat,lon); mapZoom=maxOf(mapZoom,16.0)
                dialog.dismiss(); capturedPhotos.clear(); showTab(Tab.MAP); toast("Report added to the map.")
                syncCloudReports(owner)
            }
        }
        dialog.setOnDismissListener { if(photoDialog===dialog) photoDialog=null }
        dialog.show()
        dialog.window?.setLayout(-1,-2)
    }

    private fun addPhotoMarkers(report: PhotoReport) {
        val map=currentMap ?: return
        if(!currentStyleReady) return
        val coordinates=report.location.split(",").map { it.trim().toDoubleOrNull() ?: return }
        if(coordinates.size!=2) return
        val point=LatLng(coordinates[0],coordinates[1])
        val photos=report.photos.filter { File(it).exists() }
        val bitmap=photos.firstOrNull()?.let { makePhotoBubble(it) } ?: makePhotoPlaceholder()
        val icon=IconFactory.getInstance(this).fromBitmap(bitmap)
        val marker=map.addMarker(MarkerOptions().position(point).title(report.description).snippet("${report.time} · ${report.location}").icon(icon))
        photoMarkerDetails[marker.id]=PhotoMarkerDetail(report,photos)
    }

    private fun showPhotoDetails(detail: PhotoMarkerDetail) {
        val report=detail.report
        val sheet=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(12),dp(20),dp(16))
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii=floatArrayOf(dp(28).toFloat(),dp(28).toFloat(),dp(28).toFloat(),dp(28).toFloat(),0f,0f,0f,0f)
            }
        }
        sheet.addView(View(this).apply { background=rounded(Color.rgb(209,213,219),8) },LinearLayout.LayoutParams(dp(42),dp(5)).apply {
            gravity=Gravity.CENTER_HORIZONTAL; bottomMargin=dp(14)
        })
        val hero=ImageView(this).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP
            background=rounded(Color.rgb(229,231,235),18)
            clipToOutline=true
        }
        val firstPhoto=detail.photos.firstOrNull()
        if(firstPhoto!=null) hero.setImageBitmap(loadPhotoPreview(firstPhoto,dp(420)))
        else hero.setImageBitmap(makePhotoPlaceholder())
        sheet.addView(hero,LinearLayout.LayoutParams(-1,dp(180)).apply { bottomMargin=dp(10) })

        if(detail.photos.size>1) {
            val strip=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            detail.photos.forEach { path ->
                val thumbnail=ImageView(this).apply {
                    setImageBitmap(loadPhotoPreview(path,dp(120)))
                    scaleType=ImageView.ScaleType.CENTER_CROP
                    background=rounded(Color.rgb(229,231,235),12)
                    clipToOutline=true
                    contentDescription="View report photo"
                    setOnClickListener { hero.setImageBitmap(loadPhotoPreview(path,dp(420))) }
                }
                strip.addView(thumbnail,LinearLayout.LayoutParams(dp(62),dp(62)).apply { rightMargin=dp(8) })
            }
            val photosScroll=HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled=false
                addView(strip)
            }
            sheet.addView(photosScroll,LinearLayout.LayoutParams(-1,dp(68)).apply { bottomMargin=dp(8) })
        }

        sheet.addView(TextView(this).apply {
            text=report.description
            textSize=20f
            setTextColor(Color.rgb(17,24,39))
            setTypeface(null,android.graphics.Typeface.BOLD)
        })
        sheet.addView(TextView(this).apply {
            text="${report.time}  ·  ${report.location}"
            textSize=13f
            setTextColor(Color.rgb(107,114,128))
            setPadding(0,dp(5),0,dp(2))
        })

        sheet.addView(TextView(this).apply {
            text="Discussion"; textSize=17f; setTextColor(Color.rgb(17,24,39)); setTypeface(null,android.graphics.Typeface.BOLD)
            setPadding(0,dp(12),0,dp(6))
        })
        val comments=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val commentsScroll=ScrollView(this).apply { isFillViewport=true; addView(comments) }
        sheet.addView(commentsScroll,LinearLayout.LayoutParams(-1,0,1f))

        fun loadComments() {
            runDb({ db.photoComments(report.id) }) { items ->
                comments.removeAllViews()
                if(items.isEmpty()) comments.addView(TextView(this).apply {
                    text="No comments yet. Start the discussion."; textSize=14f; setTextColor(Color.rgb(107,114,128))
                    setPadding(0,dp(8),0,dp(12))
                })
                items.forEach { item ->
                    val entry=LinearLayout(this).apply {
                        orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10))
                        background=rounded(Color.rgb(249,250,251),14,Color.rgb(229,231,235))
                    }
                    entry.addView(TextView(this).apply {
                        val posted=SimpleDateFormat("MMM d · h:mm a",Locale.getDefault()).format(Date(item.createdAt))
                        text="${if(item.anonymous) "Anonymous" else "WalkIt user"}  ·  $posted"
                        textSize=12f; setTextColor(Color.rgb(107,114,128))
                    })
                    if(item.text.isNotBlank()) entry.addView(TextView(this).apply {
                        text=item.text; textSize=14f; setTextColor(Color.rgb(31,41,55)); setPadding(0,dp(4),0,0)
                    })
                    item.attachmentPath?.takeIf { File(it).exists() }?.let { path ->
                        entry.addView(ImageView(this).apply {
                            setImageBitmap(loadPhotoPreview(path,dp(320))); scaleType=ImageView.ScaleType.CENTER_CROP
                            clipToOutline=true; background=rounded(Color.rgb(229,231,235),12)
                        },LinearLayout.LayoutParams(-1,dp(120)).apply { topMargin=dp(8) })
                    }
                    comments.addView(entry,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
                }
            }
        }
        loadComments()

        val commentInput=EditText(this).apply {
            hint="Add to the discussion…"; textSize=14f; minLines=2; maxLines=3; gravity=Gravity.TOP
            setPadding(dp(12),dp(10),dp(12),dp(10)); background=rounded(Color.rgb(249,250,251),14,Color.rgb(229,231,235))
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        sheet.addView(commentInput,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        val composer=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val anonymous=Switch(this).apply { text="Anonymous"; textSize=12f; setTextColor(Color.rgb(75,85,99)) }
        val attach=TextView(this).apply {
            text="＋ Photo"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.rgb(37,99,235))
            setPadding(dp(8),dp(10),dp(8),dp(10)); setOnClickListener { discussionPhotoPicker.launch("image/*") }
        }
        val preview=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; clipToOutline=true; background=rounded(Color.rgb(229,231,235),8) }
        discussionPhotoPreview=preview
        composer.addView(anonymous,LinearLayout.LayoutParams(0,dp(44),1f))
        composer.addView(attach,LinearLayout.LayoutParams(-2,dp(44)))
        composer.addView(preview,LinearLayout.LayoutParams(dp(40),dp(40)).apply { leftMargin=dp(4); rightMargin=dp(6) })
        val post=TextView(this).apply {
            text="Post"; textSize=14f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(null,android.graphics.Typeface.BOLD)
            background=rounded(Color.rgb(37,99,235),12); setPadding(dp(16),dp(10),dp(16),dp(10))
        }
        composer.addView(post)
        sheet.addView(composer)

        val dialog=Dialog(this)
        photoDialog=dialog
        selectedDiscussionPhoto=null
        discussionPhotoPreview=preview
        post.setOnClickListener {
            val owner=userId ?: return@setOnClickListener
            if(commentInput.text.toString().isBlank() && selectedDiscussionPhoto==null) {
                commentInput.error="Write a comment or attach a photo"
                return@setOnClickListener
            }
            post.isEnabled=false
            runDb({ db.addPhotoComment(report.id,owner,commentInput.text.toString(),anonymous.isChecked,selectedDiscussionPhoto) }) {
                commentInput.setText("")
                selectedDiscussionPhoto=null
                preview.setImageDrawable(null)
                post.isEnabled=true
                loadComments()
            }
        }
        dialog.setContentView(sheet)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1,(resources.displayMetrics.heightPixels*0.88f).toInt())
            setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes=attributes.apply { dimAmount=0.42f }
        }
        dialog.setOnDismissListener { if(photoDialog===dialog) photoDialog=null; discussionPhotoPreview=null; selectedDiscussionPhoto=null }
        dialog.show()
        dialog.window?.setLayout(-1,(resources.displayMetrics.heightPixels*0.88f).toInt())
    }

    private fun loadPhotoPreview(path: String,size: Int): Bitmap? = try {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(path,bounds)
        var sample=1
        while(bounds.outWidth/sample>size*2 || bounds.outHeight/sample>size*2) sample*=2
        BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=sample })
    } catch (_: Exception) { null }

    private fun makePhotoPlaceholder(): Bitmap {
        val size=dp(58)
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(37,99,235) }
        canvas.drawCircle(size/2f,size/2f,size/2f,paint)
        paint.color=Color.WHITE
        canvas.drawCircle(size/2f,size/2f,dp(13).toFloat(),paint)
        paint.color=Color.rgb(37,99,235)
        canvas.drawCircle(size/2f,size/2f,dp(9).toFloat(),paint)
        return bitmap
    }

    private fun makeCameraIcon(): Bitmap {
        val size=dp(34)
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; style=Paint.Style.FILL }
        canvas.drawRoundRect(dp(3).toFloat(),dp(10).toFloat(),dp(31).toFloat(),dp(29).toFloat(),dp(4).toFloat(),dp(4).toFloat(),paint)
        canvas.drawRoundRect(dp(9).toFloat(),dp(6).toFloat(),dp(19).toFloat(),dp(12).toFloat(),dp(2).toFloat(),dp(2).toFloat(),paint)
        paint.color=Color.rgb(37,99,235)
        canvas.drawCircle(size/2f,dp(19).toFloat(),dp(7).toFloat(),paint)
        paint.color=Color.WHITE
        canvas.drawCircle(size/2f,dp(19).toFloat(),dp(4).toFloat(),paint)
        return bitmap
    }

    private fun makeMapPinIcon(): Bitmap {
        val width=dp(42); val height=dp(54)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(239,68,68); style=Paint.Style.FILL }
        val path=Path().apply {
            moveTo(width/2f,height.toFloat())
            cubicTo(width*0.36f,height*0.76f,width*0.10f,height*0.48f,width*0.10f,height*0.32f)
            cubicTo(width*0.10f,height*0.10f,width*0.30f,0f,width/2f,0f)
            cubicTo(width*0.70f,0f,width*0.90f,height*0.10f,width*0.90f,height*0.32f)
            cubicTo(width*0.90f,height*0.48f,width*0.64f,height*0.76f,width/2f,height.toFloat())
            close()
        }
        canvas.drawPath(path,paint)
        paint.color=Color.WHITE
        canvas.drawCircle(width/2f,height*0.30f,dp(7).toFloat(),paint)
        return bitmap
    }

    private fun makePhotoBubble(path: String): Bitmap? = try {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(path,bounds)
        val size=dp(58)
        var sample=1
        while(bounds.outWidth/sample>size*2 || bounds.outHeight/sample>size*2) sample*=2
        val source=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=sample }) ?: return null
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); val center=size/2f; val radius=center-dp(2)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=Color.WHITE; canvas.drawCircle(center,center,radius,paint)
        val imageRadius=radius-dp(3)
        val clip=Path().apply { addCircle(center,center,imageRadius,Path.Direction.CW) }
        canvas.save(); canvas.clipPath(clip)
        val scale=maxOf(size.toFloat()/source.width,size.toFloat()/source.height)
        val width=source.width*scale; val height=source.height*scale
        val dst=android.graphics.RectF(center-width/2,center-height/2,center+width/2,center+height/2)
        canvas.drawBitmap(source,null,dst,paint); canvas.restore()
        paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(2).toFloat(); paint.color=Color.WHITE
        canvas.drawCircle(center,center,imageRadius,paint)
        source.recycle(); bitmap
    } catch (_: Exception) { null }

    private fun rounded(color: Int,radius: Int,strokeColor: Int=Color.TRANSPARENT)=android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat()
        if(strokeColor!=Color.TRANSPARENT) setStroke(dp(1),strokeColor)
    }
    private fun dp(value: Int)= (value*resources.displayMetrics.density).toInt()

    private fun disposeMap() {
        currentStyleReady=false; currentMap=null
        userLocationMarker=null; photoMarkerDetails.clear()
        routeLine=null; routeMarkers.clear(); routePanelView=null
        routeOriginField=null; routeDestinationField=null
        mapView?.onPause(); mapView?.onStop(); mapView?.onDestroy(); mapView=null
    }
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        if(waitingForLocationSettings) {
            waitingForLocationSettings=false
            showMyLocation()
        }
    }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onDestroy() {
        pendingLocationListener?.let { locationManager?.removeUpdates(it) }
        pendingLocationListener=null
        disposeMap(); worker.shutdown(); db.close(); super.onDestroy()
    }

    companion object { private const val REQUEST_CAMERA_PERMISSION=22; private const val REQUEST_LOCATION_PERMISSION=23 }
}
