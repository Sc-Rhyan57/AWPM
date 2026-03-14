package com.rhyan57.awp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.rhyan57.awp.data.model.ConnectionState
import com.rhyan57.awp.ui.components.*
import com.rhyan57.awp.ui.theme.AwpColors
import com.rhyan57.awp.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel, onOpenExecutor: () -> Unit) {
    val ctx             = LocalContext.current
    val connectionState by vm.connectionState.collectAsState()
    val settings        by vm.settings.collectAsState()
    val consoleOutput   by vm.consoleOutput.collectAsState()
    val localIp         = remember { vm.getLocalIp() }
    val token           = remember { vm.generateSessionToken() }

    val bootstrapUrl = "http://$localIp:8080/session/$token"
    val loadstring   = """loadstring(game:HttpGet("$bootstrapUrl"))()"""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AwpColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AWP", fontSize = 28.sp, fontWeight = FontWeight.Black,
                    color = AwpColors.Primary, letterSpacing = 2.sp)
                Text("Executor Bridge", fontSize = 13.sp, color = AwpColors.TextMuted)
            }
            ConnectionBadge(connectionState)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(AwpColors.Primary, AwpColors.Accent)))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(AwpColors.Primary.copy(0.08f), AwpColors.Surface)),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(AwpColors.Roblox.copy(0.15f), CircleShape)
                            .border(2.dp, AwpColors.Roblox.copy(0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "https://cdn2.steamgriddb.com/icon_thumb/0af5b1c63c4dc7f6c571afa2d7abf39a.png",
                            contentDescription = "Roblox",
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            error = null
                        )
                        Icon(Icons.Outlined.VideogameAsset, null,
                            tint = AwpColors.Roblox, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Roblox", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = AwpColors.TextPrimary)
                        Text(
                            when (connectionState) {
                                is ConnectionState.Connected -> "Client connected ✓"
                                is ConnectionState.WaitingForClient -> "Waiting for Delta..."
                                else -> "Tap RUN to start"
                            },
                            fontSize = 13.sp,
                            color = when (connectionState) {
                                is ConnectionState.Connected -> AwpColors.Success
                                is ConnectionState.WaitingForClient -> AwpColors.Warning
                                else -> AwpColors.TextMuted
                            }
                        )
                    }
                    AwpButton(
                        text = "RUN",
                        onClick = {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("roblox://")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                            onOpenExecutor()
                        },
                        color = AwpColors.Primary,
                        icon = Icons.Outlined.PlayArrow,
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Bootstrap Loadstring", Icons.Outlined.Code) {
            Text("Paste this in Delta after Roblox opens:",
                fontSize = 12.sp, color = AwpColors.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(AwpColors.SurfaceVar, RoundedCornerShape(10.dp))
                    .border(1.dp, AwpColors.Border, RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(loadstring, fontSize = 11.sp, color = AwpColors.Accent,
                    fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(10.dp))
            AwpButton(
                text = "Copy Loadstring",
                onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("AWP", loadstring))
                },
                modifier = Modifier.fillMaxWidth(),
                color = AwpColors.PrimaryDim,
                icon = Icons.Outlined.ContentCopy
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Server Info", Icons.Outlined.Dns) {
            InfoRow("IP Address", localIp)
            InfoRow("WebSocket", "ws://$localIp:8765")
            InfoRow("HTTP", "http://$localIp:8080")
        }

        if (consoleOutput.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionCard("Console", Icons.Outlined.Terminal) {
                val scrollState = rememberScrollState()
                LaunchedEffect(consoleOutput.size) { scrollState.animateScrollTo(scrollState.maxValue) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(AwpColors.SurfaceVar, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        consoleOutput.takeLast(50).joinToString("\n"),
                        fontSize = 11.sp,
                        color = AwpColors.Success,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = AwpColors.TextMuted)
        Text(value, fontSize = 12.sp, color = AwpColors.TextSecondary,
            fontFamily = FontFamily.Monospace)
    }
}
