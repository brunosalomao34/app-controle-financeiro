package com.pessoal.controlefinanceiro.data

import android.accounts.Account
import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pessoal.controlefinanceiro.BuildConfig
import com.pessoal.controlefinanceiro.model.Lancamento
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SheetsRepository(context: Context, account: Account) {

    companion object {
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

    suspend fun testarConexao(): List<String> = withContext(Dispatchers.IO) {
        val range = "Listas!A2:A21"
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, range)
            .execute()
        response.getValues()?.map { it[0].toString() } ?: emptyList()
    }

    suspend fun proximaLinhaVazia(): Int = withContext(Dispatchers.IO) {
        val range = "Lançamentos!A3:A500"
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, range)
            .execute()
        val valores = response.getValues()
        val quantidadePreenchida = valores?.size ?: 0
        3 + quantidadePreenchida // linha 3 é a primeira de dados
    }

    suspend fun salvarLancamento(
        linha: Int,
        data: String,        // "dd/MM/yyyy"
        descricao: String,
        categoria: String,
        valorTotal: Double,
        qtdParcelas: Int?,
        mesAno: String,       // "jan/2026"
        observacoes: String
    ) = withContext(Dispatchers.IO) {
        val valueRange1 = com.google.api.services.sheets.v4.model.ValueRange()
            .setValues(listOf(listOf(data, descricao, categoria)))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!A$linha:C$linha", valueRange1)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val valueRange2 = com.google.api.services.sheets.v4.model.ValueRange()
            .setValues(listOf(listOf(
                valorTotal,
                qtdParcelas ?: "",
                mesAno,
                observacoes
            )))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:H$linha", valueRange2)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    suspend fun buscarCategorias(): List<String> = withContext(Dispatchers.IO) {
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, "Listas!A2:A21")
            .execute()
        response.getValues()?.map { it[0].toString() } ?: emptyList()
    }

    suspend fun listarLancamentos(): List<Lancamento> = withContext(Dispatchers.IO) {
        val response = sheetsService.spreadsheets().values()
            .get(SPREADSHEET_ID, "Lançamentos!A3:L500")
            .setValueRenderOption("UNFORMATTED_VALUE")
            .setDateTimeRenderOption("SERIAL_NUMBER")
            .execute()
        val linhas = response.getValues() ?: emptyList()
        val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

        linhas.mapIndexedNotNull { indice, colunas ->
            val dataSerial = (colunas.getOrNull(0) as? Number)?.toDouble() ?: return@mapIndexedNotNull null
            val dataConvertida = serialParaData(dataSerial)

            val mesAnoSerial = (colunas.getOrNull(6) as? Number)?.toDouble()
            val dataMesAno = mesAnoSerial?.let { serialParaData(it) } ?: dataConvertida
            val calendarioMesAno = Calendar.getInstance().apply { time = dataMesAno }

            Lancamento(
                linha = indice + 3,
                data = formatoData.format(dataConvertida),
                descricao = colunas.getOrNull(1)?.toString().orEmpty(),
                categoria = colunas.getOrNull(2)?.toString().orEmpty(),
                tipo = colunas.getOrNull(3)?.toString().orEmpty(),
                valorTotal = (colunas.getOrNull(4) as? Number)?.toDouble() ?: 0.0,
                valorParcela = (colunas.getOrNull(9) as? Number)?.toDouble() ?: 0.0,   // coluna J
                qtdParcelas = (colunas.getOrNull(8) as? Number)?.toInt()               // coluna I
                    ?: (colunas.getOrNull(5) as? Number)?.toInt() ?: 1,
                mesNumero = calendarioMesAno.get(Calendar.MONTH) + 1,
                anoNumero = calendarioMesAno.get(Calendar.YEAR),
                mesInicioIndex = (colunas.getOrNull(10) as? Number)?.toInt() ?: 0,     // coluna K
                mesFimIndex = (colunas.getOrNull(11) as? Number)?.toInt() ?: -1,       // coluna L
                observacoes = colunas.getOrNull(7)?.toString().orEmpty()
            )
        }
    }

    private fun serialParaData(serial: Double): Date {
        val calendario = Calendar.getInstance()
        calendario.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
        calendario.set(Calendar.MILLISECOND, 0)
        calendario.add(Calendar.DATE, serial.toInt())
        return calendario.time
    }

    suspend fun buscarLancamentoPorLinha(linha: Int): Lancamento? =
        listarLancamentos().find { it.linha == linha }

    suspend fun atualizarLancamento(
        linha: Int,
        descricao: String,
        categoria: String,
        valorTotal: Double,
        qtdParcelas: Int?,
        mesAno: String,
        observacoes: String
    ) = withContext(Dispatchers.IO) {
        val valueRangeBC = com.google.api.services.sheets.v4.model.ValueRange()
            .setValues(listOf(listOf(descricao, categoria)))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!B$linha:C$linha", valueRangeBC)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val valueRangeEH = com.google.api.services.sheets.v4.model.ValueRange()
            .setValues(listOf(listOf(valorTotal, qtdParcelas ?: "", mesAno, observacoes)))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:H$linha", valueRangeEH)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    suspend fun excluirLancamento(linha: Int) = withContext(Dispatchers.IO) {
        val valuesVazias = com.google.api.services.sheets.v4.model.ValueRange()
            .setValues(listOf(listOf("", "", "")))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!A$linha:C$linha", valuesVazias)
            .setValueInputOption("USER_ENTERED")
            .execute()

        val valuesVazias2 = com.google.api.services.sheets.v4.model.ValueRange()
            .setValues(listOf(listOf("", "", "", "")))
        sheetsService.spreadsheets().values()
            .update(SPREADSHEET_ID, "Lançamentos!E$linha:H$linha", valuesVazias2)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }
}