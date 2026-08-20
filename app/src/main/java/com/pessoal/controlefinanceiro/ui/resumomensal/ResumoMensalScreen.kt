package com.pessoal.controlefinanceiro.ui.resumomensal

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.model.Lancamento
import com.pessoal.controlefinanceiro.model.Mensalidade
import com.pessoal.controlefinanceiro.ui.mensalidade.DialogoEditarMensalidade
import com.pessoal.controlefinanceiro.ui.theme.CorDividerPadrao
import com.pessoal.controlefinanceiro.ui.theme.ElevacaoCardPadrao
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

// Altura máxima dos menus suspensos: 6 itens visíveis
private val ALTURA_MAXIMA_DROPDOWN = 305.dp

/**
 * Tela de Lista — filtra os lançamentos por Mês/Ano e, opcionalmente, por
 * Forma de Pagamento (os 3 filtros ficam numa única linha). Lançamentos
 * parcelados aparecem em todos os meses cobertos pela parcela (usando as
 * colunas auxiliares K "Mês Início" e L "Mês Fim" da planilha). Mensalidades
 * aparecem sempre no início da lista, antes dos demais lançamentos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumoMensalScreen(repository: SheetsRepository, aoEditar: (Int) -> Unit) {
    val context = LocalContext.current
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    var todosLancamentos by remember { mutableStateOf<List<Lancamento>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var lancamentoParaExcluir by remember { mutableStateOf<Lancamento?>(null) }
    var mensalidadeParaEditar by remember { mutableStateOf<Mensalidade?>(null) }

    val calendarioAtual = remember { Calendar.getInstance() }
    var mesSelecionado by remember { mutableStateOf(MESES[calendarioAtual.get(Calendar.MONTH)]) }
    var anoSelecionado by remember { mutableStateOf(calendarioAtual.get(Calendar.YEAR)) }
    var anos by remember { mutableStateOf<List<Int>>(emptyList()) }
    var mesExpandido by remember { mutableStateOf(false) }
    var anoExpandido by remember { mutableStateOf(false) }

    // Filtro por forma de pagamento — "Todos" por padrão
    var formaPagamentoFiltro by remember { mutableStateOf("Todos") }
    var formaPagamentoFiltroExpandido by remember { mutableStateOf(false) }

    // Busca por texto livre — campo fica escondido até clicar na lupa
    var buscaExpandida by remember { mutableStateOf(false) }
    var textoBusca by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    suspend fun recarregar() {
        carregando = true
        try {
            todosLancamentos = repository.listarLancamentos()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Erro ao carregar lançamentos.", Toast.LENGTH_LONG).show()
        } finally {
            carregando = false
        }
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
    // se escolhido, também pela forma de pagamento. Mensalidades (categoria
    // "Mensalidade") vêm sempre primeiro, na ordem definida na Tela de
    // Mensalidades; os demais lançamentos seguem a ordem de lançamento.
    val lancamentosFiltrados = remember(todosLancamentos, mesSelecionado, anoSelecionado, formaPagamentoFiltro, textoBusca) {
        val termoBusca = textoBusca.trim().lowercase()
        todosLancamentos
            .filter { indiceMesConsultado in it.mesInicioIndex..it.mesFimIndex }
            .filter { formaPagamentoFiltro == "Todos" || it.formaPagamento == formaPagamentoFiltro }
            .filter { lancamento ->
                if (termoBusca.isBlank()) return@filter true
                // Valor comparado: parcela (quando parcelado, é o valor exibido no card) e total
                val valoresParaComparar = listOf(
                    formatoMoeda.format(lancamento.valorTotal),
                    formatoMoeda.format(lancamento.valorParcela)
                )
                (listOf(
                    lancamento.descricao,
                    lancamento.observacoes,
                    lancamento.categoria,
                    lancamento.data,
                    lancamento.formaPagamento
                ) + valoresParaComparar).any { it.lowercase().contains(termoBusca) }
            }
            .sortedWith(
                compareByDescending<Lancamento> { it.categoria == "Mensalidade" }
                    .thenBy { if (it.categoria == "Mensalidade") (it.ordemMensalidade ?: Int.MAX_VALUE) else it.linha }
            )
    }

    // Box externo: permite que o indicador de carregamento fique sobreposto
    // (overlay) e centralizado na tela inteira, em vez de centralizado só
    // no espaço que sobra depois do cabeçalho/filtros dentro da Column.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título com os ícones de filtro (forma de pagamento) e busca alinhados
            // à direita, no topo
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Resumo Mensal", style = MaterialTheme.typography.headlineSmall)

                CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Ícone de filtro por forma de pagamento
                        Box {
                            IconButton(
                                onClick = { formaPagamentoFiltroExpandido = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.FilterAlt,
                                    contentDescription = "Filtrar por forma de pagamento",
                                    tint = if (formaPagamentoFiltro != "Todos")
                                        MaterialTheme.colorScheme.primary
                                    else
                                        LocalContentColor.current
                                )
                            }
                            DropdownMenu(
                                expanded = formaPagamentoFiltroExpandido,
                                onDismissRequest = { formaPagamentoFiltroExpandido = false },
                                modifier = Modifier.heightIn(max = ALTURA_MAXIMA_DROPDOWN)
                            ) {
                                FORMAS_PAGAMENTO_FILTRO.forEach { forma ->
                                    DropdownMenuItem(
                                        text = { Text(forma) },
                                        onClick = {
                                            formaPagamentoFiltro = forma
                                            formaPagamentoFiltroExpandido = false
                                        },
                                        trailingIcon = {
                                            if (forma == formaPagamentoFiltro) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Ícone de busca
                        IconButton(
                            onClick = {
                                buscaExpandida = !buscaExpandida
                                if (!buscaExpandida) textoBusca = ""
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Buscar lançamento",
                                tint = if (buscaExpandida) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                }
            }

            // Filtros Mês/Ano + campo de busca agrupados num único bloco — assim,
            // quando a busca está fechada, ela não soma espaçamento extra entre
            // os filtros e a linha divisória (mantendo a mesma altura das
            // telas de Mensalidades e Resumo Anual, que não têm esse campo)
            Column(verticalArrangement = Arrangement.spacedBy(if (buscaExpandida) 12.dp else 0.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExposedDropdownMenuBox(
                        expanded = mesExpandido,
                        onExpandedChange = { mesExpandido = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = mesSelecionado.first,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
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
                            singleLine = true,
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

                // Campo de busca — some/aparece ao clicar na lupa. Procura por nome
                // do item, observação, categoria, data, valor ou forma de pagamento,
                // dentro dos lançamentos já filtrados por Mês/Ano/Forma de Pagamento.
                AnimatedVisibility(visible = buscaExpandida) {
                    OutlinedTextField(
                        value = textoBusca,
                        onValueChange = { textoBusca = it },
                        singleLine = true,
                        placeholder = { Text("Buscar lançamento") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (textoBusca.isNotEmpty()) {
                                IconButton(onClick = { textoBusca = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = CorDividerPadrao)

            // O caso "carregando" não desenha nada aqui — o indicador é mostrado
            // como overlay centralizado na tela, fora desta Column (ver abaixo).
            when {
                carregando -> {}
                lancamentosFiltrados.isEmpty() -> {
                    Text(
                        if (textoBusca.isNotBlank())
                            "Nenhum lançamento encontrado para \"$textoBusca\" em ${mesSelecionado.first} de $anoSelecionado."
                        else
                            "Nenhum lançamento em ${mesSelecionado.first} de $anoSelecionado."
                    )
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(lancamentosFiltrados, key = { it.linha }) { lancamento ->
                            ItemLancamento(
                                lancamento = lancamento,
                                mesSelecionado = mesSelecionado,
                                anoSelecionado = anoSelecionado,
                                formatoMoeda = formatoMoeda,
                                aoEditar = {
                                    // Mensalidades abrem o diálogo de edição próprio delas
                                    // (com o seletor "a partir de qual mês"); lançamentos
                                    // normais navegam pra Tela de Lançamento, como sempre.
                                    if (lancamento.categoria == "Mensalidade" && lancamento.idMensalidade != null) {
                                        mensalidadeParaEditar = Mensalidade(
                                            idMensalidade = lancamento.idMensalidade,
                                            linha = lancamento.linha,
                                            nome = lancamento.descricao,
                                            valorMensal = lancamento.valorParcela,
                                            formaPagamento = lancamento.formaPagamento,
                                            observacoes = lancamento.observacoes,
                                            ordem = lancamento.ordemMensalidade ?: 0,
                                            mesInicioIndex = lancamento.mesInicioIndex,
                                            mesFimIndex = lancamento.mesFimIndex
                                        )
                                    } else {
                                        aoEditar(lancamento.linha)
                                    }
                                },
                                aoExcluir = { lancamentoParaExcluir = lancamento }
                            )
                        }
                    }
                }
            }
        }

        // Indicador de carregamento sobreposto (overlay) e centralizado na
        // tela inteira — não afetado pela altura do cabeçalho/filtros acima.
        if (carregando) {
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // Diálogo de edição de mensalidade — mesmo componente usado na Tela de Mensalidades
    mensalidadeParaEditar?.let { mensalidade ->
        DialogoEditarMensalidade(
            mensalidade = mensalidade,
            onDismiss = { mensalidadeParaEditar = null },
            onConfirmar = { novoNome, novoValor, novaForma, novasObservacoes, mesEdicao, anoEdicao ->
                scope.launch {
                    try {
                        repository.editarMensalidade(
                            mensalidade, novoNome, novoValor, novaForma, novasObservacoes, mesEdicao, anoEdicao
                        )
                        Toast.makeText(context, "Mensalidade atualizada com sucesso!", Toast.LENGTH_SHORT).show()
                        mensalidadeParaEditar = null
                        recarregar()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro ao atualizar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Diálogo de confirmação de exclusão — texto e ação mudam conforme o item
    // seja uma mensalidade (usa excluirMensalidade, que também limpa o ID da
    // coluna N) ou um lançamento comum (usa excluirLancamento, avisando quando
    // parcelado).
    lancamentoParaExcluir?.let { lancamento ->
        val ehMensalidade = lancamento.categoria == "Mensalidade" && lancamento.idMensalidade != null

        AlertDialog(
            onDismissRequest = { lancamentoParaExcluir = null },
            title = { Text(if (ehMensalidade) "Remover mensalidade?" else "Remover lançamento?") },
            text = {
                when {
                    ehMensalidade -> Text(
                        "\"${lancamento.descricao}\" deixará de ser lançada a partir de agora. " +
                                "Os meses já passados continuam registrados normalmente."
                    )
                    lancamento.qtdParcelas > 1 -> Text(
                        "\"${lancamento.descricao}\" está parcelado em ${lancamento.qtdParcelas}x. " +
                                "Remover vai excluir TODAS as ${lancamento.qtdParcelas} parcelas, não só a deste mês. " +
                                "Essa ação não pode ser desfeita."
                    )
                    else -> Text("\"${lancamento.descricao}\" será removido. Essa ação não pode ser desfeita.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            if (ehMensalidade) {
                                repository.excluirMensalidade(
                                    Mensalidade(
                                        idMensalidade = lancamento.idMensalidade!!,
                                        linha = lancamento.linha,
                                        nome = lancamento.descricao,
                                        valorMensal = lancamento.valorParcela,
                                        formaPagamento = lancamento.formaPagamento,
                                        observacoes = lancamento.observacoes,
                                        mesInicioIndex = lancamento.mesInicioIndex,
                                        mesFimIndex = lancamento.mesFimIndex,
                                        ordem = lancamento.ordemMensalidade ?: 0
                                    )
                                )
                            } else {
                                repository.excluirLancamento(lancamento.linha)
                            }
                            lancamentoParaExcluir = null
                            recarregar()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(context, "Erro ao remover: ${e.message}", Toast.LENGTH_LONG).show()
                        }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = ElevacaoCardPadrao)
    ) {
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
                // Sempre reserva a linha da observação (mesmo vazia), pra todos os
                // cards terem a mesma altura — quando não há observação, o texto
                // fica transparente mas ocupa o espaço.
                Text(
                    lancamento.observacoes.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (lancamento.observacoes.isNotBlank())
                        LocalContentColor.current
                    else
                        Color.Transparent
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