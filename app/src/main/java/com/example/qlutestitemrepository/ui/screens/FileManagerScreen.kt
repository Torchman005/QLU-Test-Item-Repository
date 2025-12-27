package com.example.qlutestitemrepository.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.qlutestitemrepository.R
import com.example.qlutestitemrepository.util.AppFile
import com.example.qlutestitemrepository.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    navController: NavController
) {
    val context = LocalContext.current
    // Check either MANAGE_EXTERNAL_STORAGE or SAF
    var hasPermission by remember { 
        mutableStateOf(checkStoragePermission(context) || FileUtils.getSafUri(context) != null) 
    }
    
    // Lifecycle observer to check permission on resume (e.g. coming back from Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = checkStoragePermission(context) || FileUtils.getSafUri(context) != null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission launcher for Android < 11
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true || 
                        permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
    }
    
    // SAF Launcher
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            FileUtils.saveSafUri(context, uri)
            hasPermission = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (hasPermission) {
            FileManagerContent(
                modifier = Modifier.padding(padding),
                context = context
            )
        } else {
            PermissionRequestContent(
                modifier = Modifier.padding(padding),
                onRequestPermission = {
                    requestStoragePermission(context, permissionLauncher)
                },
                onRequestSaf = {
                    safLauncher.launch(Uri.parse(FileUtils.getTestsRoot().toURI().toString())) // Hint location
                }
            )
        }
    }
}

@Composable
fun PermissionRequestContent(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
    onRequestSaf: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "需要文件访问权限",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "为了查看和管理您下载的试卷文件，我们需要访问存储空间的权限。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
            Text("授予所有文件访问权限 (推荐)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(onClick = onRequestSaf, modifier = Modifier.fillMaxWidth()) {
            Text("无法授权? 仅授予文件夹访问权限")
        }
        Text(
            text = "如果您无法手动开启上述权限，请点击此按钮选择 'Downloads/tests' 文件夹。",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerContent(
    modifier: Modifier = Modifier,
    context: Context
) {
    var currentPath by remember { mutableStateOf("") }
    var fileList by remember { mutableStateOf(FileUtils.listFiles(context, currentPath)) }
    
    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<AppFile>()) }

    // Refresh list when path changes
    LaunchedEffect(currentPath) {
        fileList = FileUtils.listFiles(context, currentPath)
        // Exit selection mode when path changes
        isSelectionMode = false
        selectedFiles = emptySet()
    }

    // Handle Back Press logic
    BackHandler(enabled = currentPath.isNotEmpty() || isSelectionMode) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedFiles = emptySet()
        } else {
            val parent = if (currentPath.contains("/")) currentPath.substringBeforeLast("/") else ""
            currentPath = if (parent == ".") "" else parent
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Toolbar for Selection Mode
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    isSelectionMode = false
                    selectedFiles = emptySet()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Selection")
                }
                Text("${selectedFiles.size} 已选择")
                Row {
                    IconButton(onClick = {
                        FileUtils.shareFiles(context, selectedFiles.toList())
                        isSelectionMode = false
                        selectedFiles = emptySet()
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Selected")
                    }
                    IconButton(onClick = {
                         if (FileUtils.deleteFiles(context, selectedFiles.toList())) {
                             Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                             fileList = FileUtils.listFiles(context, currentPath) // Refresh
                         } else {
                             Toast.makeText(context, "部分删除失败", Toast.LENGTH_SHORT).show()
                             fileList = FileUtils.listFiles(context, currentPath) // Refresh
                         }
                         isSelectionMode = false
                         selectedFiles = emptySet()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                    }
                }
            }
        } else {
            // Path Breadcrumb
            Text(
                text = if (currentPath.isEmpty()) "Root" else currentPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Back to parent directory item (only show when not in selection mode and not root)
            if (currentPath.isNotEmpty() && !isSelectionMode) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clickable {
                                val parent = if (currentPath.contains("/")) currentPath.substringBeforeLast("/") else ""
                                currentPath = if (parent == ".") "" else parent
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = "返回上一级", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (fileList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("空文件夹")
                    }
                }
            }

            items(fileList) { appFile ->
                val isSelected = selectedFiles.contains(appFile)
                FileItemRow(
                    file = appFile,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onLongClick = {
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedFiles = selectedFiles + appFile
                        }
                    },
                    onSelect = {
                        selectedFiles = if (isSelected) selectedFiles - appFile else selectedFiles + appFile
                        if (selectedFiles.isEmpty()) {
                            isSelectionMode = false
                        }
                    },
                    onFolderClick = { 
                         if (isSelectionMode) {
                             selectedFiles = if (isSelected) selectedFiles - appFile else selectedFiles + appFile
                             if (selectedFiles.isEmpty()) isSelectionMode = false
                         } else {
                             val newPath = if (currentPath.isEmpty()) appFile.name else "$currentPath/${appFile.name}"
                             currentPath = newPath
                         }
                    },
                    onDelete = {
                        if (FileUtils.deleteFile(context, appFile)) {
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            fileList = FileUtils.listFiles(context, currentPath) // Refresh
                        } else {
                            Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenOther = { FileUtils.openWithOther(context, appFile) },
                    onPreview = { FileUtils.openFileDirectly(context, appFile) }, // Changed to direct open
                    onExport = { FileUtils.exportFile(context, appFile) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: AppFile,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onFolderClick: () -> Unit,
    onDelete: () -> Unit,
    onOpenOther: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelect()
                    } else {
                        if (file.isDirectory) {
                            onFolderClick()
                        } else {
                            onPreview()
                        }
                    }
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Icon(
                imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(FileUtils.formatDate(file.lastModified))
                        if (!file.isDirectory) {
                            append("  |  ")
                            append(FileUtils.formatSize(file.length))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (file.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("删除", color = Color.Red) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red) },
                                onClick = {
                                    expanded = false
                                    onDelete()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("用其他应用打开") },
                                leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onOpenOther()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("直接打开") }, // Updated label
                                leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onPreview()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("分享") },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onExport() 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出") },
                                leadingIcon = { Icon(Icons.Filled.Output, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除", color = Color.Red) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red) },
                                onClick = {
                                    expanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

fun requestStoragePermission(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    } else {
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }
}
