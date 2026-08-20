package com.pessoal.controlefinanceiro.ui.mensalidade

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pessoal.controlefinanceiro.data.SheetsRepository
import com.pessoal.controlefinanceiro.model.Mensalidade
import com.pessoal.controlefinanceiro.ui.theme.CorDividerPadrao
import com.pessoal.controlefinanceiro.ui.theme.ElevacaoCardPadrao
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// Opções fixas de forma de pagamento (mesmas da Tela de Lançamento)
private val FORMAS_PAGAMENTO = listOf("Dinheiro", "Pix", "Boleto", "Débito", "Crédito")

// Nomes completos dos meses, usados nos rótulos de período e no seletor "a partir de qual mês"
private val NOMES_MESES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

// Altura máxima dos menus suspensos: 6 itens visíveis
private val ALTURA_MAXIMA_DROPDOWN = 305.dp

/**
 * Tela de Mensalidades — cadastra um valor fixo mensal (ex: academia, streaming)
 * que se repete automaticamente do mês atual até dezembro do ano corrente,
 * reaproveitando o mesmo mecanismo de parcelas usado nas compras parceladas.
 * Mostra também a lista de mensalidades ativas, com opção de editar (a partir
 * de qual mês vale a alteração) e excluir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensalidadeScreen(repository: SheetsRepository) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    val scope = rememberCoroutineScope()

    // Controla se o formulário "Nova Mensalidade" está expandido
    var formularioExpandido by remember { mutableStateOf(false) }

    // Campos do formulário de nova mensalidade
    var nome by remember { mutableStateOf("") }
    var valorDigitos by remember { mutableStateOf("") }
    var valorCampo by remember { mutableStateOf(TextFieldValue(formatarValorMonetario(""))) }
    var formaPagamentoExpandida by remember { mutableStateOf(false) }
    var formaPagamentoSelecionada by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var observacoes by remember { mutableStateOf("") }

    // Lista de mensalidades ativas
    var mensalidades by remember { mutableStateOf<List<Mensalidade>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var mensalidadeParaEditar by remember { mutableStateOf<Mensalidade?>(null) }
    var mensalidadeParaExcluir by remember { mutableStateOf<Mensalidade?>(null) }

    suspend fun recarregar() {
        carregando = true
        try {
            mensalidades = repository.listarMensalidadesAtivas()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: "Erro ao carregar mensalidades.",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            carregando = false
        }
    }

    fun moverMensalidade(indice: Int, novoIndice: Int) {
        if (novoIndice < 0 || novoIndice >= mensalidades.size) return
        val novaLista = mensalidades.toMutableList()
        val item = novaLista.removeAt(indice)
        novaLista.add(novoIndice, item)
        mensalidades = novaLista // atualiza a tela na hora
        scope.launch {
            repository.atualizarOrdemMensalidades(novaLista) // persiste na planilha
        }
    }

    LaunchedEffect(Unit) { recarregar() }

    // Box externo: permite que o indicador de carregamento fique sobreposto
    // (overlay) e centralizado na tela inteira, em vez de centralizado só
    // no espaço que sobra depois do cabeçalho/formulário dentro da Column.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Mensalidades", style = MaterialTheme.typography.headlineSmall)

            // ── Formulário de nova mensalidade — clicável pra abrir/fechar ──
            // Padding vertical de 8dp (não 16) deixa o cabeçalho fechado com
            // altura próxima à de um campo de filtro (Mês/Ano/Pagamento),
            // igual às Telas de Resumo Mensal e Anual.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { formularioExpandido = !formularioExpandido },
                elevation = CardDefaults.cardElevation(defaultElevation = ElevacaoCardPadrao)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Nova Mensalidade",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { formularioExpandido = !formularioExpandido }) {
                            Icon(
                                imageVector = if (formularioExpandido) Icons.Filled.ArrowCircleUp else Icons.Filled.ArrowCircleDown,
                                contentDescription = if (formularioExpandido) "Recolher formulário" else "Expandir formulário"
                            )
                        }
                    }

                    if (formularioExpandido) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = nome,
                            onValueChange = { nome = it.replace("\n", "") },
                            label = { Text("Descrição") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = observacoes,
                            onValueChange = { observacoes = it.replace("\n", "") },
                            label = { Text("Observações (opcional)") },
                            singleLine = false,
                            minLines = 1,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valorCampo,
                            onValueChange = { novoValor ->
                                val apenasDigitos = novoValor.text.filter { it.isDigit() }.take(9)
                                valorDigitos = apenasDigitos
                                val textoFormatado = formatarValorMonetario(apenasDigitos)
                                valorCampo = TextFieldValue(
                                    text = textoFormatado,
                                    selection = TextRange(textoFormatado.length)
                                )
                            },
                            label = { Text("Valor Mensal") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = formaPagamentoExpandida,
                            onExpandedChange = { formaPagamentoExpandida = it }
                        ) {
                            OutlinedTextField(
                                value = formaPagamentoSelecionada,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Forma de Pagamento") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formaPagamentoExpandida) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = formaPagamentoExpandida,
                                onDismissRequest = { formaPagamentoExpandida = false },
                                modifier = Modifier.heightIn(max = ALTURA_MAXIMA_DROPDOWN)
                            ) {
                                FORMAS_PAGAMENTO.forEach { forma ->
                                    DropdownMenuItem(text = { Text(forma) }, onClick = {
                                        formaPagamentoSelecionada = forma
                                        formaPagamentoExpandida = false
                                    })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            enabled = !salvando && nome.isNotBlank() &&
                                    formaPagamentoSelecionada.isNotBlank() && valorDigitos.isNotBlank(),
                            onClick = {
                                salvando = true
                                scope.launch {
                                    try {
                                        val valorMensal = (valorDigitos.toLongOrNull() ?: 0L) / 100.0
                                        repository.criarMensalidade(nome, valorMensal, formaPagamentoSelecionada, observacoes)
                                        Toast.makeText(context, "Mensalidade salva com sucesso!", Toast.LENGTH_SHORT).show()
                                        nome = ""
                                        valorDigitos = ""
                                        valorCampo = TextFieldValue(formatarValorMonetario(""))
                                        formaPagamentoSelecionada = ""
                                        observacoes = ""
                                        recarregar()
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        salvando = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (salvando) "Salvando..." else "Salvar Mensalidade")
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = CorDividerPadrao)

            Text(
                "Mensalidades Ativas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // O caso "carregando" não desenha nada aqui — o indicador é mostrado
            // como overlay centralizado na tela, fora desta Column (ver abaixo).
            when {
                carregando -> {}
                mensalidades.isEmpty() -> {
                    Text("Nenhuma mensalidade ativa no momento.")
                }

                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(
                            mensalidades,
                            key = { _, m -> m.idMensalidade }) { indice, mensalidade ->
                            ItemMensalidade(
                                mensalidade = mensalidade,
                                formatoMoeda = formatoMoeda,
                                podeSubir = indice > 0,
                                podeDescer = indice < mensalidades.size - 1,
                                aoEditar = { mensalidadeParaEditar = mensalidade },
                                aoExcluir = { mensalidadeParaExcluir = mensalidade },
                                aoMoverParaCima = { moverMensalidade(indice, indice - 1) },
                                aoMoverParaBaixo = { moverMensalidade(indice, indice + 1) }
                            )
                        }
                    }
                }
            }
        }

        // Indicador de carregamento sobreposto (overlay) e centralizado na
        // tela inteira — não afetado pela altura do cabeçalho/formulário acima.
        if (carregando) {
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // Diálogo de edição — permite escolher a partir de qual mês vale a mudança
    mensalidadeParaEditar?.let { mensalidade ->
        DialogoEditarMensalidade(
            mensalidade = mensalidade,
            onDismiss = { mensalidadeParaEditar = null },
            onConfirmar = { novoNome, novoValor, novaForma, novasObservacoes, mesEdicao, anoEdicao ->
                scope.launch {
                    try {
                        repository.editarMensalidade(
                            mensalidade,
                            novoNome,
                            novoValor,
                            novaForma,
                            novasObservacoes,
                            mesEdicao,
                            anoEdicao
                        )
                        Toast.makeText(
                            context,
                            "Mensalidade atualizada com sucesso!",
                            Toast.LENGTH_SHORT
                        ).show()
                        mensalidadeParaEditar = null
                        recarregar()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Erro ao atualizar: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    // Diálogo de confirmação de exclusão
    mensalidadeParaExcluir?.let { mensalidade ->
        AlertDialog(
            onDismissRequest = { mensalidadeParaExcluir = null },
            title = { Text("Remover mensalidade?") },
            text = {
                Text(
                    "\"${mensalidade.nome}\" deixará de ser lançada a partir de agora. " +
                            "Os meses já passados continuam registrados normalmente."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            repository.excluirMensalidade(mensalidade)
                            mensalidadeParaExcluir = null
                            Toast.makeText(context, "Mensalidade removida.", Toast.LENGTH_SHORT)
                                .show()
                            recarregar()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Erro ao remover: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { mensalidadeParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }
}

/** Card de uma mensalidade ativa, com período de vigência, botões de
 *  editar/remover e setas pra reordenar a posição na lista. */
