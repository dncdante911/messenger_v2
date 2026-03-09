package com.worldmates.messenger.ui.groups

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    companion object {
        const val QR_RESULT_KEY = "QR_RESULT"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "QrScannerActivity"

        fun createIntent(context: Context): Intent =
            Intent(context, QrScannerActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WorldMatesThemedApp {
                QrScannerScreen(
                    hasCameraPermission = hasCameraPermission(),
                    onRequestPermission = { requestCameraPermission() },
                    onQrFound = { result -> returnResult(result) },
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted — recompose will pick up the change
                recreate()
            } else {
                finish()
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun returnResult(result: String) {
        val intent = Intent().putExtra(QR_RESULT_KEY, result)
        setResult(RESULT_OK, intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScannerScreen(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onQrFound: (String) -> Unit,
    onClose: () -> Unit
) {
    var resultHandled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.qr_scanner_title))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd)
                        )
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
            if (hasCameraPermission) {
                CameraPreviewWithAnalysis(
                    onQrFound = { result ->
                        if (!resultHandled) {
                            resultHandled = true
                            onQrFound(result)
                        }
                    }
                )
                ScanningOverlay()
            } else {
                PermissionRequestContent(onRequestPermission = onRequestPermission)
            }
        }
    }
}

@Composable
private fun CameraPreviewWithAnalysis(
    onQrFound: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { MultiFormatReader() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            decodeQrFromImage(imageProxy, reader) { result ->
                                onQrFound(result)
                            }
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("QrScannerActivity", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun decodeQrFromImage(
    imageProxy: ImageProxy,
    reader: MultiFormatReader,
    onResult: (String) -> Unit
) {
    try {
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val width = imageProxy.width
        val height = imageProxy.height

        // Convert YUV to RGB array for ZXing RGBLuminanceSource
        val rgbArray = IntArray(width * height)
        for (i in rgbArray.indices) {
            val y = bytes[i].toInt() and 0xFF
            rgbArray[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
        }

        val source = RGBLuminanceSource(width, height, rgbArray)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        val result = reader.decodeWithState(binaryBitmap)
        onResult(result.text)
    } catch (e: NotFoundException) {
        // No QR code found in this frame — expected, do nothing
    } catch (e: Exception) {
        Log.w("QrScannerActivity", "QR decode error", e)
    } finally {
        reader.reset()
        imageProxy.close()
    }
}

@Composable
private fun ScanningOverlay() {
    val overlayColor = Color.Black.copy(alpha = 0.5f)
    val borderColor = Color.White
    val cornerColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSize = size.width * 0.65f
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2f

            // Dark overlay — four rectangles around the scan box
            drawRect(color = overlayColor, topLeft = Offset(0f, 0f), size = Size(size.width, top))
            drawRect(color = overlayColor, topLeft = Offset(0f, top + boxSize), size = Size(size.width, size.height - top - boxSize))
            drawRect(color = overlayColor, topLeft = Offset(0f, top), size = Size(left, boxSize))
            drawRect(color = overlayColor, topLeft = Offset(left + boxSize, top), size = Size(size.width - left - boxSize, boxSize))

            // Scan box border
            drawRoundRect(
                color = borderColor.copy(alpha = 0.6f),
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Corner accents
            val cornerLength = boxSize * 0.12f
            val strokeWidth = 3.dp.toPx()
            val radius = 12.dp.toPx()

            // Top-left corner
            drawLine(cornerColor, Offset(left + radius, top), Offset(left + cornerLength, top), strokeWidth)
            drawLine(cornerColor, Offset(left, top + radius), Offset(left, top + cornerLength), strokeWidth)

            // Top-right corner
            drawLine(cornerColor, Offset(left + boxSize - cornerLength, top), Offset(left + boxSize - radius, top), strokeWidth)
            drawLine(cornerColor, Offset(left + boxSize, top + radius), Offset(left + boxSize, top + cornerLength), strokeWidth)

            // Bottom-left corner
            drawLine(cornerColor, Offset(left + radius, top + boxSize), Offset(left + cornerLength, top + boxSize), strokeWidth)
            drawLine(cornerColor, Offset(left, top + boxSize - cornerLength), Offset(left, top + boxSize - radius), strokeWidth)

            // Bottom-right corner
            drawLine(cornerColor, Offset(left + boxSize - cornerLength, top + boxSize), Offset(left + boxSize - radius, top + boxSize), strokeWidth)
            drawLine(cornerColor, Offset(left + boxSize, top + boxSize - cornerLength), Offset(left + boxSize, top + boxSize - radius), strokeWidth)
        }

        // Instruction label below the scan box
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.scan_qr_code),
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.camera_permission_required),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(onClick = onRequestPermission) {
            Text(text = stringResource(R.string.scan_qr_code))
        }
    }
}
