package com.accbot.dca.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.accbot.dca.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor
import java.util.concurrent.Executors

private enum class ScanMode { QR, TEXT }

/**
 * Target field for multi-field scanning.
 * @param label Display label for the field chip
 * @param key Unique key to identify the field in the result map
 */
data class ScanTargetField(val label: String, val key: String)

/**
 * Button that opens QR scanner when clicked.
 * Use as trailingIcon in OutlinedTextField.
 */
@Composable
fun QrScannerButton(
    onScanResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showScanner by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showScanner = true },
        modifier = modifier,
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = stringResource(R.string.qr_scan_code),
            tint = if (enabled) accentColor() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }

    if (showScanner) {
        QrScannerDialog(
            onDismiss = { showScanner = false },
            onScanResult = { result ->
                onScanResult(result)
                showScanner = false
            }
        )
    }
}

/**
 * Full-screen dialog with camera preview for QR code and text scanning.
 */
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onScanResult: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scanMode by remember { mutableStateOf(ScanMode.QR) }
    // Detected text blocks in TEXT mode
    var detectedTexts by remember { mutableStateOf<List<String>>(emptyList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                // Camera preview - key on scanMode to force recreation when mode changes
                key(scanMode) {
                    when (scanMode) {
                        ScanMode.QR -> CameraPreviewWithQrScanner(
                            onScanResult = onScanResult
                        )
                        ScanMode.TEXT -> CameraPreviewWithTextScanner(
                            onTextsDetected = { texts -> detectedTexts = texts }
                        )
                    }
                }
            } else {
                // Permission not granted
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.qr_camera_permission_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.qr_camera_permission_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                    ) {
                        Text(stringResource(R.string.qr_grant_permission))
                    }
                }
            }

            // Top bar: Close button + Mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spacer for symmetry
                Spacer(modifier = Modifier.size(48.dp))

                // Mode toggle
                if (hasCameraPermission) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(4.dp)
                    ) {
                        ModeChip(
                            text = stringResource(R.string.qr_mode_qr),
                            isSelected = scanMode == ScanMode.QR,
                            onClick = {
                                scanMode = ScanMode.QR
                                detectedTexts = emptyList()
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ModeChip(
                            text = stringResource(R.string.qr_mode_text),
                            isSelected = scanMode == ScanMode.TEXT,
                            onClick = { scanMode = ScanMode.TEXT }
                        )
                    }
                }

                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.qr_close_scanner),
                        tint = Color.White
                    )
                }
            }

            // Bottom section
            if (hasCameraPermission) {
                when (scanMode) {
                    ScanMode.QR -> {
                        // QR instructions + scan frame
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(32.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.qr_point_camera),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.qr_scanning_auto),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // Scan frame overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(250.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            ScanFrameCorners()
                        }
                    }
                    ScanMode.TEXT -> {
                        // Text scan reticle overlay
                        TextScanReticle(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(bottom = 140.dp)
                        )

                        // Text mode: show detected texts as selectable items
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.qr_point_camera_text),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.qr_tap_to_select),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )

                            if (detectedTexts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(detectedTexts) { text ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onScanResult(text) },
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color.White.copy(alpha = 0.15f)
                                            )
                                        ) {
                                            Text(
                                                text = text,
                                                modifier = Modifier.padding(12.dp),
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.qr_no_text_detected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Multi-field scanner dialog for scanning multiple credentials at once.
 * Shows field assignment chips and lets user assign detected texts to fields.
 */
@Composable
fun MultiFieldScannerDialog(
    targetFields: List<ScanTargetField>,
    onDismiss: () -> Unit,
    onResult: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scanMode by remember { mutableStateOf(ScanMode.TEXT) }
    var detectedTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var assignments by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var activeFieldKey by remember { mutableStateOf(targetFields.firstOrNull()?.key) }

    val assignedCount = assignments.count { it.value.isNotBlank() }
    val totalCount = targetFields.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                key(scanMode) {
                    when (scanMode) {
                        ScanMode.QR -> CameraPreviewWithQrScanner(
                            onScanResult = { value ->
                                activeFieldKey?.let { key ->
                                    val cleanValue = cleanQrValue(value)
                                    assignments = assignments + (key to cleanValue)
                                    // Auto-advance to next empty field
                                    val nextEmpty = targetFields.firstOrNull { f ->
                                        f.key != key && !assignments.containsKey(f.key)
                                    }
                                    activeFieldKey = nextEmpty?.key
                                }
                            }
                        )
                        ScanMode.TEXT -> CameraPreviewWithTextScanner(
                            onTextsDetected = { texts -> detectedTexts = texts }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.qr_camera_permission_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.qr_camera_permission_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                    ) {
                        Text(stringResource(R.string.qr_grant_permission))
                    }
                }
            }

            // Top bar: Close button + Mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(48.dp))

                if (hasCameraPermission) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(4.dp)
                    ) {
                        ModeChip(
                            text = stringResource(R.string.qr_mode_qr),
                            isSelected = scanMode == ScanMode.QR,
                            onClick = {
                                scanMode = ScanMode.QR
                                detectedTexts = emptyList()
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ModeChip(
                            text = stringResource(R.string.qr_mode_text),
                            isSelected = scanMode == ScanMode.TEXT,
                            onClick = { scanMode = ScanMode.TEXT }
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.qr_close_scanner),
                        tint = Color.White
                    )
                }
            }

            // Text scan reticle for TEXT mode
            if (hasCameraPermission && scanMode == ScanMode.TEXT) {
                TextScanReticle(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 160.dp)
                )
            }

            // QR scan frame for QR mode
            if (hasCameraPermission && scanMode == ScanMode.QR) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 80.dp)
                        .size(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    ScanFrameCorners()
                }
            }

            // Bottom sheet with field chips and detected texts
            if (hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(16.dp)
                ) {
                    // Header
                    Text(
                        text = stringResource(R.string.qr_assign_to_fields),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.qr_tap_field_then_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    // Field chips
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (field in targetFields) {
                            val assignedValue = assignments[field.key]
                            val isActive = activeFieldKey == field.key
                            val isAssigned = !assignedValue.isNullOrBlank()

                            FieldChip(
                                label = field.label,
                                assignedValue = assignedValue,
                                isActive = isActive,
                                isAssigned = isAssigned,
                                onClick = {
                                    if (isAssigned) {
                                        // Clear assignment and make active
                                        assignments = assignments - field.key
                                        activeFieldKey = field.key
                                    } else {
                                        activeFieldKey = field.key
                                    }
                                }
                            )
                        }
                    }

                    // Detected text cards
                    Spacer(modifier = Modifier.height(12.dp))
                    if (scanMode == ScanMode.TEXT) {
                        if (detectedTexts.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(detectedTexts) { text ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = activeFieldKey != null) {
                                                activeFieldKey?.let { key ->
                                                    assignments = assignments + (key to text)
                                                    // Auto-advance to next empty field
                                                    val nextEmpty =
                                                        targetFields.firstOrNull { f ->
                                                            f.key != key && assignments[f.key].isNullOrBlank()
                                                        }
                                                    activeFieldKey = nextEmpty?.key
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    ) {
                                        Text(
                                            text = text,
                                            modifier = Modifier.padding(12.dp),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.qr_no_text_detected),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Done button
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onResult(assignments) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = assignedCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                    ) {
                        Text(
                            text = stringResource(R.string.qr_done_count, assignedCount, totalCount)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldChip(
    label: String,
    assignedValue: String?,
    isActive: Boolean,
    isAssigned: Boolean,
    onClick: () -> Unit
) {
    val accent = accentColor()
    val success = successColor()

    val backgroundColor = when {
        isAssigned -> success
        isActive -> accent
        else -> Color.Transparent
    }
    val borderColor = when {
        isAssigned -> success
        isActive -> accent
        else -> Color.White.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor.copy(alpha = if (isAssigned || isActive) 1f else 0f))
            .border(
                width = 1.dp,
                color = if (isAssigned || isActive) Color.Transparent else borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive && !isAssigned) {
            Text(
                text = "\u25B8 ",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (isAssigned) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = if (isAssigned) "$label: ${assignedValue?.take(12)}..." else label,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isActive || isAssigned) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Horizontal reticle overlay for TEXT scan mode.
 * Shows corner brackets indicating the text scanning area.
 */
@Composable
private fun TextScanReticle(modifier: Modifier = Modifier) {
    val cornerColor = accentColor()
    val cornerSize = 32.dp
    val cornerWidth = 3.dp

    Box(
        modifier = modifier
            .width(280.dp)
            .height(80.dp)
    ) {
        // Top-left corner
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(cornerSize)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(cornerWidth)
                    .background(cornerColor)
            )
        }

        // Top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(cornerSize)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(cornerWidth)
                    .background(cornerColor)
            )
        }

        // Bottom-left corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(cornerSize)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(cornerWidth)
                    .background(cornerColor)
            )
        }

        // Bottom-right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(cornerSize)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxHeight()
                    .width(cornerWidth)
                    .background(cornerColor)
            )
        }
    }
}

