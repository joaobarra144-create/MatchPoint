plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // 1. Alvo Android
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // 2. Alvo WebAssembly (Wasm) - Ajustado para gerar executável
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "retroarkanoid"
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply { add(project.projectDir.path) }
                }
            }
        }
        binaries.executable() // ESSENCIAL: Sem isto o Gradle não cria as tarefas BrowserRun
    }

    // 3. Organização de pastas e dependências
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources) // Útil para assets partilhados
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.core:core-ktx:1.12.0")
            }
        }

        val wasmJsMain by getting {
            dependencies {
                // Dependências específicas da Web
            }
        }
    }
}

android {
    namespace = "com.jarbas.retroarkanoid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarbas.Tenis_Web"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Mapeamento de pastas para o Android
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
            assets.srcDirs("src/androidMain/assets")
            java.srcDirs("src/androidMain/kotlin") 
        }
    }
}