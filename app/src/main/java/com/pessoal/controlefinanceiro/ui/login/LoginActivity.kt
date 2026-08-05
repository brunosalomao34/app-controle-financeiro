package com.pessoal.controlefinanceiro.ui.login

import android.app.Activity
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
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.data.possuiConexaoInternet
import com.pessoal.controlefinanceiro.ui.comum.SemConexaoScreen
import com.pessoal.controlefinanceiro.ui.nav.AppNavHost

/**
 * Activity única do app. Ordem de verificação ao abrir:
 * 1) Tem internet? Se não, mostra SemConexaoScreen.
 * 2) Tem sessão do Google salva com permissão da planilha? Se sim, entra direto.
 * 3) Senão, mostra o botão "Entrar com Google".
 * Uma vez conectado, entrega o controle pro AppNavHost (Navigation Compose
 * com as 3 telas principais).
 */
class LoginActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    // Callbacks preenchidos dentro do setContent, chamados pelo resultado do login
    private var onLoginSuccess: (GoogleSignInAccount) -> Unit = {}
    private var onLoginError: (Exception) -> Unit = {}

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                onLoginSuccess(task.getResult(Exception::class.java))
            } catch (e: Exception) {
                onLoginError(e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val opcoesLogin = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, opcoesLogin)

        setContent {
            var conectado by remember { mutableStateOf(false) }
            var repositorio by remember { mutableStateOf<SheetsRepository?>(null) }
            var status by remember { mutableStateOf("Não conectado") }
            var verificandoLoginSalvo by remember { mutableStateOf(true) }

            // Contador incrementado pelo botão "Tentar novamente" da tela de
            // sem conexão — cada incremento dispara uma nova checagem abaixo
            var tentativaConexao by remember { mutableIntStateOf(0) }
            var temConexao by remember { mutableStateOf(possuiConexaoInternet(this@LoginActivity)) }

            // Só tenta o login automático se houver internet no momento
            LaunchedEffect(tentativaConexao) {
                temConexao = possuiConexaoInternet(this@LoginActivity)
                if (temConexao) {
                    val contaSalva = GoogleSignIn.getLastSignedInAccount(this@LoginActivity)
                    if (contaSalva != null &&
                        GoogleSignIn.hasPermissions(contaSalva, Scope(SheetsScopes.SPREADSHEETS))
                    ) {
                        repositorio = SheetsRepository(this@LoginActivity, contaSalva.account!!)
                        conectado = true
                    }
                    verificandoLoginSalvo = false
                }
            }

            onLoginSuccess = { account ->
                repositorio = SheetsRepository(this@LoginActivity, account.account!!)
                conectado = true
            }
            onLoginError = { e ->
                status = "Erro no login: ${e.message}"
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        // 1) Sem internet: mostra antes de qualquer outra checagem
                        !temConexao -> {
                            SemConexaoScreen(aoTentarNovamente = { tentativaConexao++ })
                        }
                        // 2) Ainda checando se existe login salvo
                        verificandoLoginSalvo -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        // 3) Login já confirmado (automático ou manual) → app principal
                        conectado && repositorio != null -> {
                            AppNavHost(repository = repositorio!!)
                        }
                        // 4) Sem sessão salva → pede login manual
                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = status)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { signInLauncher.launch(googleSignInClient.signInIntent) }) {
                                    Text("Entrar com Google")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}