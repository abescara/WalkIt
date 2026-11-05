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
            runOnUiThread { capturedPhotos.addAll(imported); showPhotoForm() }
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
        if (tab!=Tab.MAP) {
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

    private fun showMapScreen() {
        screen.setBackgroundColor(Color.WHITE)
        val map=MapView(this)
        mapView=map
        screen.addView(map,FrameLayout.LayoutParams(-1,-1))

        val topNote=TextView(this).apply {
            text="Philippines · sample map"; textSize=13f; setTextColor(Color.rgb(55,65,81))
            setPadding(16,10,16,10); background=rounded(Color.WHITE,32,0x22000000)
            elevation=6f
        }
        val topParams=FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin=18 }
        screen.addView(topNote,topParams)

        val camera=ImageView(this).apply {
            setImageBitmap(makeCameraIcon())
            contentDescription="Add photo report"
            scaleType=ImageView.ScaleType.CENTER
            background=rounded(Color.rgb(37,99,235),60)
            elevation=10f; setOnClickListener { requestCamera() }
        }
        val cameraParams=FrameLayout.LayoutParams(dp(64),dp(64),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(86) }
        screen.addView(camera,cameraParams)

        val locate=TextView(this).apply {
            text="◎"; textSize=30f; gravity=Gravity.CENTER; contentDescription="Show my location"
            setTextColor(Color.rgb(37,99,235)); background=rounded(Color.WHITE,32,0x22000000)
            elevation=8f; setOnClickListener { showMyLocation() }
        }
        screen.addView(locate,FrameLayout.LayoutParams(dp(52),dp(52),Gravity.END or Gravity.BOTTOM).apply {
            rightMargin=dp(20); bottomMargin=dp(88)
        })

        addBottomNavigation()
        map.onCreate(null)
        map.onStart()
        map.onResume()
        map.getMapAsync { maplibreMap ->
            currentMap=maplibreMap
            maplibreMap.setStyle("https://styles.maptoolkit.org/street.json") {
                currentStyleReady=true
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
        if(keepPhotos && capturedPhotos.isNotEmpty()) { showTab(Tab.MAP); showPhotoForm() }
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
        val location=input("Latitude, longitude")
        val target=mapTarget
        location.setText("${"%.6f".format(Locale.US,target.latitude)}, ${"%.6f".format(Locale.US,target.longitude)}")
        sheet.addView(description,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        val metadata=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        metadata.addView(time,LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(6) })
        metadata.addView(location,LinearLayout.LayoutParams(0,-2,1.25f).apply { leftMargin=dp(6) })
        sheet.addView(metadata)
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
            val parts=location.text.toString().split(",").map { it.trim() }
            val lat=parts.getOrNull(0)?.toDoubleOrNull(); val lon=parts.getOrNull(1)?.toDoubleOrNull()
            if(description.text.toString().isBlank()) { description.error="Add a description"; return@setOnClickListener }
            if(time.text.toString().isBlank()) { time.error="Add a time"; return@setOnClickListener }
            if(lat==null || lon==null || lat !in 4.0..21.8 || lon !in 116.0..127.0) { location.error="Choose a location in the Philippines"; return@setOnClickListener }
            val owner=userId ?: return@setOnClickListener
            val paths=capturedPhotos.toList(); submit.isEnabled=false; submit.text="Submitting…"
            runDb({ db.savePhotoReport(owner,description.text.toString(),time.text.toString(),"$lat, $lon",paths) }) { id ->
                val report=PhotoReport(id,description.text.toString(),time.text.toString(),"$lat, $lon",paths)
                mapTarget=LatLng(lat,lon); mapZoom=16.0
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
            setPadding(dp(20),dp(12),dp(20),dp(24))
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
        sheet.addView(hero,LinearLayout.LayoutParams(-1,dp(236)).apply { bottomMargin=dp(12) })

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
                strip.addView(thumbnail,LinearLayout.LayoutParams(dp(72),dp(72)).apply { rightMargin=dp(8) })
            }
            val photosScroll=HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled=false
                addView(strip)
            }
            sheet.addView(photosScroll,LinearLayout.LayoutParams(-1,dp(78)).apply { bottomMargin=dp(10) })
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

        val dialog=Dialog(this)
        dialog.setContentView(sheet)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1,-2)
            setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes=attributes.apply { dimAmount=0.42f }
        }
        dialog.show()
        dialog.window?.setLayout(-1,-2)
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
