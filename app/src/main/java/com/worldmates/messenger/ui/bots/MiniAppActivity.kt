package com.worldmates.messenger.ui.bots

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.WebAppTokenResponse
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.ui.theme.WorldMatesTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "MiniAppActivity"

/**
 * MiniAppActivity — полноэкранный WebView для отображения Mini Apps (Web Apps ботов).
 *
 * Запуск:
 *   MiniAppActivity.start(context, botId = "bot_xxx", botName = "My Bot",
 *                         webAppUrl = "https://...", accessToken = "...")
 *
 * Жизненный цикл:
 *   1. Activity стартует → ViewModel делает POST /createWebAppToken
 *   2. Получает init_data → открывает WebView с URL + ?initData=...
 *   3. После загрузки страницы инжектирует window.WorldMatesWebApp объект
 *   4. Mini App вызывает WorldMatesWebApp.sendData(...) → JS-мост → REST /answerWebAppQuery
 *   5. WorldMatesWebApp.close() → finish()
 */
class MiniAppActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_BOT_ID      = "extra_bot_id"
        private const val EXTRA_BOT_NAME    = "extra_bot_name"
        private const val EXTRA_WEB_APP_URL = "extra_web_app_url"
        private const val EXTRA_ACCESS_TOKEN = "extra_access_token"

        fun start(
            context: Context,
            botId: String,
            botName: String,
            webAppUrl: String,
            accessToken: String
        ) {
            context.startActivity(
                Intent(context, MiniAppActivity::class.java).apply {
                    putExtra(EXTRA_BOT_ID,       botId)
                    putExtra(EXTRA_BOT_NAME,     botName)
                    putExtra(EXTRA_WEB_APP_URL,  webAppUrl)
                    putExtra(EXTRA_ACCESS_TOKEN, accessToken)
                }
            )
        }
    }

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK)
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data) ?: emptyArray()
        else emptyArray()
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val botId       = intent.getStringExtra(EXTRA_BOT_ID)       ?: run { finish(); return }
        val botName     = intent.getStringExtra(EXTRA_BOT_NAME)      ?: getString(R.string.mini_app_default_title)
        val webAppUrl   = intent.getStringExtra(EXTRA_WEB_APP_URL)   ?: run { finish(); return }

        val viewModel = ViewModelProvider(
            this,
            MiniAppViewModelFactory(botId, webAppUrl)
        )[MiniAppViewModel::class.java]

        viewModel.init()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.onBackPressed(
                    onFireJsBackButton = {
                        viewModel.webViewRef?.evaluateJavascript(
                            "(function(){try{var b=window.WorldMatesWebApp&&window.WorldMatesWebApp.BackButton;if(b&&b._callbacks)b._callbacks.forEach(function(c){try{c();}catch(e){}});}catch(e){}})()",
                            null
                        )
                    },
                    onExit = { finish() }
                )
            }
        })

        setContent {
            WorldMatesTheme {
                MiniAppScreen(
                    botName   = botName,
                    viewModel = viewModel,
                    onClose   = { finish() },
                    onFileChooser = { callback, params ->
                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = callback
                        try { filePickerLauncher.launch(params.createIntent()) }
                        catch (e: Exception) { fileChooserCallback = null; callback.onReceiveValue(null) }
                    }
                )
            }
        }
    }
}

// ─── Data classes ─────────────────────────────────────────────────────────────

