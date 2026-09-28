plugins {
    id("wifilens.jvm.library")
}

dependencies {
    api(libs.javax.inject) // @Qualifier/@Inject so pure modules can declare injectable types without Android
    implementation(libs.kotlinx.coroutines.core)
}
