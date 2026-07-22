plugins {
    alias(libs.plugins.agp.lib)
}

android {
    sourceSets {
        named("main") {
            java.srcDir("${rootProject.projectDir}/src")             
        }
    }
    namespace = "org.lsposed.npatch.share"

    buildFeatures {
        androidResources = false
        buildConfig = false
    }
}

tasks.withType<JavaCompile>().configureEach {
    exclude("**/android/app/**")
}

dependencies {
    implementation(projects.services.daemonService)
}
