package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.signOut
import com.example.data.prefs.StudioPreferences
import com.example.data.prefs.StudioUiPrefs
import com.example.ui.components.EditorialCard
import com.example.ui.components.GovernanceNoticeBanner
import com.example.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dashboardViewModel: DashboardViewModel,
    studioPreferences: StudioPreferences,
    userEmail: String,
    onBack: () -> Unit,
    onSignedOut: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profile by dashboardViewModel.userProfile.collectAsStateWithLifecycle()
    val uiPrefs by studioPreferences.prefsFlow.collectAsStateWithLifecycle(initialValue = StudioUiPrefs())

    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var penName by remember(profile?.defaultPenName) { mutableStateOf(profile?.defaultPenName.orEmpty()) }
    var fontSize by remember(uiPrefs.editorFontSizeSp) { mutableFloatStateOf(uiPrefs.editorFontSizeSp.toFloat()) }
    var dyslexiaSpacing by remember(profile?.dyslexiaFriendlySpacing) { mutableStateOf(profile?.dyslexiaFriendlySpacing ?: false) }
    var focusDefault by remember(uiPrefs.focusModeDefault) { mutableStateOf(uiPrefs.focusModeDefault) }
    var autoSave by remember(uiPrefs.autoSaveEnabled) { mutableStateOf(uiPrefs.autoSaveEnabled) }
    var showDeleteAccountDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings & Security", style = MaterialTheme.typography.headlineSmall) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                EditorialCard(
                    title = "Author Profile",
                    subtitle = userEmail.ifBlank { "Authenticated via Google Sign-In" }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Author Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = penName,
                            onValueChange = { penName = it },
                            label = { Text("Default Pen Name (Title & Copyright Pages)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                EditorialCard(
                    title = "Editorial Studio & Accessibility",
                    subtitle = "Calm typography and distraction-free writing defaults"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Theme Appearance", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SYSTEM" to "System", "LIGHT" to "Parchment", "DARK" to "Midnight").forEach { (key, label) ->
                                FilterChip(
                                    selected = uiPrefs.darkModeOverride == key,
                                    onClick = { scope.launch { studioPreferences.setDarkModeOverride(key) } },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Text("Manuscript Editor Font Size: ${fontSize.toInt()}sp", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 14f..26f,
                            steps = 11
                        )

                        SettingToggleRow("Dyslexia-Friendly Line Spacing", dyslexiaSpacing) { dyslexiaSpacing = it }
                        SettingToggleRow("Open Studio in Focus Mode by Default", focusDefault) { focusDefault = it }
                        SettingToggleRow("Cloud Autosave Enabled", autoSave) { autoSave = it }

                        Button(
                            onClick = {
                                scope.launch {
                                    studioPreferences.setEditorFontSize(fontSize.toInt())
                                    studioPreferences.setFocusModeDefault(focusDefault)
                                    studioPreferences.setAutoSaveEnabled(autoSave)
                                }
                                dashboardViewModel.updateProfileSettings(
                                    displayName = displayName,
                                    penName = penName,
                                    fontSizeSp = fontSize.toLong(),
                                    dyslexiaSpacing = dyslexiaSpacing,
                                    focusMode = focusDefault,
                                    autoSave = autoSave
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_settings_button")
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save Preferences")
                        }
                    }
                }
            }

            item {
                EditorialCard(
                    title = "Security & Data Isolation",
                    subtitle = "Zero-trust Cloud Firestore rules + App Check AI attestation"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GovernanceNoticeBanner(
                            text = "All books, chapters, checkpoints, Knowledge Codex entries, and research sources are isolated strictly under your authenticated UID in Cloud Firestore. Gemini inference runs through Firebase AI Logic with App Check — no client API keys are exposed."
                        )
                        OutlinedButton(
                            onClick = {
                                signOut(credentialManager, scope, onSignOutComplete = onSignedOut)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                        OutlinedButton(
                            onClick = { showDeleteAccountDataDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("delete_account_data_button")
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete All Cloud Manuscripts & Profile")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAccountDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDataDialog = false },
            title = { Text("Delete All Cloud Data?") },
            text = {
                Text("This permanently erases your MeaningMatters profile, all books, chapters, version checkpoints, research sources, and reviews from Cloud Firestore. Export a JSON backup first if you wish to keep a local copy.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteAccountDataDialog = false
                        dashboardViewModel.deleteAllCloudData {
                            signOut(credentialManager, scope, onSignOutComplete = onSignedOut)
                        }
                    }
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
