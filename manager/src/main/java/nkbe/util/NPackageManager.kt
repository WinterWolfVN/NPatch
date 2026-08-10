package nkbe.util

import android.R
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstallerHidden.SessionParamsHidden
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.rikka.tools.refine.Refine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.appiconloader.AppIconLoader
import org.lsposed.npatch.config.ConfigManager
import org.lsposed.npatch.config.Configs
import org.lsposed.npatch.lspApp
import org.lsposed.npatch.share.Constants
import java.io.File
import java.io.IOException
import java.text.Collator
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object NPackageManager {

    private const val TAG = "LSPPackageManager"
    private const val SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS"

    const val STATUS_USER_CANCELLED = -2

    @Parcelize
    class AppInfo(val app: ApplicationInfo, val label: String, val apksPath: String? = null) : Parcelable {
        val isXposedModule: Boolean
            get() = app.metaData?.get("xposedminversion") != null
    }

    var appList by mutableStateOf(listOf<AppInfo>())
        private set

    @SuppressLint("StaticFieldLeak")
    private val iconLoader = AppIconLoader(lspApp.resources.getDimensionPixelSize(R.dimen.app_icon_size), false, lspApp)
    private val appIcon = mutableMapOf<String, ImageBitmap>()


    suspend fun fetchAppList() {
        withContext(Dispatchers.IO) {
            val pm = lspApp.packageManager
            val collection = mutableListOf<AppInfo>()
            val applicationList: List<ApplicationInfo>

            if (ShizukuApi.isPermissionGranted) {
                applicationList = runCatching {
                    ShizukuApi.getInstalledApplications()
                }.getOrElse {
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                }
            } else {
                applicationList = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }

            applicationList.forEach {
                val label = pm.getApplicationLabel(it)
                collection.add(AppInfo(it, label.toString()))
                appIcon[it.packageName] = iconLoader.loadIcon(it).asImageBitmap()
            }

            collection.sortWith(compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::label))
            val modules = buildMap {
                collection.forEach { if (it.isXposedModule) put(it.app.packageName, it.app.sourceDir) }
            }
            ConfigManager.updateModules(modules)
            appList = collection
        }
    }

    fun getIcon(appInfo: AppInfo) = appIcon[appInfo.app.packageName]!!

    suspend fun cleanTmpApkDir() {
        withContext(Dispatchers.IO) {
            lspApp.tmpApkDir.listFiles()?.forEach(File::delete)
        }
    }

    suspend fun cleanExternalTmpApkDir(){
        withContext(Dispatchers.IO) {
            lspApp.externalCacheDir?.listFiles()?.forEach(File::delete)
        }
    }

    suspend fun install(): Pair<Int, String?> {
        var status = PackageInstaller.STATUS_FAILURE
        var message: String? = null
        withContext(Dispatchers.IO) {
            runCatching {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                try {
                    val hiddenParams = Refine.unsafeCast<SessionParamsHidden>(params)
                    var flags = hiddenParams.installFlags
                    flags = flags or PackageManagerHidden.INSTALL_ALLOW_TEST or PackageManagerHidden.INSTALL_REPLACE_EXISTING
                    hiddenParams.installFlags = flags
                } catch (e: Throwable) {}

                val session = ShizukuApi.createPackageInstallerSession(params)
                
                if (session == null) {
                    val uri = Configs.storageDirectory?.toUri() ?: throw IOException("Uri is null")
                    val root = DocumentFile.fromTreeUri(lspApp, uri) ?: throw IOException("DocumentFile is null")
                    root.listFiles().forEach { file ->
                        if (file.name?.endsWith(Constants.PATCH_FILE_SUFFIX) == true) {
                            val cacheFile = File(lspApp.cacheDir, file.name!!)
                            lspApp.contentResolver.openInputStream(file.uri)?.use { input ->
                                cacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            ShizukuApi.installApkNormal(lspApp, cacheFile)
                            status = PackageInstaller.STATUS_PENDING_USER_ACTION
                            return@runCatching
                        }
                    }
                } else {
                    session.use { s ->
                        val uri = Configs.storageDirectory?.toUri() ?: throw IOException("Uri is null")
                        val root = DocumentFile.fromTreeUri(lspApp, uri) ?: throw IOException("DocumentFile is null")
                        root.listFiles().forEach { file ->
                            if (file.name?.endsWith(Constants.PATCH_FILE_SUFFIX) != true) return@forEach
                            lspApp.contentResolver.openInputStream(file.uri)?.use { input ->
                                s.openWrite(file.name!!, 0, input.available().toLong()).use { output ->
                                    input.copyTo(output)
                                    s.fsync(output)
                                }
                            }
                        }
                        var result: Intent? = null
                        suspendCoroutine { cont ->
                            val adapter = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                                result = intent
                                cont.resume(Unit)
                            }
                            s.commit(IntentSenderHelper.newIntentSender(adapter))
                        }
                        result?.let {
                            status = it.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                            message = it.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        }
                    }
                }
            }.onFailure {
                status = PackageInstaller.STATUS_FAILURE
                message = it.message
            }
        }
        return Pair(status, message)
    }

    suspend fun uninstall(packageName: String): Pair<Int, String?> {
        var status = PackageInstaller.STATUS_FAILURE
        var message: String? = null
        withContext(Dispatchers.IO) {
            runCatching {
                if (ShizukuApi.isPermissionGranted) {
                    var result: Intent? = null
                    suspendCoroutine { cont ->
                        val adapter = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                            result = intent
                            cont.resume(Unit)
                        }
                        ShizukuApi.uninstallPackage(packageName, IntentSenderHelper.newIntentSender(adapter))
                    }
                    result?.let {
                        status = it.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        message = it.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    }
                } else {
                    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    lspApp.startActivity(intent)
                    status = PackageInstaller.STATUS_PENDING_USER_ACTION
                }
            }.onFailure {
                status = PackageInstaller.STATUS_FAILURE
                message = it.message
            }
        }
        return Pair(status, message)
    }

    suspend fun getAppInfoFromApks(apks: List<Uri>): Result<List<AppInfo>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                var primary: ApplicationInfo? = null
                val splits = mutableListOf<String>()
                val appInfos = apks.mapNotNull { uri ->
                    val src = DocumentFile.fromSingleUri(lspApp, uri) ?: return@mapNotNull null
                    val name = src.name ?: return@mapNotNull null

                    // APKs
                    if (name.endsWith(".apks")) {
                        var baseAppInfo: ApplicationInfo? = null
                        val apksSplits = mutableListOf<String>()
                        lspApp.contentResolver.openInputStream(uri)?.use { input ->
                            ZipInputStream(input).use { zip ->
                                var entry = zip.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                                        val dst = File(lspApp.tmpApkDir, File(entry.name).name)
                                        dst.outputStream().use { zip.copyTo(it) }
                                        if (File(entry.name).name == "base.apk") {
                                            val info = lspApp.packageManager.getPackageArchiveInfo(dst.absolutePath, PackageManager.GET_META_DATA)?.applicationInfo
                                            if (info != null) {
                                                info.sourceDir = dst.absolutePath
                                                baseAppInfo = info
                                                if (primary == null) primary = info
                                            }
                                        } else {
                                            apksSplits.add(dst.absolutePath)
                                        }
                                    }
                                    entry = zip.nextEntry
                                }
                            }
                        }
                        baseAppInfo?.splitSourceDirs = apksSplits.toTypedArray()
                        return@mapNotNull baseAppInfo?.let {
                            AppInfo(it, lspApp.packageManager.getApplicationLabel(it).toString(), uri.toString())
                        }
                    }

                    // APK 
                    val dst = lspApp.tmpApkDir.resolve(name)
                    lspApp.contentResolver.openInputStream(uri)?.use { input ->
                        dst.outputStream().use { output -> input.copyTo(output) }
                    }
                    val appInfo = lspApp.packageManager.getPackageArchiveInfo(dst.absolutePath, PackageManager.GET_META_DATA)?.applicationInfo
                    appInfo?.sourceDir = dst.absolutePath
                    if (appInfo == null) {
                        splits.add(dst.absolutePath)
                        return@mapNotNull null
                    }
                    if (primary == null) primary = appInfo
                    AppInfo(appInfo, lspApp.packageManager.getApplicationLabel(appInfo).toString())
                }
                primary?.splitSourceDirs = splits.toTypedArray()
                if (appInfos.isEmpty()) throw IOException("No apks")
                appInfos
            }.onFailure {
                cleanTmpApkDir()
            }
        }
    }

    fun getLaunchIntentForPackage(packageName: String): Intent? {
        val pm = lspApp.packageManager
        return pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun getSettingsIntent(packageName: String): Intent? {
        val intent = Intent(SETTINGS_CATEGORY).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ris = lspApp.packageManager.queryIntentActivities(intent, 0)
        return if (ris.isNotEmpty()) intent else getLaunchIntentForPackage(packageName)
    }
}
