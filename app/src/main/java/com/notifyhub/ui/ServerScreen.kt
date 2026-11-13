package com.notifyhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ServerScreen(vm: ServerViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var deviceId by remember(s.deviceId) { mutableStateOf(s.deviceId) }
    var allowHttp by remember(s.allowHttp) { mutableStateOf(s.allowHttp) }
    var credential by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Server settings")
        SectionCard {
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                label = { Text("Backend URL") }, placeholder = { Text("https://notify.example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = deviceId, onValueChange = { deviceId = it }, singleLine = true,
                label = { Text("Device identifier") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = credential, onValueChange = { credential = it }, singleLine = true,
                label = { Text(if (vm.hasCredential) "Device credential (stored; enter to replace)" else "Device credential") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (vm.hasCredential) OutlinedButton(onClick = vm::clearCredential) { Text("Remove stored credential") }
            Text(
                "The credential is encrypted with a key held in the Android Keystore.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow HTTP (local development only)")
                    Text(
                        "HTTPS is required otherwise. Plain HTTP only works in debug builds.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = allowHttp, onCheckedChange = { allowHttp = it })
            }
        }
        Button(onClick = { vm.save(url, deviceId, allowHttp, credential); credential = "" }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
        OutlinedButton(onClick = vm::testConnection, enabled = !vm.testing, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.testing) "Testing…" else "Test connection (placeholder)")
        }
        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
