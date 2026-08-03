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
}