@Composable
private fun ModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) accentColor() else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Composable
private fun ScanFrameCorners() {
    val cornerColor = accentColor()
    val cornerSize = 40.dp
    val cornerWidth = 4.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Top-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(cornerSize)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(cornerWidth)
                    .background(cornerColor)
            )
        }

        // Top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(cornerSize)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(cornerWidth)
                    .background(cornerColor)
            )
        }

        // Bottom-left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(cornerSize)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(cornerWidth)
                    .background(cornerColor)
            )
        }

        // Bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(cornerSize)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxHeight()
                    .width(cornerWidth)
                    .background(cornerColor)
            )
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
internal fun CameraPreviewWithQrScanner(
    onScanResult: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasScanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { previewView ->
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val barcodeScanner = BarcodeScanning.getClient()
            val executor = Executors.newSingleThreadExecutor()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !hasScanned) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                barcode.rawValue?.let { value ->
                                    if (!hasScanned) {
                                        hasScanned = true
                                        val cleanValue = cleanQrValue(value)
                                        onScanResult(cleanValue)
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
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
                // Camera initialization failed
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
internal fun CameraPreviewWithTextScanner(
    onTextsDetected: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { previewView ->
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val executor = Executors.newSingleThreadExecutor()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            // Filter text blocks: only show those with 8+ alphanumeric chars
                            // (API keys/secrets are typically long alphanumeric strings)
                            val candidates = visionText.textBlocks
                                .flatMap { block ->
                                    block.lines.map { line -> line.text.trim() }
                                }
                                .filter { text ->
                                    val alphanumCount = text.count { it.isLetterOrDigit() }
                                    alphanumCount >= 8 && text.length <= 256
                                }
                                .distinct()

                            onTextsDetected(candidates)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
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
                // Camera initialization failed
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

/**
 * Clean QR code value by removing common prefixes.
 * Supports:
 * - bitcoin:ADDRESS?amount=X
 * - ethereum:ADDRESS
 * - litecoin:ADDRESS
 */
private fun cleanQrValue(value: String): String {
    val prefixes = listOf(
        "bitcoin:", "ethereum:", "litecoin:", "Bitcoin:",
        "BITCOIN:", "ETH:", "LTC:", "BTC:"
    )

    var cleaned = value.trim()

    // Remove crypto prefix
    for (prefix in prefixes) {
        if (cleaned.startsWith(prefix, ignoreCase = true)) {
            cleaned = cleaned.removePrefix(prefix)
            break
        }
    }

    // Remove query parameters (e.g., ?amount=0.001&label=...)
    val queryIndex = cleaned.indexOf('?')
    if (queryIndex > 0) {
        cleaned = cleaned.substring(0, queryIndex)
    }

    return cleaned
}
