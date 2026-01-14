package com.example.qlutestitemrepository.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qlutestitemrepository.R
import com.example.qlutestitemrepository.data.model.GitHubFileItem
import com.example.qlutestitemrepository.ui.HomeViewModel
import com.example.qlutestitemrepository.util.FileUtils
import com.example.qlutestitemrepository.util.SharePlatform
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val items = viewModel.fileItems
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage
    val currentPath = viewModel.currentPath
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<GitHubFileItem?>(null) }
    var fileToShare by remember { mutableStateOf<GitHubFileItem?>(null) }
    var fileToRedownload by remember { mutableStateOf<GitHubFileItem?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // File Options Dialog
    if (selectedFile != null) {
        val file = selectedFile!!
        AlertDialog(
            onDismissRequest = { selectedFile = null },
            title = { Text(file.name) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            if (file.downloadUrl != null) {
                                FileUtils.openFile(context, file.path, file.downloadUrl)
                            } else {
                                Toast.makeText(context, "无法预览此文件", Toast.LENGTH_SHORT).show()
                            }
                            selectedFile = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("预览")
                    }
                    TextButton(
                        onClick = {
                            if (file.downloadUrl != null) {
                                scope.launch {
                                    Toast.makeText(context, "开始下载...", Toast.LENGTH_SHORT).show()
                                    val success = FileUtils.downloadToTests(context, file.downloadUrl, file.path)
                                    if (success) {
                                        Toast.makeText(context, "下载完成", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                selectedFile = null
                            } else {
                                Toast.makeText(context, "无法下载此文件", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("下载到本地")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFile = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // Share Dialog
    if (fileToShare != null) {
        val file = fileToShare!!
        AlertDialog(
            onDismissRequest = { fileToShare = null },
            title = { Text("分享文件") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            FileUtils.shareFile(context, file.path, SharePlatform.QQ)
                            fileToShare = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("分享到QQ")
                    }
                    TextButton(
                        onClick = {
                            FileUtils.shareFile(context, file.path, SharePlatform.WECHAT)
                            fileToShare = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("分享到微信")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fileToShare = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Redownload Dialog
    if (fileToRedownload != null) {
        val file = fileToRedownload!!
        AlertDialog(
            onDismissRequest = { fileToRedownload = null },
            title = { Text("重新下载") },
            text = { Text("是否重新下载 ${file.name}？") },
            confirmButton = {
                TextButton(onClick = {
                    fileToRedownload = null
                    scope.launch {
                        if (FileUtils.isFileDownloaded(file.path, context)) {
                             Toast.makeText(context, "文件已存在", Toast.LENGTH_SHORT).show()
                        }
                        
                        Toast.makeText(context, "开始重新下载...", Toast.LENGTH_SHORT).show()
                        val success = FileUtils.downloadToTests(context, file.downloadUrl ?: "", file.path)
                        if (success) {
                            Toast.makeText(context, "下载完成", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        } else {
                            Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRedownload = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Handle system back press
    BackHandler(enabled = viewModel.currentPath.isNotEmpty()) {
        viewModel.navigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                 Icon(
                     painter = painterResource(id = R.drawable.logo),
                     contentDescription = "Logo",
                     modifier = Modifier.size(60.dp),
                     tint = Color.Unspecified
                 )
                 Spacer(modifier = Modifier.width(8.dp))
                 Text(text = "Home", style = MaterialTheme.typography.titleLarge)
            }
            IconButton(onClick = onThemeToggle) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Filled.Nightlight else Icons.Filled.WbSunny,
                    contentDescription = "Toggle Theme"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        if (isLoading && items.isEmpty()) { // Only show full screen loader if no items
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null && items.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     Text("Error loading data", color = MaterialTheme.colorScheme.error)
                     Text(errorMessage, style = MaterialTheme.typography.bodySmall)
                     Button(onClick = { viewModel.refresh() }) {
                         Text("Retry")
                     }
                 }
            }
        } else {
            // Pull To Refresh Box
            val pullRefreshState = rememberPullToRefreshState()
            
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refresh() },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                // List
                key(refreshTrigger) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Back to parent directory item
                        if (currentPath.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp) // Slightly smaller for "Back"
                                        .clickable { viewModel.navigateBack() },
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

                        // Filter items based on search query
                        val filteredItems = if (searchQuery.isEmpty()) {
                            items
                        } else {
                            items.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }

                        items(filteredItems) { item ->
                            val isDownloaded = remember(refreshTrigger, item.name) {
                                item.type != "dir" && FileUtils.isFileDownloaded(item.path, context)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (item.type == "dir") {
                                                viewModel.navigateTo(item)
                                            } else {
                                                // Check if file is already downloaded in Downloads folder
                                                if (FileUtils.isFileDownloaded(item.path, context)) {
                                                    FileUtils.openFile(context, item.path, item.downloadUrl ?: "")
                                                } else {
                                                    // Handle file click -> Show Dialog
                                                    selectedFile = item
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (item.type != "dir") {
                                                fileToRedownload = item
                                            }
                                        }
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.type == "dir") Icons.Filled.Folder else Icons.Filled.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp), // Adjusted size
                                        tint = if (item.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = item.name, 
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f) // Push content to start, share button to end
                                    )
                                    
                                    if (isDownloaded) {
                                        IconButton(
                                            onClick = { fileToShare = item }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Share,
                                                contentDescription = "Share",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
