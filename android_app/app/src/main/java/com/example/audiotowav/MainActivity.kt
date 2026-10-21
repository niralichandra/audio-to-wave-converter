package com.example.audiotowav

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConverterScreen()
                }
            }
        }
    }
}

@Composable
fun ConverterScreen(viewModel: ConverterViewModel = viewModel()) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addUris(context, uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Audio to WAV Converter",
            style = MaterialTheme.typography.headlineMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { launcher.launch("audio/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Select Audio Files")
            }
            
            if (viewModel.selectedTasks.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.clearSelection() },
                    modifier = Modifier.weight(0.5f)
                ) {
                    Text("Clear")
                }
            }
        }

        if (viewModel.selectedTasks.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(viewModel.selectedTasks) { task ->
                        TaskItem(task)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No files selected", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            }
        }

        SettingsPanel(viewModel)

        Button(
            onClick = { viewModel.convert(context) },
            enabled = viewModel.selectedTasks.isNotEmpty() && !viewModel.isConverting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (viewModel.isConverting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Converting...")
            } else {
                Text("Convert to WAV")
            }
        }

        Text(
            text = viewModel.statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = if (viewModel.statusMessage.contains("Error") || viewModel.statusMessage.contains("Failed")) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
fun TaskItem(task: ConversionTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.inputFileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = task.status,
                style = MaterialTheme.typography.labelSmall,
                color = when (task.status) {
                    "Success" -> Color(0xFF4CAF50)
                    "Failed", "Error", "Save Failed" -> MaterialTheme.colorScheme.error
                    "Converting..." -> MaterialTheme.colorScheme.primary
                    else -> Color.Gray
                }
            )
        }
        
        if (task.isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (task.status == "Success") {
            Icon(Icons.Default.Done, contentDescription = "Success", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SettingsPanel(viewModel: ConverterViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = viewModel.sampleRate,
                onValueChange = { viewModel.sampleRate = it },
                label = { Text("Sample Rate") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            OutlinedTextField(
                value = viewModel.channels,
                onValueChange = { viewModel.channels = it },
                label = { Text("Channels") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}
