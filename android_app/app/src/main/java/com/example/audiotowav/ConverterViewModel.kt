package com.example.audiotowav

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

data class ConversionTask(
    val uri: Uri,
    val inputFileName: String,
    var status: String = "Pending",
    var isProcessing: Boolean = false
)

class ConverterViewModel : ViewModel() {

    var selectedTasks = mutableStateListOf<ConversionTask>()
    var isConverting by mutableStateOf(false)
    var statusMessage by mutableStateOf("Select audio files to begin")
    var logOutput by mutableStateOf("")

    var sampleRate by mutableStateOf("44100")
    var channels by mutableStateOf("2")

    fun addUris(context: Context, uris: List<Uri>) {
        uris.forEach { uri ->
            val fileName = getFileName(context, uri) ?: "unknown_file"
            selectedTasks.add(ConversionTask(uri, fileName))
        }
        statusMessage = "${selectedTasks.size} files selected"
    }

    fun clearSelection() {
        selectedTasks.clear()
        statusMessage = "Select audio files to begin"
    }

    fun convert(context: Context) {
        if (selectedTasks.isEmpty()) return

        isConverting = true
        statusMessage = "Starting conversion..."
        logOutput = ""

        viewModelScope.launch(Dispatchers.IO) {
            for (index in selectedTasks.indices) {
                val task = selectedTasks[index]
                updateTask(index, task.copy(isProcessing = true, status = "Converting..."))

                try {
                    val inputPath = getPathFromUri(context, task.uri) ?: throw Exception("Could not resolve file path")
                    
                    // Create temporary output file
                    val tempOutputFile = File(context.cacheDir, "temp_output_${System.currentTimeMillis()}.wav")
                    
                    val command = StringBuilder("-y -i \"$inputPath\"")
                    if (sampleRate.isNotEmpty()) {
                        command.append(" -ar $sampleRate")
                    }
                    if (channels.isNotEmpty()) {
                        command.append(" -ac $channels")
                    }
                    command.append(" \"${tempOutputFile.absolutePath}\"")

                    val session = FFmpegKit.execute(command.toString())
                    
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        val outputUri = saveToMediaStore(context, tempOutputFile, task.inputFileName.substringBeforeLast(".") + ".wav")
                        if (outputUri != null) {
                            updateTask(index, task.copy(isProcessing = false, status = "Success"))
                        } else {
                            updateTask(index, task.copy(isProcessing = false, status = "Save Failed"))
                        }
                    } else {
                        updateTask(index, task.copy(isProcessing = false, status = "Failed"))
                        logOutput += "Error converting ${task.inputFileName}: ${session.failStackTrace}\n"
                    }
                    
                    // Cleanup temp files
                    File(inputPath).delete()
                    tempOutputFile.delete()

                } catch (e: Exception) {
                    updateTask(index, task.copy(isProcessing = false, status = "Error"))
                    logOutput += "Error converting ${task.inputFileName}: ${e.message}\n"
                }
            }
            isConverting = false
            statusMessage = "All conversions finished"
        }
    }

    private fun updateTask(index: Int, newTask: ConversionTask) {
        viewModelScope.launch(Dispatchers.Main) {
            if (index < selectedTasks.size) {
                selectedTasks[index] = newTask
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            uri.path?.substringAfterLast('/')
        } ?: uri.path?.substringAfterLast('/')
    }

    private fun getPathFromUri(context: Context, uri: Uri): String? {
        return try {
            val tempFile = File(context.cacheDir, "temp_input_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToMediaStore(context: Context, sourceFile: File, targetFileName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AudioToWav")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)

        return uri?.also {
            try {
                resolver.openOutputStream(it)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
            } catch (e: Exception) {
                resolver.delete(it, null, null)
                return null
            }
        }
    }
}
