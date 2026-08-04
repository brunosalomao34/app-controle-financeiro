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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.ui.lancamento.LancamentoScreen
import com.pessoal.controlefinanceiro.ui.lista.ListaLancamentosScreen

object Rotas {
    const val LANCAMENTO = "lancamento"
    const val LISTA = "lista"
    const val RESUMO = "resumo"
}

private data class ItemMenu(val rota: String, val label: String, val icone: androidx.compose.ui.graphics.vector.ImageVector)

private val itensMenu = listOf(
    ItemMenu(Rotas.LANCAMENTO, "Lançar", Icons.Default.Add),
    ItemMenu(Rotas.LISTA, "Lista", Icons.Default.List),
    ItemMenu(Rotas.RESUMO, "Resumo", Icons.Default.PieChart)
)

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
                .consumeWindowInsets(paddingInterno)
        ) {
            composable(Rotas.LANCAMENTO) {
                LancamentoScreen(repository = repository)
            }
            composable(Rotas.LISTA) {
                ListaLancamentosScreen(
                    repository = repository,
                    aoEditar = { linha ->
                        navController.navigate("${Rotas.LANCAMENTO}?linha=$linha")
                    }
                )
            }
            composable(Rotas.RESUMO) {
                // próxima etapa
            }
            composable(
                route = "${Rotas.LANCAMENTO}?linha={linha}",
                arguments = listOf(androidx.navigation.navArgument("linha") {
                    type = androidx.navigation.NavType.IntType
                    defaultValue = -1
                })
            ) { backStackEntry ->
                val linha = backStackEntry.arguments?.getInt("linha") ?: -1
                LancamentoScreen(
                    repository = repository,
                    linhaEdicao = if (linha == -1) null else linha,
                    aoSalvarComSucesso = { navController.popBackStack() }
                )
            }
        }
    }
}