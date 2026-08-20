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
    tertiary = DouradoDestaque,
    onTertiary = Color.White,
    tertiaryContainer = DouradoDestaqueContainer,
    onTertiaryContainer = DouradoDestaqueEscuro,
    error = VermelhoSaida,
    onError = Color.White,
    errorContainer = VermelhoSaidaContainer,
    onErrorContainer = VermelhoSaida,
    background = FundoApp,
    onBackground = TextoPrincipal,
    surface = SuperficieCard,
    onSurface = TextoPrincipal,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TextoSecundario,
    outline = Contorno,
    outlineVariant = ContornoSutil
)

// Cantos mais arredondados em Cards, campos de texto, menus e botões — dá
// um visual mais suave e moderno em todas as telas, sem precisar mexer
// tela por tela
private val FormasApp = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Elevação padrão dos Cards do app — sombra sutil que separa visualmente o
// card do fundo, aplicada de forma consistente em todas as telas
val ElevacaoCardPadrao = 2.dp

// Cor padrão dos HorizontalDivider do app — mais escura que o outlineVariant
// do tema, pra marcar melhor a separação entre seções, aplicada de forma
// consistente em todas as telas
val CorDividerPadrao = ContornoDivider

/**
 * Tema visual único do app — aplicado uma vez, na raiz (LoginActivity),
 * e herdado automaticamente por todas as telas via MaterialTheme.
 */
@Composable
fun ControleFinanceiroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaDeCoresApp,
        typography = AppTypography,
        shapes = FormasApp,
        content = content
    )
}