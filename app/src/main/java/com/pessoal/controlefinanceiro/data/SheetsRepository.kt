package com.pessoal.controlefinanceiro.data

import android.accounts.Account
import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.pessoal.controlefinanceiro.BuildConfig
import com.pessoal.controlefinanceiro.model.Lancamento
import com.pessoal.controlefinanceiro.model.Mensalidade
import com.pessoal.controlefinanceiro.model.ResumoMes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Camada de acesso à planilha do Google Sheets.
 * Todas as funções rodam em Dispatchers.IO, pois são chamadas de rede.
 *
 * Colunas da aba "Lançamentos" (referência rápida):
 * A=Data | B=Descrição | C=Categoria | D=Tipo (fórmula, não editar)
 * E=Valor Total | F=Forma de Pagamento | G=Qtd. Parcelas | H=Mês/Ano | I=Observações
 * J=Parcelas aux | K=Valor Parcela aux | L=Mês Início aux | M=Mês Fim aux
 * N=ID Mensalidade (preenchido só em linhas geradas pela Tela de Mensalidades)
 * (D e J-M são só leitura — nunca escrever nelas)
 */
class SheetsRepository(context: Context, account: Account) {

    companion object {
        // ID da planilha vem do local.properties (não fica hardcoded no código-fonte)
        val SPREADSHEET_ID: String = BuildConfig.SPREADSHEET_ID

        // Última linha considerada nas buscas/gravações. Se um dia a planilha
        // ultrapassar isso, é só aumentar esse número (e estender as fórmulas
        // ARRAYFORMULA na planilha até a mesma linha).
        private const val ULTIMA_LINHA = 5000
    }

    private val credential = GoogleAccountCredential.usingOAuth2(
        context, listOf(SheetsScopes.SPREADSHEETS)
    ).also { it.selectedAccount = account }

    private val sheetsService: Sheets = Sheets.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName("Controle Financeiro").build()

    // Cache simples da lista de anos disponíveis, pra não bater na API toda vez
    // que o usuário troca de tela.
    private var anosCache: List<Int>? = null

    // Abreviações de mês usadas para montar o texto "jan/2026" gravado na coluna
    // Mês/Ano — usado pelas funções de Mensalidade, que calculam o mês sozinhas
    // (as telas de UI têm sua própria lista com nomes por extenso).
    private val MESES_ABREVIADOS = listOf(
        "jan", "fev", "mar", "abr", "mai", "jun",
        "jul", "ago", "set", "out", "nov", "dez"
    )