@Composable
private fun ItemMensalidade(
    mensalidade: Mensalidade,
    formatoMoeda: NumberFormat,
    podeSubir: Boolean,
    podeDescer: Boolean,
    aoEditar: () -> Unit,
    aoExcluir: () -> Unit,
    aoMoverParaCima: () -> Unit,
    aoMoverParaBaixo: () -> Unit
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
            Column {
                IconButton(onClick = aoMoverParaCima, enabled = podeSubir) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Mover para cima")
                }
                IconButton(onClick = aoMoverParaBaixo, enabled = podeDescer) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Mover para baixo")
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(mensalidade.nome, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatoMoeda.format(mensalidade.valorMensal)}/mês • ${mensalidade.formaPagamento}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mensalidade.observacoes.isNotBlank()) {
                    Text(
                        mensalidade.observacoes,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "Vigente: ${formatarPeriodo(mensalidade.mesInicioIndex, mensalidade.mesFimIndex)}",
                    style = MaterialTheme.typography.bodySmall
                )
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

/**
 * Diálogo de edição de mensalidade: permite trocar nome/valor/forma de
 * pagamento/observações e escolher a partir de qual mês essa mudança passa
 * a valer (do mês atual até dezembro do ano vigente).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoEditarMensalidade(
    mensalidade: Mensalidade,
    onDismiss: () -> Unit,
    onConfirmar: (novoNome: String, novoValor: Double, novaForma: String, novasObservacoes: String, mesEdicao: Int, anoEdicao: Int) -> Unit
) {
    var descricao by remember { mutableStateOf(mensalidade.nome) }
    var valorDigitos by remember { mutableStateOf((mensalidade.valorMensal * 100).toLong().toString()) }
    var valorCampo by remember {
        mutableStateOf(
            TextFieldValue(
                formatarValorMonetario(valorDigitos),
                selection = TextRange(formatarValorMonetario(valorDigitos).length)
            )
        )
    }
    var observacoes by remember { mutableStateOf(mensalidade.observacoes) }
    var formaPagamentoExpandida by remember { mutableStateOf(false) }
    var formaPagamentoSelecionada by remember { mutableStateOf(mensalidade.formaPagamento) }

    // Opções de "a partir de qual mês": do mês atual (não dá pra editar o passado)
    // até dezembro do ano em que o segmento termina
    val calendarioAtual = remember { Calendar.getInstance() }
    val indiceMesAtual = calendarioAtual.get(Calendar.YEAR) * 12 + (calendarioAtual.get(Calendar.MONTH) + 1)
    val indiceInicial = maxOf(indiceMesAtual, mensalidade.mesInicioIndex)

    val opcoesMes = remember {
        (indiceInicial..mensalidade.mesFimIndex).map { indice ->
            val ano = (indice - 1) / 12
            val mes = indice - ano * 12
            Triple(mes, ano, "${NOMES_MESES[mes - 1]}/$ano")
        }
    }
    var opcaoSelecionada by remember { mutableStateOf(opcoesMes.firstOrNull()) }
    var mesEdicaoExpandido by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Mensalidade") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it.replace("\n", "") },
                    label = { Text("Descrição") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = observacoes,
                    onValueChange = { observacoes = it.replace("\n", "") },
                    label = { Text("Observações (opcional)") },
                    singleLine = false,
                    minLines = 1,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = valorCampo,
                    onValueChange = { novoValor ->
                        val apenasDigitos = novoValor.text.filter { it.isDigit() }.take(9)
                        valorDigitos = apenasDigitos
                        val textoFormatado = formatarValorMonetario(apenasDigitos)
                        valorCampo = TextFieldValue(text = textoFormatado, selection = TextRange(textoFormatado.length))
                    },
                    label = { Text("Valor Mensal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = formaPagamentoExpandida,
                    onExpandedChange = { formaPagamentoExpandida = it }
                ) {
                    OutlinedTextField(
                        value = formaPagamentoSelecionada,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Forma de Pagamento") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formaPagamentoExpandida) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = formaPagamentoExpandida,
                        onDismissRequest = { formaPagamentoExpandida = false },
                        modifier = Modifier.heightIn(max = ALTURA_MAXIMA_DROPDOWN)
                    ) {
                        FORMAS_PAGAMENTO.forEach { forma ->
                            DropdownMenuItem(text = { Text(forma) }, onClick = {
                                formaPagamentoSelecionada = forma
                                formaPagamentoExpandida = false
                            })
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = mesEdicaoExpandido,
                    onExpandedChange = { mesEdicaoExpandido = it }
                ) {
                    OutlinedTextField(
                        value = opcaoSelecionada?.third.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("A partir de") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mesEdicaoExpandido) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = mesEdicaoExpandido,
                        onDismissRequest = { mesEdicaoExpandido = false },
                        modifier = Modifier.heightIn(max = ALTURA_MAXIMA_DROPDOWN)
                    ) {
                        opcoesMes.forEach { opcao ->
                            DropdownMenuItem(text = { Text(opcao.third) }, onClick = {
                                opcaoSelecionada = opcao
                                mesEdicaoExpandido = false
                            })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = descricao.isNotBlank() && formaPagamentoSelecionada.isNotBlank() &&
                        valorDigitos.isNotBlank() && opcaoSelecionada != null,
                onClick = {
                    val (mes, ano, _) = opcaoSelecionada!!
                    val valorMensal = (valorDigitos.toLongOrNull() ?: 0L) / 100.0
                    onConfirmar(descricao, valorMensal, formaPagamentoSelecionada, observacoes, mes, ano)
                }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Formata "início-fim" de um período (índices ano*12+mês) como "Agosto/2026 até Dezembro/2026". */
private fun formatarPeriodo(mesInicioIndex: Int, mesFimIndex: Int): String {
    val anoInicio = (mesInicioIndex - 1) / 12
    val mesInicio = mesInicioIndex - anoInicio * 12
    val anoFim = (mesFimIndex - 1) / 12
    val mesFim = mesFimIndex - anoFim * 12
    return "${NOMES_MESES[mesInicio - 1]}/$anoInicio até ${NOMES_MESES[mesFim - 1]}/$anoFim"
}

/** Formata dígitos puros (representando centavos) como "R$ 1.234,56". */
private fun formatarValorMonetario(digitos: String): String {
    val valor = if (digitos.isEmpty()) 0L else digitos.toLong()
    val reais = valor / 100
    val centavos = valor % 100
    val formatador = NumberFormat.getInstance(Locale("pt", "BR"))
    return "R$ ${formatador.format(reais)},${centavos.toString().padStart(2, '0')}"
}