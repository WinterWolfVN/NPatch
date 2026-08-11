package org.lsposed.npatch.ui.page

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.ramcosta.composedestinations.annotation.Destination
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nkbe.util.NPackageManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lsposed.npatch.R
import org.lsposed.npatch.lspApp
import org.lsposed.npatch.repo.OnlineModule
import org.lsposed.npatch.repo.RepoLoader
import org.lsposed.npatch.ui.component.CenterTopBar
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "RepoScreen"

@Destination
@Composable
fun RepoScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var downloadingModule by remember { mutableStateOf<OnlineModule?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val installedPackages = remember {
        NPackageManager.appList.map { it.app.packageName }.toSet()
    }

    val filtered = remember(RepoLoader.modules, searchQuery) {
        if (searchQuery.isEmpty()) RepoLoader.modules
        else RepoLoader.modules.filter { module ->
            module.name?.contains(searchQuery, ignoreCase = true) == true ||
            module.description?.contains(searchQuery, ignoreCase = true) == true ||
            module.summary?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    LaunchedEffect(Unit) {
        RepoLoader.loadLocal()
        if (RepoLoader.modules.isEmpty()) RepoLoader.refresh()
    }

    // Download + patch flow
    fun onModuleClick(module: OnlineModule) {
        val downloadUrl = module.releases.firstOrNull()?.releaseAssets?.firstOrNull()?.downloadUrl
        if (downloadUrl == null) {
            downloadError = "No download URL"
            return
        }
        downloadingModule = module
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder().url(downloadUrl).build()
                    val dst = File(lspApp.tmpApkDir, "${module.name}.apk")
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        dst.outputStream().use { response.body?.byteStream()?.copyTo(it) }
                    }
                    dst
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    lspApp, "${lspApp.packageName}.fileprovider", file
                )
                NPackageManager.getAppInfoFromApks(listOf(uri))
                    .onSuccess {
                        downloadingModule = null
                        navController.navigate(BottomBarDestination.Manage)
                        navController.navigate("new_patch")
                    }
                    .onFailure { throw it }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                downloadError = e.localizedMessage ?: e.javaClass.simpleName
                downloadingModule = null
            }
        }
    }

    // Download dialog
    if (downloadingModule != null) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.loading)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(downloadingModule?.description ?: downloadingModule?.name ?: "")
                }
            }
        )
    }

    // Error dialog
    if (downloadError != null) {
        AlertDialog(
            onDismissRequest = { downloadError = null },
            confirmButton = {
                TextButton(onClick = { downloadError = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.error_unknown)) },
            text = { Text(downloadError ?: "") }
        )
    }

    Scaffold(
        topBar = { CenterTopBar(stringResource(BottomBarDestination.Repo.label)) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(android.R.string.search_go)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, null)
                        }
                    }
                },
                singleLine = true,
            )

            PullToRefreshBox(
                isRefreshing = RepoLoader.isLoading,
                onRefresh = { scope.launch { RepoLoader.refresh() } },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    RepoLoader.isLoading && RepoLoader.modules.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    RepoLoader.error != null && RepoLoader.modules.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.ErrorOutline, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp))
                                Text(RepoLoader.error ?: "", color = MaterialTheme.colorScheme.error)
                                Button(onClick = { scope.launch { RepoLoader.refresh() } }) {
                                    Text(stringResource(R.string.repo_retry))
                                }
                            }
                        }
                    }
                    filtered.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.list_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.name ?: it.hashCode() }) { module ->
                                RepoItem(
                                    module = module,
                                    isInstalled = module.name in installedPackages,
                                    onClick = { onModuleClick(module) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoItem(
    module: OnlineModule,
    isInstalled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = module.description ?: module.name ?: "Unknown",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isInstalled) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
            }
        }
        Text(module.name ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val author = module.collaborators.firstOrNull()?.name ?: module.collaborators.firstOrNull()?.login
        if (author != null) {
            Text(author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!module.summary.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(module.summary, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

