package com.example.jarvis.engine

import android.content.Context
import android.os.Environment
import com.example.jarvis.model.JarvisFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class JarvisFileManager(private val context: Context) {

    private val workspaceRoot: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "JARVIS_Storage").apply {
            if (!exists()) mkdirs()
        }
    }

    val downloadsDir: File
        get() = File(workspaceRoot, "Downloads").apply { if (!exists()) mkdirs() }

    val documentsDir: File
        get() = File(workspaceRoot, "Documents").apply { if (!exists()) mkdirs() }

    val notesDir: File
        get() = File(workspaceRoot, "Notes").apply { if (!exists()) mkdirs() }

    suspend fun ensureSeedFiles() = withContext(Dispatchers.IO) {
        val dl = downloadsDir
        if (dl.listFiles().isNullOrEmpty()) {
            File(dl, "Vivo_Y21_5G_Specifications.pdf").writeText(
                "Vivo Y21 5G Specifications:\nDisplay: 6.51-inch HD+ Halo FullView\nProcessor: MediaTek Dimensity 700 5G\nRAM/Storage: 4GB/8GB RAM, 128GB Storage\nBattery: 5000mAh with 18W Fast Charge\nCamera: 50MP Rear + 8MP Front"
            )
            File(dl, "Android_AI_Agent_Guide.pdf").writeText(
                "JARVIS Android AI Architecture:\nObserve -> Plan -> Execute -> Verify -> Self-Correct.\nUses AccessibilityService, NotificationListenerService, and Gemini Reasoning."
            )
            File(dl, "Sprint_Report_Q4.pdf").writeText(
                "Quarterly Engineering Report - Completed milestones and voice automation benchmarks."
            )
            File(dl, "Voice_Command_CheatSheet.txt").writeText(
                "Sample Commands:\n1. Hey Jarvis, YouTube kholo\n2. Volume 40% karo\n3. WhatsApp pe Rahul ko message bhejo\n4. PDF files dhundo"
            )
        }
        val docs = documentsDir
        if (docs.listFiles().isNullOrEmpty()) {
            File(docs, "Meeting_Notes_Rahul.txt").writeText(
                "Discussion with Rahul regarding project deployment at 6 PM."
            )
            File(docs, "Monthly_Budget_2026.csv").writeText(
                "Category,Amount\nCloud_API,1200\nHardware,4500\nOperations,2100"
            )
        }
    }

    suspend fun listFilesInFolder(folderName: String? = null): List<JarvisFileItem> = withContext(Dispatchers.IO) {
        ensureSeedFiles()
        val targetFolder = when (folderName?.trim()?.lowercase()) {
            "downloads", "download" -> downloadsDir
            "documents", "document", "docs" -> documentsDir
            "notes", "note" -> notesDir
            null, "", "all", "root" -> workspaceRoot
            else -> {
                val custom = File(workspaceRoot, folderName)
                if (custom.exists() && custom.isDirectory) custom else workspaceRoot
            }
        }

        val collected = mutableListOf<File>()
        if (targetFolder == workspaceRoot) {
            // Include top-level folders and recursively their files
            workspaceRoot.listFiles()?.forEach { f ->
                collected.add(f)
                if (f.isDirectory) {
                    f.listFiles()?.forEach { child -> collected.add(child) }
                }
            }
            // Also check public Android Downloads folder if readable
            runCatching {
                val pubDl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                pubDl?.listFiles()?.take(15)?.forEach { collected.add(it) }
            }
        } else {
            targetFolder.listFiles()?.forEach { collected.add(it) }
        }

        collected
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenByDescending { it.lastModified() })
            .map { file -> file.toJarvisFileItem() }
    }

    suspend fun searchFiles(query: String, fileType: String? = null): List<JarvisFileItem> = withContext(Dispatchers.IO) {
        ensureSeedFiles()
        val all = listFilesInFolder("all").filter { !it.isDirectory }
        val qLower = query.trim().lowercase()
        val typeLower = fileType?.trim()?.lowercase() ?: ""

        all.filter { item ->
            val matchesType = when {
                typeLower.isBlank() -> true
                typeLower.contains("pdf") -> item.extension.equals("pdf", ignoreCase = true)
                typeLower.contains("txt") || typeLower.contains("text") || typeLower.contains("doc") ->
                    item.extension.lowercase() in listOf("txt", "doc", "docx", "md")
                typeLower.contains("image") || typeLower.contains("photo") ->
                    item.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
                else -> item.extension.equals(typeLower, ignoreCase = true)
            }
            val matchesQuery = qLower.isBlank() ||
                qLower == "all" ||
                qLower == "pdf" ||
                item.name.lowercase().contains(qLower) ||
                item.categoryLabel.lowercase().contains(qLower)
            matchesType && matchesQuery
        }
    }

    suspend fun createFolder(folderName: String): SystemActionOutcome = withContext(Dispatchers.IO) {
        val clean = folderName.trim().replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
        if (clean.isBlank()) {
            return@withContext SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Invalid folder name.",
                verificationDetail = "Folder name was empty.",
                spokenFeedback = "Please provide a valid folder name."
            )
        }
        val target = File(workspaceRoot, clean)
        val created = if (target.exists()) true else target.mkdirs()
        val verified = target.exists() && target.isDirectory
        SystemActionOutcome(
            executed = created,
            verified = verified,
            summary = "Created folder '$clean' in JARVIS Workspace.",
            verificationDetail = "Verified directory exists at ${target.absolutePath} (exists=$verified).",
            spokenFeedback = if (verified) "Folder $clean create kar diya hai." else "Could not create folder $clean."
        )
    }

    suspend fun renameFile(oldNameQuery: String, newName: String): SystemActionOutcome = withContext(Dispatchers.IO) {
        ensureSeedFiles()
        val allFiles = listFilesInFolder("all")
        val targetItem = allFiles.firstOrNull {
            it.name.equals(oldNameQuery.trim(), ignoreCase = true) ||
                it.name.lowercase().contains(oldNameQuery.trim().lowercase())
        } ?: return@withContext SystemActionOutcome(
            executed = false,
            verified = false,
            summary = "File '$oldNameQuery' not found.",
            verificationDetail = "Searched workspace; no file matched '$oldNameQuery'.",
            spokenFeedback = "Mujhe $oldNameQuery naam ki file nahi mili."
        )

        val srcFile = File(targetItem.path)
        val ext = srcFile.extension
        val finalNewName = if (newName.contains(".") || ext.isBlank()) newName.trim() else "${newName.trim()}.$ext"
        val destFile = File(srcFile.parentFile, finalNewName)
        val ok = srcFile.renameTo(destFile)
        val verified = destFile.exists() && !srcFile.exists()

        SystemActionOutcome(
            executed = ok,
            verified = verified,
            summary = "Renamed '${srcFile.name}' to '${destFile.name}'.",
            verificationDetail = "Verified '${destFile.name}' exists=${destFile.exists()} and old file removed=${!srcFile.exists()}.",
            spokenFeedback = if (verified) "File ka naam change karke $finalNewName kar diya hai." else "I couldn't rename that file."
        )
    }

    suspend fun copyOrMoveFile(
        sourceQuery: String,
        destFolderName: String,
        isMove: Boolean
    ): SystemActionOutcome = withContext(Dispatchers.IO) {
        ensureSeedFiles()
        val allFiles = listFilesInFolder("all").filter { !it.isDirectory }
        val targetItem = allFiles.firstOrNull {
            it.name.lowercase().contains(sourceQuery.trim().lowercase())
        } ?: return@withContext SystemActionOutcome(
            executed = false,
            verified = false,
            summary = "Source file '$sourceQuery' not found.",
            verificationDetail = "No matching file in workspace.",
            spokenFeedback = "Source file nahi mili."
        )

        val srcFile = File(targetItem.path)
        val destFolder = when (destFolderName.trim().lowercase()) {
            "downloads", "download" -> downloadsDir
            "documents", "docs" -> documentsDir
            "notes" -> notesDir
            else -> File(workspaceRoot, destFolderName.trim()).apply { if (!exists()) mkdirs() }
        }
        val destFile = File(destFolder, srcFile.name)

        return@withContext try {
            srcFile.copyTo(destFile, overwrite = true)
            if (isMove && srcFile.absolutePath != destFile.absolutePath) {
                srcFile.delete()
            }
            val verified = destFile.exists() && (!isMove || !srcFile.exists() || srcFile.absolutePath == destFile.absolutePath)
            val verb = if (isMove) "Moved" else "Copied"
            SystemActionOutcome(
                executed = true,
                verified = verified,
                summary = "$verb '${srcFile.name}' to '${destFolder.name}'.",
                verificationDetail = "Verified '${destFile.name}' exists in '${destFolder.name}' (${destFile.length()} bytes).",
                spokenFeedback = "$verb ${srcFile.name} to ${destFolder.name}."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "File operation failed: ${e.localizedMessage}",
                verificationDetail = "Exception: ${e.localizedMessage}",
                spokenFeedback = "File operation complete nahi ho paya."
            )
        }
    }

    suspend fun deleteFile(targetQuery: String): SystemActionOutcome = withContext(Dispatchers.IO) {
        ensureSeedFiles()
        val allFiles = listFilesInFolder("all").filter { !it.isDirectory }
        val targetItem = allFiles.firstOrNull {
            it.name.equals(targetQuery.trim(), ignoreCase = true) ||
                it.name.lowercase().contains(targetQuery.trim().lowercase())
        } ?: return@withContext SystemActionOutcome(
            executed = false,
            verified = false,
            summary = "File '$targetQuery' not found to delete.",
            verificationDetail = "No matching file for '$targetQuery'.",
            spokenFeedback = "Delete karne ke liye $targetQuery file nahi mili."
        )

        val file = File(targetItem.path)
        val deleted = file.delete()
        val verified = !file.exists()
        SystemActionOutcome(
            executed = deleted,
            verified = verified,
            summary = "Deleted file '${targetItem.name}'.",
            verificationDetail = "Verified file.exists() == ${file.exists()} after delete.",
            spokenFeedback = if (verified) "${targetItem.name} delete kar di gayi hai." else "File delete nahi ho saki."
        )
    }

    suspend fun readFilePreview(path: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(path)
            if (f.exists() && f.isFile) {
                f.readText().take(800)
            } else {
                "File cannot be previewed."
            }
        }.getOrDefault("Unable to read file contents.")
    }

    private fun File.toJarvisFileItem(): JarvisFileItem {
        val ext = extension.lowercase()
        val cat = when {
            isDirectory -> "Folder"
            ext == "pdf" -> "PDF Document"
            ext in listOf("txt", "md", "doc", "docx") -> "Text / Note"
            ext in listOf("csv", "xls", "xlsx") -> "Spreadsheet"
            ext in listOf("jpg", "jpeg", "png", "webp") -> "Image"
            else -> "File"
        }
        return JarvisFileItem(
            name = name,
            path = absolutePath,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) (listFiles()?.size?.toLong() ?: 0L) else length(),
            lastModifiedMillis = lastModified(),
            extension = ext,
            categoryLabel = cat
        )
    }
}
