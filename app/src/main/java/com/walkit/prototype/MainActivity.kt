package com.walkit.prototype

import android.app.Dialog
import android.Manifest
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
import android.widget.*
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
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var db: WalkItDb
    private lateinit var screen: FrameLayout
    private var userId: Long? = null
    private var mapView: MapView? = null
    private var currentMap: org.maplibre.android.maps.MapLibreMap? = null
    private var currentStyleReady = false
    private var selectedTab = Tab.MAP
    private val capturedPhotos = mutableListOf<String>()
    private val photoDir by lazy { File(filesDir, "walkit-photos").apply { mkdirs() } }
    private var photoDialog: Dialog? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) worker.execute {
            val imported=uris.mapNotNull { uri ->
                try {
                    val file=File(photoDir,"walkit_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
                    contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
                    file.absolutePath.takeIf { file.exists() && file.length()>0 }
                } catch (_: Exception) { null }
            }
            runOnUiThread { capturedPhotos.addAll(imported); showPhotoLocationPicker() }
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
    private var captureInProgress = false
    private var locationManager: LocationManager? = null
    private var pendingLocationListener: LocationListener? = null
    private var lastUserLocation: LatLng? = null
    private var userLocationMarker: org.maplibre.android.annotations.Marker? = null
    private val photoMarkerDetails = mutableMapOf<Long, PhotoMarkerDetail>()
    private var mapTarget = LatLng(12.8797,121.7740)
    private var mapZoom = 5.5
    private var pickingPhotoLocation = false
    private var routePlannerVisible = false
    private var routePanelView: View? = null
    private var routeOriginField: EditText? = null
    private var routeDestinationField: EditText? = null
    private var routeLine: Polyline? = null
    private val routeMarkers = mutableListOf<org.maplibre.android.annotations.Marker>()

    private enum class Tab { MAP, FORUMS, PROFILE }
    private data class PhotoMarkerDetail(val report: PhotoReport, val photos: List<String>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = WalkItDb(this)
        MapLibre.getInstance(this)
        window.statusBarColor = Color.rgb(248, 250, 252)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        screen = FrameLayout(this)
        setContentView(screen)
        runDb({ db.ensureGuestUser() }) { id -> userId=id; showTab(Tab.MAP) }
    }

    private fun <T> runDb(task: () -> T, done: (T) -> Unit) {
        worker.execute {
            try { val result=task(); runOnUiThread { done(result) } }
            catch (e: Exception) { runOnUiThread { toast(e.message ?: "Something went wrong") } }
        }
    }

    private fun toast(message: String) = Toast.makeText(this,message,Toast.LENGTH_LONG).show()

    private fun showTab(tab: Tab) {
        selectedTab=tab
        if (tab!=Tab.MAP || currentMap!=null) {
            currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
            disposeMap()
        }
        screen.removeAllViews()
        when (tab) {
            Tab.MAP -> showMapScreen()
            Tab.FORUMS -> showEmptyScreen("Forums")
            Tab.PROFILE -> showEmptyScreen("Profile")
        }
    }

    private fun showEmptyScreen(title: String) {
        screen.setBackgroundColor(Color.rgb(248,250,252))
        val message=TextView(this).apply {
            text=title; textSize=24f; setTextColor(Color.rgb(31,41,55)); gravity=Gravity.CENTER
        }
        screen.addView(message,FrameLayout.LayoutParams(-1,-1))
        addBottomNavigation()
    }

    private fun showPhotoLocationPicker() {
        currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
        if(lastUserLocation!=null) { mapTarget=lastUserLocation!!; mapZoom=16.0 }
        pickingPhotoLocation=true
        routePlannerVisible=false
        disposeMap()
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
        showTab(Tab.MAP)
        showPhotoForm()
    }

    private fun showMapScreen(photoLocationPicking: Boolean = false) {
        screen.setBackgroundColor(Color.WHITE)
        val map=MapView(this)
        mapView=map
        screen.addView(map,FrameLayout.LayoutParams(-1,-1))

        val camera=ImageView(this).apply {
            setImageBitmap(makeCameraIcon())
            contentDescription="Add photo report"
            scaleType=ImageView.ScaleType.CENTER
            background=rounded(Color.rgb(37,99,235),60)
            elevation=10f; setOnClickListener { requestCamera() }
        }
        if(!photoLocationPicking) {
            val cameraParams=FrameLayout.LayoutParams(dp(64),dp(64),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(86) }
            screen.addView(camera,cameraParams)
        }

        val locate=TextView(this).apply {
            text="◎"; textSize=30f; gravity=Gravity.CENTER; contentDescription="Show my location"
            setTextColor(Color.rgb(37,99,235)); background=rounded(Color.WHITE,32,0x22000000)
            elevation=8f; setOnClickListener { showMyLocation() }
        }
        screen.addView(locate,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.END or Gravity.TOP).apply {
            rightMargin=dp(18); topMargin=dp(26)
        })

        if(photoLocationPicking) {
            val centerPin=ImageView(this).apply {
                setImageBitmap(makeMapPinIcon()); contentDescription="Selected photo location"
                elevation=12f; translationY=-dp(27).toFloat()
            }
            screen.addView(centerPin,FrameLayout.LayoutParams(dp(44),dp(54),Gravity.CENTER))
            val hint=TextView(this).apply {
                text="Move the map to choose where this photo was taken"
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
                setOnClickListener { capturedPhotos.clear(); pickingPhotoLocation=false; showTab(Tab.MAP) }
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
            val routeToggle=TextView(this).apply {
                text="⇄"; textSize=26f; gravity=Gravity.CENTER; contentDescription="Open route planner"
                setTextColor(Color.rgb(37,99,235)); background=rounded(Color.WHITE,28,0x22000000); elevation=8f
                setOnClickListener { routePlannerVisible=!routePlannerVisible; showRoutePlannerPanel() }
            }
            screen.addView(routeToggle,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP or Gravity.END).apply {
                rightMargin=dp(18); topMargin=dp(92)
            })
            if(routePlannerVisible) showRoutePlannerPanel()
            addBottomNavigation()
        }

        map.onCreate(null)
        map.onStart()
        map.onResume()
        map.getMapAsync { maplibreMap ->
            currentMap=maplibreMap
            maplibreMap.setStyle("https://styles.maptoolkit.org/street.json") {
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

    private fun requestWalkingRoute(start: EditText,end: EditText,button: TextView) {
        fun parse(value: String): LatLng? {
            val pieces=value.split(",").map { it.trim().toDoubleOrNull() ?: return null }
            if(pieces.size!=2 || pieces[0] !in 4.0..21.8 || pieces[1] !in 116.0..127.0) return null
            return LatLng(pieces[0],pieces[1])
        }
        val from=parse(start.text.toString())
        val to=parse(end.text.toString())
        if(from==null) { start.error="Enter a valid Philippines coordinate"; return }
        if(to==null) { end.error="Enter a valid Philippines coordinate"; return }
        button.isEnabled=false
        button.text="Finding…"
        worker.execute {
            try {
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
                }
            } catch (e: Exception) {
                runOnUiThread {
                    button.isEnabled=true; button.text="Find route"
                    toast("Could not find a walking route: ${e.message ?: "try again"}")
                }
            }
        }
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
        currentMap?.cameraPosition?.let { position -> position.target?.let { mapTarget=it }; mapZoom=position.zoom }
        disposeMap(); screen.removeAllViews(); screen.setBackgroundColor(Color.BLACK)
        window.statusBarColor=Color.BLACK; window.navigationBarColor=Color.BLACK
        window.decorView.systemUiVisibility=0
        val preview=PreviewView(this).apply { scaleType=PreviewView.ScaleType.FILL_CENTER }
        screen.addView(preview,FrameLayout.LayoutParams(-1,-1))
        val controller=LifecycleCameraController(this).apply {
            imageCaptureMode=ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            bindToLifecycle(this@MainActivity)
        }
        preview.controller=controller; cameraController=controller

        val close=TextView(this).apply {
            text="×"; textSize=34f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            background=rounded(0x55000000,30); contentDescription="Close camera"
            setOnClickListener { finishCamera(false) }
        }
        screen.addView(close,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP or Gravity.START).apply { setMargins(dp(18),dp(22),0,0) })

        val done=TextView(this).apply {
            text="Done 0"; textSize=15f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(15),dp(8),dp(15),dp(8)); background=rounded(0x55000000,30)
            contentDescription="Done taking photos"; visibility=View.GONE
            setOnClickListener { finishCamera(true) }
        }
        screen.addView(done,FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP or Gravity.END).apply { setMargins(0,dp(26),dp(18),0) })

        val modes=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; background=rounded(0xCC111827.toInt(),28)
            setPadding(dp(4),dp(4),dp(4),dp(4))
        }
        val single=TextView(this).apply { text="Single"; gravity=Gravity.CENTER; setPadding(dp(18),dp(9),dp(18),dp(9)) }
        val batch=TextView(this).apply { text="Batch"; gravity=Gravity.CENTER; setPadding(dp(18),dp(9),dp(18),dp(9)) }
        single.setOnClickListener { cameraModeBatch=false; updateCameraMode(single,batch,done) }
        batch.setOnClickListener { cameraModeBatch=true; updateCameraMode(single,batch,done) }
        modes.addView(single); modes.addView(batch)
        screen.addView(modes,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(132) })

        val shutter=FrameLayout(this).apply { background=rounded(Color.WHITE,60); elevation=10f; contentDescription="Take photo"; isClickable=true }
        val inner=View(this).apply { background=rounded(Color.rgb(37,99,235),60) }
        shutter.addView(inner,FrameLayout.LayoutParams(dp(62),dp(62),Gravity.CENTER))
        shutter.setOnClickListener { if(!captureInProgress) capturePhoto(done,shutter) }
        screen.addView(shutter,FrameLayout.LayoutParams(dp(82),dp(82),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(32) })
        updateCameraMode(single,batch,done)
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
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if(provider==null) { toast("Turn on Location in your device settings, then try again."); return }
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
    override fun onResume() { super.onResume(); mapView?.onResume() }
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
