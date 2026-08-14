package nkbe.util

import android.content.Intent
import android.content.IntentSender
import android.content.pm.*
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Process
import android.os.SystemProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.rikka.tools.refine.Refine
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File

object ShizukuApi {

    private fun IBinder.wrap() = ShizukuBinderWrapper(this)
    private fun IInterface.asShizukuBinder() = this.asBinder().wrap()

    private val iPackageManager: IPackageManager by lazy {
        IPackageManager.Stub.asInterface(SystemServiceHelper.getSystemService("package").wrap())
    }

    private val iPackageInstaller: IPackageInstaller by lazy {
        IPackageInstaller.Stub.asInterface(iPackageManager.packageInstaller.asShizukuBinder())
    }

    private val packageInstaller: PackageInstaller? by lazy {
        val userId = Process.myUserHandle().hashCode()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Refine.unsafeCast(PackageInstallerHidden(iPackageInstaller, "com.android.shell", null, userId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Refine.unsafeCast(PackageInstallerHidden(iPackageInstaller, "com.android.shell", userId))
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    var isBinderAvailable = false
    var isPermissionGranted by mutableStateOf(false)

    fun init() {
        Shizuku.addBinderReceivedListenerSticky {
            isBinderAvailable = true
            isPermissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addBinderDeadListener {
            isBinderAvailable = false
            isPermissionGranted = false
        }
    }
    
    fun getInstalledApplications(): List<ApplicationInfo> {
        val userId = Process.myUserHandle().hashCode()
        val flags: Long = PackageManager.GET_META_DATA.toLong()
        return iPackageManager.getInstalledApplications(flags, userId).list
    }

    fun createPackageInstallerSession(params: PackageInstaller.SessionParams): PackageInstaller.Session? {
        val installer = packageInstaller ?: return null
        val sessionId = installer.createSession(params)
        val iSession = IPackageInstallerSession.Stub.asInterface(iPackageInstaller.openSession(sessionId).asShizukuBinder())
        return Refine.unsafeCast(PackageInstallerHidden.SessionHidden(iSession))
    }

    fun isPackageInstalledWithoutPatch(packageName: String): Boolean {
        val userId = Process.myUserHandle().hashCode()
        val app = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            iPackageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA.toLong(), userId)
        } else {
            iPackageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA, userId)
        }
        return (app != null) && (app.metaData?.containsKey("npatch") != true)
    }

    fun uninstallPackage(packageName: String, intentSender: IntentSender) {
        packageInstaller?.uninstall(packageName, intentSender)
    }

    fun performDexOptMode(packageName: String): Boolean {
        return iPackageManager.performDexOptMode(
            packageName,
            SystemProperties.getBoolean("dalvik.vm.usejitprofiles", false),
            "verify", true, true, null
        )
    }

    fun installApkNormal(context: android.content.Context, apkFile: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
