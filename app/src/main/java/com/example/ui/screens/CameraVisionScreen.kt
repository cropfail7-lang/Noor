package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.VisionUiState
import com.example.ui.theme.NoorCyan
import com.example.ui.theme.NoorGold
import com.example.ui.theme.NoorRose
import com.example.ui.theme.NoorRoseContainer
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UrduFontFamily
import java.nio.ByteBuffer

@Composable
fun CameraVisionScreen(
    cameraPermissionGranted: Boolean,
    initialQuestion: String,
    visionState: VisionUiState,
    onPermissionResult: () -> Unit,
    onAnalyzeBitmap: (Bitmap, String) -> Unit,
    onSpeakText: (String) -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var questionText by rememberSaveable(initialQuestion) { mutableStateOf(initialQuestion) }
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onPermissionResult()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val bmp = loadBitmapFromUri(context, it)
            if (bmp != null) {
                selectedBitmap = bmp
                onAnalyzeBitmap(bmp, questionText)
            }
        }
    }

    DisposableEffect(torchEnabled, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
        onDispose {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI VISION LAB",
                        style = MaterialTheme.typography.titleLarge,
                        color = NoorCyan
                    )
                    Text(
                        text = "کیمرہ سے اشیاء، دستاویزات اور ماحول پہچانیں",
                        style = TextStyle(fontFamily = UrduFontFamily, fontSize = 13.sp),
                        color = TextSecondary
                    )
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = BorderStroke(1.dp, NoorGold.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NoorGold),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("pick_photo_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick photo")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CameraX Preview or Gallery Bitmap Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ObsidianDeep)
                    .border(1.5.dp, NoorCyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                val galleryPreview = selectedBitmap
                if (galleryPreview != null) {
                    Image(
                        bitmap = galleryPreview.asImageBitmap(),
                        contentDescription = "Selected image for AI vision",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    OutlinedButton(
                        onClick = {
                            selectedBitmap = null
                            onClearResult()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ObsidianVoid.copy(alpha = 0.8f),
                            contentColor = NoorCyan
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Text("Live Camera")
                    }
                } else if (cameraPermissionGranted) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        },
                        update = { previewView ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    imageCapture = capture

                                    val selector = CameraSelector.Builder()
                                        .requireLensFacing(lensFacing)
                                        .build()

                                    cameraProvider.unbindAll()
                                    boundCamera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        capture
                                    )
                                } catch (e: Exception) {
                                    Log.e("CameraVisionScreen", "CameraX bind failed", e)
                                }
                            }, ContextCompat.getMainExecutor(context))
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Futuristic HUD Reticle & Camera Controls
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = NoorCyan.copy(alpha = 0.45f),
                        modifier = Modifier.size(84.dp)
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledIconButton(
                            onClick = { torchEnabled = !torchEnabled },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = ObsidianVoid.copy(alpha = 0.72f),
                                contentColor = if (torchEnabled) NoorGold else TextPrimary
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle flash"
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = ObsidianVoid.copy(alpha = 0.72f),
                                contentColor = NoorCyan
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera")
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Camera permission needed",
                            tint = NoorCyan,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "لائیو کیمرہ ویژن کے لیے کیمرہ اجازت درکار ہے",
                            style = TextStyle(fontFamily = UrduFontFamily, fontSize = 15.sp),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NoorCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("grant_camera_button")
                        ) {
                            Text("Grant Camera Access")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Urdu Vision Prompts
            val visionPrompts = remember {
                listOf(
                    "یہ کیا چیز ہے؟",
                    "اس دستاویز کو اردو میں پڑھیں",
                    "میرے اردگرد کیا ہے؟",
                    "لکھائی کا اردو ترجمہ کریں"
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visionPrompts) { prompt ->
                    AssistChip(
                        onClick = { questionText = prompt },
                        label = {
                            Text(
                                text = prompt,
                                style = TextStyle(fontFamily = UrduFontFamily, fontSize = 13.sp),
                                color = NoorCyan
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                label = {
                    Text(
                        text = "کیمرہ سے متعلق سوال (Urdu / English)",
                        style = TextStyle(fontFamily = UrduFontFamily, fontSize = 13.sp)
                    )
                },
                placeholder = {
                    Text(
                        text = "مثال: اس صفحے پر کیا لکھا ہے؟",
                        style = TextStyle(fontFamily = UrduFontFamily, fontSize = 14.sp),
                        color = TextMuted
                    )
                },
                textStyle = TextStyle(
                    fontFamily = UrduFontFamily,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    textDirection = TextDirection.Content
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NoorCyan,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vision_question_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val existing = selectedBitmap
                    if (existing != null) {
                        onAnalyzeBitmap(existing, questionText)
                    } else {
                        val capture = imageCapture
                        if (capture != null) {
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bmp = imageProxyToBitmap(image)
                                        image.close()
                                        if (bmp != null) {
                                            onAnalyzeBitmap(bmp, questionText)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraVisionScreen", "Capture failed", exception)
                                    }
                                }
                            )
                        }
                    }
                },
                enabled = (cameraPermissionGranted || selectedBitmap != null) && visionState !is VisionUiState.Analyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NoorCyan,
                    contentColor = ObsidianVoid
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("capture_analyze_button")
            ) {
                if (visionState is VisionUiState.Analyzing) {
                    CircularProgressIndicator(
                        color = ObsidianVoid,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("نور تصویر دیکھ رہی ہے...")
                } else {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedBitmap != null) "Analyze Selected Image" else "Capture & Ask Noor",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Vision Analysis Result Card
            when (visionState) {
                is VisionUiState.Result -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                        border = BorderStroke(1.dp, NoorCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "نور کا بصری تجزیہ • ${visionState.emotion.labelUrdu}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NoorGold
                                )
                                FilledIconButton(
                                    onClick = { onSpeakText(visionState.answer) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = ObsidianSurface,
                                        contentColor = NoorCyan
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak answer")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = visionState.answer,
                                style = TextStyle(
                                    fontFamily = UrduFontFamily,
                                    fontSize = 16.sp,
                                    lineHeight = 28.sp,
                                    textDirection = TextDirection.Content
                                ),
                                color = TextPrimary
                            )
                        }
                    }
                }
                is VisionUiState.Error -> {
                    Surface(
                        color = NoorRoseContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, NoorRose)
                    ) {
                        Text(
                            text = visionState.message,
                            color = TextPrimary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer: ByteBuffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        Log.e("CameraVisionScreen", "Failed to decode picked image", e)
        null
    }
}
