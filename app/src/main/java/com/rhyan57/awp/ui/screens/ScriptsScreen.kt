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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.rhyan57.awp.data.model.ScriptBloxScript
import com.rhyan57.awp.ui.components.AwpTextField
import com.rhyan57.awp.ui.theme.AwpColors
import com.rhyan57.awp.ui.viewmodel.MainViewModel

@Composable
fun ScriptsScreen(vm: MainViewModel, onSendToExecutor: (String) -> Unit) {
    val sbScripts by vm.sbScripts.collectAsState()
    val loading   by vm.sbLoading.collectAsState()
    var query     by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AwpColors.Background)
            .padding(16.dp)
    ) {
        Text("Scripts", fontSize = 22.sp, fontWeight = FontWeight.Black,
            color = AwpColors.TextPrimary, modifier = Modifier.padding(bottom = 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwpTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search ScriptBlox",
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { vm.loadScriptBlox(query) },
                modifier = Modifier
                    .size(48.dp)
                    .background(AwpColors.Primary, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Outlined.Search, null, tint = AwpColors.OnPrimary)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AwpColors.Primary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sbScripts) { script ->
                    ScriptCard(script = script, onSendToExecutor = onSendToExecutor)
                }
                if (sbScripts.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.SearchOff, null, tint = AwpColors.TextMuted,
                                    modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No scripts found", color = AwpColors.TextMuted, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptCard(script: ScriptBloxScript, onSendToExecutor: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AwpColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AwpColors.Border)
    ) {
        Row(Modifier.padding(12.dp)) {
            if (script.imageUrl != null) {
                AsyncImage(
                    model = script.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(script.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = AwpColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    if (script.verified) {
                        Icon(Icons.Outlined.Verified, null, tint = AwpColors.Accent,
                            modifier = Modifier.size(14.dp).padding(start = 4.dp))
                    }
                }
                Text(script.game, fontSize = 12.sp, color = AwpColors.Primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Visibility, null, tint = AwpColors.TextMuted,
                        modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(script.views.toString(), fontSize = 11.sp, color = AwpColors.TextMuted)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { onSendToExecutor(script.script) },
                modifier = Modifier
                    .size(38.dp)
                    .background(AwpColors.Primary.copy(0.15f), RoundedCornerShape(10.dp))
                    .align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Outlined.Send, null, tint = AwpColors.Primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
