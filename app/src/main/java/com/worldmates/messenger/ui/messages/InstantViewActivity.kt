package com.worldmates.messenger.ui.messages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.R
import com.worldmates.messenger.network.NodeInstantViewResponse
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.ui.theme.WorldMatesTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Instant View — article reader screen.
 * Launched when user taps a link in a message and server has an Instant View for it.
 *
 * Usage:
 *   InstantViewActivity.start(context, url = "https://...", accessToken = "xxx")
 */
class InstantViewActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL          = "extra_url"
        private const val EXTRA_ACCESS_TOKEN = "extra_access_token"

        fun start(context: Context, url: String, accessToken: String) {
            context.startActivity(Intent(context, InstantViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_ACCESS_TOKEN, accessToken)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }

        val viewModel: InstantViewViewModel by viewModels()

        viewModel.load(url)

        setContent {
            WorldMatesTheme {
                InstantViewScreen(
                    url = url,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class InstantViewViewModel : ViewModel() {

    private val _state = MutableStateFlow<InstantViewState>(InstantViewState.Loading)
    val state: StateFlow<InstantViewState> = _state

    fun load(url: String) {
        viewModelScope.launch {
            _state.value = InstantViewState.Loading
            try {
                val resp = NodeRetrofitClient.api.getInstantView(url)
                if (resp.apiStatus == 200 && resp.contentHtml != null) {
                    _state.value = InstantViewState.Success(resp)
                } else {
                    _state.value = InstantViewState.Error(resp.errorMessage ?: "Failed to load")
                }
            } catch (e: Exception) {
                _state.value = InstantViewState.Error(e.message ?: "Network error")
            }
        }
    }
}

sealed class InstantViewState {
    object Loading : InstantViewState()
    data class Success(val data: NodeInstantViewResponse) : InstantViewState()
    data class Error(val message: String) : InstantViewState()
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantViewScreen(
    url: String,
    viewModel: InstantViewViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val s = state) {
                        is InstantViewState.Success -> Text(
                            text = s.data.siteName ?: url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        else -> Text(stringResource(R.string.instant_view_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.open_in_browser))
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val s = state) {
                is InstantViewState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is InstantViewState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.instant_view_error),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }) {
                            Text(stringResource(R.string.open_in_browser))
                        }
                    }
                }
                is InstantViewState.Success -> {
                    InstantViewContent(data = s.data)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InstantViewContent(data: NodeInstantViewResponse) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        data.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Description
        data.description?.let { desc ->
            if (desc.isNotBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Reading time
        Text(
            text = stringResource(R.string.instant_view_reading_time, data.readingTimeMin),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Content rendered as HTML in a WebView
        data.contentHtml?.let { html ->
            InstantViewWebContent(html = html)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InstantViewWebContent(html: String) {
    var webViewHeight by remember { mutableIntStateOf(1000) }

    val styledHtml = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0">
        <style>
          * { box-sizing: border-box; }
          body {
            font-family: -apple-system, 'Segoe UI', sans-serif;
            font-size: 16px; line-height: 1.7;
            color: #212121; margin: 0; padding: 0;
            background: transparent;
          }
          img { max-width: 100%; height: auto; border-radius: 8px; margin: 8px 0; }
          p  { margin: 0 0 12px; }
          h1,h2,h3,h4 { font-weight: 700; margin: 16px 0 8px; }
          blockquote {
            border-left: 3px solid #1976D2; margin: 12px 0;
            padding: 4px 12px; background: #E3F2FD; border-radius: 4px;
          }
          a { color: #1976D2; text-decoration: none; }
        </style>
        </head>
        <body>$html</body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (newProgress == 100) {
                            view?.evaluateJavascript("document.body.scrollHeight") { result ->
                                val h = result?.replace("\"", "")?.toIntOrNull() ?: 1000
                                webViewHeight = h
                            }
                        }
                    }
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(webViewHeight.dp)
    )
}
