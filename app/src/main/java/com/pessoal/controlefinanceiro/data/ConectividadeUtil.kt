package com.pessoal.controlefinanceiro.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Verifica se o aparelho tem conexão de rede ativa (Wi-Fi, dados móveis
 * ou ethernet) com acesso real à internet — não só "conectado à rede",
 * mas validado (ex: Wi-Fi sem internet não conta).
 */
fun possuiConexaoInternet(context: Context): Boolean {
    val gerenciador = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false

    val rede = gerenciador.activeNetwork ?: return false
    val capacidades = gerenciador.getNetworkCapabilities(rede) ?: return false

    return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}