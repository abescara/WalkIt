plugins { id("com.android.application") }

android {
    namespace = "com.walkit.prototype"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.walkit.prototype"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependencies {
        implementation("org.maplibre.gl:android-sdk:13.6.1")
    }
}
