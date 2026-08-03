package com.pessoal.controlefinanceiro.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.ui.lancamento.LancamentoScreen

object Rotas {
    const val LANCAMENTO = "lancamento"
    const val LISTA = "lista"
    const val RESUMO = "resumo"
}

@Composable
fun AppNavHost(repository: SheetsRepository, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Rotas.LANCAMENTO) {
        composable(Rotas.LANCAMENTO) {
            LancamentoScreen(repository = repository)
        }
        composable(Rotas.LISTA) {
            // próxima etapa
        }
        composable(Rotas.RESUMO) {
            // próxima etapa
        }
    }
}