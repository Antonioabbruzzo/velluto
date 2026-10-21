package com.velluto.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Palette.TextDim,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Palette.TextDim,
    cursorColor = Palette.Accent,
)

@Composable
fun LoginScreen(vm: AppViewModel) {
    var url by remember { mutableStateOf("http://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    val busy = vm.loadState is LoadState.Loading

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text("VELL", color = Palette.Accent, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Text("UTO", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
            }
            Text("Accedi con i dati del tuo provider", color = Palette.TextDim, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                label = { Text("URL del server") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                colors = loginFieldColors(),
                modifier = Modifier.width(460.dp).focusRequester(fr).dpadVerticalFocus(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = user, onValueChange = { user = it }, singleLine = true,
                label = { Text("Username") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = loginFieldColors(),
                modifier = Modifier.width(460.dp).dpadVerticalFocus(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                colors = loginFieldColors(),
                modifier = Modifier.width(460.dp).dpadVerticalFocus(),
            )
            Spacer(Modifier.height(20.dp))
            if (busy) {
                CircularProgressIndicator(color = Palette.Accent)
            } else {
                TvButton(
                    "Accedi",
                    primary = true,
                    onClick = {
                        error = null
                        if (url.length < 10 || user.isBlank() || pass.isBlank()) {
                            error = "Compila tutti i campi"
                        } else {
                            vm.login(url, user, pass) { error = it }
                        }
                    },
                )
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Palette.Accent, fontSize = 14.sp)
            }
        }
    }
    RequestFocus(fr, "login")
}

@Composable
fun LoadingScreen(vm: AppViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = vm.loadState) {
            is LoadState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Qualcosa è andato storto", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(s.message, color = Palette.TextDim, fontSize = 15.sp)
                Spacer(Modifier.height(20.dp))
                val fr = remember { FocusRequester() }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton("Riprova", onClick = { vm.loadCatalog() }, primary = true, focusRequester = fr)
                    TvButton("Cambia account", onClick = { vm.logout() })
                }
                RequestFocus(fr, s)
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    Text("VELL", color = Palette.Accent, fontSize = 52.sp, fontWeight = FontWeight.Black)
                    Text("UTO", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(color = Palette.Accent)
                Spacer(Modifier.height(12.dp))
                Text(
                    (s as? LoadState.Loading)?.message ?: "Caricamento…",
                    color = Palette.TextDim,
                    fontSize = 14.sp,
                )
                // Sempre disponibile: se il provider non risponde non si resta bloccati qui.
                Spacer(Modifier.height(28.dp))
                val fr = remember { FocusRequester() }
                TvButton("Cambia account", onClick = { vm.logout() }, focusRequester = fr)
                RequestFocus(fr, "loading")
            }
        }
    }
}
