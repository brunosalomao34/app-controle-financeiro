package com.pessoal.controlefinanceiro.ui.lancamento

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pessoal.controlefinanceiro.data.SheetsRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue

private val MESES = listOf(
    "Janeiro" to "jan", "Fevereiro" to "fev", "Março" to "mar", "Abril" to "abr",
    "Maio" to "mai", "Junho" to "jun", "Julho" to "jul", "Agosto" to "ago",
    "Setembro" to "set", "Outubro" to "out", "Novembro" to "nov", "Dezembro" to "dez"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LancamentoScreen(repository: SheetsRepository) {
    var descricao by remember { mutableStateOf("") }
    var valorDigitos by remember { mutableStateOf("") } // só dígitos, representando centavos
    var qtdParcelas by remember { mutableStateOf("") }
    var observacoes by remember { mutableStateOf("") }

    var categoriaExpandida by remember { mutableStateOf(false) }
    var categoriaSelecionada by remember { mutableStateOf("") }
    var categorias by remember { mutableStateOf<List<String>>(emptyList()) }

    // Data de lançamento: sempre a data atual, sem edição
    val dataAtual = remember { Date() }
    val formatoData = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    var mesExpandido by remember { mutableStateOf(false) }
    var anoExpandido by remember { mutableStateOf(false) }
    val calendarioAtual = remember { Calendar.getInstance() }
    var mesSelecionado by remember { mutableStateOf(MESES[calendarioAtual.get(Calendar.MONTH)]) }
    var anoSelecionado by remember { mutableStateOf(calendarioAtual.get(Calendar.YEAR)) }
    val anos = remember { (2024..2030).toList() }

    var salvando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        categorias = repository.buscarCategorias()
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Novo Lançamento", style = MaterialTheme.typography.headlineSmall)

        // Descrição — sem quebra de linha
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

        // Categoria (dropdown)
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
                onDismissRequest = { categoriaExpandida = false }
            ) {
                categorias.forEach { cat ->
                    DropdownMenuItem(text = { Text(cat) }, onClick = {
                        categoriaSelecionada = cat
                        categoriaExpandida = false
                    })
                }
            }
        }

        var valorCampo by remember {
            mutableStateOf(TextFieldValue(formatarValorMonetario("")))
        }

        // Valor Total — máscara estilo bancário (preenche a partir dos centavos)
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
            label = { Text("Valor Total") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        // Qtd. Parcelas
        OutlinedTextField(
            value = qtdParcelas,
            onValueChange = { qtdParcelas = it.filter { c -> c.isDigit() } },
            label = { Text("Qtd. Parcelas (opcional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        // Mês/Ano
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

        // Observações
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

        Button(
            enabled = !salvando && categoriaSelecionada.isNotBlank() && valorDigitos.isNotBlank(),
            onClick = {
                salvando = true
                scope.launch {
                    try {
                        val linha = repository.proximaLinhaVazia()
                        val valorTotal = (valorDigitos.toLongOrNull() ?: 0L) / 100.0
                        repository.salvarLancamento(
                            linha = linha,
                            data = formatoData.format(dataAtual),
                            descricao = descricao,
                            categoria = categoriaSelecionada,
                            valorTotal = valorTotal,
                            qtdParcelas = qtdParcelas.toIntOrNull(),
                            mesAno = "${mesSelecionado.second}/$anoSelecionado",
                            observacoes = observacoes
                        )
                        mensagem = "Lançamento salvo com sucesso!"
                        descricao = ""; valorDigitos = ""; qtdParcelas = ""; observacoes = ""
                        categoriaSelecionada = ""
                    } catch (e: Exception) {
                        mensagem = "Erro ao salvar: ${e.message}"
                    } finally {
                        salvando = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (salvando) "Salvando..." else "Salvar Lançamento")
        }

        if (mensagem.isNotBlank()) {
            Text(mensagem, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatarValorMonetario(digitos: String): String {
    val valor = if (digitos.isEmpty()) 0L else digitos.toLong()
    val reais = valor / 100
    val centavos = valor % 100
    val formatador = NumberFormat.getInstance(Locale("pt", "BR"))
    return "R$ ${formatador.format(reais)},${centavos.toString().padStart(2, '0')}"
}