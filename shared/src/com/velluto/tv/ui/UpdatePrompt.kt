package com.velluto.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Firestick: appena la nuova versione è scaricata compare questo avviso (mai durante la visione).
 * Android chiede comunque la sua conferma: qui basta OK su "Installa", poi "Installa" di Android.
 * Sul PC non serve: l'aggiornamento si installa da solo alla chiusura.
 */
@Composable
fun UpdatePrompt(vm: AppViewModel) {
    val upd = vm.update ?: return
    if (vm.updateFile == null || vm.updatePlatformSilent || vm.updatePromptDismissed) return
    val installFr = remember { FocusRequester() }
    Dialog(onDismissRequest = { vm.updatePromptDismissed = true }) {
        val shape = RoundedCornerShape(14.dp)
        Column(
            Modifier
                .clip(shape)
                .background(Palette.Surface)
                .border(1.dp, Palette.Stroke, shape)
                .padding(28.dp)
                .width(460.dp),
        ) {
            Text("Aggiornamento pronto", color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Velluto ${upd.versionName}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text(
                "È già scaricata: premi Installa e conferma con «Installa» nella schermata di Android. Ci vogliono pochi secondi.",
                color = Palette.Text, fontSize = 14.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row {
                TvButton(
                    text = "Installa",
                    primary = true,
                    focusRequester = installFr,
                    onClick = {
                        vm.updatePromptDismissed = true
                        vm.installUpdate()
                    },
                )
                Spacer(Modifier.width(12.dp))
                TvButton(text = "Più tardi", onClick = { vm.updatePromptDismissed = true })
            }
        }
    }
    RequestFocus(installFr, "update-prompt")
}
