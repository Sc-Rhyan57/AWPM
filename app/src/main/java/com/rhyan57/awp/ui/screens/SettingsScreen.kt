package com.rhyan57.awp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rhyan57.awp.ui.components.*
import com.rhyan57.awp.ui.theme.AwpColors
import com.rhyan57.awp.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AwpColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Black,
            color = AwpColors.TextPrimary, modifier = Modifier.padding(bottom = 14.dp))

        SectionCard("Executor", Icons.Outlined.Terminal) {
            SettingToggleRow(
                title = "Delete Original Executor GUI",
                subtitle = "Destroys the original executor GUI on connect",
                checked = settings.deleteOriginalGuiOnExec,
                onCheckedChange = { vm.updateSetting { copy(deleteOriginalGuiOnExec = it) } },
                icon = Icons.Outlined.DeleteForever
            )
            HorizontalDivider(color = AwpColors.Border, modifier = Modifier.padding(vertical = 6.dp))
            SettingToggleRow(
                title = "Auto Execute",
                subtitle = "Run scripts marked as Auto-Exec on connect",
                checked = settings.autoExecEnabled,
                onCheckedChange = { vm.updateSetting { copy(autoExecEnabled = it) } },
                icon = Icons.Outlined.PlayCircle
            )
        }

        Spacer(Modifier.height(14.dp))

        SectionCard("Interface", Icons.Outlined.Visibility) {
            SettingToggleRow(
                title = "Show UI",
                subtitle = "Toggle executor interface visibility",
                checked = settings.uiVisible,
                onCheckedChange = { vm.updateSetting { copy(uiVisible = it) } },
                icon = Icons.Outlined.Visibility
            )
            HorizontalDivider(color = AwpColors.Border, modifier = Modifier.padding(vertical = 6.dp))
            SettingToggleRow(
                title = "Lock UI Position",
                subtitle = "Prevent accidental UI dismissal",
                checked = settings.uiLocked,
                onCheckedChange = { vm.updateSetting { copy(uiLocked = it) } },
                icon = Icons.Outlined.Lock
            )
        }

        Spacer(Modifier.height(14.dp))

        SectionCard("Server Ports", Icons.Outlined.Dns) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("WebSocket Port", fontSize = 12.sp, color = AwpColors.TextMuted,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(AwpColors.SurfaceVar, RoundedCornerShape(10.dp))
                            .border(1.dp, AwpColors.Border, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text("${settings.wsPort}", fontSize = 14.sp, color = AwpColors.Accent,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("HTTP Port", fontSize = 12.sp, color = AwpColors.TextMuted,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(AwpColors.SurfaceVar, RoundedCornerShape(10.dp))
                            .border(1.dp, AwpColors.Border, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text("${settings.httpPort}", fontSize = 14.sp, color = AwpColors.Accent,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Ports are fixed. Restart app to apply changes.",
                fontSize = 11.sp, color = AwpColors.TextMuted)
        }

        Spacer(Modifier.height(14.dp))

        SectionCard("About", Icons.Outlined.Info) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AWP Mobile", fontSize = 13.sp, color = AwpColors.TextPrimary)
                Text("By Rhyan57", fontSize = 13.sp, color = AwpColors.Primary, fontWeight = FontWeight.Bold)
            }
            Text("Android executor bridge for Delta + Roblox",
                fontSize = 12.sp, color = AwpColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(20.dp))
    }
}
