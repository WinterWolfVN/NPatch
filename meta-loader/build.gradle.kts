import java.util.Locale

plugins {
    alias(libs.plugins.agp.app)
}

android {
    defaultConfig {
        multiDexEnabled = true
    }
    
    sourceSets {
        named("main") {
            java.srcDir("${rootProject.projectDir}/src") 
            exclude("android/os/**")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
    }
    namespace = "org.lsposed.npatch.metaloader"
}

androidComponents.onVariants { variant ->
    val variantCapped = variant.name.replaceFirstChar { it.uppercase() }
    val variantLowered = variant.name.lowercase()

    tasks.register<Copy>("copyDex$variantCapped") {
    dependsOn("assemble$variantCapped")
    val dexOutPath = if (variant.buildType == "release")
        "${layout.buildDirectory.get()}/intermediates/dex/$variantLowered/minify${variantCapped}WithR8" else
        "${layout.buildDirectory.get()}/intermediates/dex/$variantLowered/mergeDex$variantCapped"
    from(dexOutPath)
    rename("classes.dex", "metaloader.dex")
    into("${rootProject.projectDir}/out/assets/${variant.name}/npatch")
}
    tasks.register("copy$variantCapped") {
        dependsOn("copyDex$variantCapped")

        doLast {
            println("Loader dex has been copied to ${rootProject.projectDir}${File.separator}out")
        }
    }
}

dependencies {
    compileOnly(projects.hiddenapi.stubs)
    implementation(projects.share.java)
    implementation(libs.hiddenapibypass) 
}
