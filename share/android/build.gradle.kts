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
        source(file("${rootProject.projectDir}/src/android/os/SharedMemory.java"))
    }

dependencies {
    implementation(projects.services.daemonService)
}
