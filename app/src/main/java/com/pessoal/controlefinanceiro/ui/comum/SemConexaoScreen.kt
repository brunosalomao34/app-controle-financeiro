package com.pessoal.controlefinanceiro.ui.comum

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Exibida sempre que o app detecta que não há conexão com a internet. */
@Composable
fun SemConexaoScreen(aoTentarNovamente: () -> Unit) {
    // Enquanto tentando = true, o botão mostra o mesmo spinner de
    // carregamento usado nas telas de Resumo, no lugar do ícone de reload
    var tentando by remember { mutableStateOf(false) }

    LaunchedEffect(tentando) {
        if (tentando) {
            aoTentarNovamente()
            delay(1000) // tempo mínimo pro spinner ficar visível
            tentando = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Sem conexão com a internet", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Verifique sua conexão Wi-Fi ou dados móveis e tente novamente.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { tentando = true },
                enabled = !tentando
            ) {
                if (tentando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (tentando) "Tentando..." else "Tentar novamente")
            }
        }
    }
}