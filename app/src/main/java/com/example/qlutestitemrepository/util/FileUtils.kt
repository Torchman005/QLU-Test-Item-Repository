package com.example.qlutestitemrepository.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppFile(
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val length: Long,
    val file: File? = null,
    val documentFile: DocumentFile? = null
) {
    val extension: String
        get() = name.substringAfterLast('.', "")
}

object FileUtils {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_SAF_URI = "saf_uri"

    fun getTestsRoot(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "tests")
    }

    fun listFiles(context: Context, subPath: String = ""): List<AppFile> {
        // 1. Try standard File API (MANAGE_EXTERNAL_STORAGE)
        if (Environment.isExternalStorageManager()) {
             val root = getTestsRoot()
             val targetDir = if (subPath.isEmpty()) root else File(root, subPath)
             if (!targetDir.exists()) targetDir.mkdirs()
             
             return targetDir.listFiles()?.map { 
                 AppFile(it.name, it.isDirectory, it.lastModified(), it.length(), file = it)
             }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        }

        // 2. Try SAF (DocumentFile)
        val safUriStr = getSafUri(context)
        if (safUriStr != null) {
            val treeUri = Uri.parse(safUriStr)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc != null && rootDoc.canRead()) {
                // Navigate to subPath
                var targetDoc = rootDoc
                if (subPath.isNotEmpty()) {
                    val parts = subPath.split("/")
                    for (part in parts) {
                        targetDoc = targetDoc?.findFile(part)
                        if (targetDoc == null) break
                    }
                }

                return targetDoc?.listFiles()?.map { 
                    AppFile(it.name ?: "", it.isDirectory, it.lastModified(), it.length(), documentFile = it)
                }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
            }
        }

        return emptyList()
    }
    
    fun saveSafUri(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_SAF_URI, uri.toString())
        }
    }

    fun getSafUri(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SAF_URI, null)
    }

    fun deleteFile(context: Context, appFile: AppFile): Boolean {
        return try {
            if (appFile.file != null) {
                if (appFile.file.isDirectory) appFile.file.deleteRecursively() else appFile.file.delete()
            } else if (appFile.documentFile != null) {
                appFile.documentFile.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // Legacy helper for other screens using File directly (might need refactor later)
    fun listFilesLegacy(subPath: String = ""): List<File> {
         val root = getTestsRoot()
         val targetDir = if (subPath.isEmpty()) root else File(root, subPath)
         if (!targetDir.exists()) targetDir.mkdirs()
         return targetDir.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun openFileDirectly(context: Context, appFile: AppFile) {
        try {
            val uri = if (appFile.file != null) {
                 getFileUri(context, appFile.file)
            } else if (appFile.documentFile != null) {
                 appFile.documentFile.uri
            } else {
                return
            }
            
            val mimeType = if (appFile.file != null) getMimeType(appFile.file) else appFile.documentFile?.type ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法直接打开文件，请尝试'用其他应用打开'", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFiles(context: Context, appFiles: List<AppFile>) {
        if (appFiles.isEmpty()) return

        try {
            val uris = ArrayList<Uri>()
            var mimeType = "*/*"
            
            appFiles.forEach { appFile ->
                val uri = if (appFile.file != null) {
                     getFileUri(context, appFile.file)
                } else if (appFile.documentFile != null) {
                     appFile.documentFile.uri
                } else {
                    null
                }
                if (uri != null) {
                    uris.add(uri)
                    // Simple mime type detection: if all are same, use it, else */*
                    // For now keeping it simple as */* or maybe image/* if useful
                }
            }

            if (uris.isEmpty()) return

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "分享文件"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun deleteFiles(context: Context, appFiles: List<AppFile>): Boolean {
        var allSuccess = true
        appFiles.forEach { 
            if (!deleteFile(context, it)) {
                allSuccess = false
            }
        }
        return allSuccess
    }

    fun openWithOther(context: Context, appFile: AppFile) {
        try {
            val uri = if (appFile.file != null) {
                 getFileUri(context, appFile.file)
            } else if (appFile.documentFile != null) {
                 appFile.documentFile.uri
            } else {
                return
            }
            
            val mimeType = if (appFile.file != null) getMimeType(appFile.file) else appFile.documentFile?.type ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "用其他应用打开"))
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportFile(context: Context, appFile: AppFile) {
         val uri = if (appFile.file != null) getFileUri(context, appFile.file) else appFile.documentFile?.uri ?: return
         val mimeType = if (appFile.file != null) getMimeType(appFile.file) else appFile.documentFile?.type ?: "*/*"
         
         shareFileUri(context, uri, mimeType, null)
    }

    private fun shareFileUri(context: Context, uri: Uri, mimeType: String?, platform: SharePlatform?) {
         try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (platform != null) {
                val packageName = when (platform) {
                    SharePlatform.QQ -> "com.tencent.mobileqq"
                    SharePlatform.WECHAT -> "com.tencent.mm"
                }
                intent.setPackage(packageName)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "未安装该应用", Toast.LENGTH_SHORT).show()
                }
            } else {
                val chooser = Intent.createChooser(intent, "导出/分享文件")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Keep existing shareFile for legacy callers
    fun shareFile(context: Context, file: File, platform: SharePlatform?) {
        val uri = getFileUri(context, file)
        val mimeType = getMimeType(file)
        shareFileUri(context, uri, mimeType, platform)
    }
    
    fun shareFile(context: Context, fileName: String, platform: SharePlatform) {
         val file = getTestsFile(fileName)
         shareFile(context, file, platform)
    }

    fun openFile(context: Context, fileName: String, remoteUrl: String) {
        val file = getTestsFile(fileName)
        if (file.exists()) {
            try {
                val uri = getFileUri(context, file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(file))
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "无法打开本地文件，尝试在线预览", Toast.LENGTH_SHORT).show()
                openRemote(context, remoteUrl)
            }
        } else {
            openRemote(context, remoteUrl)
        }
    }

    private fun openRemote(context: Context, url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(browserIntent)
    }

    suspend fun saveToUri(context: Context, url: String, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    URL(url).openStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun downloadToTests(context: Context, url: String, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val safUriStr = getSafUri(context)

                if (safUriStr != null) {
                    // ===== 使用 SAF 目录 =====
                    val treeUri = Uri.parse(safUriStr)
                    var current =
                        DocumentFile.fromTreeUri(context, treeUri)
                            ?: return@withContext false

                    val parts = fileName.split("/")

                    // 创建子目录
                    for (i in 0 until parts.size - 1) {
                        val dirName = parts[i]
                        current =
                            current.findFile(dirName)
                                ?: current.createDirectory(dirName)
                                ?: return@withContext false
                    }

                    val name = parts.last()
                    val target =
                        current.findFile(name)
                            ?: current.createFile(
                                "application/octet-stream",
                                name
                            )
                            ?: return@withContext false

                    context.contentResolver.openOutputStream(target.uri)?.use { output ->
                        URL(url).openStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    // ===== fallback：Downloads/tests =====
                    val destFile = getTestsFile(fileName)
                    destFile.parentFile?.mkdirs()
    
                    URL(url).openStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun isFileDownloaded(fileName: String, context: Context): Boolean {
        val safUriStr = getSafUri(context)
        if (safUriStr != null) {
            var current =
                DocumentFile.fromTreeUri(context, Uri.parse(safUriStr))
                    ?: return false

            for (part in fileName.split("/")) {
                current = current.findFile(part) ?: return false
            }
            return current.exists()
        }
        return getTestsFile(fileName).exists()
    }

    fun getTestsFile(fileName: String): File {
        // Use Downloads/tests folder
        val testsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "tests")
        return File(testsDir, fileName)
    }

    private fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun getMimeType(file: File): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath) ?: "pdf"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/pdf"
    }
}

enum class SharePlatform {
    QQ, WECHAT
}
