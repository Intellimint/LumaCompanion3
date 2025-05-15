package org.luma.lumacompanion3

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationLogger {
    private const val LOG_FILE_NAME = "conversation_log.enc"  // Encrypted

    fun saveEntry(
        context: Context,
        userInput: String,
        aiResponse: String,
        sentiment: String,
        flags: ModerationResult
    ) {
        try {
            // --- Encryption setup (created once per app install) ---
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val logFile = File(context.filesDir, LOG_FILE_NAME)
            val encryptedFile = EncryptedFile.Builder(
                context,
                logFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
            val json = JSONObject().apply {
                put("timestamp", timestamp)
                put("userInput", userInput)
                put("aiResponse", aiResponse)
                put("sentiment", sentiment)
                put("flagged", flags.flagged)
                put("selfHarmScore", flags.selfHarmScore)
                put("violenceScore", flags.violenceScore)
                put("hateScore", flags.hateScore)
                put("harassmentScore", flags.harassmentScore)
                put("overallSentiment", flags.overallSentiment)
            }
            
            encryptedFile.openFileOutput().use { it.write((json.toString() + "\n").toByteArray()) }
            Log.d("ConversationLogger", "Logged entry: $json")
        } catch (e: Exception) {
            Log.e("ConversationLogger", "Failed to log entry: ${e.message}")
        }
    }
    
    fun deleteAll(context: Context) {
        try {
            File(context.filesDir, LOG_FILE_NAME).delete()
            Log.d("ConversationLogger", "Deleted conversation log file")
        } catch (e: Exception) {
            Log.e("ConversationLogger", "Failed to delete log file: ${e.message}")
        }
    }
    
    fun exportToShareSheet(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (!logFile.exists()) {
                Log.d("ConversationLogger", "No log file to export")
                return
            }
            
            // Create master key for decryption
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                
            val encryptedFile = EncryptedFile.Builder(
                context,
                logFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            
            // Create temporary decrypted file for sharing
            val tempFile = File(context.cacheDir, "conversation_export.jsonl")
            if (tempFile.exists()) tempFile.delete()
            
            // Read encrypted content and write to temp file
            val encryptedInput = encryptedFile.openFileInput()
            val decryptedContent = encryptedInput.readBytes()
            tempFile.writeBytes(decryptedContent)
            
            // Create share intent
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_STREAM, 
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                )
                type = "application/json"
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Start share sheet
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Conversation Data"))
            
            // Schedule temp file deletion after sharing (it will persist during the share)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (tempFile.exists()) tempFile.delete()
            }, 60000) // Delete after 1 minute
            
            Log.d("ConversationLogger", "Started sharing log file")
        } catch (e: Exception) {
            Log.e("ConversationLogger", "Failed to share log file: ${e.message}")
        }
    }
} 