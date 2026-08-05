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
import com.pessoal.controlefinanceiro.model.ResumoMes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Camada de acesso à planilha do Google Sheets.
 * Todas as funções rodam em Dispatchers.IO, pois são chamadas de rede.
 *
 * Colunas da aba "Lançamentos" (referência rápida):
 * A=Data | B=Descrição | C=Categoria | D=Tipo (fórmula, não editar)
 * E=Valor Total | F=Qtd. Parcelas | G=Mês/Ano | H=Observações
 * I=Parcelas aux | J=Valor Parcela aux | K=Mês Início aux | L=Mês Fim aux
 * (D e I-L são só leitura — nunca escrever nelas)
 */
class SheetsRepository(context: Context, account: Account) {

    companion object {
        // ID da planilha vem do local.properties (não fica hardcoded no código-fonte)
        val SPREADSHEET_ID: String = BuildConfig.SPREADSHEET_ID
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

    /**
     * Descobre em qual linha da aba Lançamentos deve ser inserido o próximo
     * lançamento (primeira linha vazia a partir da linha 3).
     */
    suspend fun proximaLinhaVazia(): Int = withContext(Dispatchers.IO) {
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, "Lançamentos!A3:A500")
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
        data: String,          // "dd/MM/yyyy"
        descricao: String,
        categoria: String,
        valorTotal: Double,
        qtdParcelas: Int?,
        mesAno: String,         // "jan/2026"
        observacoes: String
    ) = withContext(Dispatchers.IO) {
        val colunasAC = ValueRange().setValues(listOf(listOf(data, descricao, categoria)))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!A$linha:C$linha", colunasAC)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val colunasEH = ValueRange().setValues(
            listOf(listOf(valorTotal, qtdParcelas ?: "", mesAno, observacoes))
        )
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:H$linha", colunasEH)
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
        qtdParcelas: Int?,
        mesAno: String,
        observacoes: String
    ) = withContext(Dispatchers.IO) {
        val colunasBC = ValueRange().setValues(listOf(listOf(descricao, categoria)))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!B$linha:C$linha", colunasBC)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val colunasEH = ValueRange().setValues(
            listOf(listOf(valorTotal, qtdParcelas ?: "", mesAno, observacoes))
        )
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:H$linha", colunasEH)
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

        val colunasVaziasEH = ValueRange().setValues(listOf(listOf("", "", "", "")))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:H$linha", colunasVaziasEH)
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
            .get(SPREADSHEET_ID, "Lançamentos!A3:L500")
            .setValueRenderOption("UNFORMATTED_VALUE")
            .setDateTimeRenderOption("SERIAL_NUMBER")
            .execute()
        val linhas = response.getValues() ?: emptyList()
        val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

        linhas.mapIndexedNotNull { indice, colunas ->
            val dataSerial = (colunas.getOrNull(0) as? Number)?.toDouble()
                ?: return@mapIndexedNotNull null // linha sem data = linha vazia, ignora

            val dataConvertida = serialParaData(dataSerial)

            // Coluna G (Mês/Ano) também vem como serial de data
            val mesAnoSerial = (colunas.getOrNull(6) as? Number)?.toDouble()
            val dataMesAno = mesAnoSerial?.let { serialParaData(it) } ?: dataConvertida
            val calendarioMesAno = Calendar.getInstance().apply { time = dataMesAno }

            Lancamento(
                linha = indice + 3,
                data = formatoData.format(dataConvertida),
                descricao = colunas.getOrNull(1)?.toString().orEmpty(),
                categoria = colunas.getOrNull(2)?.toString().orEmpty(),
                tipo = colunas.getOrNull(3)?.toString().orEmpty(),                      // coluna D
                valorTotal = (colunas.getOrNull(4) as? Number)?.toDouble() ?: 0.0,      // coluna E
                valorParcela = (colunas.getOrNull(9) as? Number)?.toDouble() ?: 0.0,    // coluna J
                qtdParcelas = (colunas.getOrNull(8) as? Number)?.toInt()                // coluna I
                    ?: (colunas.getOrNull(5) as? Number)?.toInt() ?: 1,
                mesNumero = calendarioMesAno.get(Calendar.MONTH) + 1,
                anoNumero = calendarioMesAno.get(Calendar.YEAR),
                mesInicioIndex = (colunas.getOrNull(10) as? Number)?.toInt() ?: 0,      // coluna K
                mesFimIndex = (colunas.getOrNull(11) as? Number)?.toInt() ?: -1,        // coluna L
                observacoes = colunas.getOrNull(7)?.toString().orEmpty()
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
}