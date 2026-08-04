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
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.model.Lancamento
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

private val MESES = listOf(
    "Janeiro" to "jan", "Fevereiro" to "fev", "Março" to "mar", "Abril" to "abr",
    "Maio" to "mai", "Junho" to "jun", "Julho" to "jul", "Agosto" to "ago",
    "Setembro" to "set", "Outubro" to "out", "Novembro" to "nov", "Dezembro" to "dez"
)

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
    val anos = remember { (2024..2030).toList() }
    var mesExpandido by remember { mutableStateOf(false) }
    var anoExpandido by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    suspend fun recarregar() {
        carregando = true
        todosLancamentos = repository.listarLancamentos()
        carregando = false
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scope.launch { recarregar() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mesAnoFiltro = "${mesSelecionado.second}/$anoSelecionado"
    val lancamentosFiltrados = remember(todosLancamentos, mesSelecionado, anoSelecionado) {
        val numeroDoMes = MESES.indexOf(mesSelecionado) + 1
        val indiceMesConsultado = anoSelecionado * 12 + numeroDoMes

        todosLancamentos
            .filter { indiceMesConsultado in it.mesInicioIndex..it.mesFimIndex }
            .sortedBy { it.linha }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Lançamentos", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))

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

        if (carregando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (lancamentosFiltrados.isEmpty()) {
            Text("Nenhum lançamento em ${mesSelecionado.first} de $anoSelecionado.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lancamentosFiltrados, key = { it.linha }) { lanc ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lanc.descricao, style = MaterialTheme.typography.titleMedium)
                                Text("${lanc.categoria} • ${lanc.data}", style = MaterialTheme.typography.bodySmall)

                                val indiceMesConsultado = anoSelecionado * 12 + (MESES.indexOf(mesSelecionado) + 1)
                                val numeroDaParcela = indiceMesConsultado - lanc.mesInicioIndex + 1

                                if (lanc.qtdParcelas > 1) {
                                    Text(
                                        "${formatoMoeda.format(lanc.valorParcela)}  (parcela $numeroDaParcela/${lanc.qtdParcelas})",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (lanc.tipo == "Entrada") Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                } else {
                                    Text(
                                        formatoMoeda.format(lanc.valorTotal),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (lanc.tipo == "Entrada") Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                            }
                            IconButton(onClick = { aoEditar(lanc.linha) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar")
                            }
                            IconButton(onClick = { lancamentoParaExcluir = lanc }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover")
                            }
                        }
                    }
                }
            }
        }
    }

    lancamentoParaExcluir?.let { lanc ->
        AlertDialog(
            onDismissRequest = { lancamentoParaExcluir = null },
            title = { Text("Remover lançamento?") },
            text = {
                if (lanc.qtdParcelas > 1) {
                    Text(
                        "\"${lanc.descricao}\" está parcelado em ${lanc.qtdParcelas}x. " +
                                "Remover vai excluir TODAS as ${lanc.qtdParcelas} parcelas, não só a deste mês. " +
                                "Essa ação não pode ser desfeita."
                    )
                } else {
                    Text("\"${lanc.descricao}\" será removido. Essa ação não pode ser desfeita.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.excluirLancamento(lanc.linha)
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