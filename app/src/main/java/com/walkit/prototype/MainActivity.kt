package com.walkit.prototype

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var db: WalkItDb
    private lateinit var screen: FrameLayout
    private var userId: Long? = null
    private var mapView: MapView? = null
    private var currentMap: org.maplibre.android.maps.MapLibreMap? = null
    private var currentStyleReady = false
    private var selectedTab = Tab.MAP
    private val capturedPhotos = mutableListOf<String>()
    private var pendingCameraPath: String? = null
    private val photoDir by lazy { File(filesDir, "walkit-photos").apply { mkdirs() } }

    private enum class Tab { MAP, FORUMS, PROFILE }

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
        if (tab!=Tab.MAP) disposeMap()
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
            text="Sample map · location not tracked"; textSize=13f; setTextColor(Color.rgb(55,65,81))
            setPadding(16,10,16,10); background=rounded(Color.WHITE,32,0x22000000)
            elevation=6f
        }
        val topParams=FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin=18 }
        screen.addView(topNote,topParams)

        val camera=TextView(this).apply {
            text="📷"; textSize=26f; gravity=Gravity.CENTER; contentDescription="Add photo report"
            setTextColor(Color.WHITE); background=rounded(Color.rgb(37,99,235),60)
            elevation=10f; setOnClickListener { showPhotoActions() }
        }
        val cameraParams=FrameLayout.LayoutParams(dp(64),dp(64),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(86) }
        screen.addView(camera,cameraParams)

        addBottomNavigation()
        map.onCreate(null)
        map.onStart()
        map.onResume()
        map.getMapAsync { maplibreMap ->
            currentMap=maplibreMap
            maplibreMap.setStyle("https://styles.maptoolkit.org/street.json") {
                currentStyleReady=true
                maplibreMap.cameraPosition=CameraPosition.Builder().target(LatLng(14.5995,120.9842)).zoom(15.5).build()
                maplibreMap.addPolyline(PolylineOptions().add(LatLng(14.6000,120.9828),LatLng(14.5996,120.9840),LatLng(14.5987,120.9852)).color(Color.rgb(37,99,235)).width(6f))
                maplibreMap.addPolyline(PolylineOptions().add(LatLng(14.6000,120.9828),LatLng(14.6008,120.9842),LatLng(14.5987,120.9852)).color(Color.rgb(22,163,74)).width(6f))
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

    private fun showPhotoActions() {
        AlertDialog.Builder(this).setTitle("Add a photo report")
            .setItems(arrayOf("Take photo", "Choose photos")) { _,which ->
                capturedPhotos.clear()
                if (which==0) launchCamera() else launchPhotoPicker()
            }.show()
    }

    private fun launchCamera() {
        val file=File(photoDir,"walkit_${System.currentTimeMillis()}.jpg")
        pendingCameraPath=file.absolutePath
        val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",file)
        val intent=Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT,uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        try { startActivityForResult(intent,REQUEST_CAMERA) }
        catch (_: Exception) { toast("No camera app is available.") }
    }

    private fun launchPhotoPicker() {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        }
        startActivityForResult(intent,REQUEST_PICK)
    }

    @Deprecated("Uses activity result codes to support the platform camera intent")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode!=RESULT_OK) return
        when(requestCode) {
            REQUEST_CAMERA -> {
                pendingCameraPath?.let { path -> if(File(path).exists() && File(path).length()>0) capturedPhotos.add(path) }
                if(capturedPhotos.isEmpty()) { toast("No photo was captured."); return }
                AlertDialog.Builder(this).setTitle("Photo added")
                    .setMessage("Take another photo for this report?")
                    .setNegativeButton("Continue") { _,_ -> showPhotoForm() }
                    .setPositiveButton("Take another") { _,_ -> launchCamera() }.show()
            }
            REQUEST_PICK -> {
                val uris=mutableListOf<Uri>()
                data?.clipData?.let { clip -> for(i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
                if(uris.isEmpty()) data?.data?.let(uris::add)
                worker.execute {
                    uris.forEach { uri -> copyPhoto(uri)?.let(capturedPhotos::add) }
                    runOnUiThread { if(capturedPhotos.isEmpty()) toast("No photos selected.") else showPhotoForm() }
                }
            }
        }
    }

    private fun copyPhoto(uri: Uri): String? = try {
        val file=File(photoDir,"walkit_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
        contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
        if(file.exists() && file.length()>0) file.absolutePath else null
    } catch (_: Exception) { null }

    private fun showPhotoForm() {
        if(capturedPhotos.isEmpty()) return
        val pad=dp(20)
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(pad,dp(8),pad,0) }
        val preview=TextView(this).apply {
            text="${capturedPhotos.size} photo${if(capturedPhotos.size==1) "" else "s"} ready to submit"
            textSize=15f; setTextColor(Color.rgb(55,65,81)); setPadding(0,0,0,dp(10))
        }
        val description=EditText(this).apply { hint="Description"; minLines=2; gravity=Gravity.TOP; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        val time=EditText(this).apply { hint="Time"; setSingleLine(true); setText(SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date())) }
        val target=currentMap?.cameraPosition?.target
        val location=EditText(this).apply {
            hint="Location (latitude, longitude)"; setSingleLine(true)
            if(target!=null) setText("${"%.6f".format(Locale.US,target.latitude)}, ${"%.6f".format(Locale.US,target.longitude)}")
        }
        form.addView(preview); form.addView(description); form.addView(time); form.addView(location)
        AlertDialog.Builder(this).setTitle("Photo report").setView(form)
            .setNegativeButton("Cancel") { _,_ -> capturedPhotos.clear() }
            .setPositiveButton("Submit",null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val parts=location.text.toString().split(",").map { it.trim() }
                        val lat=parts.getOrNull(0)?.toDoubleOrNull(); val lon=parts.getOrNull(1)?.toDoubleOrNull()
                        if(description.text.toString().isBlank()) { description.error="Add a description"; return@setOnClickListener }
                        if(lat==null || lon==null || lat !in -90.0..90.0 || lon !in -180.0..180.0) { location.error="Enter latitude, longitude"; return@setOnClickListener }
                        val owner=userId ?: return@setOnClickListener
                        val paths=capturedPhotos.toList()
                        runDb({ db.savePhotoReport(owner,description.text.toString(),time.text.toString(),"$lat, $lon",paths) }) { id ->
                            val report=PhotoReport(id,description.text.toString(),time.text.toString(),"$lat, $lon",paths)
                            if(selectedTab!=Tab.MAP) showTab(Tab.MAP) else addPhotoMarkers(report)
                            capturedPhotos.clear(); dialog.dismiss(); toast("Photo report added to the map.")
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun addPhotoMarkers(report: PhotoReport) {
        val map=currentMap ?: return
        if(!currentStyleReady) return
        val coordinates=report.location.split(",").map { it.trim().toDoubleOrNull() ?: return }
        if(coordinates.size!=2) return
        val point=LatLng(coordinates[0],coordinates[1])
        report.photos.forEachIndexed { index,path ->
            val bitmap=makePhotoBubble(path) ?: return@forEachIndexed
            val icon=IconFactory.getInstance(this).fromBitmap(bitmap)
            val spread=index/2+1
            val offset=if(index==0) 0.0 else spread*0.000025
            val location=if(index%2==0) LatLng(point.latitude+offset,point.longitude) else LatLng(point.latitude,point.longitude+offset)
            map.addMarker(MarkerOptions().position(location).title(report.description).snippet("${report.time} · ${report.location}").icon(icon))
        }
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
    override fun onDestroy() { disposeMap(); worker.shutdown(); db.close(); super.onDestroy() }

    companion object { private const val REQUEST_CAMERA=20; private const val REQUEST_PICK=21 }
}
