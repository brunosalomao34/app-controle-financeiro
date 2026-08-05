package com.pessoal.controlefinanceiro.ui.lista

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * Tela de Lista — filtra os lançamentos por Mês/Ano.
 * Lançamentos parcelados aparecem em todos os meses cobertos pela parcela
 * (usando as colunas auxiliares K "Mês Início" e L "Mês Fim" da planilha).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaLancamentosScreen(repository: SheetsRepository, aoEditar: (Int) -> Unit) {
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

    // Filtra pelo mês/ano selecionado, usando o intervalo de parcelas (K/L)
    // em vez de comparar a coluna Mês/Ano diretamente
    val lancamentosFiltrados = remember(todosLancamentos, mesSelecionado, anoSelecionado) {
        val numeroDoMes = MESES.indexOf(mesSelecionado) + 1
        val indiceMesConsultado = anoSelecionado * 12 + numeroDoMes

        todosLancamentos
            .filter { indiceMesConsultado in it.mesInicioIndex..it.mesFimIndex }
            .sortedBy { it.linha } // ordem de lançamento
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Lançamentos", style = MaterialTheme.typography.headlineSmall)
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
                ExposedDropdownMenu(expanded = mesExpandido, onDismissRequest = { mesExpandido = false }) {
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
                ExposedDropdownMenu(expanded = anoExpandido, onDismissRequest = { anoExpandido = false }) {
                    anos.forEach { a ->
                        DropdownMenuItem(text = { Text(a.toString()) }, onClick = {
                            anoSelecionado = a
                            anoExpandido = false
                        })
                    }
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
                Text("${lancamento.categoria} • ${lancamento.data}", style = MaterialTheme.typography.bodySmall)

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