    /**
     * Descobre em qual linha da aba Lançamentos deve ser inserido o próximo
     * lançamento (primeira linha vazia a partir da linha 3).
     */
    suspend fun proximaLinhaVazia(): Int = withContext(Dispatchers.IO) {
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, "Lançamentos!A3:A$ULTIMA_LINHA")
            .execute()
        val quantidadePreenchida = response.getValues()?.size ?: 0
        3 + quantidadePreenchida
    }

    /**
     * Grava um novo lançamento. Escreve só nas colunas A-C e E-H (nunca em D ou I-L,
     * que são fórmulas). USER_ENTERED faz o Sheets interpretar "jan/2026" como data.
     */
    suspend fun salvarLancamento(
        linha: Int,
        data: String,
        descricao: String,
        categoria: String,
        valorTotal: Double,
        formaPagamento: String,
        qtdParcelas: Int?,
        mesAno: String,
        observacoes: String
    ) = withContext(Dispatchers.IO) {
        val colunasAC = ValueRange().setValues(listOf(listOf(data, descricao, categoria)))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!A$linha:C$linha", colunasAC)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val colunasEI = ValueRange().setValues(
            listOf(listOf(valorTotal, formaPagamento, qtdParcelas ?: "", mesAno, observacoes))
        )
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:I$linha", colunasEI)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    /**
     * Atualiza um lançamento existente (usado na edição). Não mexe na coluna A
     * (data de lançamento original permanece intacta).
     */
    suspend fun atualizarLancamento(
        linha: Int,
        descricao: String,
        categoria: String,
        valorTotal: Double,
        formaPagamento: String,
        qtdParcelas: Int?,
        mesAno: String,
        observacoes: String
    ) = withContext(Dispatchers.IO) {
        val colunasBC = ValueRange().setValues(listOf(listOf(descricao, categoria)))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!B$linha:C$linha", colunasBC)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val colunasEI = ValueRange().setValues(
            listOf(listOf(valorTotal, formaPagamento, qtdParcelas ?: "", mesAno, observacoes))
        )
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:I$linha", colunasEI)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    /**
     * "Remove" um lançamento limpando o conteúdo das colunas A-C e E-H da linha
     * (não apaga a linha inteira, pra não desalinhar as fórmulas ARRAYFORMULA
     * nem as linhas abaixo).
     */
    suspend fun excluirLancamento(linha: Int) = withContext(Dispatchers.IO) {
        val colunasVaziasAC = ValueRange().setValues(listOf(listOf("", "", "")))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!A$linha:C$linha", colunasVaziasAC)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val colunasVaziasEI = ValueRange().setValues(listOf(listOf("", "", "", "", "")))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:I$linha", colunasVaziasEI)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    /**
     * Lista todos os lançamentos cadastrados. Usa UNFORMATTED_VALUE + SERIAL_NUMBER
     * pra receber datas como número puro (mais confiável que texto formatado,
     * que varia conforme a formatação da célula no Sheets).
     */
    suspend fun listarLancamentos(): List<Lancamento> = withContext(Dispatchers.IO) {
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, "Lançamentos!A3:O$ULTIMA_LINHA")
            .setValueRenderOption("UNFORMATTED_VALUE")
            .setDateTimeRenderOption("SERIAL_NUMBER")
            .execute()
        val linhas = response.getValues() ?: emptyList()
        val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

        linhas.mapIndexedNotNull { indice, colunas ->
            val dataSerial = (colunas.getOrNull(0) as? Number)?.toDouble() ?: return@mapIndexedNotNull null
            val dataConvertida = serialParaData(dataSerial)

            val mesAnoSerial = (colunas.getOrNull(7) as? Number)?.toDouble()
            val dataMesAno = mesAnoSerial?.let { serialParaData(it) } ?: dataConvertida
            val calendarioMesAno = Calendar.getInstance().apply { time = dataMesAno }

            Lancamento(
                linha = indice + 3,
                data = formatoData.format(dataConvertida),
                descricao = colunas.getOrNull(1)?.toString().orEmpty(),
                categoria = colunas.getOrNull(2)?.toString().orEmpty(),
                tipo = colunas.getOrNull(3)?.toString().orEmpty(),
                valorTotal = (colunas.getOrNull(4) as? Number)?.toDouble() ?: 0.0,
                formaPagamento = colunas.getOrNull(5)?.toString().orEmpty(),
                qtdParcelas = (colunas.getOrNull(9) as? Number)?.toInt()
                    ?: (colunas.getOrNull(6) as? Number)?.toInt() ?: 1,
                valorParcela = (colunas.getOrNull(10) as? Number)?.toDouble() ?: 0.0,
                mesNumero = calendarioMesAno.get(Calendar.MONTH) + 1,
                anoNumero = calendarioMesAno.get(Calendar.YEAR),
                mesInicioIndex = (colunas.getOrNull(11) as? Number)?.toInt() ?: 0,
                mesFimIndex = (colunas.getOrNull(12) as? Number)?.toInt() ?: -1,
                observacoes = colunas.getOrNull(8)?.toString().orEmpty(),
                idMensalidade = colunas.getOrNull(13)?.toString()?.takeIf { it.isNotBlank() },
                ordemMensalidade = (colunas.getOrNull(14) as? Number)?.toInt()  // O
            )
        }
    }

    /** Busca um único lançamento pela linha (usado ao abrir a tela de edição). */
    suspend fun buscarLancamentoPorLinha(linha: Int): Lancamento? =
        listarLancamentos().find { it.linha == linha }

    /** Lista fixa de categorias, cadastrada na aba "Listas". */
    suspend fun buscarCategorias(): List<String> = withContext(Dispatchers.IO) {
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, "Listas!A2:A21")
            .execute()
        response.getValues()?.map { it[0].toString() } ?: emptyList()
    }

    /** Totais mês a mês de uma aba "Resumo {ano}" (linhas 3 a 14 = jan a dez). */
    suspend fun buscarResumo(ano: Int): List<ResumoMes> = withContext(Dispatchers.IO) {
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, "Resumo $ano!A3:D14")
            .setValueRenderOption("UNFORMATTED_VALUE")
            .execute()
        val linhas = response.getValues() ?: emptyList()

        (1..12).map { mes ->
            val colunas = linhas.getOrNull(mes - 1) ?: emptyList()
            ResumoMes(
                mes = mes,
                totalEntradas = (colunas.getOrNull(1) as? Number)?.toDouble() ?: 0.0,
                totalSaidas = (colunas.getOrNull(2) as? Number)?.toDouble() ?: 0.0,
                saldo = (colunas.getOrNull(3) as? Number)?.toDouble() ?: 0.0
            )
        }
    }

    /**
     * Descobre quais anos têm aba de Resumo na planilha (ex: "Resumo 2026",
     * "Resumo 2027"), pra alimentar os dropdowns de ano do app dinamicamente.
     */
    suspend fun buscarAnosDisponiveis(): List<Int> = withContext(Dispatchers.IO) {
        anosCache?.let { return@withContext it }

        val planilha = sheetsService.spreadsheets()
            .get(SPREADSHEET_ID)
            .setFields("sheets.properties.title")
            .execute()

        val regexNomeAba = Regex("""^Resumo (\d{4})$""")
        val anos = planilha.sheets
            .mapNotNull { it.properties?.title }
            .mapNotNull { titulo -> regexNomeAba.find(titulo)?.groupValues?.get(1)?.toIntOrNull() }
            .sorted()

        anosCache = anos
        anos
    }

    // ─────────────────────────────────────────────────────────────
    // Mensalidades — reaproveitam o mecanismo de parcelas: uma
    // mensalidade é uma linha só, com Qtd. Parcelas = meses restantes
    // até dezembro e Valor Total = valor mensal x meses restantes.
    // ─────────────────────────────────────────────────────────────

    /**
     * Cria uma nova mensalidade: lança uma única linha cobrindo do mês atual
     * até dezembro do ano corrente. Gera um novo ID (coluna N) que identifica
     * essa mensalidade para futuras edições/exclusões.
     */
    suspend fun criarMensalidade(
        nome: String,
        valorMensal: Double,
        formaPagamento: String,
        observacoes: String
    ) = withContext(Dispatchers.IO) {
        val calendarioAtual = Calendar.getInstance()
        val mesAtual = calendarioAtual.get(Calendar.MONTH) + 1
        val anoAtual = calendarioAtual.get(Calendar.YEAR)
        val mesesRestantes = 13 - mesAtual

        val ordem = listarMensalidadesAtivas().size // novas mensalidades entram no final da lista

        val linha = proximaLinhaVazia()
        salvarSegmentoMensalidade(
            linha = linha,
            data = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date()),
            nome = nome,
            valorTotal = valorMensal * mesesRestantes,
            formaPagamento = formaPagamento,
            qtdParcelas = mesesRestantes,
            mesAno = "${MESES_ABREVIADOS[mesAtual - 1]}/$anoAtual",
            observacoes = observacoes,
            idMensalidade = UUID.randomUUID().toString(),
            ordem = ordem
        )
    }

    /**
     * Lista as mensalidades ativas hoje: agrupa os lançamentos da categoria
     * "Mensalidade" por ID (coluna N) e retorna, de cada grupo, o segmento
     * vigente (cujo intervalo Mês Início/Mês Fim cobre o mês atual em diante)
     * — ignorando segmentos antigos deixados por edições passadas.
     */
    suspend fun listarMensalidadesAtivas(): List<Mensalidade> {
        val calendarioAtual = Calendar.getInstance()
        val indiceMesAtual = calendarioAtual.get(Calendar.YEAR) * 12 + (calendarioAtual.get(Calendar.MONTH) + 1)

        return listarLancamentos()
            .filter { it.categoria == "Mensalidade" && !it.idMensalidade.isNullOrBlank() }
            .groupBy { it.idMensalidade!! }
            .mapNotNull { (id, segmentos) ->
                val segmentoVigente = segmentos
                    .filter { it.mesFimIndex >= indiceMesAtual }
                    .maxByOrNull { it.mesInicioIndex }
                    ?: return@mapNotNull null

                Mensalidade(
                    idMensalidade = id,
                    linha = segmentoVigente.linha,
                    nome = segmentoVigente.descricao,
                    valorMensal = segmentoVigente.valorParcela,
                    formaPagamento = segmentoVigente.formaPagamento,
                    observacoes = segmentoVigente.observacoes,
                    ordem = segmentoVigente.ordemMensalidade ?: 0,
                    mesInicioIndex = segmentoVigente.mesInicioIndex,
                    mesFimIndex = segmentoVigente.mesFimIndex
                )
            }
            .sortedBy { it.ordem } // aplica a ordem definida pelo usuário
    }

    /**
     * Edita uma mensalidade a partir de um mês/ano escolhido:
     * - Se o mês escolhido é o próprio início do segmento vigente, só
     *   sobrescreve essa linha com os novos dados.
     * - Se for um mês no meio do intervalo, encurta o segmento atual
     *   (mantendo os dados antigos até o mês anterior) e cria um novo
     *   segmento a partir do mês escolhido, com os dados novos — os dois
     *   segmentos compartilham o mesmo ID Mensalidade.
     */
    suspend fun editarMensalidade(
        mensalidade: Mensalidade,
        novoNome: String,
        novoValorMensal: Double,
        novaFormaPagamento: String,
        novasObservacoes: String,
        mesEdicao: Int,
        anoEdicao: Int
    ) {
        val indiceMesEdicao = anoEdicao * 12 + mesEdicao
        val mesAnoNovoSegmento = "${MESES_ABREVIADOS[mesEdicao - 1]}/$anoEdicao"

        if (indiceMesEdicao <= mensalidade.mesInicioIndex) {
            val qtdParcelas = mensalidade.mesFimIndex - mensalidade.mesInicioIndex + 1
            atualizarLancamento(
                linha = mensalidade.linha,
                descricao = novoNome,
                categoria = "Mensalidade",
                valorTotal = novoValorMensal * qtdParcelas,
                formaPagamento = novaFormaPagamento,
                qtdParcelas = qtdParcelas,
                mesAno = mesAnoNovoSegmento,
                observacoes = novasObservacoes
            )
        } else {
            val anoInicioSegmento = (mensalidade.mesInicioIndex - 1) / 12
            val mesInicioSegmento = mensalidade.mesInicioIndex - anoInicioSegmento * 12
            val qtdParcelasSegmentoAntigo = indiceMesEdicao - mensalidade.mesInicioIndex

            atualizarLancamento(
                linha = mensalidade.linha,
                descricao = mensalidade.nome,
                categoria = "Mensalidade",
                valorTotal = mensalidade.valorMensal * qtdParcelasSegmentoAntigo,
                formaPagamento = mensalidade.formaPagamento,
                qtdParcelas = qtdParcelasSegmentoAntigo,
                mesAno = "${MESES_ABREVIADOS[mesInicioSegmento - 1]}/$anoInicioSegmento",
                observacoes = mensalidade.observacoes
            )

            val qtdParcelasNovoSegmento = 13 - mesEdicao
            salvarSegmentoMensalidade(
                linha = proximaLinhaVazia(),
                data = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date()),
                nome = novoNome,
                valorTotal = novoValorMensal * qtdParcelasNovoSegmento,
                formaPagamento = novaFormaPagamento,
                qtdParcelas = qtdParcelasNovoSegmento,
                mesAno = mesAnoNovoSegmento,
                observacoes = novasObservacoes,
                idMensalidade = mensalidade.idMensalidade,
                ordem = mensalidade.ordem // mantém a mesma posição na lista
            )
        }
    }

    /**
     * Exclui uma mensalidade: limpa o segmento vigente (mês atual em diante)
     * daquele ID, incluindo a coluna N — preserva o histórico de meses já
     * passados, que continuam registrados normalmente na planilha.
     */
    suspend fun excluirMensalidade(mensalidade: Mensalidade) = withContext(Dispatchers.IO) {
        excluirLancamento(mensalidade.linha)

        val colunaNVazia = ValueRange().setValues(listOf(listOf("")))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!N${mensalidade.linha}", colunaNVazia)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    /**
     * Grava um segmento de mensalidade (linha inteira: A-C, E-I e N). Usado
     * tanto na criação quanto na edição "a partir de um mês" (que gera um
     * novo segmento a partir do mês escolhido).
     */
    private suspend fun salvarSegmentoMensalidade(
        linha: Int,
        data: String,
        nome: String,
        valorTotal: Double,
        formaPagamento: String,
        qtdParcelas: Int,
        mesAno: String,
        observacoes: String,
        idMensalidade: String,
        ordem: Int
    ) = withContext(Dispatchers.IO) {
        val colunasAC = ValueRange().setValues(listOf(listOf(data, nome, "Mensalidade")))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!A$linha:C$linha", colunasAC)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val colunasEI = ValueRange().setValues(
            listOf(listOf(valorTotal, formaPagamento, qtdParcelas, mesAno, observacoes))
        )
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:I$linha", colunasEI)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val colunasNO = ValueRange().setValues(listOf(listOf(idMensalidade, ordem)))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!N$linha:O$linha", colunasNO)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    /**
     * Converte o número serial de data do Google Sheets (dias desde 30/12/1899)
     * para um objeto Date do Java.
     */
    private fun serialParaData(serial: Double): Date {
        val calendario = Calendar.getInstance()
        calendario.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
        calendario.set(Calendar.MILLISECOND, 0)
        calendario.add(Calendar.DATE, serial.toInt())
        return calendario.time
    }

    /**
     * Salva a nova ordem de exibição das mensalidades (coluna O), na ordem em
     * que aparecem na lista recebida. Usa batchUpdate pra gravar tudo numa
     * única chamada à API, independente de quantas mensalidades existam.
     */
    suspend fun atualizarOrdemMensalidades(mensalidadesOrdenadas: List<Mensalidade>) = withContext(Dispatchers.IO) {
        val dadosAtualizacao = mensalidadesOrdenadas.mapIndexed { indice, mensalidade ->
            ValueRange()
                .setRange("Lançamentos!O${mensalidade.linha}")
                .setValues(listOf(listOf(indice)))
        }
        val requisicao = com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest()
            .setValueInputOption("USER_ENTERED")
            .setData(dadosAtualizacao)
        sheetsService.spreadsheets().values()
            .batchUpdate(SPREADSHEET_ID, requisicao)
            .execute()
    }
}