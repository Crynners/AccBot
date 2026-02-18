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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.accbot.dca.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.launch
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
    var isFrozen by remember { mutableStateOf(false) }

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
                            onTextsDetected = { texts -> if (!isFrozen) detectedTexts = texts }
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
                                isFrozen = false
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

                        // Frozen overlay
                        if (isFrozen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                            )
                        }

                        // Capture / Rescan button
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = 20.dp)
                        ) {
                            if (isFrozen) {
                                IconButton(
                                    onClick = { isFrozen = false },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                        .border(2.dp, Color.White, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .border(3.dp, Color.White, CircleShape)
                                        .clickable { isFrozen = true }
                                        .padding(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White, CircleShape)
                                    )
                                }
                            }
                        }

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
                                text = if (isFrozen) stringResource(R.string.qr_frozen_hint)
                                       else stringResource(R.string.qr_tap_to_capture),
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
    var isFrozen by remember { mutableStateOf(false) }
    var assignments by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var activeFieldKey by remember { mutableStateOf(targetFields.firstOrNull()?.key) }

    val assignedCount = assignments.count { it.value.isNotBlank() }
    val totalCount = targetFields.size

    // Pulsing animation for active field
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Pop animation for assigned fields
    val popScales = remember { targetFields.associate { it.key to Animatable(1f) } }
    val prevAssignmentsRef = remember { mutableMapOf<String, String>() }

    LaunchedEffect(assignments) {
        for (field in targetFields) {
            val current = assignments[field.key]
            val previous = prevAssignmentsRef[field.key]
            if (!current.isNullOrBlank() && (previous.isNullOrBlank() || current != previous)) {
                popScales[field.key]?.let { anim ->
                    launch {
                        anim.snapTo(1f)
                        anim.animateTo(1.15f, tween(125))
                        anim.animateTo(1f, tween(125))
                    }
                }
            }
        }
        prevAssignmentsRef.clear()
        prevAssignmentsRef.putAll(assignments)
    }

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
                            onTextsDetected = { texts -> if (!isFrozen) detectedTexts = texts },
                            reticleBottomPaddingDp = 160f
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
                                isFrozen = false
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

                // Frozen overlay
                if (isFrozen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    )
                }

                // Capture / Rescan button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 20.dp)
                ) {
                    if (isFrozen) {
                        IconButton(
                            onClick = { isFrozen = false },
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .border(2.dp, Color.White, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(3.dp, Color.White, CircleShape)
                                .clickable { isFrozen = true }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                }
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
                    // Dynamic instruction
                    val activeFieldLabel = targetFields.firstOrNull { it.key == activeFieldKey }?.label
                    if (assignedCount == totalCount) {
                        Text(
                            text = stringResource(R.string.qr_all_fields_assigned),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = successColor()
                        )
                    } else if (activeFieldLabel != null) {
                        Text(
                            text = stringResource(R.string.qr_now_assign, activeFieldLabel),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor()
                        )
                    } else {
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
                    }

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
                            val chipScale = popScales[field.key]?.value ?: 1f

                            FieldChip(
                                label = field.label,
                                assignedValue = assignedValue,
                                isActive = isActive,
                                isAssigned = isAssigned,
                                pulseAlpha = if (isActive && !isAssigned) pulseAlpha else 1f,
                                chipScale = chipScale,
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
                            // Build reverse map: text -> field label for assigned texts
                            val textToFieldLabel = assignments.entries
                                .filter { it.value.isNotBlank() }
                                .associate { (key, value) ->
                                    value to targetFields.first { it.key == key }.label
                                }
                            // Sort: unassigned first, assigned at bottom
                            val sortedTexts = detectedTexts.sortedBy {
                                if (textToFieldLabel.containsKey(it)) 1 else 0
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sortedTexts) { text ->
                                    val assignedToLabel = textToFieldLabel[text]
                                    val isTextAssigned = assignedToLabel != null

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(if (isTextAssigned) 0.35f else 1f)
                                            .clickable(enabled = !isTextAssigned && activeFieldKey != null) {
                                                activeFieldKey?.let { key ->
                                                    assignments = assignments + (key to text)
                                                    val nextEmpty =
                                                        targetFields.firstOrNull { f ->
                                                            f.key != key && assignments[f.key].isNullOrBlank()
                                                        }
                                                    activeFieldKey = nextEmpty?.key
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color.White.copy(
                                                alpha = if (isTextAssigned) 0.08f else 0.15f
                                            )
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = text,
                                                modifier = Modifier.weight(1f),
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isTextAssigned) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = assignedToLabel,
                                                    color = successColor().copy(alpha = 0.8f),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
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
                    val allAssigned = assignedCount == totalCount
                    Button(
                        onClick = { onResult(assignments) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = assignedCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allAssigned) successColor() else accentColor()
                        )
                    ) {
                        if (allAssigned) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
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
    pulseAlpha: Float = 1f,
    chipScale: Float = 1f,
    onClick: () -> Unit
) {
    val accent = accentColor()
    val success = successColor()

    val backgroundColor = when {
        isAssigned -> success.copy(alpha = 0.2f)
        isActive -> accent.copy(alpha = pulseAlpha)
        else -> Color.Transparent
    }
    val borderColor = when {
        isAssigned -> success
        isActive -> accent
        else -> Color.White.copy(alpha = 0.5f)
    }

    Column(
        modifier = Modifier
            .scale(chipScale)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (isActive && !isAssigned) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    tint = success
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isActive || isAssigned) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        if (isAssigned && !assignedValue.isNullOrBlank()) {
            Text(
                text = "${assignedValue.take(20)}...",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Horizontal reticle overlay for TEXT scan mode.
 * Shows corner brackets indicating the text scanning area.
 */
@Composable
private fun TextScanReticle(modifier: Modifier = Modifier) {
    val cornerColor = accentColor()
    val cornerSize = 20.dp
    val cornerWidth = 3.dp

    Box(
        modifier = modifier
            .width(280.dp)
            .height(48.dp)
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
    onTextsDetected: (List<String>) -> Unit,
    reticleBottomPaddingDp: Float = 140f
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
                val viewW = previewView.width
                val viewH = previewView.height

                if (mediaImage == null || viewW == 0 || viewH == 0) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                // Effective image size after rotation (ML Kit coordinate space)
                val rotation = imageProxy.imageInfo.rotationDegrees
                val imgW: Int
                val imgH: Int
                if (rotation == 90 || rotation == 270) {
                    imgW = imageProxy.height
                    imgH = imageProxy.width
                } else {
                    imgW = imageProxy.width
                    imgH = imageProxy.height
                }

                // Map on-screen reticle to image coordinates.
                // PreviewView uses FILL_CENTER (center-crop) scaling.
                val density = previewView.resources.displayMetrics.density
                val imageAspect = imgW.toFloat() / imgH
                val viewAspect = viewW.toFloat() / viewH

                val scale: Float
                val offsetX: Float
                val offsetY: Float
                if (imageAspect > viewAspect) {
                    scale = viewH.toFloat() / imgH
                    offsetX = (viewW - imgW * scale) / 2f
                    offsetY = 0f
                } else {
                    scale = viewW.toFloat() / imgW
                    offsetX = 0f
                    offsetY = (viewH - imgH * scale) / 2f
                }

                // Reticle is 280x48dp, center-aligned with padding(bottom = Xdp).
                // padding(bottom=X) + center alignment => reticle center Y = viewCenter - X/2.
                val reticleWPx = 280f * density
                val reticleHPx = 48f * density
                val reticleCX = viewW / 2f
                val reticleCY = viewH / 2f - (reticleBottomPaddingDp * density) / 2f

                val scrLeft = reticleCX - reticleWPx / 2f
                val scrTop = reticleCY - reticleHPx / 2f
                val scrRight = reticleCX + reticleWPx / 2f
                val scrBottom = reticleCY + reticleHPx / 2f

                // Convert screen coords to image coords
                val iLeft = ((scrLeft - offsetX) / scale).toInt()
                val iTop = ((scrTop - offsetY) / scale).toInt()
                val iRight = ((scrRight - offsetX) / scale).toInt()
                val iBottom = ((scrBottom - offsetY) / scale).toInt()

                // 20% margin for tolerance
                val mx = ((iRight - iLeft) * 0.2f).toInt()
                val my = ((iBottom - iTop) * 0.2f).toInt()
                val fLeft = iLeft - mx
                val fTop = iTop - my
                val fRight = iRight + mx
                val fBottom = iBottom + my

                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val candidates = visionText.textBlocks
                            .flatMap { block ->
                                block.lines.filter { line ->
                                    val box = line.boundingBox ?: return@filter false
                                    val cx = (box.left + box.right) / 2
                                    val cy = (box.top + box.bottom) / 2
                                    cx in fLeft..fRight && cy in fTop..fBottom
                                }.flatMap { line ->
                                    buildList {
                                        add(line.text.trim())
                                        if (line.elements.size > 1) {
                                            for (element in line.elements) {
                                                add(element.text.trim())
                                            }
                                        }
                                    }
                                }
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
