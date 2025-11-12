package com.ayaspacexml.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.ayaspacexml.app.ui.theme.AYASpaceXMLTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        enableEdgeToEdge()
        setContent {
            AYASpaceXMLTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(prefs = prefs)
                }
            }
        }
    }
}

fun getDisplayNameFromUri(uriString: String?): String {
    if (uriString == null) return "Not Selected"
    return try {
        uriString.toUri().lastPathSegment?.substringAfterLast(':') ?: "Selected"
    } catch (e: Exception) {
        "Selected"
    }
}

@Composable
fun MainScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var fromPathUri by remember { mutableStateOf(prefs.getString("from_path", null)) }
    var toPathUri by remember { mutableStateOf(prefs.getString("to_path", null)) }
    var isCopying by remember { mutableStateOf(false) }
    var isSourceValid by remember { mutableStateOf(false) }
    var sourceChecked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun validateSourceFolder(context: Context, uriString: String?): Boolean {
        if (uriString == null) return false
        return withContext(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(context, uriString.toUri()) ?: return@withContext false
            val hasGamelists = tree.findFile("gamelists")?.isDirectory == true
            val hasMedia = tree.findFile("downloaded_media")?.isDirectory == true
            hasGamelists && hasMedia
        }
    }

    // Validate saved folder automatically on launch
    LaunchedEffect(fromPathUri) {
        if (fromPathUri != null && !sourceChecked) {
            val valid = validateSourceFolder(context, fromPathUri)
            isSourceValid = valid
            sourceChecked = true
        }
    }

    val fromPathLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val contentResolver = context.contentResolver
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                fromPathUri = uri.toString()
                prefs.edit { putString("from_path", fromPathUri) }

                coroutineScope.launch {
                    val valid = validateSourceFolder(context, fromPathUri)
                    isSourceValid = valid
                    if (!valid) {
                        Toast.makeText(
                            context,
                            "Selected folder must contain 'gamelists' and 'downloaded_media'.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Valid source folder selected.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    )

    val toPathLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val contentResolver = context.contentResolver
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                toPathUri = uri.toString()
                prefs.edit { putString("to_path", toPathUri) }
            }
        }
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "AYASpaceXML",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Text(
                text = "Copy ES-DE 'gamelist.xml' files and media assets ('image', 'thumbnail') to enable full AYASpace compatibility.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Source
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Source Folder",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Choose the folder containing your 'gamelists' and 'downloaded_media' directories (e.g., ES-DE folder)",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = getDisplayNameFromUri(fromPathUri),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { fromPathLauncher.launch(null) },
                        modifier = Modifier.widthIn(min = 160.dp)
                    ) {
                        Text("Select Source")
                    }

                    if (fromPathUri != null && !isSourceValid && sourceChecked) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "⚠ Folder must contain 'gamelists' and 'downloaded_media'.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Destination
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Destination Folder",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Choose the folder with your system subdirectories (e.g., 'nds', '3ds'). Gamelists will be copied into each system folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = getDisplayNameFromUri(toPathUri),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { toPathLauncher.launch(null) },
                        modifier = Modifier.widthIn(min = 160.dp)
                    ) {
                        Text("Select Destination")
                    }
                }
            }

            // Copy button
            Button(
                onClick = {
                    if (fromPathUri != null && toPathUri != null) {
                        isCopying = true
                        coroutineScope.launch {
                            GamelistCopier.copyGamelists(context, fromPathUri!!, toPathUri!!)
                            isCopying = false
                            Toast.makeText(
                                context,
                                "Gamelists copied successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                enabled = fromPathUri != null && toPathUri != null && !isCopying && isSourceValid,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isCopying) "Copying..." else "Copy Gamelists",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
