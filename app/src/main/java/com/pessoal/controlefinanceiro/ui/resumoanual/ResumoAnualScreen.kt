package com.pessoal.controlefinanceiro.ui.resumoanual

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.model.ResumoMes
import com.pessoal.controlefinanceiro.ui.theme.CorDividerPadrao
import com.pessoal.controlefinanceiro.ui.theme.ElevacaoCardPadrao
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
    val context = LocalContext.current

    var anoSelecionado by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var anoExpandido by remember { mutableStateOf(false) }
    var anosDisponiveis by remember { mutableStateOf<List<Int>>(emptyList()) }
    var resumo by remember { mutableStateOf<List<ResumoMes>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }

    // Carrega a lista de anos com aba de Resumo na planilha (uma vez só)
    LaunchedEffect(Unit) {
        try {
            anosDisponiveis = repository.buscarAnosDisponiveis()
            if (anosDisponiveis.isNotEmpty() && anoSelecionado !in anosDisponiveis) {
                anoSelecionado = anosDisponiveis.last()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Erro ao carregar anos.", Toast.LENGTH_LONG).show()
        }
    }

    // Recarrega o resumo e atualiza o gráfico sempre que o ano muda
    LaunchedEffect(anoSelecionado) {
        carregando = true
        try {
            resumo = repository.buscarResumo(anoSelecionado)
            modelProducer.runTransaction {
                columnSeries {
                    series(resumo.map { it.totalEntradas })
                    series(resumo.map { it.totalSaidas })
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Erro ao carregar resumo.", Toast.LENGTH_LONG).show()
        } finally {
            carregando = false
        }
    }

    val totalEntradasAno = resumo.sumOf { it.totalEntradas }
    val totalSaidasAno = resumo.sumOf { it.totalSaidas }
    val saldoAno = totalEntradasAno - totalSaidasAno

    // Box externo: permite que o indicador de carregamento fique sobreposto
    // (overlay) e centralizado na tela inteira, em vez de centralizado só
    // no espaço que sobra depois do cabeçalho/filtro dentro da Column.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

            HorizontalDivider(thickness = 1.dp, color = CorDividerPadrao)

            // Enquanto carrega, não desenha nada aqui — o indicador é mostrado
            // como overlay centralizado na tela, fora desta Column (ver abaixo).
            if (!carregando) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CardTabelaMensal(resumo, formatoMoeda)

                    HorizontalDivider(thickness = 1.dp, color = CorDividerPadrao)

                    CardTotaisDoAno(totalEntradasAno, totalSaidasAno, saldoAno, formatoMoeda)
                }
            }
        }

        // Indicador de carregamento sobreposto (overlay) e centralizado na
        // tela inteira — não afetado pela altura do cabeçalho/seletor acima.
        if (carregando) {
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = ElevacaoCardPadrao)
    ) {
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = ElevacaoCardPadrao)
    ) {
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