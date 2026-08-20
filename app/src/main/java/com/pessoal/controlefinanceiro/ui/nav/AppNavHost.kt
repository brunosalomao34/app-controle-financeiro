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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            NavigationBar(tonalElevation = 4.dp) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val rotaAtual = backStackEntry?.destination?.route

                itensMenu.forEach { item ->
                    val selecionado = rotaAtual == item.rota
                    NavigationBarItem(
                        selected = selecionado,
                        onClick = {
                            navController.navigate(item.rota) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        },
                        // Marcação clara da tela atual: ícone preenchido em cor de destaque
                        // com um "pill" de fundo (indicatorColor) quando selecionado; nos
                        // demais itens, ícone e texto ficam numa cor neutra/apagada.
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        icon = { Icon(item.icone, contentDescription = item.label) },
                        label = {
                            // Sempre reserva 2 linhas (mesmo que a segunda fique em branco, como em
                            // "Mensalidades") — isso garante que todos os itens tenham a mesma
                            // altura de rótulo, mantendo os ícones alinhados na mesma posição
                            // vertical na bottom bar. O rótulo do item ativo fica em negrito,
                            // reforçando a marcação da tela atual.
                            val partes = item.label.split(" ", limit = 2)
                            val segundaLinha = partes.getOrElse(1) { "" }
                            Text(
                                text = "${partes[0]}\n$segundaLinha",
                                textAlign = TextAlign.Center,
                                fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
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