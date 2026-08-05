package com.pessoal.controlefinanceiro.ui.nav

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.ui.comum.ConteudoComConexao
import com.pessoal.controlefinanceiro.ui.lancamento.LancamentoScreen
import com.pessoal.controlefinanceiro.ui.lista.ListaLancamentosScreen
import com.pessoal.controlefinanceiro.ui.resumo.ResumoScreen

/** Nomes das rotas de navegação usadas no app. */
object Rotas {
    const val LANCAMENTO = "lancamento"
    const val LISTA = "lista"
    const val RESUMO = "resumo"
}

private data class ItemMenu(val rota: String, val label: String, val icone: ImageVector)

private val itensMenu = listOf(
    ItemMenu(Rotas.LANCAMENTO, "Lançar", Icons.Default.Add),
    ItemMenu(Rotas.LISTA, "Lista", Icons.Default.List),
    ItemMenu(Rotas.RESUMO, "Resumo", Icons.Default.PieChart)
)

/**
 * Estrutura de navegação do app: bottom navigation com 3 abas
 * (Lançar, Lista, Resumo) + rota extra de edição de lançamento
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
                            // Navegação direta: limpa a pilha até a tela inicial
                            // e vai reto pra aba clicada, sem restaurar estado antigo
                            navController.navigate(item.rota) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(item.icone, contentDescription = item.label) },
                        label = { Text(item.label) }
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
                // libera as telas filhas pra tratar seus próprios insets de teclado
                // (necessário pra imePadding() funcionar certo dentro do Scaffold)
                .consumeWindowInsets(paddingInterno)
        ) {
            composable(Rotas.LANCAMENTO) {
                ConteudoComConexao {
                    LancamentoScreen(repository = repository)
                }
            }

            composable(Rotas.LISTA) {
                ConteudoComConexao {
                    ListaLancamentosScreen(
                        repository = repository,
                        aoEditar = { linha -> navController.navigate("${Rotas.LANCAMENTO}?linha=$linha") }
                    )
                }
            }

            composable(Rotas.RESUMO) {
                ConteudoComConexao {
                    ResumoScreen(repository = repository)
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
                    LancamentoScreen(
                        repository = repository,
                        linhaEdicao = if (linha == -1) null else linha,
                        aoSalvarComSucesso = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}