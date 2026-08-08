package com.pessoal.controlefinanceiro.model

/**
 * Representa uma mensalidade ativa (ex: academia, streaming, plano de saúde).
 * Corresponde ao "segmento" atualmente vigente de um ID Mensalidade (coluna N) —
 * uma mensalidade pode ter vários segmentos ao longo do tempo (um por edição
 * "a partir de tal mês"), mas só o vigente (que cobre o mês atual em diante)
 * é retornado pelas funções de listagem.
 */
data class Mensalidade(
    val idMensalidade: String,
    val linha: Int,
    val nome: String,
    val valorMensal: Double,
    val formaPagamento: String,
    val observacoes: String,
    val mesInicioIndex: Int, // mês/ano em que esse segmento começou (ano*12 + mês)
    val mesFimIndex: Int     // mês/ano em que esse segmento termina (dezembro do ano)
)