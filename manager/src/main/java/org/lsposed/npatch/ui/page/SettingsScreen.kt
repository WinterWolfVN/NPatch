package org.lsposed.npatch.ui.page

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Ballot
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import com.ramcosta.composedestinations.annotation.Destination
import kotlinx.coroutines.launch
import org.lsposed.npatch.R
import org.lsposed.npatch.config.Configs
import org.lsposed.npatch.config.MyKeyStore
import org.lsposed.npatch.ui.component.AnywhereDropdown
import org.lsposed.npatch.ui.component.CenterTopBar
import org.lsposed.npatch.ui.component.settings.SettingsItem
import org.lsposed.npatch.ui.component.settings.SettingsSwitch
import org.lsposed.npatch.ui.util.LocalSnackbarHost
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

private const val TAG = "SettingsScreen"

@Destination
@Composable
fun SettingsScreen() {
    Scaffold(
        topBar = { CenterTopBar(stringResource(BottomBarDestination.Settings.label)) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            KeyStore()
            Language()
            DetailPatchLogs()
            StorageDirectory()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyStore() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    AnywhereDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        onClick = { expanded = true },
        surface = {
            SettingsItem(
                icon = Icons.Outlined.Ballot,
                title = stringResource(R.string.settings_keystore),
                desc = stringResource(if (MyKeyStore.useDefault) R.string.settings_keystore_default else R.string.settings_keystore_custom)
            )
        }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings_keystore_default)) },
            onClick = {
                scope.launch { MyKeyStore.reset() }
                expanded = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings_keystore_custom)) },
            onClick = {
                expanded = false
                showDialog = true
            }
        )
    }

    if (showDialog) {
        var wrongKeystore by rememberSaveable { mutableStateOf(false) }
        var wrongPassword by rememberSaveable { mutableStateOf(false) }
        var wrongAliasName by rememberSaveable { mutableStateOf(false) }
        var wrongAliasPassword by rememberSaveable { mutableStateOf(false) }

        var path by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var alias by rememberSaveable { mutableStateOf("") }
        var aliasPassword by rememberSaveable { mutableStateOf("") }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.openInputStream(uri).use { input ->
                MyKeyStore.tmpFile.outputStream().use { output ->
                    input?.copyTo(output)
                }
            }
            path = uri.path ?: ""
        }

        AlertDialog(
            onDismissRequest = { expanded = false; showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        wrongKeystore = false
                        wrongPassword = false
                        wrongAliasName = false
                        wrongAliasPassword = false

                        if (path.isEmpty()) {
                            wrongKeystore = true
                            return@TextButton
                        }
                        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                        try {
                            MyKeyStore.tmpFile.inputStream().use { input ->
                                keyStore.load(input, password.toCharArray())
                            }
                        } catch (e: IOException) {
                            wrongKeystore = true
                            if (e.message == "KeyStore integrity check failed.") {
                                wrongPassword = true
                            }
                            return@TextButton
                        }
                        if (!keyStore.containsAlias(alias)) {
                            wrongAliasName = true
                            return@TextButton
                        }
                        try {
                            keyStore.getKey(alias, aliasPassword.toCharArray())
                        } catch (e: GeneralSecurityException) {
                            wrongAliasPassword = true
                            return@TextButton
                        }

                        scope.launch { MyKeyStore.setCustom(password, alias, aliasPassword) }
                        expanded = false
                        showDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { expanded = false; showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.settings_keystore_dialog_title),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect { interaction ->
                            if (interaction is PressInteraction.Release) {
                                launcher.launch("*/*")
                            }
                        }
                    }

                    // Error Message Handling
                    val wrongText = when {
                        wrongAliasPassword -> stringResource(R.string.settings_keystore_wrong_alias_password)
                        wrongAliasName -> stringResource(R.string.settings_keystore_wrong_alias)
                        wrongPassword -> stringResource(R.string.settings_keystore_wrong_password)
                        wrongKeystore -> stringResource(R.string.settings_keystore_wrong_keystore)
                        else -> null
                    }

                    Text(
                        modifier = Modifier.padding(bottom = 8.dp),
                        text = wrongText ?: stringResource(R.string.settings_keystore_desc),
                        color = if (wrongText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_keystore_file)) },
                        placeholder = { Text(stringResource(R.string.settings_keystore_file)) },
                        singleLine = true,
                        isError = wrongKeystore,
                        interactionSource = interactionSource,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.settings_keystore_password)) },
                        singleLine = true,
                        isError = wrongPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text(stringResource(R.string.settings_keystore_alias)) },
                        singleLine = true,
                        isError = wrongAliasName,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aliasPassword,
                        onValueChange = { aliasPassword = it },
                        label = { Text(stringResource(R.string.settings_keystore_alias_password)) },
                        singleLine = true,
                        isError = wrongAliasPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}

