plugins {
    alias(libs.plugins.agp.lib)
}

android {
    sourceSets {
        named("main") {
            java.srcDir("${rootProject.projectDir}/oldlib")             
        }
    }
    namespace = "org.lsposed.npatch.share"

    buildFeatures {
        androidResources = false
        buildConfig = false
    }
}

tasks.withType<JavaCompile>().configureEach {
    exclude("**/dalvik/system/**")
}

dependencies {
    implementation(projects.services.daemonService)
}
