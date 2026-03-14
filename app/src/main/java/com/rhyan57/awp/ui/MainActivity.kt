package com.rhyan57.awp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.rhyan57.awp.ui.screens.*
import com.rhyan57.awp.ui.theme.*
import com.rhyan57.awp.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme(colorScheme = AwpColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = AwpColors.Background) {
                    AwpApp(vm)
                }
            }
        }
    }
}

@Composable
fun AwpApp(vm: MainViewModel) {
    var currentTab        by remember { mutableStateOf(0) }
    var executorOpen      by remember { mutableStateOf(false) }
    val editorContent     by vm.editorContent.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AwpColors.Background,
            bottomBar = {
                NavigationBar(
                    containerColor = AwpColors.Surface,
                    tonalElevation = 0.dp
                ) {
                    listOf(
                        Triple(Icons.Outlined.Home,       "Home",     0),
                        Triple(Icons.Outlined.Terminal,   "Executor", 1),
                        Triple(Icons.Outlined.Code,       "Scripts",  2),
                        Triple(Icons.Outlined.FolderOpen, "Mine",     3),
                        Triple(Icons.Outlined.Settings,   "Settings", 4)
                    ).forEach { (icon, label, idx) ->
                        NavigationBarItem(
                            selected = currentTab == idx,
                            onClick  = { currentTab = idx },
                            icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = AwpColors.Primary,
                                selectedTextColor   = AwpColors.Primary,
                                unselectedIconColor = AwpColors.TextMuted,
                                unselectedTextColor = AwpColors.TextMuted,
                                indicatorColor      = AwpColors.Primary.copy(0.12f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "tab"
                ) { tab ->
                    when (tab) {
                        0 -> HomeScreen(vm, onOpenExecutor = { currentTab = 1 })
                        1 -> ExecutorScreen(vm)
                        2 -> ScriptsScreen(vm, onSendToExecutor = { script ->
                                vm.setEditorContent(script)
                                currentTab = 1
                            })
                        3 -> MyScriptsScreen(vm, onSendToExecutor = { script ->
                                vm.setEditorContent(script)
                                currentTab = 1
                            })
                        4 -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}
