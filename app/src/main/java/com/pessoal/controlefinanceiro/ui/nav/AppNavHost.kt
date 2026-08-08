package com.pessoal.controlefinanceiro.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.ui.comum.ConteudoComConexao
import com.pessoal.controlefinanceiro.ui.lancamento.NovoLancamentoScreen
import com.pessoal.controlefinanceiro.ui.mensalidade.MensalidadeScreen
import com.pessoal.controlefinanceiro.ui.resumomensal.ResumoMensalScreen
import com.pessoal.controlefinanceiro.ui.resumoanual.ResumoAnualScreen

/** Nomes das rotas de navegação usadas no app. */
object Rotas {
    const val LANCAMENTO = "lancamento"
    const val MENSALIDADE = "mensalidade"
    const val LISTA = "lista"
    const val RESUMO = "resumo"
}

private data class ItemMenu(val rota: String, val label: String, val icone: ImageVector)

// Rótulos exibidos na bottom navigation, abaixo de cada ícone
private val itensMenu = listOf(
    ItemMenu(Rotas.LANCAMENTO, "Novo Lançamento", Icons.Default.Add),
    ItemMenu(Rotas.MENSALIDADE, "Mensalidades", Icons.Default.Repeat),
    ItemMenu(Rotas.LISTA, "Resumo Mensal", Icons.Default.List),
    ItemMenu(Rotas.RESUMO, "Resumo Anual", Icons.Default.PieChart)
)

/**
 * Estrutura de navegação do app: bottom navigation com 4 abas
 * (Lançar, Mensalidade, Lista, Resumo) + rota extra de edição de lançamento
 * (acessada a partir da Lista, não aparece na bottom bar).
 */
@Composable
fun AppNavHost(repository: SheetsRepository) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val rotaAtual = backStackEntry?.destination?.route

                itensMenu.forEach { item ->
                    NavigationBarItem(
                        selected = rotaAtual == item.rota,
                        onClick = {
                            navController.navigate(item.rota) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(item.icone, contentDescription = item.label) },
                        label = {
                            // Quebra o rótulo em 2 linhas quando tem 2 palavras (primeira em cima,
                            // resto embaixo); rótulos de 1 palavra só (como "Mensalidades") ficam
                            // numa linha só, sem deixar uma segunda linha em branco.
                            val partes = item.label.split(" ", limit = 2)
                            val texto = if (partes.size > 1) "${partes[0]}\n${partes[1]}" else partes[0]
                            Text(
                                text = texto,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    ) { paddingInterno ->
        NavHost(
            navController = navController,
            startDestination = Rotas.LANCAMENTO,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingInterno)
                .consumeWindowInsets(paddingInterno),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            composable(Rotas.LANCAMENTO) {
                ConteudoComConexao {
                    NovoLancamentoScreen(repository = repository)
                }
            }

            composable(Rotas.MENSALIDADE) {
                ConteudoComConexao {
                    MensalidadeScreen(repository = repository)
                }
            }

            composable(Rotas.LISTA) {
                ConteudoComConexao {
                    ResumoMensalScreen(
                        repository = repository,
                        aoEditar = { linha -> navController.navigate("${Rotas.LANCAMENTO}?linha=$linha") }
                    )
                }
            }

            composable(Rotas.RESUMO) {
                ConteudoComConexao {
                    ResumoAnualScreen(repository = repository)
                }
            }

            // Rota de edição: mesma tela de Lançamento, mas recebendo a linha
            // a editar. Ao salvar, volta pra tela anterior (a Lista).
            composable(
                route = "${Rotas.LANCAMENTO}?linha={linha}",
                arguments = listOf(navArgument("linha") {
                    type = NavType.IntType
                    defaultValue = -1
                })
            ) { backStackEntry ->
                val linha = backStackEntry.arguments?.getInt("linha") ?: -1
                ConteudoComConexao {
                    NovoLancamentoScreen(
                        repository = repository,
                        linhaEdicao = if (linha == -1) null else linha,
                        aoSalvarComSucesso = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}