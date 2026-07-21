package com.example

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.data.kgvn.KgvnWebSessionManager
import com.example.data.project.ProjectDatabase
import com.example.data.project.ProjectRepository
import com.example.domain.model.Project
import com.example.feature.auth.AuthScreen
import com.example.feature.editor.EditorScreen
import com.example.feature.editor.PosterEditorViewModel
import com.example.feature.home.HomeScreen
import com.example.feature.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Screen {
    HOME, AUTH, EDITOR, SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var projectDatabase: ProjectDatabase
    private lateinit var projectRepository: ProjectRepository
    private lateinit var webSessionManager: KgvnWebSessionManager
    private lateinit var viewModel: PosterEditorViewModel

    // Web bridge logger
    private val logs = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize DB and Repositories
        projectDatabase = ProjectDatabase.getDatabase(this)
        projectRepository = ProjectRepository(projectDatabase.projectDao())
        
        // 2. Initialize KGVN Web Session Manager with logging
        webSessionManager = KgvnWebSessionManager(this) { message ->
            runOnUiThread {
                logs.add("[${System.currentTimeMillis() % 100000}] $message")
                if (logs.size > 200) {
                    logs.removeRange(0, 50)
                }
            }
        }

        // 3. Initialize ViewModel using custom Factory
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PosterEditorViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PosterEditorViewModel(projectRepository, webSessionManager) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        viewModel = ViewModelProvider(this, factory)[PosterEditorViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.HOME) }
                    val projects by projectRepository.allProjects.collectAsState(initial = emptyList())
                    val sessionState by webSessionManager.sessionState.collectAsState()

                    val sessionText = when (val state = sessionState) {
                        is KgvnWebSessionManager.SessionState.NotStarted -> "Chưa liên kết"
                        is KgvnWebSessionManager.SessionState.Loading -> "Đang kiểm tra..."
                        is KgvnWebSessionManager.SessionState.Active -> "Đã đăng nhập (ID: ${state.campRoleId.take(6)})"
                        is KgvnWebSessionManager.SessionState.Error -> "Lỗi kết nối"
                    }

                    when (currentScreen) {
                        Screen.HOME -> {
                            HomeScreen(
                                projects = projects,
                                onProjectClick = { projectId ->
                                    viewModel.loadProject(projectId)
                                    currentScreen = Screen.EDITOR
                                },
                                onDeleteProject = { projectId ->
                                    lifecycleScopeLaunch {
                                        projectRepository.deleteProjectById(projectId)
                                        Toast.makeText(this@MainActivity, "Đã xóa dự án", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onCreateNew = {
                                    viewModel.updateProjectState { Project(name = "Poster mới ${projects.size + 1}") }
                                    currentScreen = Screen.EDITOR
                                },
                                onOpenUrl = { url ->
                                    webSessionManager.loadUrl(url)
                                    currentScreen = Screen.AUTH
                                },
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                onNavigateToAuth = { currentScreen = Screen.AUTH },
                                sessionStateText = sessionText
                            )
                        }
                        Screen.AUTH -> {
                            AuthScreen(
                                webSessionManager = webSessionManager,
                                logs = logs,
                                onBack = { currentScreen = Screen.HOME }
                            )
                        }
                        Screen.EDITOR -> {
                            EditorScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = Screen.HOME }
                            )
                        }
                        Screen.SETTINGS -> {
                            SettingsScreen(
                                onBack = { currentScreen = Screen.HOME },
                                onClearAllData = {
                                    lifecycleScopeLaunch {
                                        // Clear Database
                                        projectDatabase.clearAllTables()
                                        // Clear Cookies
                                        CookieManager.getInstance().removeAllCookies(null)
                                        webSessionManager.logout()
                                        Toast.makeText(this@MainActivity, "Đã xóa toàn bộ dữ liệu ứng dụng", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        // Use CoroutineScope associated with MainActivity lifecycle
        runOnUiThread {
            lifecycleScope.launch(Dispatchers.IO) {
                block()
            }
        }
    }
}