@Composable
private fun Language() {
    val systemDefault = stringResource(R.string.settings_language_system)
    val languages = remember(systemDefault) {
        linkedMapOf(
            "" to systemDefault,
            "af" to "Afrikaans",
            "ar" to "العربية",
            "bg" to "Български",
            "bn" to "বাংলা",
            "ca" to "Català",
            "cs" to "Čeština",
            "da" to "Dansk",
            "de" to "Deutsch",
            "el" to "Ελληνικά",
            "en" to "English",
            "es" to "Español",
            "et" to "Eesti",
            "fa" to "فارسی",
            "fi" to "Suomi",
            "fr" to "Français",
            "hi" to "हिन्दी",
            "hr" to "Hrvatski",
            "hu" to "Magyar",
            "in" to "Bahasa Indonesia",
            "it" to "Italiano",
            "iw" to "עברית",
            "ja" to "日本語",
            "ko" to "한국어",
            "ku" to "Kurdî",
            "lt" to "Lietuvių",
            "nl" to "Nederlands",
            "no" to "Norsk",
            "pl" to "Polski",
            "pt" to "Português",
            "pt-BR" to "Português (Brasil)",
            "ro" to "Română",
            "ru" to "Русский",
            "si" to "සිංහල",
            "sk" to "Slovenčina",
            "sv" to "Svenska",
            "th" to "ภาษาไทย",
            "tr" to "Türkçe",
            "uk" to "Українська",
            "ur" to "اردو",
            "vi" to "Tiếng Việt",
            "zh-CN" to "简体中文",
            "zh-HK" to "中文 (香港)",
            "zh-TW" to "繁體中文",
        )
    }
    

    var expanded by remember { mutableStateOf(false) }

    val currentTag = remember {        
            AppCompatDelegate.getApplicationLocales()
                .toLanguageTags()
                .takeIf { it.isNotEmpty() && it != "und" }
                ?: ""        
    }

    val currentLabel = remember(currentTag, systemDefault) {
        languages.entries.firstOrNull { (tag, _) ->
            tag.isNotEmpty() && currentTag.startsWith(tag)
        }?.value ?: systemDefault
    }

    Box {
        SettingsItem(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.settings_language),
            desc = currentLabel,
            modifier = Modifier.clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
            offset = DpOffset(x = 200.dp, y = 0.dp)
        ) {
            languages.forEach { (tag, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        val localeList = if (tag.isEmpty()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(tag)
                        }
                        AppCompatDelegate.setApplicationLocales(localeList)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailPatchLogs() {
    SettingsSwitch(
        modifier = Modifier.clickable { Configs.detailPatchLogs = !Configs.detailPatchLogs },
        checked = Configs.detailPatchLogs,
        icon = Icons.Outlined.BugReport,
        title = stringResource(R.string.settings_detail_patch_logs)
    )
}

@Composable
private fun StorageDirectory() {
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val errorText = stringResource(R.string.patch_select_dir_error)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        try {
            if (it.resultCode == Activity.RESULT_CANCELED) return@rememberLauncherForActivityResult
            val uri = it.data?.data ?: throw IOException("No data")
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Configs.storageDirectory = uri.toString()
            Log.i(TAG, "Storage directory: ${uri.path}")
        } catch (e: Exception) {
            Log.e(TAG, "Error when requesting saving directory", e)
            scope.launch { snackbarHost.showSnackbar(errorText) }
        }
    }
    SettingsItem(
        title = stringResource(R.string.settings_storage_directory),
        desc = Configs.storageDirectory ?: "undefined",
        icon = Icons.Outlined.Folder,
        modifier = Modifier.clickable { launcher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) }
    )
}
