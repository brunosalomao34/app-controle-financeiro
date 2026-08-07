package com.pessoal.controlefinanceiro.ui.resumomensal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.model.Lancamento
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale


private val MESES = listOf(
    "Janeiro" to "jan", "Fevereiro" to "fev", "Março" to "mar", "Abril" to "abr",
    "Maio" to "mai", "Junho" to "jun", "Julho" to "jul", "Agosto" to "ago",
    "Setembro" to "set", "Outubro" to "out", "Novembro" to "nov", "Dezembro" to "dez"
)

// "Todos" + opções fixas de forma de pagamento (mesmas da Tela de Lançamento)
private val FORMAS_PAGAMENTO_FILTRO = listOf("Todos", "Dinheiro", "Pix", "Boleto", "Débito", "Crédito")

// Altura máxima dos menus suspensos: ~5 itens visíveis (48dp cada), com scroll pro resto
private val ALTURA_MAXIMA_DROPDOWN = 240.dp

/** Totais de um mês para uma forma de pagamento específica. */
private data class TotalFormaPagamento(
    val forma: String,
    val entradas: Double,
    val saidas: Double,
    val saldo: Double
)

/**
 * Tela de Lista — filtra os lançamentos por Mês/Ano e, opcionalmente, por
 * Forma de Pagamento. Lançamentos parcelados aparecem em todos os meses
 * cobertos pela parcela (usando as colunas auxiliares K "Mês Início" e
 * L "Mês Fim" da planilha). Mostra também uma tabela com Entradas/Saídas/
 * Saldo do mês, quebrada por forma de pagamento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumoMensalScreen(repository: SheetsRepository, aoEditar: (Int) -> Unit) {
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    var todosLancamentos by remember { mutableStateOf<List<Lancamento>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var lancamentoParaExcluir by remember { mutableStateOf<Lancamento?>(null) }

    val calendarioAtual = remember { Calendar.getInstance() }
    var mesSelecionado by remember { mutableStateOf(MESES[calendarioAtual.get(Calendar.MONTH)]) }
    var anoSelecionado by remember { mutableStateOf(calendarioAtual.get(Calendar.YEAR)) }
    var anos by remember { mutableStateOf<List<Int>>(emptyList()) }
    var mesExpandido by remember { mutableStateOf(false) }
    var anoExpandido by remember { mutableStateOf(false) }

    // Filtro por forma de pagamento — "Todos" por padrão
    var formaPagamentoFiltro by remember { mutableStateOf("Todos") }
    var formaPagamentoFiltroExpandido by remember { mutableStateOf(false) }

    // Controla se a tabela de totais por forma de pagamento está expandida
    var tabelaExpandida by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    suspend fun recarregar() {
        carregando = true
        todosLancamentos = repository.listarLancamentos()
        carregando = false
    }

    // Carrega a lista de anos que têm aba de Resumo na planilha
    LaunchedEffect(Unit) {
        anos = repository.buscarAnosDisponiveis()
        if (anos.isNotEmpty() && anoSelecionado !in anos) {
            anoSelecionado = anos.last()
        }
    }

    // Recarrega os lançamentos toda vez que a tela volta a ficar visível
    // (primeira abertura + retorno de uma edição/outra aba)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { recarregar() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Índice numérico do mês/ano selecionado (mesmo cálculo usado nas colunas K/L)
    val indiceMesConsultado = anoSelecionado * 12 + (MESES.indexOf(mesSelecionado) + 1)

    // Filtra pelo mês/ano selecionado (via intervalo de parcelas K/L) e,
    // se escolhido, também pela forma de pagamento
    val lancamentosFiltrados = remember(todosLancamentos, mesSelecionado, anoSelecionado, formaPagamentoFiltro) {
        todosLancamentos
            .filter { indiceMesConsultado in it.mesInicioIndex..it.mesFimIndex }
            .filter { formaPagamentoFiltro == "Todos" || it.formaPagamento == formaPagamentoFiltro }
            .sortedBy { it.linha } // ordem de lançamento
    }

    // Tabela: uma linha fixa pra cada forma de pagamento (Dinheiro, Pix, Boleto,
    // Débito, Crédito), sempre visíveis mesmo com valor zero no mês. Soma
    // Entradas e Saídas separadamente (usando o valor da parcela quando aplicável).
    val totaisPorForma = remember(lancamentosFiltrados) {
        fun valorEfetivo(l: Lancamento) = if (l.qtdParcelas > 1) l.valorParcela else l.valorTotal

        FORMAS_PAGAMENTO_FILTRO.drop(1) // remove "Todos", mantém só as formas reais
            .map { forma ->
                val itensDaForma = lancamentosFiltrados.filter { it.formaPagamento == forma }
                val entradas = itensDaForma.filter { it.tipo == "Entrada" }.sumOf(::valorEfetivo)
                val saidas = itensDaForma.filter { it.tipo == "Saída" }.sumOf(::valorEfetivo)
                TotalFormaPagamento(forma, entradas, saidas, entradas - saidas)
            }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Resumo Mensal", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))

        // Filtro Mês/Ano
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = mesExpandido,
                onExpandedChange = { mesExpandido = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = mesSelecionado.first,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Mês") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mesExpandido) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = mesExpandido,
                    onDismissRequest = { mesExpandido = false },
                    modifier = Modifier.heightIn(max = ALTURA_MAXIMA_DROPDOWN)
                ) {
                    MESES.forEach { m ->
                        DropdownMenuItem(text = { Text(m.first) }, onClick = {
                            mesSelecionado = m
                            mesExpandido = false
                        })
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = anoExpandido,
                onExpandedChange = { anoExpandido = it },
                modifier = Modifier.weight(1f)
            ) {
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
                    anos.forEach { a ->
                        DropdownMenuItem(text = { Text(a.toString()) }, onClick = {
                            anoSelecionado = a
                            anoExpandido = false
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filtro por Forma de Pagamento
        ExposedDropdownMenuBox(
            expanded = formaPagamentoFiltroExpandido,
            onExpandedChange = { formaPagamentoFiltroExpandido = it }
        ) {
            OutlinedTextField(
                value = formaPagamentoFiltro,
                onValueChange = {},
                readOnly = true,
                label = { Text("Forma de Pagamento") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formaPagamentoFiltroExpandido) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = formaPagamentoFiltroExpandido,
                onDismissRequest = { formaPagamentoFiltroExpandido = false },
                modifier = Modifier.heightIn(max = ALTURA_MAXIMA_DROPDOWN)
            ) {
                FORMAS_PAGAMENTO_FILTRO.forEach { forma ->
                    DropdownMenuItem(text = { Text(forma) }, onClick = {
                        formaPagamentoFiltro = forma
                        formaPagamentoFiltroExpandido = false
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            carregando -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            lancamentosFiltrados.isEmpty() -> {
                Text("Nenhum lançamento em ${mesSelecionado.first} de $anoSelecionado.")
            }
            else -> {
                CardTabelaPorFormaPagamento(
                    mesLabel = "${mesSelecionado.first} de $anoSelecionado",
                    totaisPorForma = totaisPorForma,
                    formatoMoeda = formatoMoeda,
                    expandida = tabelaExpandida,
                    aoAlternarExpandida = { tabelaExpandida = !tabelaExpandida }
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lancamentosFiltrados, key = { it.linha }) { lancamento ->
                        ItemLancamento(
                            lancamento = lancamento,
                            mesSelecionado = mesSelecionado,
                            anoSelecionado = anoSelecionado,
                            formatoMoeda = formatoMoeda,
                            aoEditar = { aoEditar(lancamento.linha) },
                            aoExcluir = { lancamentoParaExcluir = lancamento }
                        )
                    }
                }
            }
        }
    }

    // Diálogo de confirmação de exclusão — avisa quando o lançamento é parcelado
    lancamentoParaExcluir?.let { lancamento ->
        AlertDialog(
            onDismissRequest = { lancamentoParaExcluir = null },
            title = { Text("Remover lançamento?") },
            text = {
                if (lancamento.qtdParcelas > 1) {
                    Text(
                        "\"${lancamento.descricao}\" está parcelado em ${lancamento.qtdParcelas}x. " +
                                "Remover vai excluir TODAS as ${lancamento.qtdParcelas} parcelas, não só a deste mês. " +
                                "Essa ação não pode ser desfeita."
                    )
                } else {
                    Text("\"${lancamento.descricao}\" será removido. Essa ação não pode ser desfeita.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.excluirLancamento(lancamento.linha)
                        lancamentoParaExcluir = null
                        recarregar()
                    }
                }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { lancamentoParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Tabela: linhas = forma de pagamento (fixas), colunas = Entrada / Saída / Saldo do mês.
 * Pode ser recolhida através do botão no canto superior direito.
 */
