package com.pessoal.controlefinanceiro.ui.lancamento

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pessoal.controlefinanceiro.data.SheetsRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Nome completo exibido na tela ↔ abreviação gravada na planilha (jan, fev...)
private val MESES = listOf(
    "Janeiro" to "jan", "Fevereiro" to "fev", "Março" to "mar", "Abril" to "abr",
    "Maio" to "mai", "Junho" to "jun", "Julho" to "jul", "Agosto" to "ago",
    "Setembro" to "set", "Outubro" to "out", "Novembro" to "nov", "Dezembro" to "dez"
)

// Opções fixas de forma de pagamento
private val FORMAS_PAGAMENTO = listOf("Dinheiro", "Pix", "Boleto", "Débito", "Crédito")

// Altura máxima dos menus suspensos: ~5 itens visíveis (48dp cada), com scroll pro resto
private val ALTURA_MAXIMA_DROPDOWN = 240.dp

/**
 * Tela de Lançamento — funciona em dois modos:
 * - Criar (linhaEdicao == null): formulário limpo, data = hoje, permanece na tela após salvar.
 * - Editar (linhaEdicao != null): carrega os dados da linha informada e, ao salvar,
 *   chama aoSalvarComSucesso() (usado pra voltar à Tela de Lista).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LancamentoScreen(
    repository: SheetsRepository,
    linhaEdicao: Int? = null,
    aoSalvarComSucesso: () -> Unit = {}
) {
    val modoEdicao = linhaEdicao != null
    val context = LocalContext.current

    // Campos do formulário
    var descricao by remember { mutableStateOf("") }
    var valorDigitos by remember { mutableStateOf("") } // só dígitos → representa centavos
    var valorCampo by remember { mutableStateOf(TextFieldValue(formatarValorMonetario(""))) }
    var qtdParcelas by remember { mutableStateOf("") }
    var observacoes by remember { mutableStateOf("") }

    var categoriaExpandida by remember { mutableStateOf(false) }
    var categoriaSelecionada by remember { mutableStateOf("") }
    var categorias by remember { mutableStateOf<List<String>>(emptyList()) }

    var formaPagamentoExpandida by remember { mutableStateOf(false) }
    var formaPagamentoSelecionada by remember { mutableStateOf("") }

    // Data de lançamento: sempre a data atual, não é editável nem exibida
    val dataAtual = remember { Date() }
    val formatoData = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    var mesExpandido by remember { mutableStateOf(false) }
    var anoExpandido by remember { mutableStateOf(false) }
    val calendarioAtual = remember { Calendar.getInstance() }
    var mesSelecionado by remember { mutableStateOf(MESES[calendarioAtual.get(Calendar.MONTH)]) }
    var anoSelecionado by remember { mutableStateOf(calendarioAtual.get(Calendar.YEAR)) }
    var anos by remember { mutableStateOf<List<Int>>(emptyList()) } // só anos com aba Resumo

    var carregandoEdicao by remember { mutableStateOf(modoEdicao) }
    var salvando by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Carrega categorias, anos disponíveis e, se for edição, os dados do lançamento
    LaunchedEffect(Unit) {
        categorias = repository.buscarCategorias()
        anos = repository.buscarAnosDisponiveis()

        if (linhaEdicao != null) {
            val lancamento = repository.buscarLancamentoPorLinha(linhaEdicao)
            if (lancamento != null) {
                descricao = lancamento.descricao
                categoriaSelecionada = lancamento.categoria
                valorDigitos = (lancamento.valorTotal * 100).toLong().toString()
                valorCampo = TextFieldValue(
                    text = formatarValorMonetario(valorDigitos),
                    selection = TextRange(formatarValorMonetario(valorDigitos).length)
                )
                formaPagamentoSelecionada = lancamento.formaPagamento
                qtdParcelas = if (lancamento.qtdParcelas > 1) lancamento.qtdParcelas.toString() else ""
                observacoes = lancamento.observacoes
                mesSelecionado = MESES.getOrElse(lancamento.mesNumero - 1) { mesSelecionado }
                anoSelecionado = lancamento.anoNumero
            }
            carregandoEdicao = false
        }
    }

    // Enquanto busca os dados de edição, mostra só um loading
    if (carregandoEdicao) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()          // não fica colado na barra de notificação
            .imePadding()                 // ajusta o conteúdo quando o teclado abre
            .pointerInput(Unit) {
                // tocar fora de qualquer campo tira o foco e fecha o teclado
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (modoEdicao) "Editar Lançamento" else "Novo Lançamento",
            style = MaterialTheme.typography.headlineSmall
        )

        // Descrição — sem quebra de linha, primeira letra maiúscula automática
        OutlinedTextField(
            value = descricao,
            onValueChange = { descricao = it.replace("\n", "") },
            label = { Text("Descrição") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Categoria — lista fixa vinda da aba "Listas"
        ExposedDropdownMenuBox(
            expanded = categoriaExpandida,
            onExpandedChange = { categoriaExpandida = it }
        ) {
            OutlinedTextField(
                value = categoriaSelecionada,
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoria") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoriaExpandida) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = categoriaExpandida,
                onDismissRequest = { categoriaExpandida = false },
                modifier = Modifier.heightIn(max = ALTURA_MAXIMA_DROPDOWN)
            ) {
                categorias.forEach { cat ->
                    DropdownMenuItem(text = { Text(cat) }, onClick = {
                        categoriaSelecionada = cat
                        categoriaExpandida = false
                    })
                }
            }
        }

        // Valor Total — máscara estilo bancário: digita da direita pra esquerda,
        // cursor sempre fixado no final (TextFieldValue controla a seleção manualmente)
        OutlinedTextField(
            value = valorCampo,
            onValueChange = { novoValor ->
                val apenasDigitos = novoValor.text.filter { it.isDigit() }.take(9)
                valorDigitos = apenasDigitos
                val textoFormatado = formatarValorMonetario(apenasDigitos)
                valorCampo = TextFieldValue(text = textoFormatado, selection = TextRange(textoFormatado.length))
            },
            label = { Text("Valor Total") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        // Forma de Pagamento — lista fixa (Dinheiro, Pix, Boleto, Débito, Crédito)
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

        // Qtd. Parcelas — opcional; se vazio, a planilha assume 1 parcela
        OutlinedTextField(
            value = qtdParcelas,
            onValueChange = { qtdParcelas = it.filter { c -> c.isDigit() } },
            label = { Text("Qtd. Parcelas (opcional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        // Mês/Ano — seleção por dropdown (nunca digitado à mão)
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

        // Observações — cresce com o texto (sem quebra de linha manual)
        OutlinedTextField(
            value = observacoes,
            onValueChange = { observacoes = it.replace("\n", "") },
            label = { Text("Observações") },
            singleLine = false,
            minLines = 1,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Botão salvar — comportamento muda conforme o modo (criar vs editar)
        Button(
            enabled = !salvando && categoriaSelecionada.isNotBlank() &&
                    formaPagamentoSelecionada.isNotBlank() && valorDigitos.isNotBlank(),
            onClick = {
                salvando = true
                scope.launch {
                    try {
                        val valorTotal = (valorDigitos.toLongOrNull() ?: 0L) / 100.0
                        val mesAno = "${mesSelecionado.second}/$anoSelecionado"

                        if (modoEdicao && linhaEdicao != null) {
                            repository.atualizarLancamento(
                                linha = linhaEdicao,
                                descricao = descricao,
                                categoria = categoriaSelecionada,
                                valorTotal = valorTotal,
                                formaPagamento = formaPagamentoSelecionada,
                                qtdParcelas = qtdParcelas.toIntOrNull(),
                                mesAno = mesAno,
                                observacoes = observacoes
                            )
                            Toast.makeText(context, "Lançamento atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                            aoSalvarComSucesso() // volta pra Lista, no caso de edição
                        } else {
                            val linha = repository.proximaLinhaVazia()
                            repository.salvarLancamento(
                                linha = linha,
                                data = formatoData.format(dataAtual),
                                descricao = descricao,
                                categoria = categoriaSelecionada,
                                valorTotal = valorTotal,
                                formaPagamento = formaPagamentoSelecionada,
                                qtdParcelas = qtdParcelas.toIntOrNull(),
                                mesAno = mesAno,
                                observacoes = observacoes
                            )
                            Toast.makeText(context, "Lançamento salvo com sucesso!", Toast.LENGTH_SHORT).show()
                            // limpa o formulário pra permitir lançar o próximo item
                            descricao = ""
                            valorDigitos = ""
                            valorCampo = TextFieldValue(formatarValorMonetario(""))
                            formaPagamentoSelecionada = ""
                            qtdParcelas = ""
                            observacoes = ""
                            categoriaSelecionada = ""
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        salvando = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (salvando) "Salvando..." else if (modoEdicao) "Salvar Alterações" else "Salvar Lançamento")
        }
    }
}

/** Formata dígitos puros (representando centavos) como "R$ 1.234,56". */
private fun formatarValorMonetario(digitos: String): String {
    val valor = if (digitos.isEmpty()) 0L else digitos.toLong()
    val reais = valor / 100
    val centavos = valor % 100
    val formatador = NumberFormat.getInstance(Locale("pt", "BR"))
    return "R$ ${formatador.format(reais)},${centavos.toString().padStart(2, '0')}"
}