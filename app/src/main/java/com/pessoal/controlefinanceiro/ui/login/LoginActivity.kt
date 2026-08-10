package com.pessoal.controlefinanceiro.ui.login

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.pessoal.controlefinanceiro.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.pessoal.controlefinanceiro.ui.theme.ControleFinanceiroTheme

/**
 * Activity única do app. Ordem de verificação ao abrir:
 * 1) Tem internet? Se não, mostra SemConexaoScreen.
 * 2) Tem sessão do Google salva com permissão da planilha? Se sim, entra direto.
 * 3) Senão, mostra a tela de boas-vindas com o botão "Entrar com Google".
 * Uma vez conectado, entrega o controle pro AppNavHost (Navigation Compose
 * com as telas principais).
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
            var mensagemErro by remember { mutableStateOf<String?>(null) }
            var entrando by remember { mutableStateOf(false) }
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
                entrando = false
            }
            onLoginError = { e ->
                mensagemErro = "Erro no login: ${e.message}"
                entrando = false
            }

            ControleFinanceiroTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                        // 4) Sem sessão salva → tela de boas-vindas com login manual
                        else -> {
                            TelaBoasVindas(
                                entrando = entrando,
                                mensagemErro = mensagemErro,
                                aoClicarEntrar = {
                                    entrando = true
                                    mensagemErro = null
                                    signInLauncher.launch(googleSignInClient.signInIntent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tela de boas-vindas exibida antes do login: ícone do app dentro de um
 * círculo colorido, nome e subtítulo, e o botão "Entrar com Google" dentro
 * de um card, com uma explicação curta do que o app faz.
 */
@Composable
private fun TelaBoasVindas(
    entrando: Boolean,
    mensagemErro: String?,
    aoClicarEntrar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Imagem do app, colocada diretamente em res/drawable/logo_app.png
        Image(
            painter = painterResource(id = R.drawable.logo_app),
            contentDescription = "Ícone do app",
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Controle Financeiro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Seus lançamentos, mensalidades e resumos, direto na sua planilha do Google Sheets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Entre com sua conta Google para acessar sua planilha",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = aoClicarEntrar,
                    enabled = !entrando,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (entrando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Entrar com Google")
                    }
                }

                if (mensagemErro != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        mensagemErro,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}