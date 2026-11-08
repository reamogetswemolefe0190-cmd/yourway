package com.example.wififingerprintsurvey.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wififingerprintsurvey.SurveyRecord
import com.example.wififingerprintsurvey.WifiMeasurement
import com.example.wififingerprintsurvey.WifiSurveyDbHelper
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab {
    SURVEY, FLOOR_MAP, HISTORY
}

@Composable
fun MainScreen(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dbHelper = remember { WifiSurveyDbHelper(context.applicationContext) }
    
    var currentTab by remember { mutableStateOf(AppTab.SURVEY) }
    var locationLabel by remember { mutableStateOf("") }
    var currentScanResults by remember { mutableStateOf(emptyList<WifiMeasurement>()) }
    var isScanning by remember { mutableStateOf(false) }
    var savedRecords by remember { mutableStateOf(emptyList<SurveyRecord>()) }
    var scanMessage by remember { mutableStateOf("") }
    
    // Load records from DB
    LaunchedEffect(Unit) {
        savedRecords = dbHelper.getAllSurveys()
    }
    
    val refreshRecords = {
        savedRecords = dbHelper.getAllSurveys()
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.SURVEY,
                    onClick = { currentTab = AppTab.SURVEY },
                    icon = { Icon(Icons.Default.EditLocation, contentDescription = "Quick Survey") },
                    label = { Text("Survey") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.FLOOR_MAP,
                    onClick = { currentTab = AppTab.FLOOR_MAP },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Floor Map") },
                    label = { Text("Floor Map") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.HISTORY,
                    onClick = { currentTab = AppTab.HISTORY },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "History") },
                    label = { Text("History") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
            Text(
                text = "Wi-Fi Fingerprint Survey",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center
            )

            if (!permissionGranted) {
                PermissionRequestView(onRequestPermission = onRequestPermission)
            } else {
                when (currentTab) {
                    AppTab.SURVEY -> {
                        SurveyTab(
                            locationLabel = locationLabel,
                            onLabelChange = { locationLabel = it },
                            currentScanResults = currentScanResults,
                            isScanning = isScanning,
                            scanMessage = scanMessage,
                            onScanClick = {
                                isScanning = true
                                scanMessage = "Scanning..."
                                triggerWifiScan(context,
                                    onResults = { results ->
                                        currentScanResults = results.sortedByDescending { it.rssi }
                                        isScanning = false
                                        scanMessage = if (results.isEmpty()) "No Wi-Fi networks found." else "Scan completed: ${results.size} APs found."
                                    },
                                    onError = { error ->
                                        isScanning = false
                                        scanMessage = error
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            onSaveClick = {
                                if (locationLabel.isBlank()) {
                                    Toast.makeText(context, "Please enter a location label", Toast.LENGTH_SHORT).show()
                                } else if (currentScanResults.isEmpty()) {
                                    Toast.makeText(context, "No scan data to save", Toast.LENGTH_SHORT).show()
                                } else {
                                    val id = dbHelper.saveSurvey(locationLabel.trim(), currentScanResults)
                                    if (id != -1L) {
                                        locationLabel = ""
                                        currentScanResults = emptyList()
                                        scanMessage = ""
                                        refreshRecords()
                                        Toast.makeText(context, "Fingerprint saved successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save to database", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                    AppTab.FLOOR_MAP -> {
                        FloorMapTab(
                            dbHelper = dbHelper,
                            savedRecords = savedRecords,
                            onRecordSaved = refreshRecords
                        )
                    }
                    AppTab.HISTORY -> {
                        HistoryTab(
                            savedRecords = savedRecords,
                            onDeleteRecord = { record ->
                                if (dbHelper.deleteSurvey(record.id)) {
                                    refreshRecords()
                                    Toast.makeText(context, "Record deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequestView(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permission Alert",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Location Permission Required",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Android requires Fine Location permission to scan for nearby Wi-Fi access points. Please grant this permission to continue.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Grant Permission", color = Color.White)
            }
        }
    }
}

@Composable
fun ColumnScope.SurveyTab(
    locationLabel: String,
    onLabelChange: (String) -> Unit,
    currentScanResults: List<WifiMeasurement>,
    isScanning: Boolean,
    scanMessage: String,
    onScanClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "1. Label Location",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = locationLabel,
                onValueChange = onLabelChange,
                label = { Text("Location Name (e.g. Eng Block, Room 204)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "2. Gather Fingerprint",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onScanClick,
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan")
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = onSaveClick,
                    enabled = locationLabel.isNotBlank() && currentScanResults.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save")
                }
            }
            
            if (scanMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = scanMessage,
                    fontSize = 13.sp,
                    color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    Text(
        text = "Current Scan Results (${currentScanResults.size} networks)",
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )

    if (currentScanResults.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tap 'Scan' to start recording Wi-Fi signals",
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(currentScanResults) { result ->
                WifiResultRow(result)
            }
        }
    }
}

@Composable
fun FloorMapTab(
    dbHelper: WifiSurveyDbHelper,
    savedRecords: List<SurveyRecord>,
    onRecordSaved: () -> Unit
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    // Metric Building Dimensions (in meters)
    var buildingWidthText by remember { mutableStateOf("50") }
    var buildingLengthText by remember { mutableStateOf("30") }
    val buildingWidthMeters = buildingWidthText.toFloatOrNull() ?: 50f
    val buildingLengthMeters = buildingLengthText.toFloatOrNull() ?: 30f
    
    var pinnedX by remember { mutableStateOf<Float?>(null) }
    var pinnedY by remember { mutableStateOf<Float?>(null) }
    var locationLabel by remember { mutableStateOf("") }
    
    var scanResults by remember { mutableStateOf(emptyList<WifiMeasurement>()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("") }

    // --- No-Map Landmark Surveying State ---
    var isNoMapMode by remember { mutableStateOf(true) }
    var startLandmark by remember { mutableStateOf("") }
    var finishLandmark by remember { mutableStateOf("") }
    var isNoMapWalkActive by remember { mutableStateOf(false) }
    var noMapStartTime by remember { mutableStateOf(0L) }
    val noMapScans = remember { mutableStateListOf<Pair<Long, List<WifiMeasurement>>>() }

    // --- Start-to-Finish Path Interpolation State ---
    var isInterpolationMode by remember { mutableStateOf(false) }
    var interpStartPos by remember { mutableStateOf<Offset?>(null) }
    var interpEndPos by remember { mutableStateOf<Offset?>(null) }
    var isInterpolationWalkActive by remember { mutableStateOf(false) }
    var interpStartTime by remember { mutableStateOf(0L) }
    val interpScans = remember { mutableStateListOf<Pair<Long, List<WifiMeasurement>>>() }

    // --- Pedestrian Dead Reckoning (PDR) & Calibration State ---
    var isWalkModeActive by remember { mutableStateOf(false) }
    var stepCount by remember { mutableStateOf(0) }
    var stepLengthMeters by remember { mutableStateOf(0.7f) }
    var currentHeadingDeg by remember { mutableStateOf(0f) }
    var compassOffsetDeg by remember { mutableStateOf(0f) }
    var autoScanEnabled by remember { mutableStateOf(false) }
    var autoScanInterval by remember { mutableStateOf(5) }

    // Two-Point Calibration State
    var isCalibrating by remember { mutableStateOf(false) }
    var calibrationStartPos by remember { mutableStateOf<Offset?>(null) }
    var calibrationHeadingList = remember { mutableStateListOf<Float>() }

    // Usability & Alignment States
    var isPhoneMisaligned by remember { mutableStateOf(false) }
    var isStandstillDetected by remember { mutableStateOf(false) }

    // Sensor Registration
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var lastAccelMagnitude = 0f
        var lastStepTimestamp = 0L
        
        // Sliding window for standstill detection (accelerometer variance)
        val accelMagnitudeHistory = mutableListOf<Float>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                
                // 1. Process Compass Heading and Device Alignment (Pitch/Roll)
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    
                    val azimuthRad = orientation[0]
                    val pitchRad = orientation[1]
                    val rollRad = orientation[2]

                    var deg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                    if (deg < 0) deg += 360f
                    currentHeadingDeg = deg

                    val pitchDeg = Math.toDegrees(pitchRad.toDouble())
                    val rollDeg = Math.toDegrees(rollRad.toDouble())

                    // Warn if phone is tilted upright (>35 degrees) or significantly rolled sideways
                    isPhoneMisaligned = Math.abs(pitchDeg) > 35 || Math.abs(rollDeg) > 35
                }

                // Function to process a single step
                fun onStepDetected() {
                    stepCount++
                    
                    // If calibration is active, collect heading readings during the walk segment
                    if (isCalibrating) {
                        calibrationHeadingList.add(currentHeadingDeg)
                    }

                    if (isWalkModeActive && pinnedX != null && pinnedY != null) {
                        val effectiveHeadingDeg = (currentHeadingDeg + compassOffsetDeg) % 360f
                        val rad = Math.toRadians(effectiveHeadingDeg.toDouble())
                        val dxMeters = stepLengthMeters * Math.sin(rad).toFloat()
                        val dyMeters = -stepLengthMeters * Math.cos(rad).toFloat()

                        val dXNorm = dxMeters / buildingWidthMeters
                        val dYNorm = dyMeters / buildingLengthMeters

                        val newX = (pinnedX!! + dXNorm).coerceIn(0f, 1f)
                        val newY = (pinnedY!! + dYNorm).coerceIn(0f, 1f)

                        pinnedX = (newX * 1000).toInt() / 1000f
                        pinnedY = (newY * 1000).toInt() / 1000f

                        val realX = String.format(Locale.US, "%.1fm", pinnedX!! * buildingWidthMeters)
                        val realY = String.format(Locale.US, "%.1fm", pinnedY!! * buildingLengthMeters)
                        locationLabel = "Point ($realX, $realY) - Step $stepCount"

                        // Auto-Scan check
                        if (autoScanEnabled && stepCount % autoScanInterval == 0 && !isScanning) {
                            isScanning = true
                            scanMessage = "Auto-Scanning step $stepCount..."
                            triggerWifiScan(context,
                                onResults = { results ->
                                    scanResults = results.sortedByDescending { it.rssi }
                                    isScanning = false
                                    val id = dbHelper.saveSurvey(
                                        label = locationLabel,
                                        measurements = scanResults,
                                        coordX = pinnedX,
                                        coordY = pinnedY,
                                        floorPlanName = "PDR Walk Mode (${buildingWidthMeters}mx${buildingLengthMeters}m)"
                                    )
                                    if (id != -1L) {
                                        onRecordSaved()
                                        scanMessage = "Auto-saved at step $stepCount (${results.size} APs)"
                                    }
                                },
                                onError = { err ->
                                    isScanning = false
                                    scanMessage = err
                                }
                            )
                        }
                    }
                }

                // 2. Hardware Step Detector
                if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                    onStepDetected()
                }

                // 3. Fallback Peak-Detection Accelerometer Step Detector & Standstill Checker
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val mag = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    val delta = mag - lastAccelMagnitude
                    lastAccelMagnitude = mag

                    // Keep a 2-second moving history of acceleration magnitude for standstill detection
                    accelMagnitudeHistory.add(mag)
                    if (accelMagnitudeHistory.size > 100) { // approx 2 seconds at 50Hz sensor rate
                        accelMagnitudeHistory.removeAt(0)
                    }

                    // Calculate variance of magnitude values
                    if (accelMagnitudeHistory.size >= 50) {
                        val mean = accelMagnitudeHistory.average().toFloat()
                        val variance = accelMagnitudeHistory.map { (it - mean) * (it - mean) }.sum() / accelMagnitudeHistory.size
                        isStandstillDetected = variance < 0.05f // very low acceleration variance means stationary
                    }

                    val now = System.currentTimeMillis()
                    if (delta > 3.2f && (now - lastStepTimestamp) > 400L) {
                        lastStepTimestamp = now
                        onStepDetected()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        if (stepSensor != null) {
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (accelSensor != null) {
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Background Scanning Effect for No-Map Landmark Mode
    LaunchedEffect(isNoMapWalkActive) {
        if (isNoMapWalkActive) {
            while (true) {
                triggerWifiScan(context,
                    onResults = { results ->
                        val timestamp = System.currentTimeMillis()
                        noMapScans.add(timestamp to results)
                        Toast.makeText(context, "Logged walk scan (${results.size} APs)", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        // Ignore scan errors, keep logging
                    }
                )
                kotlinx.coroutines.delay(3500L) // Scan periodically every 3.5 seconds
            }
        }
    }

    // Function to calculate and save landmark walk scans
    fun finishNoMapWalk() {
        val endTime = System.currentTimeMillis()
        val duration = endTime - noMapStartTime
        if (duration <= 0 || startLandmark.isBlank() || finishLandmark.isBlank()) {
            isNoMapWalkActive = false
            return
        }

        var savedCount = 0
        for ((scanTime, results) in noMapScans) {
            val progress = ((scanTime - noMapStartTime).toFloat() / duration).coerceIn(0f, 1f)
            val roundedProgress = (progress * 1000).toInt() / 1000f

            val label = "Path: $startLandmark to $finishLandmark (Progress: ${(roundedProgress * 100).toInt()}%)"

            val id = dbHelper.saveSurvey(
                label = label,
                measurements = results,
                coordX = roundedProgress, // Store progress ratio on X axis
                coordY = 0.0f,            // Y is 0 for 1D paths
                floorPlanName = "Landmark Path"
            )
            if (id != -1L) {
                savedCount++
            }
        }

        Toast.makeText(context, "Saved $savedCount landmark path scans!", Toast.LENGTH_LONG).show()
        onRecordSaved()

        // Reset states
        isNoMapWalkActive = false
        startLandmark = ""
        finishLandmark = ""
        noMapScans.clear()
    }

    // Background Scanning Effect for Interpolation Mode
    LaunchedEffect(isInterpolationWalkActive) {
        if (isInterpolationWalkActive) {
            while (true) {
                triggerWifiScan(context,
                    onResults = { results ->
                        val timestamp = System.currentTimeMillis()
                        interpScans.add(timestamp to results)
                        Toast.makeText(context, "Scan captured during walk (${results.size} APs)", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        // Suppress scan errors during walking session
                    }
                )
                kotlinx.coroutines.delay(3500L) // Scan periodically every 3.5 seconds
            }
        }
    }

    // Function to calculate and save interpolated path scans
    fun finishInterpolationWalk() {
        val endTime = System.currentTimeMillis()
        val duration = endTime - interpStartTime
        if (duration <= 0 || interpStartPos == null || interpEndPos == null) {
            isInterpolationWalkActive = false
            return
        }

        var savedCount = 0
        for ((scanTime, results) in interpScans) {
            val progress = ((scanTime - interpStartTime).toFloat() / duration).coerceIn(0f, 1f)
            
            // Derive exact interpolated (X,Y) coordinates along vector A -> B
            val x = interpStartPos!!.x + progress * (interpEndPos!!.x - interpStartPos!!.x)
            val y = interpStartPos!!.y + progress * (interpEndPos!!.y - interpStartPos!!.y)
            
            val roundedX = (x * 1000).toInt() / 1000f
            val roundedY = (y * 1000).toInt() / 1000f
            
            val realX = String.format(Locale.US, "%.1fm", roundedX * buildingWidthMeters)
            val realY = String.format(Locale.US, "%.1fm", roundedY * buildingLengthMeters)
            val label = "Interpolated ($realX, $realY)"
            
            val id = dbHelper.saveSurvey(
                label = label,
                measurements = results,
                coordX = roundedX,
                coordY = roundedY,
                floorPlanName = "Interpolation Walk (${buildingWidthMeters}mx${buildingLengthMeters}m)"
            )
            if (id != -1L) {
                savedCount++
            }
        }

        Toast.makeText(context, "Saved $savedCount interpolated walk scans successfully!", Toast.LENGTH_LONG).show()
        onRecordSaved()

        // Reset walk parameters
        isInterpolationWalkActive = false
        interpStartPos = null
        interpEndPos = null
        interpScans.clear()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bm = BitmapFactory.decodeStream(stream)
                    loadedBitmap = bm?.asImageBitmap()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load floor image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Selector Header (Simple vs Advanced)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Surveying Mode:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isNoMapMode,
                        onClick = { isNoMapMode = true },
                        label = { Text("Landmark (No Map)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = !isNoMapMode,
                        onClick = { isNoMapMode = false },
                        label = { Text("Advanced Grid Map", fontSize = 11.sp) }
                    )
                }
            }
        }

        if (isNoMapMode) {
            // RENDER PURE NO-MAP LANDMARK INTERFACE
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Landmark Walk Surveying",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Walk at a constant speed from Start to Finish. The app tags all scans with landmark progress.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = startLandmark,
                        onValueChange = { startLandmark = it },
                        label = { Text("Start Landmark (e.g. Staircase 2F)", color = Color.LightGray) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF38BDF8),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isNoMapWalkActive
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = finishLandmark,
                        onValueChange = { finishLandmark = it },
                        label = { Text("Finish Landmark (e.g. Library Entrance)", color = Color.LightGray) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF38BDF8),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isNoMapWalkActive
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (startLandmark.isBlank() || finishLandmark.isBlank()) {
                                Toast.makeText(context, "Please enter both Start and Finish landmarks!", Toast.LENGTH_SHORT).show()
                            } else if (!isNoMapWalkActive) {
                                isNoMapWalkActive = true
                                noMapStartTime = System.currentTimeMillis()
                                noMapScans.clear()
                                Toast.makeText(context, "Walk session started! Walk normally.", Toast.LENGTH_SHORT).show()
                            } else {
                                finishNoMapWalk()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNoMapWalkActive) Color(0xFFEF4444) else Color(0xFF3B82F6)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isNoMapWalkActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isNoMapWalkActive) "Finish Walk" else "Start Walk",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isNoMapWalkActive) {
                        Text(
                            text = "Recording Walk... Scans Captured: ${noMapScans.size}",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Ready to record",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            // Floor Plan Header & Dimension Inputs Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedImageUri != null) "Floor Plan: Custom Image" else "Interactive Floor Plan Grid",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Load Image", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Building Dimensions Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = buildingWidthText,
                            onValueChange = { buildingWidthText = it },
                            label = { Text("Width (m)", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )

                        OutlinedTextField(
                            value = buildingLengthText,
                            onValueChange = { buildingLengthText = it },
                            label = { Text("Length (m)", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                        )
                    }
                }
            }

            // Live Warning Alerts (Orientation / Standstill)
            AnimatedVisibility(
                visible = !isInterpolationMode && (isPhoneMisaligned || (isWalkModeActive && isStandstillDetected)),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPhoneMisaligned) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPhoneMisaligned) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isPhoneMisaligned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPhoneMisaligned) "⚠️ Hold Phone Flat & Forward for Accurate Compass Tracking!" else "Standstill detected. Long-press map to correct location drift.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPhoneMisaligned) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // PDR Walk Mode & Calibration Control Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCalibrating) MaterialTheme.colorScheme.secondaryContainer else if (isWalkModeActive || isInterpolationWalkActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    // Segmented Mode Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isInterpolationMode,
                            onClick = {
                                isInterpolationMode = false
                                isWalkModeActive = false
                                isInterpolationWalkActive = false
                            },
                            label = { Text("PDR Mode (Compass)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isInterpolationMode,
                            onClick = {
                                isInterpolationMode = true
                                isWalkModeActive = false
                                isInterpolationWalkActive = false
                            },
                            label = { Text("Start-Finish Interpolate", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isInterpolationMode) {
                            // Two-Point Path Calibration Button
                            Button(
                                onClick = {
                                    if (!isCalibrating) {
                                        isCalibrating = true
                                        isWalkModeActive = false
                                        calibrationStartPos = null
                                        calibrationHeadingList.clear()
                                        stepCount = 0
                                        Toast.makeText(context, "Step 1: Tap your starting point A on the map.", Toast.LENGTH_LONG).show()
                                    } else {
                                        isCalibrating = false
                                        Toast.makeText(context, "Calibration canceled.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCalibrating) Color(0xFFF59E0B) else MaterialTheme.colorScheme.secondary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(if (isCalibrating) "Cancel" else "Calib Path", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            // Walk Mode Button
                            Button(
                                onClick = {
                                    if (pinnedX == null || pinnedY == null) {
                                        Toast.makeText(context, "Tap your starting point on the map first!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isWalkModeActive = !isWalkModeActive
                                        if (isWalkModeActive) {
                                            stepCount = 0
                                            Toast.makeText(context, "Walk Mode STARTED! Walk normally.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isWalkModeActive) Color(0xFFEF4444) else Color(0xFF10B981)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                enabled = !isCalibrating,
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(if (isWalkModeActive) Icons.Default.DirectionsWalk else Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(if (isWalkModeActive) "Pause" else "Walk Mode", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            // Steps Text
                            Text(
                                text = "Steps: $stepCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            // Auto-Scan Box with non-wrapping Text
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Checkbox(
                                    checked = autoScanEnabled,
                                    onCheckedChange = { autoScanEnabled = it },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Auto-Scan",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            // Start-Finish Interpolation Walk Controls
                            Button(
                                onClick = {
                                    if (interpStartPos == null || interpEndPos == null) {
                                        Toast.makeText(context, "Tap Start (A) and Finish (B) on the map first!", Toast.LENGTH_SHORT).show()
                                    } else if (!isInterpolationWalkActive) {
                                        isInterpolationWalkActive = true
                                        interpStartTime = System.currentTimeMillis()
                                        interpScans.clear()
                                        Toast.makeText(context, "Walk STARTED! Walk steadily from A to B.", Toast.LENGTH_LONG).show()
                                    } else {
                                        finishInterpolationWalk()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isInterpolationWalkActive) Color(0xFFEF4444) else Color(0xFF3B82F6)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Icon(
                                    imageVector = if (isInterpolationWalkActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isInterpolationWalkActive) "Finish Walk" else "Start Walk",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = if (isInterpolationWalkActive) "Scans: ${interpScans.size}" else "Tap A (Start) & B (End)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1.2f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Interactive Canvas Container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                val roundedX = (normX * 1000).toInt() / 1000f
                                val roundedY = (normY * 1000).toInt() / 1000f

                                if (isInterpolationMode) {
                                    if (interpStartPos == null) {
                                        interpStartPos = Offset(roundedX, roundedY)
                                        Toast.makeText(context, "Start Point A set. Now tap Finish Point B.", Toast.LENGTH_SHORT).show()
                                    } else if (interpEndPos == null) {
                                        interpEndPos = Offset(roundedX, roundedY)
                                        Toast.makeText(context, "Finish Point B set. Tap 'Start Walk' to begin.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        interpStartPos = Offset(roundedX, roundedY)
                                        interpEndPos = null
                                        Toast.makeText(context, "Start Point A reset. Tap Finish Point B.", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (isCalibrating) {
                                    if (calibrationStartPos == null) {
                                        calibrationStartPos = offset
                                        pinnedX = roundedX
                                        pinnedY = roundedY
                                        Toast.makeText(context, "Step 2: Walk 5-10 steps in a straight line, then tap Point B on the map.", Toast.LENGTH_LONG).show()
                                    } else {
                                        // Calculate heading angle implied by Map Vector A -> B
                                        val dx = offset.x - calibrationStartPos!!.x
                                        val dy = offset.y - calibrationStartPos!!.y
                                        
                                        // Screen coordinates: Y goes down, so invert dy to calculate mathematical heading
                                        val mapAngleRad = Math.atan2(dx.toDouble(), -dy.toDouble())
                                        var mapAngleDeg = Math.toDegrees(mapAngleRad).toFloat()
                                        if (mapAngleDeg < 0) mapAngleDeg += 360f

                                        // Average compass reading during segment
                                        val avgSensorHeading = if (calibrationHeadingList.isNotEmpty()) {
                                            calibrationHeadingList.average().toFloat()
                                        } else {
                                            currentHeadingDeg
                                        }

                                        // Store offset
                                        compassOffsetDeg = (mapAngleDeg - avgSensorHeading) % 360f
                                        isCalibrating = false
                                        isWalkModeActive = true
                                        stepCount = 0
                                        pinnedX = roundedX
                                        pinnedY = roundedY
                                        
                                        val realX = String.format(Locale.US, "%.1fm", pinnedX!! * buildingWidthMeters)
                                        val realY = String.format(Locale.US, "%.1fm", pinnedY!! * buildingLengthMeters)
                                        locationLabel = "Point ($realX, $realY)"

                                        Toast.makeText(context, "Calibration Complete! Offset: ${compassOffsetDeg.toInt()}°", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    // Default Single-Tap: move reticle anchor
                                    pinnedX = roundedX
                                    pinnedY = roundedY
                                    val realX = String.format(Locale.US, "%.1fm", pinnedX!! * buildingWidthMeters)
                                    val realY = String.format(Locale.US, "%.1fm", pinnedY!! * buildingLengthMeters)
                                    locationLabel = "Point ($realX, $realY)"
                                }
                            },
                            onLongPress = { offset ->
                                if (!isInterpolationMode) {
                                    // Snap/Re-anchor target reticle directly
                                    val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                    val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                    pinnedX = (normX * 1000).toInt() / 1000f
                                    pinnedY = (normY * 1000).toInt() / 1000f

                                    val realX = String.format(Locale.US, "%.1fm", pinnedX!! * buildingWidthMeters)
                                    val realY = String.format(Locale.US, "%.1fm", pinnedY!! * buildingLengthMeters)
                                    locationLabel = "Point ($realX, $realY) [Re-anchored]"
                                    
                                    Toast.makeText(context, "Pin snapped/re-anchored to coordinate!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
            ) {
                val containerWidth = constraints.maxWidth.toFloat()
                val containerHeight = constraints.maxHeight.toFloat()

                // 1. Render floor image or architectural grid background
                if (loadedBitmap != null) {
                    Image(
                        bitmap = loadedBitmap!!,
                        contentDescription = "Floor Plan",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    // Architectural Floor Grid Drawing
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridColor = Color(0xFF334155)
                        val roomBorderColor = Color(0xFF38BDF8)
                        
                        // Draw grid
                        val step = 40.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += step
                        }
                        
                        // Draw Rooms & Corridors
                        val w = size.width
                        val h = size.height

                        // Main Corridor
                        drawRect(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(w * 0.3f, 0f),
                            size = Size(w * 0.4f, h)
                        )

                        // Rooms Left
                        drawRect(
                            color = roomBorderColor,
                            topLeft = Offset(w * 0.05f, h * 0.05f),
                            size = Size(w * 0.22f, h * 0.4f),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawRect(
                            color = roomBorderColor,
                            topLeft = Offset(w * 0.05f, h * 0.52f),
                            size = Size(w * 0.22f, h * 0.4f),
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Rooms Right
                        drawRect(
                            color = roomBorderColor,
                            topLeft = Offset(w * 0.73f, h * 0.05f),
                            size = Size(w * 0.22f, h * 0.4f),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawRect(
                            color = roomBorderColor,
                            topLeft = Offset(w * 0.73f, h * 0.52f),
                            size = Size(w * 0.22f, h * 0.4f),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                // 2. Draw existing sampled pins from DB
                Canvas(modifier = Modifier.fillMaxSize()) {
                    savedRecords.forEach { record ->
                        if (record.coordX != null && record.coordY != null) {
                            val px = record.coordX * containerWidth
                            val py = record.coordY * containerHeight
                            
                            // Draw glowing radar circle for sampled location
                            drawCircle(
                                color = Color(0xFF10B981).copy(alpha = 0.3f),
                                radius = 18.dp.toPx(),
                                center = Offset(px, py)
                            )
                            drawCircle(
                                color = Color(0xFF10B981),
                                radius = 6.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }
                }

                // 3. Draw active target pin & compass heading vector if in PDR mode
                if (!isInterpolationMode && pinnedX != null && pinnedY != null) {
                    val targetPx = pinnedX!! * containerWidth
                    val targetPy = pinnedY!! * containerHeight

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val reticleColor = if (isCalibrating) Color(0xFFF59E0B) else if (isWalkModeActive) Color(0xFF10B981) else Color(0xFFF43F5E)
                        drawCircle(
                            color = reticleColor.copy(alpha = 0.35f),
                            radius = 24.dp.toPx(),
                            center = Offset(targetPx, targetPy)
                        )
                        drawCircle(
                            color = reticleColor,
                            radius = 8.dp.toPx(),
                            center = Offset(targetPx, targetPy)
                        )
                        
                        val len = 14.dp.toPx()
                        drawLine(Color.White, Offset(targetPx - len, targetPy), Offset(targetPx + len, targetPy), strokeWidth = 2.dp.toPx())
                        drawLine(Color.White, Offset(targetPx, targetPy - len), Offset(targetPx, targetPy + len), strokeWidth = 2.dp.toPx())

                        val effectiveHeading = (currentHeadingDeg + compassOffsetDeg) % 360f
                        val rad = Math.toRadians(effectiveHeading.toDouble())
                        val arrowLen = 40.dp.toPx()
                        val arrowX = targetPx + arrowLen * Math.sin(rad).toFloat()
                        val arrowY = targetPy - arrowLen * Math.cos(rad).toFloat()

                        drawLine(
                            color = Color(0xFF38BDF8),
                            start = Offset(targetPx, targetPy),
                            end = Offset(arrowX, arrowY),
                            strokeWidth = 3.dp.toPx()
                        )
                        drawCircle(
                            color = Color(0xFF38BDF8),
                            radius = 5.dp.toPx(),
                            center = Offset(arrowX, arrowY)
                        )
                    }
                }

                // 4. Draw Start-to-Finish Interpolation Path Line & Points
                if (isInterpolationMode) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (interpStartPos != null) {
                            val sx = interpStartPos!!.x * containerWidth
                            val sy = interpStartPos!!.y * containerHeight
                            drawCircle(
                                color = Color(0xFF3B82F6), // Blue Start
                                radius = 12.dp.toPx(),
                                center = Offset(sx, sy)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(sx, sy)
                            )
                        }

                        if (interpEndPos != null) {
                            val ex = interpEndPos!!.x * containerWidth
                            val ey = interpEndPos!!.y * containerHeight
                            drawCircle(
                                color = Color(0xFF10B981), // Green End
                                radius = 12.dp.toPx(),
                                center = Offset(ex, ey)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(ex, ey)
                            )
                        }

                        if (interpStartPos != null && interpEndPos != null) {
                            val sx = interpStartPos!!.x * containerWidth
                            val sy = interpStartPos!!.y * containerHeight
                            val ex = interpEndPos!!.x * containerWidth
                            val ey = interpEndPos!!.y * containerHeight
                            
                            // Draw dashed line representing path segment
                            drawLine(
                                color = Color.White.copy(alpha = 0.8f),
                                start = Offset(sx, sy),
                                end = Offset(ex, ey),
                                strokeWidth = 3.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                            )
                        }
                    }
                }

                // Floating Coordinate & Badge Indicator
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        val badgeText = if (isInterpolationMode) {
                            val startStr = if (interpStartPos != null) "A:(${interpStartPos!!.x}, ${interpStartPos!!.y})" else "A:unset"
                            val endStr = if (interpEndPos != null) "B:(${interpEndPos!!.x}, ${interpEndPos!!.y})" else "B:unset"
                            val activeStr = if (isInterpolationWalkActive) " [WALKING]" else ""
                            "Path: $startStr -> $endStr$activeStr"
                        } else if (pinnedX != null && pinnedY != null) {
                            val realX = String.format(Locale.US, "%.1fm", pinnedX!! * buildingWidthMeters)
                            val realY = String.format(Locale.US, "%.1fm", pinnedY!! * buildingLengthMeters)
                            val modeStr = if (isCalibrating) " [CALIBRATING]" else if (isWalkModeActive) " [WALK ACTIVE]" else ""
                            "Pinned: X=$pinnedX ($realX), Y=$pinnedY ($realY)$modeStr"
                        } else {
                            "Tap floor map to set start point"
                        }
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Sampling & Save Control Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = locationLabel,
                        onValueChange = { locationLabel = it },
                        label = { Text("Point Label (e.g. Pillar A12 / Hallway)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (pinnedX == null || pinnedY == null) {
                                    Toast.makeText(context, "Tap the map to select a point first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isScanning = true
                                scanMessage = "Scanning..."
                                triggerWifiScan(context,
                                    onResults = { results ->
                                        scanResults = results.sortedByDescending { it.rssi }
                                        isScanning = false
                                        scanMessage = "Scan completed: ${results.size} APs found."
                                    },
                                    onError = { err ->
                                        isScanning = false
                                        scanMessage = err
                                    }
                                )
                            },
                            enabled = pinnedX != null && pinnedY != null && !isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.WifiTethering, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan APs")
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                if (pinnedX == null || pinnedY == null) {
                                    Toast.makeText(context, "Tap map to set pin", Toast.LENGTH_SHORT).show()
                                } else if (scanResults.isEmpty()) {
                                    Toast.makeText(context, "Scan Wi-Fi signals first", Toast.LENGTH_SHORT).show()
                                } else {
                                    val realX = String.format(Locale.US, "%.1fm", pinnedX!! * buildingWidthMeters)
                                    val realY = String.format(Locale.US, "%.1fm", pinnedY!! * buildingLengthMeters)
                                    val label = locationLabel.ifBlank { "Point ($realX, $realY)" }
                                    val id = dbHelper.saveSurvey(
                                        label = label,
                                        measurements = scanResults,
                                        coordX = pinnedX,
                                        coordY = pinnedY,
                                        floorPlanName = if (selectedImageUri != null) "Custom Image (${buildingWidthMeters}mx${buildingLengthMeters}m)" else "Default Grid (${buildingWidthMeters}mx${buildingLengthMeters}m)"
                                    )
                                    if (id != -1L) {
                                        onRecordSaved()
                                        scanResults = emptyList()
                                        scanMessage = ""
                                        Toast.makeText(context, "Pinned fingerprint saved!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = pinnedX != null && pinnedY != null && scanResults.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.PinDrop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Pin")
                        }
                    }

                    if (scanMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scanMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WifiResultRow(result: WifiMeasurement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.ssid.ifBlank { "[Hidden Network]" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = result.bssid,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            RssiBadge(rssi = result.rssi)
        }
    }
}

@Composable
fun RssiBadge(rssi: Int) {
    val (color, label) = when {
        rssi >= -50 -> Color(0xFF4CAF50) to "Excellent"
        rssi >= -70 -> Color(0xFF8BC34A) to "Good"
        rssi >= -85 -> Color(0xFFFF9800) to "Fair"
        else -> Color(0xFFF44336) to "Weak"
    }
    
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(90.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            Text(
                text = "$rssi dBm",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HistoryTab(
    savedRecords: List<SurveyRecord>,
    onDeleteRecord: (SurveyRecord) -> Unit
) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(formatDatasetToJson(savedRecords))
                    }
                }
                Toast.makeText(context, "Dataset exported successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Fingerprints (${savedRecords.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Button(
                onClick = { exportLauncher.launch("wifi_fingerprints_${System.currentTimeMillis()}.json") },
                enabled = savedRecords.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export JSON", fontSize = 12.sp)
            }
        }

        if (savedRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved surveys yet.\nGo to the Survey or Floor Map tab to record location fingerprints.",
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedRecords) { record ->
                    HistoryRecordCard(record, onDeleteRecord)
                }
            }
        }
    }
}

@Composable
fun HistoryRecordCard(record: SurveyRecord, onDeleteRecord: (SurveyRecord) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedTime = sdf.format(Date(record.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (record.coordX != null && record.coordY != null) {
                        Text(
                            text = "Coordinates: X=${String.format(Locale.US, "%.3f", record.coordX)}, Y=${String.format(Locale.US, "%.3f", record.coordY)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${record.measurements.size} APs",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                    
                    IconButton(onClick = { onDeleteRecord(record) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Record",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SSID / BSSID", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                        Text("RSSI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                    }
                    
                    record.measurements.forEach { measure ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(measure.ssid.ifBlank { "[Hidden Network]" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(measure.bssid, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(
                                text = "${measure.rssi} dBm",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = getRssiColor(measure.rssi),
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(80.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun getRssiColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> Color(0xFF4CAF50)
        rssi >= -70 -> Color(0xFF8BC34A)
        rssi >= -85 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

fun formatDatasetToJson(records: List<SurveyRecord>): String {
    val sb = java.lang.StringBuilder()
    sb.append("[\n")
    records.forEachIndexed { rIndex, record ->
        sb.append("  {\n")
        sb.append("    \"id\": ${record.id},\n")
        sb.append("    \"label\": \"${record.label.replace("\"", "\\\"")}\",\n")
        sb.append("    \"timestamp\": ${record.timestamp},\n")
        sb.append("    \"coord_x\": ${record.coordX ?: "null"},\n")
        sb.append("    \"coord_y\": ${record.coordY ?: "null"},\n")
        sb.append("    \"floor_plan\": \"${(record.floorPlanName ?: "").replace("\"", "\\\"")}\",\n")
        sb.append("    \"measurements\": [\n")
        record.measurements.forEachIndexed { mIndex, m ->
            sb.append("      {\n")
            sb.append("        \"ssid\": \"${m.ssid.replace("\"", "\\\"")}\",\n")
            sb.append("        \"bssid\": \"${m.bssid}\",\n")
            sb.append("        \"rssi\": ${m.rssi}\n")
            sb.append("      }${if (mIndex < record.measurements.size - 1) "," else ""}\n")
        }
        sb.append("    ]\n")
        sb.append("  }${if (rIndex < records.size - 1) "," else ""}\n")
    }
    sb.append("]\n")
    return sb.toString()
}

fun triggerWifiScan(
    context: Context,
    onResults: (List<WifiMeasurement>) -> Unit,
    onError: (String) -> Unit
) {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    if (!wifiManager.isWifiEnabled) {
        onError("Wi-Fi is disabled. Please enable it in settings.")
        return
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val results = try {
                wifiManager.scanResults
            } catch (e: SecurityException) {
                onError("Location permission denied. Cannot retrieve Wi-Fi scans.")
                emptyList()
            }
            
            val measurements = results.map {
                WifiMeasurement(
                    bssid = it.BSSID ?: "Unknown",
                    rssi = it.level,
                    ssid = it.SSID ?: "Unknown"
                )
            }
            onResults(measurements)
            
            try {
                c.unregisterReceiver(this)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
        }
    }
    
    val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
    context.registerReceiver(receiver, filter)
    
    val success = wifiManager.startScan()
    if (!success) {
        // scanFailure: trigger immediate fetch from cache since startScan is throttled
        val results = try {
            wifiManager.scanResults
        } catch (e: SecurityException) {
            onError("Location permission denied.")
            emptyList()
        }
        val measurements = results.map {
            WifiMeasurement(
                bssid = it.BSSID ?: "Unknown",
                rssi = it.level,
                ssid = it.SSID ?: "Unknown"
            )
        }
        onResults(measurements)
        
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
