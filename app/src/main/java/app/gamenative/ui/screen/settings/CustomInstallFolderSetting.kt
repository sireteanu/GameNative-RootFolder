package app.gamenative.ui.screen.settings

import android.os.Environment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import app.gamenative.ui.component.settings.SettingsMenuLink
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.CustomGameScanner
import java.io.File

/**
 * GameNative-RootFolder fork: lets the user pick any folder (including primary internal shared
 * storage such as /storage/emulated/0) as the install root. Games are installed to
 * <root>/Steam/steamapps/common, <root>/GOG/..., <root>/Epic/..., <root>/Amazon/...
 *
 * This reuses upstream's external-storage plumbing (useExternalStorage + externalStoragePath),
 * so no other code paths need to change.
 */
@Composable
fun CustomInstallFolderSetting() {
    val context = LocalContext.current
    val defaultRoot = remember { Environment.getExternalStorageDirectory().absolutePath }

    var current by remember {
        mutableStateOf(if (PrefManager.useExternalStorage) PrefManager.externalStoragePath else "")
    }
    var showDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    SettingsMenuLink(
        colors = settingsTileColorsAlt(),
        title = { Text(text = "Custom install folder") },
        subtitle = {
            Text(
                text = if (current.isBlank()) {
                    "Off — games install inside the app (fastest)"
                } else {
                    "Steam games go to ${File(current, "Steam").absolutePath}"
                },
            )
        },
        onClick = {
            input = current.ifBlank { defaultRoot }
            showDialog = true
        },
    )

    if (!showDialog) return

    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text("Custom install folder") },
        text = {
            Column {
                Text(
                    "Root folder for game installs. Steam games go to <root>/Steam/steamapps/common. " +
                        "Shared storage is slower than the app's private folder for file-heavy games, " +
                        "but games survive uninstalling the app.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Folder path") },
                    placeholder = { Text(defaultRoot) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Already-installed games are not moved. Leave empty and Save to turn this off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val path = input.trim().trimEnd('/')
                if (path.isEmpty()) {
                    // setter also clears externalStoragePath
                    PrefManager.useExternalStorage = false
                    current = ""
                    showDialog = false
                    SnackbarManager.show("Custom install folder turned off")
                    return@TextButton
                }

                if (!CustomGameScanner.hasStoragePermission(context, path)) {
                    SnackbarManager.show("Allow \"All files access\" for this app, then come back and press Save again")
                    CustomGameScanner.requestManageExternalStoragePermission(context)
                    return@TextButton
                }

                val dir = File(path)
                // plain mkdirs: no .nomedia here, since the root may be /storage/emulated/0 itself
                if (!(dir.isDirectory || dir.mkdirs()) || !dir.canWrite()) {
                    SnackbarManager.show("Can't write to $path")
                    return@TextButton
                }

                // order matters: the useExternalStorage setter resets externalStoragePath
                PrefManager.useExternalStorage = true
                PrefManager.externalStoragePath = dir.absolutePath
                current = dir.absolutePath
                showDialog = false
                SnackbarManager.show("New installs will go to ${dir.absolutePath}")
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { showDialog = false }) { Text("Cancel") }
        },
    )
}
