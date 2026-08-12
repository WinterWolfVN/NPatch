package org.lsposed.npatch.repo

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lsposed.npatch.lspApp
import java.io.File
import java.util.concurrent.TimeUnit

// ── Data Models ──────────────────────────────────────────────────────────────

data class OnlineModule(
    @field:SerializedName("name") @field:Expose val name: String? = null,
    @field:SerializedName("description") @field:Expose val description: String? = null,
    @field:SerializedName("summary") @field:Expose val summary: String? = null,
    @field:SerializedName("homepageUrl") @field:Expose val homepageUrl: String? = null,
    @field:SerializedName("collaborators") @field:Expose val collaborators: List<Collaborator> = emptyList(),
    @field:SerializedName("latestRelease") @field:Expose val latestRelease: String? = null,
    @field:SerializedName("latestReleaseTime") @field:Expose val latestReleaseTime: String? = null,
    @field:SerializedName("releases") @field:Expose val releases: List<Release> = emptyList(),
    @field:SerializedName("scope") @field:Expose val scope: List<String> = emptyList(),
    @field:SerializedName("updatedAt") @field:Expose val updatedAt: String? = null,
    @field:SerializedName("createdAt") @field:Expose val createdAt: String? = null,
)

data class Release(
    @field:SerializedName("name") @field:Expose val name: String? = null,
    @field:SerializedName("description") @field:Expose val description: String? = null,
    @field:SerializedName("tagName") @field:Expose val tagName: String? = null,
    @field:SerializedName("publishedAt") @field:Expose val publishedAt: String? = null,
    @field:SerializedName("releaseAssets") @field:Expose val releaseAssets: List<ReleaseAsset> = emptyList(),
)

data class ReleaseAsset(
    @field:SerializedName("name") @field:Expose val name: String? = null,
    @field:SerializedName("downloadUrl") @field:Expose val downloadUrl: String? = null,
    @field:SerializedName("size") @field:Expose val size: Int = 0,
)

data class Collaborator(
    @field:SerializedName("login") @field:Expose val login: String? = null,
    @field:SerializedName("name") @field:Expose val name: String? = null,
)

// ── RepoLoader ───────────────────────────────────────────────────────────────

object RepoLoader {

    private const val TAG = "RepoLoader"
    private const val REPO_URL = "https://modules.lsposed.org/modules.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val repoFile = File(lspApp.filesDir, "modules.json")
    private val gson = Gson()

    var modules by mutableStateOf<List<OnlineModule>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    suspend fun loadLocal() {
        if (!repoFile.exists()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val json = repoFile.readText()
                modules = gson.fromJson(json, Array<OnlineModule>::class.java).toList()
            }.onFailure {
                Log.e(TAG, "loadLocal failed", it)
            }
        }
    }

    suspend fun refresh() {
        if (isLoading) return
        isLoading = true
        error = null
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(REPO_URL).build()
                val json = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.string() ?: throw Exception("Empty response")
                }
                repoFile.writeText(json)
                modules = gson.fromJson(json, Array<OnlineModule>::class.java).toList()
            }.onFailure {
                Log.e(TAG, "refresh failed", it)
                error = it.localizedMessage ?: it.javaClass.simpleName
            }
        }
        isLoading = false
    }
}

