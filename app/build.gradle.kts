import java.util.Properties
import java.io.FileInputStream

// Lê o local.properties (nunca vai pro Git) pra pegar o ID da planilha
// sem deixar hardcoded no código-fonte
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pessoal.controlefinanceiro"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pessoal.controlefinanceiro"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Injeta o ID da planilha (lido do local.properties) como uma constante
        // acessível em Kotlin via BuildConfig.SPREADSHEET_ID
        buildConfigField(
            "String",
            "SPREADSHEET_ID",
            "\"${localProperties.getProperty("SPREADSHEET_ID", "")}\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // necessário pra gerar a classe BuildConfig com o SPREADSHEET_ID
    }

    // Resolve conflitos de arquivos de metadados duplicados entre as libs do
    // Google (auth, sheets, http-client), que trazem o mesmo META-INF/*
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    // --- Dependências padrão do template Android Studio (Compose) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- Login com Google ---
    // Login com suporte a "scopes" (permissões específicas, como acesso ao Sheets)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // --- Google Sheets API ---
    // Cliente base do Google APIs para Android (autenticação/transporte HTTP)
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents") // evita conflito com o HTTP client do Android
    }
    // Biblioteca gerada especificamente pra Sheets API (ler/escrever na planilha)
    implementation("com.google.apis:google-api-services-sheets:v4-rev20260610-2.0.0")
    // Serialização JSON usada pelas chamadas da Sheets API
    implementation("com.google.http-client:google-http-client-gson:1.44.2")

    // --- Assincronismo ---
    // Coroutines: usado em toda chamada à planilha (Dispatchers.IO), evitando
    // travar a interface enquanto espera resposta da rede
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Gráfico da Tela de Resumo ---
    // Vico: biblioteca de gráficos para Compose, usada no gráfico Entradas x Saídas
    implementation("com.patrykandpatrick.vico:compose:2.0.0-alpha.28")
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.28")

    // --- Ícones extras ---
    // Conjunto completo de ícones Material (Edit, Delete, CloudOff, etc.),
    // além dos poucos que já vêm no pacote básico do Compose
    implementation("androidx.compose.material:material-icons-extended")

    // --- Navegação ---
    // Navigation Compose: estrutura das 3 telas (Lançar/Lista/Resumo) + bottom nav
    implementation("androidx.navigation:navigation-compose:2.8.0")
}