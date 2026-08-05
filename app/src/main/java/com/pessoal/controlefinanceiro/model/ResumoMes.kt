package com.pessoal.controlefinanceiro.model

data class ResumoMes(
    val mes: Int,
    val totalEntradas: Double,
    val totalSaidas: Double,
    val saldo: Double
)