@Composable
private fun CardTabelaPorFormaPagamento(
    mesLabel: String,
    totaisPorForma: List<TotalFormaPagamento>,
    formatoMoeda: NumberFormat,
    expandida: Boolean,
    aoAlternarExpandida: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabeçalho do card: título + botão de expandir/recolher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Totais de $mesLabel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = aoAlternarExpandida) {
                    Icon(
                        imageVector = if (expandida) Icons.Filled.ArrowCircleUp else Icons.Filled.ArrowCircleDown,
                        contentDescription = if (expandida) "Recolher tabela" else "Expandir tabela"
                    )
                }
            }

            if (expandida) {
                Spacer(modifier = Modifier.height(8.dp))

                // Cabeçalho da tabela
                Row(modifier = Modifier.fillMaxWidth()) {
                    CelulaTabelaForma("Forma", peso = 1f, negrito = true)
                    CelulaTabelaForma("Entrada", peso = 1f, negrito = true)
                    CelulaTabelaForma("Saída", peso = 1f, negrito = true)
                    CelulaTabelaForma("Saldo", peso = 1f, negrito = true)
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Uma linha fixa por forma de pagamento
                totaisPorForma.forEach { totalForma ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        CelulaTabelaForma(totalForma.forma, peso = 1f)
                        CelulaTabelaForma(formatoMoeda.format(totalForma.entradas), peso = 1f)
                        CelulaTabelaForma(formatoMoeda.format(totalForma.saidas), peso = 1f)
                        CelulaTabelaForma(formatoMoeda.format(totalForma.saldo), peso = 1f)
                    }
                }
            }
        }
    }
}

