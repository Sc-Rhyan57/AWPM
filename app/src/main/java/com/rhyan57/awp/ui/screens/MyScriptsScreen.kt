package com.rhyan57.awp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.rhyan57.awp.data.model.SavedScript
import com.rhyan57.awp.ui.theme.AwpColors
import com.rhyan57.awp.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MyScriptsScreen(vm: MainViewModel, onSendToExecutor: (String) -> Unit) {
    val scripts by vm.savedScripts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AwpColors.Background)
            .padding(16.dp)
    ) {
        Text("My Scripts", fontSize = 22.sp, fontWeight = FontWeight.Black,
            color = AwpColors.TextPrimary, modifier = Modifier.padding(bottom = 14.dp))

        if (scripts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FolderOpen, null, tint = AwpColors.TextMuted,
                        modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No saved scripts yet", color = AwpColors.TextMuted, fontSize = 15.sp)
                    Text("Save scripts from the Executor tab", color = AwpColors.TextMuted,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(scripts) { script ->
                    SavedScriptCard(script = script, onSend = { onSendToExecutor(script.content) },
                        onToggleAutoExec = { vm.toggleAutoExec(script) },
                        onDelete = { vm.deleteScript(script) })
                }
            }
        }
    }
}

@Composable
private fun SavedScriptCard(
    script: SavedScript,
    onSend: () -> Unit,
    onToggleAutoExec: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AwpColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (script.isAutoExec) AwpColors.Success.copy(0.4f) else AwpColors.Border)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (script.isAutoExec) Icons.Outlined.PlayCircle else Icons.Outlined.Code,
                    null,
                    tint = if (script.isAutoExec) AwpColors.Success else AwpColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(script.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = AwpColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmt.format(Date(script.createdAt)), fontSize = 11.sp, color = AwpColors.TextMuted)
                }
                if (script.isAutoExec) {
                    Text("AUTO", fontSize = 9.sp, color = AwpColors.Success,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(AwpColors.Success.copy(0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                script.content.lines().take(3).joinToString("\n"),
                fontSize = 11.sp,
                color = AwpColors.TextSecondary,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AwpColors.SurfaceVar, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onToggleAutoExec,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (script.isAutoExec) AwpColors.Success else AwpColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (script.isAutoExec) AwpColors.Success else AwpColors.TextMuted)
                ) {
                    Text(if (script.isAutoExec) "Auto: ON" else "Auto: OFF", fontSize = 11.sp)
                }
                IconButton(onClick = onSend, modifier = Modifier
                    .size(36.dp)
                    .background(AwpColors.Primary.copy(0.15f), RoundedCornerShape(8.dp))) {
                    Icon(Icons.Outlined.Send, null, tint = AwpColors.Primary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier
                    .size(36.dp)
                    .background(AwpColors.Error.copy(0.10f), RoundedCornerShape(8.dp))) {
                    Icon(Icons.Outlined.Delete, null, tint = AwpColors.Error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
