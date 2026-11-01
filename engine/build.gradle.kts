plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.antinode.engine"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:data"))
    implementation(project(":core:dsp"))
    implementation(libs.androidx.core.ktx)
}
