package com.velluto.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsSection(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val firstFr = remember { FocusRequester() }
    val lib = vm.library.state.value

    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 32.dp, end = 40.dp)) {
        Text("Impostazioni", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        SectionTitle("Aggiornamenti")
        Info("Versione installata", vm.platform.versionName)
        Spacer(Modifier.height(10.dp))
        val upd = vm.update
        if (upd != null) {
            TvButton(
                text = if (downloading) updateStatus ?: "Download…" else "Installa ${upd.versionName}",
                icon = Icons.Filled.Refresh,
                primary = true,
                focusRequester = firstFr,
                onClick = {
                    if (downloading) return@TvButton
                    downloading = true
                    scope.launch {
                        try {
                            vm.platform.updater.install(upd) { p ->
                                updateStatus = "Download ${(p * 100).toInt()}%"
                            }
                            updateStatus = null
                        } catch (e: Exception) {
                            updateStatus = "Errore: ${e.message}"
                        } finally {
                            downloading = false
                        }
                    }
                },
            )
        } else {
            TvButton(
                text = "Controlla aggiornamenti",
                icon = Icons.Filled.Refresh,
                focusRequester = firstFr,
                onClick = {
                    updateStatus = "Controllo in corso…"
                    vm.checkForUpdate { found ->
                        updateStatus = if (found) null else "Hai già l'ultima versione"
                    }
                },
            )
        }
        updateStatus?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Palette.TextDim, fontSize = 13.sp)
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle("Account")
        vm.account?.let { a ->
            Info("Server", a.baseUrl)
            Info("Utente", a.username)
        }
        vm.accountInfo?.let { info ->
            info.expiresAt?.let {
                Info("Scadenza", SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(Date(it)))
            }
            if (info.maxConnections.isNotBlank()) Info("Connessioni max", info.maxConnections)
        }
        Info("Nella mia lista", "${lib.favorites.size} titoli")
        Info("Cronologia", "${lib.progress.size} episodi/film")
        Info(
            "Catalogo aggiornato",
            vm.catalogStatus?.takeIf { vm.loadingParts.isNotEmpty() }?.let { "in corso…" }
                ?: vm.catalogUpdatedAt?.let {
                    SimpleDateFormat("dd/MM/yyyy 'alle' HH:mm", Locale.ITALY).format(Date(it)) + "  (si aggiorna da solo una volta al giorno)"
                }
                ?: "—",
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton("Ricarica catalogo", onClick = { vm.loadCatalog(force = true) }, icon = Icons.Filled.Refresh)
            TvButton("Esci dall'account", onClick = { vm.logout() }, icon = Icons.Filled.ExitToApp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Uscendo dall'account la tua lista e la cronologia restano salvate.",
            color = Palette.TextDim,
            fontSize = 12.sp,
        )
    }
    RequestFocus(firstFr, "settings")
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.width(170.dp))
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}