/** Uma célula de texto da tabela de formas de pagamento. */
@Composable
private fun RowScope.CelulaTabelaForma(texto: String, peso: Float, negrito: Boolean = false) {
    Text(
        texto,
        modifier = Modifier.weight(peso),
        maxLines = 1,
        fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal,
        style = if (negrito) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    )
}

/** Card de um lançamento na lista, com botões de editar e remover. */
@Composable
private fun ItemLancamento(
    lancamento: Lancamento,
    mesSelecionado: Pair<String, String>,
    anoSelecionado: Int,
    formatoMoeda: NumberFormat,
    aoEditar: () -> Unit,
    aoExcluir: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(lancamento.descricao, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${lancamento.categoria} • ${lancamento.data} • ${lancamento.formaPagamento}",
                    style = MaterialTheme.typography.bodySmall
                )

                val corValor = if (lancamento.tipo == "Entrada") Color(0xFF2E7D32) else Color(0xFFC62828)

                if (lancamento.qtdParcelas > 1) {
                    // Mostra o valor da parcela (não o total) + qual número dela é neste mês
                    val indiceMesConsultado = anoSelecionado * 12 + (MESES.indexOf(mesSelecionado) + 1)
                    val numeroDaParcela = indiceMesConsultado - lancamento.mesInicioIndex + 1
                    Text(
                        "${formatoMoeda.format(lancamento.valorParcela)}  (parcela $numeroDaParcela/${lancamento.qtdParcelas})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = corValor
                    )
                } else {
                    Text(
                        formatoMoeda.format(lancamento.valorTotal),
                        style = MaterialTheme.typography.bodyLarge,
                        color = corValor
                    )
                }
            }
            IconButton(onClick = aoEditar) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = aoExcluir) {
                Icon(Icons.Default.Delete, contentDescription = "Remover")
            }
        }
    }
}