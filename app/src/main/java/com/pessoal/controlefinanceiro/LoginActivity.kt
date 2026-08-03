package com.pessoal.controlefinanceiro

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.sheets.v4.SheetsScopes

class LoginActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                onLoginSuccess(account)
            } catch (e: Exception) {
                onLoginError(e)
            }
        }
    }

    private var onLoginSuccess: (GoogleSignInAccount) -> Unit = {}
    private var onLoginError: (Exception) -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            var status by remember { mutableStateOf("Não conectado") }

            onLoginSuccess = { account ->
                status = "Conectado como: ${account.email}"
            }
            onLoginError = { e ->
                status = "Erro no login: ${e.message}"
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = status)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            signInLauncher.launch(googleSignInClient.signInIntent)
                        }) {
                            Text("Entrar com Google")
                        }
                    }
                }
            }
        }
    }
}