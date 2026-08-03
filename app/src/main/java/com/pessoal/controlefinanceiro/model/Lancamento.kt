package com.pessoal.controlefinanceiro.model

data class Lancamento(
    val linha: Int,              // número da linha na planilha (para editar/remover depois)
    val data: String,            // "dd/MM/yyyy"
    val descricao: String,
    val categoria: String,
    val valorTotal: Double,
    val qtdParcelas: Int,
    val mesAno: String,          // "jan/2026"
    val observacoes: String
)