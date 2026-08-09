package com.pessoal.controlefinanceiro.ui.resumoanual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.model.ResumoMes
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private val NOMES_MESES = listOf(
    "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"
)

// Cores fixas usadas no gráfico e na legenda
private val CorEntradas = Color(0xFF00C853) // verde vivo
private val CorSaidas = Color(0xFFD50000)   // vermelho forte

// Altura máxima dos menus suspensos: 6 itens visíveis
private val ALTURA_MAXIMA_DROPDOWN = 305.dp

/**
 * Tela de Resumo — mostra, para o ano selecionado: totais do ano,
 * tabela mês a mês e gráfico de barras (Entradas x Saídas).
 * Ordem na tela: Totais → Tabela → Gráfico.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumoAnualScreen(repository: SheetsRepository) {
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val modelProducer = remember { CartesianChartModelProducer() }

    var anoSelecionado by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var anoExpandido by remember { mutableStateOf(false) }
    var anosDisponiveis by remember { mutableStateOf<List<Int>>(emptyList()) }
    var resumo by remember { mutableStateOf<List<ResumoMes>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }

    // Carrega a lista de anos com aba de Resumo na planilha (uma vez só)
    LaunchedEffect(Unit) {
        anosDisponiveis = repository.buscarAnosDisponiveis()
        if (anosDisponiveis.isNotEmpty() && anoSelecionado !in anosDisponiveis) {
            anoSelecionado = anosDisponiveis.last()
        }
    }

    // Recarrega o resumo e atualiza o gráfico sempre que o ano muda
    LaunchedEffect(anoSelecionado) {
        carregando = true
        resumo = repository.buscarResumo(anoSelecionado)
        carregando = false

        modelProducer.runTransaction {
            columnSeries {
                series(resumo.map { it.totalEntradas })
                series(resumo.map { it.totalSaidas })
            }
        }
    }

    val totalEntradasAno = resumo.sumOf { it.totalEntradas }
    val totalSaidasAno = resumo.sumOf { it.totalSaidas }
    val saldoAno = totalEntradasAno - totalSaidasAno

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Resumo Anual", style = MaterialTheme.typography.headlineSmall)

// Seletor de ano — só mostra anos que existem na planilha
        ExposedDropdownMenuBox(expanded = anoExpandido, onExpandedChange = { anoExpandido = it }) {
            OutlinedTextField(
                value = anoSelecionado.toString(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Ano") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = anoExpandido) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = anoExpandido,
                onDismissRequest = { anoExpandido = false },
                modifier = Modifier.heightIn(max = ALTURA_MAXIMA_DROPDOWN)
            ) {
                anosDisponiveis.forEach { ano ->
                    DropdownMenuItem(text = { Text(ano.toString()) }, onClick = {
                        anoSelecionado = ano
                        anoExpandido = false
                    })
                }
            }
        }

        HorizontalDivider(thickness = 1.dp)

        if (carregando) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            CardTabelaMensal(resumo, formatoMoeda)

            HorizontalDivider(thickness = 1.dp)

            CardTotaisDoAno(totalEntradasAno, totalSaidasAno, saldoAno, formatoMoeda)

            HorizontalDivider(thickness = 1.dp)

            CardGrafico(modelProducer)
        }
    }
}

/** Card com os totais consolidados do ano selecionado. */
@Composable
private fun CardTotaisDoAno(
    totalEntradas: Double,
    totalSaidas: Double,
    saldo: Double,
    formatoMoeda: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total do Ano", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Entradas: ${formatoMoeda.format(totalEntradas)}")
            Text("Saídas: ${formatoMoeda.format(totalSaidas)}")
            Text("Saldo: ${formatoMoeda.format(saldo)}", fontWeight = FontWeight.Bold)
        }
    }
}

/** Card com a tabela de Entradas/Saídas/Saldo mês a mês. */
@Composable
private fun CardTabelaMensal(resumo: List<ResumoMes>, formatoMoeda: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                CelulaTabela("Mês", peso = 0.7f, negrito = true)
                CelulaTabela("Entradas", peso = 1.4f, negrito = true)
                CelulaTabela("Saídas", peso = 1.4f, negrito = true)
                CelulaTabela("Saldo", peso = 1.4f, negrito = true)
            }
            Spacer(modifier = Modifier.height(4.dp))
            resumo.forEach { mes ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    CelulaTabela(NOMES_MESES[mes.mes - 1], peso = 0.7f)
                    CelulaTabela(formatoMoeda.format(mes.totalEntradas), peso = 1.4f)
                    CelulaTabela(formatoMoeda.format(mes.totalSaidas), peso = 1.4f)
                    CelulaTabela(
                        formatoMoeda.format(mes.saldo),
                        peso = 1.4f,
                        cor = if (mes.saldo >= 0) CorEntradas else CorSaidas
                    )
                }
            }
        }
    }
}

/** Uma célula de texto da tabela, com peso de coluna e cor configuráveis. */
@Composable
private fun RowScope.CelulaTabela(
    texto: String,
    peso: Float,
    negrito: Boolean = false,
    cor: Color = Color.Unspecified
) {
    Text(
        texto,
        modifier = Modifier.weight(peso),
        maxLines = 1,
        fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal,
        color = cor,
        style = if (negrito) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    )
}

/** Card com o gráfico de barras Entradas x Saídas por mês, com legenda. */
@Composable
private fun CardGrafico(modelProducer: CartesianChartModelProducer) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Entradas x Saídas por mês", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(color = CorEntradas, thickness = 10.dp),
                            rememberLineComponent(color = CorSaidas, thickness = 10.dp)
                        )
                    ),
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { x, _, _ -> NOMES_MESES.getOrElse(x.toInt()) { "" } }
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendaItem(cor = CorEntradas, texto = "Entradas")
                LegendaItem(cor = CorSaidas, texto = "Saídas")
            }
        }
    }
}

/** Bolinha colorida + texto, usada na legenda do gráfico. */
@Composable
private fun LegendaItem(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(cor, shape = CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}