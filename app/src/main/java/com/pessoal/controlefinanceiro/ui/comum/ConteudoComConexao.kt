package com.pessoal.controlefinanceiro.ui.comum

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.pessoal.controlefinanceiro.data.possuiConexaoInternet

/**
 * Envolve o conteúdo de uma tela: só mostra esse conteúdo se houver
 * internet no momento; senão, mostra a SemConexaoScreen com botão
 * de "tentar novamente", que reavalia a conexão ao ser clicado.
 */
@Composable
fun ConteudoComConexao(content: @Composable () -> Unit) {
    val contexto = LocalContext.current
    var temConexao by remember { mutableStateOf(possuiConexaoInternet(contexto)) }

    if (temConexao) {
        content()
    } else {
        SemConexaoScreen(aoTentarNovamente = { temConexao = possuiConexaoInternet(contexto) })
    }
}