package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.FirebaseGeminiAuthoringService
import com.example.core.configureAppCheck
import com.example.data.prefs.StudioPreferences
import com.example.data.prefs.StudioUiPrefs
import com.example.data.repository.BookRepository
import com.example.data.repository.KnowledgeAndGovernanceRepository
import com.example.export.PublicationExporter
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BookWorkspaceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MeaningMattersTheme
import com.example.ui.viewmodel.BookWorkspaceViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_WORKSPACE = "workspace/{bookId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val studioPrefs = remember(context) { StudioPreferences(context.applicationContext) }
            val uiPrefs by studioPrefs.prefsFlow.collectAsStateWithLifecycle(initialValue = StudioUiPrefs())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (uiPrefs.darkModeOverride) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }

            MeaningMattersTheme(darkTheme = darkTheme) {
                MeaningMattersRoot(
                    studioPreferences = studioPrefs,
                    uiPrefs = uiPrefs
                )
            }
        }
    }
}

@Composable
private fun MeaningMattersRoot(
    studioPreferences: StudioPreferences,
    uiPrefs: StudioUiPrefs
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = user.uid
        val displayName = user.displayName.orEmpty().ifBlank { "Author" }
        val email = user.email.orEmpty()

        // Key composition & ViewModels by uid so signing out or switching accounts cleanly disposes Firestore listeners
        key(uid) {
            AuthenticatedAppNav(
                uid = uid,
                displayName = displayName,
                email = email,
                studioPreferences = studioPreferences,
                uiPrefs = uiPrefs,
                onSignedOut = { currentUser = null }
            )
        }
    }
}

@Composable
private fun AuthenticatedAppNav(
    uid: String,
    displayName: String,
    email: String,
    studioPreferences: StudioPreferences,
    uiPrefs: StudioUiPrefs,
    onSignedOut: () -> Unit
) {
    val navController = rememberNavController()

    val dashboardViewModel: DashboardViewModel = viewModel(
        key = "dashboard_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                DashboardViewModel(
                    bookRepository = BookRepository(db),
                    knowledgeRepository = KnowledgeAndGovernanceRepository(db),
                    currentUserId = uid,
                    displayName = displayName,
                    email = email
                )
            }
        }
    )

    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onOpenBook = { bookId ->
                    navController.navigate("workspace/$bookId")
                },
                onOpenSettings = {
                    navController.navigate(ROUTE_SETTINGS)
                }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                dashboardViewModel = dashboardViewModel,
                studioPreferences = studioPreferences,
                userEmail = email,
                onBack = { navController.popBackStack() },
                onSignedOut = onSignedOut
            )
        }

        composable(
            route = ROUTE_WORKSPACE,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
            val workspaceViewModel: BookWorkspaceViewModel = viewModel(
                key = "workspace_${uid}_$bookId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        BookWorkspaceViewModel(
                            bookRepository = BookRepository(db),
                            knowledgeRepository = KnowledgeAndGovernanceRepository(db),
                            aiService = FirebaseGeminiAuthoringService(),
                            exporter = PublicationExporter(app.applicationContext),
                            currentUserId = uid,
                            bookId = bookId,
                            authorDisplayName = displayName
                        )
                    }
                }
            )
            BookWorkspaceScreen(
                viewModel = workspaceViewModel,
                uiPrefs = uiPrefs,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
