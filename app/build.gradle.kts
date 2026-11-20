import java.util.Properties

plugins { id("com.android.application") }

val localSettings = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val googleMapsKey = localSettings.getProperty("WALKIT_GOOGLE_MAPS_KEY")
    ?: System.getenv("WALKIT_GOOGLE_MAPS_KEY")
    ?: ""

android {
    namespace = "com.walkit.prototype"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.walkit.prototype"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsKey
        buildConfigField("String", "SUPABASE_URL", "\"https://juxnlpazlvjggxmojnoq.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_PxzIfXhuYbewhHvdvdGJUg_ATV1IjPo\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependencies {
        implementation("com.google.android.gms:play-services-maps:20.1.0")
        implementation("androidx.core:core:1.15.0")
        implementation("androidx.camera:camera-camera2:1.6.2")
        implementation("androidx.camera:camera-lifecycle:1.6.2")
        implementation("androidx.camera:camera-view:1.6.2")
        implementation("androidx.activity:activity-ktx:1.13.0")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
        androidTestImplementation("androidx.test:runner:1.6.2")
        androidTestImplementation("androidx.test.ext:junit:1.2.1")
    }
}
