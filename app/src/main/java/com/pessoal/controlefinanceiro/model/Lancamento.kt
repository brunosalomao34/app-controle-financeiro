package com.pessoal.controlefinanceiro.model

data class Lancamento(
    val linha: Int,
    val data: String,
    val descricao: String,
    val categoria: String,
    val tipo: String,
    val valorTotal: Double,
    val valorParcela: Double,
    val qtdParcelas: Int,
    val mesNumero: Int,
    val anoNumero: Int,
    val mesInicioIndex: Int,   // coluna K
    val mesFimIndex: Int,      // coluna L
    val observacoes: String
)