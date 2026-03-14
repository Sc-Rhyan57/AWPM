package com.rhyan57.awp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rhyan57.awp.data.model.ConnectionState
import com.rhyan57.awp.ui.components.*
import com.rhyan57.awp.ui.theme.AwpColors
import com.rhyan57.awp.ui.viewmodel.MainViewModel

@Composable
fun ExecutorScreen(vm: MainViewModel) {
    val connectionState by vm.connectionState.collectAsState()
    val editorContent   by vm.editorContent.collectAsState()
    val savedScripts    by vm.savedScripts.collectAsState()
    val settings        by vm.settings.collectAsState()
    val uiVisible       = settings.uiVisible

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTitle      by remember { mutableStateOf("") }
    var showScripts    by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Script", color = AwpColors.TextPrimary) },
            text = {
                AwpTextField(
                    value = saveTitle,
                    onValueChange = { saveTitle = it },
                    label = "Script name",
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (saveTitle.isNotBlank() && editorContent.isNotBlank()) {
                        vm.saveScript(saveTitle.trim(), editorContent)
                        showSaveDialog = false
                        saveTitle = ""
                    }
                }) { Text("Save", color = AwpColors.Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = AwpColors.TextMuted)
                }
            },
            containerColor = AwpColors.Surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AwpColors.Background)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, null, tint = AwpColors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Executor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AwpColors.TextPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ConnectionBadge(connectionState)
                IconButton(
                    onClick = { vm.updateSetting { copy(uiVisible = !uiVisible) } },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (uiVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        null, tint = AwpColors.TextSecondary, modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { vm.updateSetting { copy(uiLocked = !settings.uiLocked) } },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (settings.uiLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        null, tint = if (settings.uiLocked) AwpColors.Warning else AwpColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { showScripts = !showScripts },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AwpColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AwpColors.TextSecondary)
            ) {
                Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Scripts", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { vm.setEditorContent("") },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AwpColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AwpColors.TextSecondary)
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { showSaveDialog = true },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AwpColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AwpColors.TextSecondary)
            ) {
                Icon(Icons.Outlined.Save, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save", fontSize = 12.sp)
            }
        }

        if (showScripts && savedScripts.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = AwpColors.Surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AwpColors.Border)
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                    savedScripts.forEach { script ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setEditorContent(script.content); showScripts = false }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (script.isAutoExec) Icons.Outlined.PlayCircle else Icons.Outlined.Code,
                                null,
                                tint = if (script.isAutoExec) AwpColors.Success else AwpColors.Primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(script.title, fontSize = 13.sp, color = AwpColors.TextPrimary,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.deleteScript(script) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Close, null,
                                    tint = AwpColors.TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AwpColors.SurfaceVar, RoundedCornerShape(12.dp))
                .border(1.dp, AwpColors.Border, RoundedCornerShape(12.dp))
        ) {
            BasicTextField(
                value = editorContent,
                onValueChange = vm::setEditorContent,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AwpColors.TextPrimary,
                    lineHeight = 20.sp
                ),
                decorationBox = { inner ->
                    if (editorContent.isEmpty()) {
                        Text("-- Write your Lua script here...",
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            color = AwpColors.TextMuted)
                    }
                    inner()
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        AwpButton(
            text = when (connectionState) {
                is ConnectionState.Connected      -> "Execute"
                is ConnectionState.WaitingForClient -> "Waiting for client..."
                else -> "Not connected"
            },
            onClick = { vm.executeScript(editorContent) },
            modifier = Modifier.fillMaxWidth(),
            color = when (connectionState) {
                is ConnectionState.Connected -> AwpColors.Success
                else -> AwpColors.TextMuted
            },
            enabled = connectionState is ConnectionState.Connected && editorContent.isNotBlank(),
            icon = Icons.Outlined.PlayArrow
        )
    }
}

@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    textStyle: androidx.compose.ui.text.TextStyle,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        decorationBox = decorationBox
    )
}