data class MainButtonUiState(
    val text: String = "",
    val color: String = "#2196F3",
    val textColor: String = "#FFFFFF",
    val isVisible: Boolean = false,
    val isActive: Boolean = true,
    val isProgress: Boolean = false
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MiniAppViewModelFactory(
    private val botId: String,
    private val webAppUrl: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MiniAppViewModel(botId, webAppUrl) as T
}

class MiniAppViewModel(
    private val botId: String,
    private val webAppUrl: String
) : ViewModel() {

    private val _state = MutableStateFlow<MiniAppState>(MiniAppState.Loading)
    val state: StateFlow<MiniAppState> = _state

    // MainButton
    private val _mainButton = MutableStateFlow<MainButtonUiState?>(null)
    val mainButton: StateFlow<MainButtonUiState?> = _mainButton

    // BackButton
    private val _backButtonVisible = MutableStateFlow(false)
    val backButtonVisible: StateFlow<Boolean> = _backButtonVisible

    // Closing confirmation
    private val _closingConfirmEnabled = MutableStateFlow(false)
    val closingConfirmEnabled: StateFlow<Boolean> = _closingConfirmEnabled

    // Reference to WebView for navigation control
    var webViewRef: WebView? = null

    fun init() {
        viewModelScope.launch {
            _state.value = MiniAppState.Loading
            try {
                val resp: WebAppTokenResponse = NodeRetrofitClient.botApi
                    .createWebAppToken(botId = botId)

                if (resp.apiStatus == 200 && resp.initData != null) {
                    val finalUrl = buildUrl(webAppUrl, resp.initData)
                    _state.value = MiniAppState.Ready(
                        url      = finalUrl,
                        initData = resp.initData,
                        queryId  = resp.queryId
                    )
                } else {
                    Log.w(TAG, "createWebAppToken failed: ${resp.errorMessage}")
                    _state.value = MiniAppState.Error
                }
            } catch (e: Exception) {
                Log.e(TAG, "createWebAppToken error", e)
                _state.value = MiniAppState.Error
            }
        }
    }

    fun sendData(data: String, queryId: String?) {
        viewModelScope.launch {
            try {
                NodeRetrofitClient.botApi.answerWebAppQuery(
                    queryId = queryId ?: "",
                    botId   = botId,
                    data    = data
                )
                Log.d(TAG, "sendData delivered: ${data.take(100)}")
            } catch (e: Exception) {
                Log.e(TAG, "sendData error", e)
            }
        }
    }

    // MainButton methods
    fun showMainButton(text: String, color: String, textColor: String) {
        _mainButton.update { (it ?: MainButtonUiState()).copy(text = text, color = color, textColor = textColor, isVisible = true) }
    }

    fun hideMainButton() { _mainButton.update { it?.copy(isVisible = false) } }

    fun setMainButtonText(text: String) { _mainButton.update { it?.copy(text = text) } }

    fun setMainButtonProgress(show: Boolean) { _mainButton.update { it?.copy(isProgress = show) } }

    fun setMainButtonEnabled(enabled: Boolean) { _mainButton.update { it?.copy(isActive = enabled) } }

    fun onMainButtonClick() {
        webViewRef?.evaluateJavascript(
            "(function(){try{var m=window.WorldMatesWebApp;if(m&&m.MainButton&&m.MainButton._callbacks)m.MainButton._callbacks.forEach(function(c){try{c();}catch(e){}});}catch(e){}})()",
            null
        )
    }

    // BackButton methods
    fun setBackButtonVisible(visible: Boolean) { _backButtonVisible.value = visible }

    fun onBackPressed(onFireJsBackButton: () -> Unit, onExit: () -> Unit) {
        if (_backButtonVisible.value) { onFireJsBackButton(); return }
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack()
        else onExit()
    }

    // Closing confirmation
    fun setClosingConfirmEnabled(enabled: Boolean) { _closingConfirmEnabled.value = enabled }

    private fun buildUrl(base: String, initData: String): String {
        val encoded = Uri.encode(initData)
        return if (base.contains('?')) "$base&initData=$encoded"
        else "$base?initData=$encoded"
    }
}

sealed class MiniAppState {
    object Loading : MiniAppState()
    object Error   : MiniAppState()
    data class Ready(val url: String, val initData: String, val queryId: String?) : MiniAppState()
}

// ─── JS Bridge ────────────────────────────────────────────────────────────────

/**
 * JavaScript-мост: window._WorldMatesWebAppBridge
 * Все методы вызываются из JavaScript-кода Mini App.
 */
class WorldMatesWebAppBridge(
    private val context: Context,
    private val viewModel: MiniAppViewModel,
    private val onClose: () -> Unit,
    private val onShowAlert: (String) -> Unit,
    private val onShowConfirm: (String, (Boolean) -> Unit) -> Unit,
    private val queryId: String?
) {

    @JavascriptInterface
    fun close() {
        Log.d(TAG, "JS: close()")
        (context as? ComponentActivity)?.runOnUiThread { onClose() }
    }

    @JavascriptInterface
    fun ready() {
        Log.d(TAG, "JS: ready() — Mini App signaled ready")
    }

    @JavascriptInterface
    fun expand() {
        Log.d(TAG, "JS: expand() — already full screen")
    }

    @JavascriptInterface
    fun sendData(data: String) {
        Log.d(TAG, "JS: sendData(${data.take(80)}...)")
        viewModel.sendData(data, queryId)
        (context as? ComponentActivity)?.runOnUiThread { onClose() }
    }

    @JavascriptInterface
    fun showAlert(message: String) {
        Log.d(TAG, "JS: showAlert($message)")
        (context as? ComponentActivity)?.runOnUiThread { onShowAlert(message) }
    }

    @JavascriptInterface
    fun showConfirm(message: String) {
        Log.d(TAG, "JS: showConfirm($message)")
        (context as? ComponentActivity)?.runOnUiThread {
            onShowConfirm(message) { /* result ignored if no callback */ }
        }
    }

    @JavascriptInterface
    fun hapticFeedback(type: String) {
        Log.d(TAG, "JS: hapticFeedback($type)")
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val durationMs = when (type) {
                "light"     -> 30L
                "medium"    -> 60L
                "heavy"     -> 100L
                "selection" -> 20L
                "error"     -> 120L
                "success"   -> 50L
                "warning"   -> 80L
                else        -> 40L
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "hapticFeedback error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun showMainButton(text: String, color: String, textColor: String) {
        Log.d(TAG, "JS: showMainButton(text=$text)")
        viewModel.showMainButton(text, color, textColor)
    }

    @JavascriptInterface
    fun hideMainButton() {
        Log.d(TAG, "JS: hideMainButton()")
        viewModel.hideMainButton()
    }

    @JavascriptInterface
    fun setMainButtonText(text: String) {
        Log.d(TAG, "JS: setMainButtonText($text)")
        viewModel.setMainButtonText(text)
    }

    @JavascriptInterface
    fun enableMainButton() {
        Log.d(TAG, "JS: enableMainButton()")
        viewModel.setMainButtonEnabled(true)
    }

    @JavascriptInterface
    fun disableMainButton() {
        Log.d(TAG, "JS: disableMainButton()")
        viewModel.setMainButtonEnabled(false)
    }

    @JavascriptInterface
    fun showMainButtonProgress(leaveActive: Boolean) {
        Log.d(TAG, "JS: showMainButtonProgress($leaveActive)")
        viewModel.setMainButtonProgress(true)
    }

    @JavascriptInterface
    fun hideMainButtonProgress() {
        Log.d(TAG, "JS: hideMainButtonProgress()")
        viewModel.setMainButtonProgress(false)
    }

    @JavascriptInterface
    fun showBackButton() {
        Log.d(TAG, "JS: showBackButton()")
        viewModel.setBackButtonVisible(true)
    }

    @JavascriptInterface
    fun hideBackButton() {
        Log.d(TAG, "JS: hideBackButton()")
        viewModel.setBackButtonVisible(false)
    }

    @JavascriptInterface
    fun enableClosingConfirmation() {
        Log.d(TAG, "JS: enableClosingConfirmation()")
        viewModel.setClosingConfirmEnabled(true)
    }

    @JavascriptInterface
    fun disableClosingConfirmation() {
        Log.d(TAG, "JS: disableClosingConfirmation()")
        viewModel.setClosingConfirmEnabled(false)
    }

    @JavascriptInterface
    fun openLink(url: String) {
        Log.d(TAG, "JS: openLink($url)")
        try {
            (context as? ComponentActivity)?.runOnUiThread {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "openLink error: ${e.message}")
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppScreen(
    botName: String,
    viewModel: MiniAppViewModel,
    onClose: () -> Unit,
    onFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val mainButton by viewModel.mainButton.collectAsState()
    val closingConfirmEnabled by viewModel.closingConfirmEnabled.collectAsState()

    var showAlertDialog by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf<Pair<String, (Boolean) -> Unit>?>(null) }
    var showCloseConfirm by remember { mutableStateOf(false) }

    val handleClose: () -> Unit = {
        if (closingConfirmEnabled) showCloseConfirm = true else onClose()
    }

    // Alert dialog
    showAlertDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { showAlertDialog = null },
            title   = null,
            text    = { Text(message) },
            confirmButton = {
                TextButton(onClick = { showAlertDialog = null }) {
                    Text(stringResource(R.string.mini_app_alert_ok))
                }
            }
        )
    }

    // Confirm dialog
    showConfirmDialog?.let { (message, callback) ->
        AlertDialog(
            onDismissRequest = { callback(false); showConfirmDialog = null },
            text    = { Text(message) },
            confirmButton = {
                TextButton(onClick = { callback(true); showConfirmDialog = null }) {
                    Text(stringResource(R.string.mini_app_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { callback(false); showConfirmDialog = null }) {
                    Text(stringResource(R.string.mini_app_confirm_cancel))
                }
            }
        )
    }

    // Closing confirmation dialog
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text(stringResource(R.string.mini_app_close_confirm_title)) },
            text  = { Text(stringResource(R.string.mini_app_close_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = { showCloseConfirm = false; onClose() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.mini_app_close_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text(stringResource(R.string.mini_app_close_confirm_stay))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = botName,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.mini_app_close)
                        )
                    }
                },
                actions = {
                    val ready = state as? MiniAppState.Ready
                    if (ready != null) {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, ready.url.toUri()))
                        }) {
                            Icon(
                                Icons.Default.OpenInBrowser,
                                contentDescription = stringResource(R.string.mini_app_open_in_browser)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state) {
                is MiniAppState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text  = stringResource(R.string.mini_app_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                MiniAppState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text  = stringResource(R.string.mini_app_error),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text  = stringResource(R.string.mini_app_error_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { viewModel.init() }) {
                            Text(stringResource(R.string.mini_app_retry))
                        }
                    }
                }

                is MiniAppState.Ready -> {
                    val ready = state as MiniAppState.Ready
                    Column(modifier = Modifier.fillMaxSize()) {
                        MiniAppWebView(
                            url           = ready.url,
                            initData      = ready.initData,
                            queryId       = ready.queryId,
                            viewModel     = viewModel,
                            onClose       = onClose,
                            onShowAlert   = { showAlertDialog = it },
                            onShowConfirm = { msg, cb -> showConfirmDialog = Pair(msg, cb) },
                            onFileChooser = onFileChooser,
                            modifier      = Modifier.weight(1f)
                        )
                        AnimatedVisibility(visible = mainButton?.isVisible == true) {
                            mainButton?.let { mb ->
                                val bgColor = remember(mb.color) {
                                    try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(mb.color)) }
                                    catch (e: Exception) { androidx.compose.ui.graphics.Color(0xFF2196F3.toInt()) }
                                }
                                val fgColor = remember(mb.textColor) {
                                    try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(mb.textColor)) }
                                    catch (e: Exception) { androidx.compose.ui.graphics.Color.White }
                                }
                                Button(
                                    onClick  = { viewModel.onMainButtonClick() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    enabled  = mb.isActive && !mb.isProgress,
                                    shape    = RoundedCornerShape(0.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = bgColor,
                                        contentColor   = fgColor
                                    )
                                ) {
                                    if (mb.isProgress) {
                                        CircularProgressIndicator(
                                            modifier    = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color       = fgColor
                                        )
                                    } else {
                                        Text(
                                            mb.text,
                                            style      = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
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

// ─── WebView Component ────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiniAppWebView(
    url: String,
    initData: String,
    queryId: String?,
    viewModel: MiniAppViewModel,
    onClose: () -> Unit,
    onShowAlert: (String) -> Unit,
    onShowConfirm: (String, (Boolean) -> Unit) -> Unit,
    onFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val bgColor      = MaterialTheme.colorScheme.background
    val textColor    = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark       = !MaterialTheme.colorScheme.background.equals(androidx.compose.ui.graphics.Color.White)

    val themeJson = remember(bgColor, textColor, primaryColor, surfaceColor) {
        buildThemeJson(bgColor.toArgb(), textColor.toArgb(), primaryColor.toArgb(), surfaceColor.toArgb())
    }

    val initDataJson = remember(initData) { buildInitDataJson(initData) }

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled        = true
                    domStorageEnabled        = true
                    databaseEnabled          = true
                    allowFileAccess          = false
                    allowContentAccess       = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    userAgentString = "${settings.userAgentString} WorldMatesApp/2.0"
                }

                setBackgroundColor(Color.TRANSPARENT)

                val bridge = WorldMatesWebAppBridge(
                    context       = ctx,
                    viewModel     = viewModel,
                    onClose       = onClose,
                    onShowAlert   = onShowAlert,
                    onShowConfirm = onShowConfirm,
                    queryId       = queryId
                )
                addJavascriptInterface(bridge, "_WorldMatesWebAppBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pageUrl: String) {
                        super.onPageFinished(view, pageUrl)
                        val script = buildInjectionScript(initData, initDataJson, themeJson, isDark)
                        view.evaluateJavascript(script) { result ->
                            Log.d(TAG, "JS inject result: $result")
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val reqUrl = request.url.toString()
                        return if (isSameDomain(url, reqUrl)) {
                            false
                        } else {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, reqUrl.toUri()))
                            } catch (e: Exception) {
                                Log.w(TAG, "Cannot open external URL: $reqUrl")
                            }
                            true
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams
                    ): Boolean {
                        onFileChooser(filePathCallback, fileChooserParams)
                        return true
                    }
                }

                viewModel.webViewRef = this

                loadUrl(url)
            }
        },
        update = { webView ->
            viewModel.webViewRef = webView
        }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Строит JavaScript-скрипт для инжекции в WebView.
 * Создаёт объект window.WorldMatesWebApp с API для Mini App разработчиков.
 */
private fun buildInjectionScript(
    initData: String,
    initDataJson: String,
    themeJson: String,
    isDark: Boolean
): String {
    val colorScheme = if (isDark) "dark" else "light"
    return """
(function() {
    if (window.WorldMatesWebApp) return;

    var MainButton = {
        text: '', color: '#2196F3', textColor: '#FFFFFF',
        isVisible: false, isActive: true, isProgressVisible: false,
        _callbacks: [],
        show: function() { this.isVisible = true; _WorldMatesWebAppBridge.showMainButton(this.text||' ', this.color, this.textColor); },
        hide: function() { this.isVisible = false; _WorldMatesWebAppBridge.hideMainButton(); },
        enable: function() { this.isActive = true; _WorldMatesWebAppBridge.enableMainButton(); },
        disable: function() { this.isActive = false; _WorldMatesWebAppBridge.disableMainButton(); },
        showProgress: function(leaveActive) { this.isProgressVisible = true; _WorldMatesWebAppBridge.showMainButtonProgress(!!leaveActive); },
        hideProgress: function() { this.isProgressVisible = false; _WorldMatesWebAppBridge.hideMainButtonProgress(); },
        setText: function(text) { this.text = text; _WorldMatesWebAppBridge.setMainButtonText(text); },
        onClick: function(cb) { this._callbacks.push(cb); },
        offClick: function(cb) { this._callbacks = this._callbacks.filter(function(c){ return c !== cb; }); }
    };

    var BackButton = {
        isVisible: false, _callbacks: [],
        show: function() { this.isVisible = true; _WorldMatesWebAppBridge.showBackButton(); },
        hide: function() { this.isVisible = false; _WorldMatesWebAppBridge.hideBackButton(); },
        onClick: function(cb) { this._callbacks.push(cb); },
        offClick: function(cb) { this._callbacks = this._callbacks.filter(function(c){ return c !== cb; }); }
    };

    window.WorldMatesWebApp = {
        initData:       '${initData.replace("'", "\\'")}',
        initDataUnsafe: $initDataJson,
        version:        '1.0',
        platform:       'android',
        colorScheme:    '$colorScheme',
        themeParams:    $themeJson,
        isExpanded:     true,
        viewportHeight: window.innerHeight,
        viewportStableHeight: window.innerHeight,
        isClosingConfirmationEnabled: false,
        MainButton:     MainButton,
        BackButton:     BackButton,

        ready: function() {
            _WorldMatesWebAppBridge.ready();
        },
        close: function() {
            _WorldMatesWebAppBridge.close();
        },
        expand: function() {
            _WorldMatesWebAppBridge.expand();
        },
        sendData: function(data) {
            if (typeof data !== 'string') {
                data = JSON.stringify(data);
            }
            _WorldMatesWebAppBridge.sendData(data);
        },
        showAlert: function(message, callback) {
            _WorldMatesWebAppBridge.showAlert(String(message));
            if (typeof callback === 'function') setTimeout(callback, 300);
        },
        showConfirm: function(message, callback) {
            _WorldMatesWebAppBridge.showConfirm(String(message));
            if (typeof callback === 'function') setTimeout(function() { callback(true); }, 300);
        },
        showPopup: function(params, callback) {
            var msg = (params && params.message) ? params.message : '';
            _WorldMatesWebAppBridge.showAlert(msg);
            if (typeof callback === 'function') setTimeout(function() { callback('ok'); }, 300);
        },
        openLink: function(url, options) {
            _WorldMatesWebAppBridge.openLink(String(url));
        },
        openTelegramLink: function(url) {
            _WorldMatesWebAppBridge.openLink(String(url));
        },
        hapticFeedback: {
            impactOccurred: function(style) {
                _WorldMatesWebAppBridge.hapticFeedback(style || 'medium');
            },
            notificationOccurred: function(type) {
                _WorldMatesWebAppBridge.hapticFeedback(type || 'success');
            },
            selectionChanged: function() {
                _WorldMatesWebAppBridge.hapticFeedback('selection');
            }
        },
        enableClosingConfirmation: function() {
            this.isClosingConfirmationEnabled = true;
            _WorldMatesWebAppBridge.enableClosingConfirmation();
        },
        disableClosingConfirmation: function() {
            this.isClosingConfirmationEnabled = false;
            _WorldMatesWebAppBridge.disableClosingConfirmation();
        },
        onEvent: function(eventType, callback) {},
        offEvent: function(eventType, callback) {},
        sendEvent: function(eventType, eventData) {}
    };

    if (!window.Telegram) {
        window.Telegram = { WebApp: window.WorldMatesWebApp };
    }

    document.dispatchEvent(new Event('WorldMatesWebAppReady'));
    document.dispatchEvent(new Event('TelegramGameProxyReady'));

    console.log('[WorldMates] Mini App bridge injected. version=1.0');
})();
""".trimIndent()
}

/** Преобразует initData строку в JSON-объект для initDataUnsafe */
private fun buildInitDataJson(initData: String): String {
    return try {
        val params = Uri.parse("?$initData")
        val obj = JSONObject()
        params.queryParameterNames.forEach { key ->
            val value = params.getQueryParameter(key) ?: return@forEach
            if (key == "user") {
                try { obj.put(key, JSONObject(value)) } catch (_: Exception) { obj.put(key, value) }
            } else {
                obj.put(key, value)
            }
        }
        obj.toString()
    } catch (e: Exception) {
        Log.w(TAG, "buildInitDataJson error: ${e.message}")
        "{}"
    }
}

/** Строит JSON с цветами темы для themeParams */
private fun buildThemeJson(bgColor: Int, textColor: Int, primaryColor: Int, surfaceColor: Int): String {
    fun Int.toHex(): String = String.format("#%06X", 0xFFFFFF and this)
    return """{"bg_color":"${bgColor.toHex()}","text_color":"${textColor.toHex()}","button_color":"${primaryColor.toHex()}","button_text_color":"#FFFFFF","hint_color":"#888888","link_color":"${primaryColor.toHex()}","secondary_bg_color":"${surfaceColor.toHex()}"}"""
}

/** Проверяет что два URL принадлежат одному домену */
private fun isSameDomain(base: String, target: String): Boolean {
    return try {
        val baseUri   = Uri.parse(base)
        val targetUri = Uri.parse(target)
        baseUri.host?.lowercase() == targetUri.host?.lowercase()
    } catch (e: Exception) {
        false
    }
}
