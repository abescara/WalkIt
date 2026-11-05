package com.walkit.prototype

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import java.util.concurrent.Executors
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import android.graphics.Color

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var db: WalkItDb
    private lateinit var root: LinearLayout
    private var userId: Long? = null // Session intentionally ends when the process is killed.
    private var mapView: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = WalkItDb(this)
        MapLibre.getInstance(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        setContentView(ScrollView(this).apply { addView(root) })
        showAuth()
    }

    private fun label(text: String) = TextView(this).apply { this.text=text; textSize=18f; setPadding(0,12,0,8) }
    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text=text; minHeight=48; setOnClickListener { action() } }
    private fun field(hint: String, password: Boolean=false) = EditText(this).apply {
        this.hint=hint; contentDescription=hint; setSingleLine(true)
        if (password) inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    private fun clear(title: String) { root.removeAllViews(); root.addView(label(title).apply { textSize=26f }) }
    private fun error(message: String) { Toast.makeText(this,message,Toast.LENGTH_LONG).show() }
    private fun <T> runDb(task: () -> T, done: (T) -> Unit) {
        worker.execute {
            try { val result=task(); runOnUiThread { done(result) } }
            catch (e: Exception) { runOnUiThread { error(e.message ?: "Operation failed") } }
        }
    }

    private fun showAuth() {
        clear("WalkIt prototype")
        root.addView(label("Local accounts on this device only. Use demonstration credentials."))
        val username=field("Username"); val password=field("Password",true)
        root.addView(username); root.addView(password)
        root.addView(button("Sign in") {
            runDb({ db.login(username.text.toString(),password.text.toString()) }) { id ->
                if (id==null) error("Invalid username or password") else { userId=id; showHome() }
            }
        })
        root.addView(button("Create account") {
            runDb({ db.register(username.text.toString(),password.text.toString()) }) { id -> userId=id; showHome() }
        })
    }

    private fun showHome() {
        if (userId==null) { showAuth(); return }
        clear("WalkIt")
        root.addView(label("Recorded conditions are unverified. Check your surroundings before walking."))
        root.addView(button("Hazard reports") { showReports() })
        root.addView(button("Compare routes") { showRoutes() })
        root.addView(button("Open map") { showMap() })
        root.addView(button("Sign out") { userId=null; showAuth() })
    }

    private fun showReports() {
        val owner=userId ?: return showAuth()
        runDb({ db.reports() }) { reports ->
            clear("Hazard reports")
            root.addView(button("Back") { showHome() })
            root.addView(button("Add report") { showAddReport() })
            if (reports.isEmpty()) root.addView(label("No reports yet."))
            reports.forEach { report ->
                root.addView(label("${report.title} · ${report.segment}\n${report.category} · severity ${report.severity} · ${report.status}\n${report.details}\nBy ${report.owner}"))
                if (report.ownerId==owner) {
                    if (report.status=="Active") root.addView(button("Mark resolved: ${report.title}") {
                        runDb({ db.resolveReport(report.id,owner) }) { showReports() }
                    })
                    root.addView(button("Delete: ${report.title}") {
                        AlertDialog.Builder(this).setMessage("Delete this report permanently?")
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("Delete") { _,_ -> runDb({ db.deleteReport(report.id,owner) }) { showReports() } }.show()
                    })
                }
            }
        }
    }

    private fun showAddReport() {
        val owner=userId ?: return showAuth()
        runDb({ db.segments() }) { segments ->
            clear("Add hazard report")
            root.addView(button("Back") { showReports() })
            val title=field("Title (5–80 characters)"); val details=field("Details (10–500 characters)")
            root.addView(title); root.addView(details)
            root.addView(label("Pathway segment"))
            val segment=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,segments.map { it.second }) }
            root.addView(segment)
            root.addView(label("Category"))
            val categories=listOf("Obstruction","Damaged Pavement","Accessibility Barrier","Other")
            val category=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,categories) }
            root.addView(category)
            root.addView(label("Severity"))
            val severity=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("1 - Low","2 - Medium","3 - High")) }
            root.addView(severity)
            val save=button("Save report") {
                val segmentId=segments[segment.selectedItemPosition].first
                runDb({ db.addReport(owner,segmentId,title.text.toString(),details.text.toString(),categories[category.selectedItemPosition],severity.selectedItemPosition+1) }) {
                    error("Report saved"); showReports()
                }
            }
            root.addView(save)
        }
    }

    private fun showRoutes() {
        if (userId==null) return showAuth()
        runDb({ db.routes() }) { routes ->
            clear("Compare routes")
            root.addView(button("Back") { showHome() })
            root.addView(label("Sample routes for demonstration only. No active reports means conditions may be unknown."))
            routes.forEach { route ->
                root.addView(label("${route.name} · ${route.meters} m\n${route.hazards} active reports · ${route.barriers} accessibility barriers"))
            }
        }
    }

    private fun showMap() {
        if (userId==null) return showAuth()
        clear("WalkIt map")
        root.addView(button("Back") { disposeMap(); showHome() })
        root.addView(label("Illustrative sample routes and hazard pins around Manila. These are not real reports or your current location."))
        val map=MapView(this)
        mapView=map
        root.addView(map, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 560))
        root.addView(label("Map data © OpenStreetMap contributors · sample geometry only"))
        map.onCreate(null)
        map.onStart()
        map.onResume()
        map.getMapAsync { maplibreMap ->
            maplibreMap.setStyle("https://demotiles.maplibre.org/style.json") {
                maplibreMap.cameraPosition=CameraPosition.Builder().target(LatLng(14.5995,120.9842)).zoom(14.0).build()
                maplibreMap.addPolyline(PolylineOptions().add(LatLng(14.6000,120.9828),LatLng(14.5996,120.9840),LatLng(14.5987,120.9852)).color(Color.rgb(37,99,235)).width(6f))
                maplibreMap.addPolyline(PolylineOptions().add(LatLng(14.6000,120.9828),LatLng(14.6008,120.9842),LatLng(14.5987,120.9852)).color(Color.rgb(22,163,74)).width(6f))
                maplibreMap.addMarker(MarkerOptions().position(LatLng(14.5996,120.9840)).title("Sample obstruction").snippet("Illustrative demo pin"))
                maplibreMap.addMarker(MarkerOptions().position(LatLng(14.6008,120.9842)).title("Sample accessibility barrier").snippet("Illustrative demo pin"))
            }
        }
    }

    private fun disposeMap() { mapView?.onPause(); mapView?.onStop(); mapView?.onDestroy(); mapView=null }
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onDestroy() { disposeMap(); worker.shutdown(); db.close(); super.onDestroy() }
}
