package com.example.feature.auth

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.kgvn.KgvnWebSessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    webSessionManager: KgvnWebSessionManager,
    logs: List<String>,
    onBack: () -> Unit
) {
    val sessionState by webSessionManager.sessionState.collectAsState()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đăng nhập KGVN", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("auth_back_button")) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Tải lại trang")
                    }
                    IconButton(onClick = { webSessionManager.logout() }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Đăng xuất", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // State header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Trạng thái kết nối", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (val state = sessionState) {
                            is KgvnWebSessionManager.SessionState.NotStarted -> {
                                Text("Chưa bắt đầu", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            is KgvnWebSessionManager.SessionState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Đang tải trang...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            is KgvnWebSessionManager.SessionState.Active -> {
                                Text("Đã liên kết thành công", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            is KgvnWebSessionManager.SessionState.Error -> {
                                Text("Lỗi: ${state.errorMsg}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    if (sessionState is KgvnWebSessionManager.SessionState.Active) {
                        val active = sessionState as KgvnWebSessionManager.SessionState.Active
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ID: ${active.campRoleId.take(8)}...", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("MsdkToken: ${if (active.msdkToken != null) "Đã nhận" else "Ngoại tuyến"}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // WebView Layout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { ctx ->
                        var localWv: WebView? = null
                        webSessionManager.initWebView { wv ->
                            localWv = wv
                            webViewInstance = wv
                        }
                        // Fallback in case init is async
                        localWv ?: WebView(ctx).apply {
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize().testTag("kgvn_webview"),
                    update = {
                        // WebView configuration updating is handled by SessionManager
                    }
                )
            }

            // Logs Console at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        "BÁO CÁO GIAO TIẾP WEB BRIDGE (MÃ HÓA BẢO MẬT)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Divider()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logs.reversed()) { log ->
                            Text(
                                log,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
