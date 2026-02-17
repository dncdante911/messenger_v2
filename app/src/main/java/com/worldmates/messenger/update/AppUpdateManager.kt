package com.worldmates.messenger.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.worldmates.messenger.BuildConfig
import com.worldmates.messenger.data.model.AppUpdateInfo
import com.worldmates.messenger.data.model.AppUpdateResponse
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "AppUpdateManager"
private const val PREFS_NAME = "app_update_prefs"
private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
private const val KEY_WIFI_ONLY = "wifi_only_update"
private const val KEY_PENDING_APK_VERSION = "pending_apk_version"
private const val KEY_LAST_CHECK_MILLIS = "last_check_millis"

data class UpdateState(
    val hasUpdate: Boolean = false,
    val latestVersion: String? = null,
    val latestVersionCode: Int = 0,
    val isMandatory: Boolean = false,
    val changelog: List<String> = emptyList(),
    val apkUrl: String? = null,
    val publishedAt: String? = null,
    val error: String? = null,
    val checkedAtMillis: Long? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val downloadProgress: Int = 0
)

sealed class DownloadState {
    object Idle : DownloadState()
    object Downloading : DownloadState()
    data class Progress(val percent: Int) : DownloadState()
    object Downloaded : DownloadState()
    object ReadyToInstall : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object AppUpdateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var periodicJob: Job? = null
    private var progressJob: Job? = null
    private var snoozedUntilMillis: Long = 0L
    private var downloadId: Long = -1
    private var appContext: Context? = null
    private const val APK_FILE_NAME = "worldmates_update.apk"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initialize with application context. Call from WMApplication.onCreate().
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        checkForPendingUpdate(context)
    }

    /**
     * Check if auto-update is enabled.
     */
    fun isAutoUpdateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
    }

    /**
     * Set auto-update preference.
     */
    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled).apply()
        Log.d(TAG, "Auto-update set to: $enabled")
    }

    /**
     * Check if WiFi-only mode is enabled.
     */
    fun isWifiOnlyEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WIFI_ONLY, true)
    }

    /**
     * Set WiFi-only update preference.
     */
    fun setWifiOnlyEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    /**
     * Start periodic background update checks.
     * Called from WMApplication.onCreate().
     */
    fun startPeriodicChecks(intervalMinutes: Long = 30L) {
        if (periodicJob?.isActive == true) return

        periodicJob = scope.launch {
            while (isActive) {
                val ctx = appContext
                if (ctx != null) {
                    runCatching {
                        val state = checkForUpdates()
                        // Auto-download in background if enabled
                        if (state.hasUpdate && isAutoUpdateEnabled(ctx)) {
                            if (!isWifiOnlyEnabled(ctx) || isOnWifi(ctx)) {
                                val apkFile = getApkFile(ctx)
                                if (!apkFile.exists()) {
                                    Log.d(TAG, "Auto-downloading update v${state.latestVersion}")
                                    silentDownload(ctx)
                                }
                            }
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Background update check failed: ${e.message}")
                    }
                }
                delay(intervalMinutes * 60_000)
            }
        }
    }

    /**
     * Check for updates from server.
     */
    suspend fun checkForUpdates(force: Boolean = false): UpdateState {
        if (!force && _state.value.checkedAtMillis != null) {
            val elapsed = System.currentTimeMillis() - (_state.value.checkedAtMillis ?: 0)
            if (elapsed < 5 * 60_000) return _state.value
        }

        return try {
            val response = fetchUpdateResponse()
            val info = response.data

            if (!response.success && info == null) {
                return updateFailure(response.message ?: "Update endpoint returned invalid payload")
            }

            val updateAvailable = info != null && isNewerVersion(info)
            val isSnoozed = System.currentTimeMillis() < snoozedUntilMillis

            // Check if APK is already downloaded
            val ctx = appContext
            val apkReady = ctx != null && updateAvailable && getApkFile(ctx).exists()

            val newState = UpdateState(
                hasUpdate = updateAvailable && !isSnoozed,
                latestVersion = info?.latestVersion,
                latestVersionCode = info?.versionCode ?: 0,
                isMandatory = info?.isMandatory ?: false,
                changelog = info?.changelog.orEmpty(),
                apkUrl = info?.apkUrl,
                publishedAt = info?.publishedAt,
                error = null,
                checkedAtMillis = System.currentTimeMillis(),
                downloadState = if (apkReady) DownloadState.ReadyToInstall else _state.value.downloadState
            )

            _state.value = newState
            Log.d(TAG, "Update check: hasUpdate=${newState.hasUpdate}, latest=${newState.latestVersion}, apkReady=$apkReady")
            newState
        } catch (e: Exception) {
            updateFailure(e.message ?: "Unknown error while checking updates", e)
        }
    }

    /**
     * Fetch update info directly from endpoint.
     */
    private suspend fun fetchUpdateResponse(): AppUpdateResponse {
        return RetrofitClient.apiService.checkMobileUpdate()
    }

    private fun updateFailure(message: String, throwable: Throwable? = null): UpdateState {
        val failed = _state.value.copy(
            error = message,
            checkedAtMillis = System.currentTimeMillis()
        )
        _state.value = failed

        if (throwable != null) {
            Log.e(TAG, "Update check failed", throwable)
        } else {
            Log.w(TAG, "Update check failed: $message")
        }

        return failed
    }

    /**
     * Snooze update prompt for specified hours.
     */
    fun snoozePrompt(hours: Int = 12) {
        snoozedUntilMillis = System.currentTimeMillis() + hours * 60L * 60_000L
        _state.value = _state.value.copy(hasUpdate = false)
    }

    /**
     * Open update URL in browser (fallback).
     */
    fun openUpdateUrl(context: Context) {
        val url = _state.value.apkUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Silent background download - downloads APK without showing
     * notification progress bar. Used for auto-update flow.
     */
    fun silentDownload(context: Context) {
        val downloadUrl = _state.value.apkUrl
        if (downloadUrl.isNullOrEmpty()) {
            Log.w(TAG, "Silent download skipped: no URL")
            return
        }

        if (_state.value.downloadState is DownloadState.Downloading ||
            _state.value.downloadState is DownloadState.Progress) {
            Log.d(TAG, "Download already in progress")
            return
        }

        _state.value = _state.value.copy(downloadState = DownloadState.Downloading, downloadProgress = 0)

        val apkFile = getApkFile(context)
        if (apkFile.exists()) apkFile.delete()

        try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("WorldMates Messenger")
                .setDescription("Завантаження оновлення...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_FILE_NAME
                )
                .setMimeType("application/vnd.android.package-archive")

            // Allow download on WiFi and mobile data
            if (!isWifiOnlyEnabled(context)) {
                request.setAllowedOverMetered(true)
                request.setAllowedOverRoaming(false)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)

            // Start progress tracking
            startProgressTracking(context, dm)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        progressJob?.cancel()
                        context.unregisterReceiver(this)

                        // Check download status
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = dm.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                            cursor.close()

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                // Save pending version for install on next app start
                                savePendingVersion(context, _state.value.latestVersion ?: "")

                                _state.value = _state.value.copy(
                                    downloadState = DownloadState.ReadyToInstall,
                                    downloadProgress = 100
                                )
                                Log.d(TAG, "Download complete, ready to install")

                                // Auto-install: trigger install prompt
                                if (isAutoUpdateEnabled(context)) {
                                    installApk(context, apkFile)
                                }
                            } else {
                                _state.value = _state.value.copy(
                                    downloadState = DownloadState.Error("Download failed with status: $status")
                                )
                            }
                        } else {
                            cursor?.close()
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

            Log.d(TAG, "Silent download started: $downloadUrl, ID: $downloadId")
        } catch (e: Exception) {
            Log.e(TAG, "Silent download failed", e)
            _state.value = _state.value.copy(
                downloadState = DownloadState.Error("Download failed: ${e.message}")
            )
        }
    }

    /**
     * Download APK via DownloadManager and trigger install.
     * User-initiated download with full notification visibility.
     */
    fun downloadAndInstall(context: Context) {
        val downloadUrl = _state.value.apkUrl
        if (downloadUrl.isNullOrEmpty()) {
            _state.value = _state.value.copy(
                downloadState = DownloadState.Error("Download URL is empty")
            )
            return
        }

        _state.value = _state.value.copy(downloadState = DownloadState.Downloading, downloadProgress = 0)

        val apkFile = getApkFile(context)
        if (apkFile.exists()) apkFile.delete()

        try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("WorldMates Messenger")
                .setDescription("Завантаження оновлення...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_FILE_NAME
                )
                .setMimeType("application/vnd.android.package-archive")

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)

            // Start progress tracking
            startProgressTracking(context, dm)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        progressJob?.cancel()
                        _state.value = _state.value.copy(
                            downloadState = DownloadState.Downloaded,
                            downloadProgress = 100
                        )
                        context.unregisterReceiver(this)
                        savePendingVersion(context, _state.value.latestVersion ?: "")
                        installApk(context, apkFile)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

            Log.d(TAG, "Download started: $downloadUrl, ID: $downloadId")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _state.value = _state.value.copy(
                downloadState = DownloadState.Error("Download failed: ${e.message}")
            )
        }
    }

    /**
     * Track download progress via DownloadManager queries.
     */
    private fun startProgressTracking(context: Context, dm: DownloadManager) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && downloadId != -1L) {
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                        val bytesDownloaded = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0L
                        val totalBytes = if (totalIdx >= 0) cursor.getLong(totalIdx) else 0L
                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        cursor.close()

                        if (status == DownloadManager.STATUS_RUNNING && totalBytes > 0) {
                            val percent = ((bytesDownloaded * 100) / totalBytes).toInt()
                            _state.value = _state.value.copy(
                                downloadState = DownloadState.Progress(percent),
                                downloadProgress = percent
                            )
                        } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            break
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            _state.value = _state.value.copy(
                                downloadState = DownloadState.Error("Download failed")
                            )
                            break
                        }
                    } else {
                        cursor?.close()
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Progress tracking error: ${e.message}")
                }
                delay(500)
            }
        }
    }

    /**
     * Install downloaded APK via FileProvider.
     */
    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Log.d(TAG, "Install intent launched")
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            _state.value = _state.value.copy(
                downloadState = DownloadState.Error("Install failed: ${e.message}")
            )
        }
    }

    /**
     * Install pending update if APK was downloaded but not yet installed.
     * Called on app start.
     */
    fun installPendingUpdate(context: Context) {
        val apkFile = getApkFile(context)
        if (apkFile.exists()) {
            _state.value = _state.value.copy(downloadState = DownloadState.ReadyToInstall)
            installApk(context, apkFile)
        }
    }

    /**
     * Check for previously downloaded but not installed update.
     */
    private fun checkForPendingUpdate(context: Context) {
        val pendingVersion = getPrefs(context).getString(KEY_PENDING_APK_VERSION, null)
        if (!pendingVersion.isNullOrEmpty()) {
            val apkFile = getApkFile(context)
            if (apkFile.exists()) {
                _state.value = _state.value.copy(
                    downloadState = DownloadState.ReadyToInstall,
                    latestVersion = pendingVersion
                )
                Log.d(TAG, "Pending update found: v$pendingVersion")
            } else {
                // APK was deleted or installed, clear pending
                clearPendingVersion(context)
            }
        }
    }

    private fun savePendingVersion(context: Context, version: String) {
        getPrefs(context).edit().putString(KEY_PENDING_APK_VERSION, version).apply()
    }

    private fun clearPendingVersion(context: Context) {
        getPrefs(context).edit().remove(KEY_PENDING_APK_VERSION).apply()
    }

    /**
     * Reset download state.
     */
    fun resetDownloadState() {
        _state.value = _state.value.copy(downloadState = DownloadState.Idle, downloadProgress = 0)
    }

    /**
     * Clean up old APK files.
     */
    fun cleanupOldApk(context: Context) {
        val apkFile = getApkFile(context)
        if (apkFile.exists()) {
            apkFile.delete()
            clearPendingVersion(context)
            Log.d(TAG, "Old APK cleaned up")
        }
    }

    /**
     * Get current app version name.
     */
    fun getCurrentVersionName(): String {
        return BuildConfig.VERSION_NAME
    }

    private fun getApkFile(context: Context): File {
        return File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            APK_FILE_NAME
        )
    }

    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isNewerVersion(updateInfo: AppUpdateInfo): Boolean {
        val currentCode = BuildConfig.VERSION_CODE
        if (updateInfo.versionCode > currentCode) return true
        return compareVersions(updateInfo.latestVersion, BuildConfig.VERSION_NAME) > 0
    }

    private fun compareVersions(latest: String, current: String): Int {
        val a = latest.split(".")
        val b = current.split(".")
        val max = maxOf(a.size, b.size)
        repeat(max) { i ->
            val ai = a.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val bi = b.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            if (ai != bi) return ai.compareTo(bi)
        }
        return 0
    }
}
