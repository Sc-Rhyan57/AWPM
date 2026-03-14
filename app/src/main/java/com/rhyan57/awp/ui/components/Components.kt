package com.rhyan57.awp.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.rhyan57.awp.data.model.ConnectionState
import com.rhyan57.awp.ui.theme.AwpColors

@Composable
fun ConnectionBadge(state: ConnectionState) {
    val (color, label, icon) = when (state) {
        is ConnectionState.Idle           -> Triple(AwpColors.TextMuted,  "Offline",   Icons.Outlined.WifiOff)
        is ConnectionState.WaitingForClient -> Triple(AwpColors.Warning, "Waiting",   Icons.Outlined.HourglassEmpty)
        is ConnectionState.Connected      -> Triple(AwpColors.Success,   "Connected", Icons.Outlined.Wifi)
        is ConnectionState.Error          -> Triple(AwpColors.Error,     "Error",     Icons.Outlined.ErrorOutline)
    }
    Row(
        modifier = Modifier
            .background(color.copy(0.12f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(0.30f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AwpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AwpColors.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(0.3f)),
        shape = RoundedCornerShape(12.dp),
        enabled = enabled
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AwpColors.Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AwpColors.Border)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, null, tint = AwpColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = AwpColors.TextMuted, letterSpacing = 1.sp)
            }
            content()
        }
    }
}

@Composable
fun AwpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AwpColors.Primary,
            unfocusedBorderColor = AwpColors.Border,
            focusedLabelColor    = AwpColors.Primary,
            unfocusedLabelColor  = AwpColors.TextMuted,
            cursorColor          = AwpColors.Primary,
            focusedTextColor     = AwpColors.TextPrimary,
            unfocusedTextColor   = AwpColors.TextPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AwpColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = AwpColors.TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = AwpColors.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = AwpColors.OnPrimary,
                checkedTrackColor  = AwpColors.Primary,
                uncheckedTrackColor= AwpColors.Border
            )
        )
    }
}
