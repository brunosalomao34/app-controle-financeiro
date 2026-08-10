package com.pessoal.controlefinanceiro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EsquemaDeCoresApp = lightColorScheme(
    primary = AzulPrimario,
    onPrimary = Color.White,
    primaryContainer = AzulPrimarioContainer,
    onPrimaryContainer = AzulEscuro,
    secondary = VerdeEntrada,
    onSecondary = Color.White,
    secondaryContainer = VerdeEntradaContainer,
    onSecondaryContainer = VerdeEntrada,
    error = VermelhoSaida,
    errorContainer = VermelhoSaidaContainer,
    background = FundoApp,
    surface = SuperficieCard,
    surfaceVariant = FundoApp
)

// Cantos mais arredondados em Cards, campos de texto e botões — dá um
// visual mais suave sem precisar mexer em nenhuma tela individualmente
private val FormasApp = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

/**
 * Tema visual único do app — aplicado uma vez, na raiz (LoginActivity),
 * e herdado automaticamente por todas as telas via MaterialTheme.
 */
@Composable
fun ControleFinanceiroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaDeCoresApp,
        shapes = FormasApp,
        content = content
    )
}