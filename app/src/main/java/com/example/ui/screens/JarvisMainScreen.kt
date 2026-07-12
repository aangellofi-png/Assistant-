package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.models.CommandLog
import com.example.data.models.Note
import com.example.data.models.Reminder
import com.example.ui.MainViewModel
import com.example.ui.components.JarvisCore
import com.example.ui.theme.*
import com.example.utils.JarvisVoiceManager
import com.example.utils.PhoneAutomationHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisMainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) }
    
    val selectedThemeColor by viewModel.selectedThemeColor.collectAsStateWithLifecycle()
    val accentColor = when (selectedThemeColor) {
        "Blue" -> JarvisBlue
        "Magenta" -> JarvisMagenta
        "Amber" -> JarvisAmber
        else -> JarvisCyan
    }
    
    // Runtime Permissions setup
    val permissionsToRequest = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS
    )

    var hasAllPermissions by remember {
        mutableStateOf(
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAllPermissions = results.values.all { it }
        if (hasAllPermissions) {
            Toast.makeText(context, "All Jarvis components initialized, Sir.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Some system pathways are restricted.", Toast.LENGTH_SHORT).show()
        }
    }

    // Trigger permissions on launch
    LaunchedEffect(Unit) {
        if (!hasAllPermissions) {
            launcher.launch(permissionsToRequest)
        }
    }

    // Telemetry digital clock state
    var telemetryTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            telemetryTime = SimpleDateFormat("HH:mm:ss : dd.MM.yyyy", Locale.US).format(Date())
            delay(1000)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDarkBg)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(JarvisDarkBg)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP HUD TELEMETRY HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "JARVIS CORE v3.5",
                        color = accentColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = telemetryTime,
                        color = JarvisTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Systems Checklist Box
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()
                    val isCharging by viewModel.isCharging.collectAsStateWithLifecycle()
                    val warningThreshold by viewModel.lowBatteryWarningThreshold.collectAsStateWithLifecycle()
                    val batteryColor = if (isCharging) Color.Green else if (batteryLevel <= warningThreshold) Color.Red else accentColor
                    val batteryLabel = if (isCharging) "⚡ CORE: $batteryLevel%" else "BAT: $batteryLevel%"

                    DiagnosticTag(label = "AI CORE", active = true, activeColor = accentColor)
                    DiagnosticTag(label = "TTS", active = viewModel.isTtsEnabled.collectAsStateWithLifecycle().value, activeColor = JarvisBlue)
                    DiagnosticTag(label = batteryLabel, active = true, activeColor = batteryColor)
                }
            }

            Divider(color = accentColor.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))

            // --- CYBER-TABS SELECTOR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisSurfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val tabs = listOf("TERMINAL HUD", "VAULTS", "SIMULATOR", "SETTINGS")
                tabs.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeTab == index) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                width = if (activeTab == index) 1.dp else 0.dp,
                                color = if (activeTab == index) accentColor.copy(alpha = 0.4f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { activeTab = index }
                            .testTag("tab_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (activeTab == index) accentColor else JarvisTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- CURRENT TAB PANEL ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (activeTab) {
                    0 -> TerminalHUDTab(viewModel, context)
                    1 -> VaultsTab(viewModel)
                    2 -> SimulatorTab(viewModel)
                    3 -> SettingsTab(viewModel)
                }
            }

            // --- EMERGENCY FLOATING INCOMING CALL / MESSAGE NOTIFICATION ---
            val activeCaller by viewModel.simulatedCaller.collectAsStateWithLifecycle()
            val activeMessage by viewModel.simulatedMessage.collectAsStateWithLifecycle()
            val draftedReply by viewModel.aiDraftedReply.collectAsStateWithLifecycle()

            if (activeCaller != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.endSimulatedCall(wasAccepted = false) },
                    containerColor = JarvisSurface,
                    modifier = Modifier.border(1.dp, JarvisMagenta, RoundedCornerShape(12.dp)),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, tint = JarvisMagenta, modifier = Modifier.size(24.dp))
                            Text("INCOMING HOLOGRAPHIC CALL", color = JarvisMagenta, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(activeCaller ?: "Unknown Contact", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Audio transmission ready... Say 'Accept' or tap", color = JarvisTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.endSimulatedCall(wasAccepted = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.testTag("accept_call")
                        ) {
                            Text("ACCEPT", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { viewModel.endSimulatedCall(wasAccepted = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisMagenta),
                            modifier = Modifier.testTag("reject_call")
                        ) {
                            Text("REJECT", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                )
            }

            if (activeMessage != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelSimulatedMessage() },
                    containerColor = JarvisSurface,
                    modifier = Modifier.border(1.dp, accentColor, RoundedCornerShape(12.dp)),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Message, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                            Text("NEW ${activeMessage?.type?.uppercase()} INCOMING", color = accentColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "From: ${activeMessage?.sender}",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"${activeMessage?.text}\"",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = accentColor.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text("AUTO AI REPLY DRAFT:", color = JarvisAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            if (draftedReply == null) {
                                LinearProgressIndicator(
                                    color = accentColor,
                                    trackColor = JarvisSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().height(2.dp)
                                )
                            } else {
                                Text(
                                    text = draftedReply ?: "",
                                    color = JarvisWhite,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(JarvisSurfaceVariant.copy(alpha = 0.4f))
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.sendAIParsedReply() },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            enabled = draftedReply != null,
                            modifier = Modifier.testTag("send_ai_reply")
                        ) {
                            Text("TRANSMIT REPLY", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.cancelSimulatedMessage() },
                            modifier = Modifier.testTag("cancel_message")
                        ) {
                            Text("DISMISS", color = JarvisTextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                )
            }
        }
    }
}

// --- TAB 1: TERMINAL HUD PANEL (RESPONSIVE M3 DASHBOARD) ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalHUDTab(viewModel: MainViewModel, context: Context) {
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val soundLevel by viewModel.soundLevel.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val currentPrompt by viewModel.currentPrompt.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsStateWithLifecycle()
    val selectedThemeColor by viewModel.selectedThemeColor.collectAsStateWithLifecycle()
    val voiceSensitivity by viewModel.voiceSensitivity.collectAsStateWithLifecycle()
    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.language.collectAsStateWithLifecycle()

    val accentColor = when (selectedThemeColor) {
        "Blue" -> JarvisBlue
        "Magenta" -> JarvisMagenta
        "Amber" -> JarvisAmber
        else -> JarvisCyan
    }

    var manualInputText by remember { mutableStateOf("") }
    var isMusicPlaying by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth > 600.dp
        
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Column 1: Central Core, Visualizers, and Console input
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Breathing Core HUD
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        JarvisCore(state = speechState, soundLevel = soundLevel)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            StatusLogDisplay(label = "INPUT PATHWAY", value = currentPrompt.ifEmpty { "[Listening for Wake-word 'Hey Jarvis' or Tap]" })
                        }
                    }
                    
                    // State visualizer card
                    JarvisStateVisualizer(
                        state = speechState,
                        soundLevel = soundLevel,
                        isProcessing = isProcessing,
                        voiceSensitivity = voiceSensitivity,
                        batteryLevel = batteryLevel,
                        language = selectedLanguage,
                        accentColor = accentColor
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Lower Input Bar
                    InputConsoleBar(
                        manualInputText = manualInputText,
                        onManualInputChange = { manualInputText = it },
                        isTtsEnabled = isTtsEnabled,
                        onToggleTts = { viewModel.isTtsEnabled.value = !isTtsEnabled },
                        speechState = speechState,
                        onMicClick = {
                            if (speechState == JarvisVoiceManager.SpeechState.LISTENING) {
                                viewModel.stopVoiceListening()
                            } else {
                                viewModel.triggerVoiceListening()
                            }
                        },
                        onSendCommand = { cmd ->
                            viewModel.processCommand(cmd, context, isVoice = false)
                        },
                        accentColor = accentColor,
                        context = context
                    )
                }

                // Column 2: Transcript Feed terminal and Quick Controls
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Output terminal
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(JarvisSurface)
                            .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.15f)), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TerminalHeader(isProcessing = isProcessing, speechState = speechState, accentColor = accentColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                item {
                                    Text(
                                        text = currentResponse,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.testTag("jarvis_output_text")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // System accelerators
                    SystemAcceleratorsGrid(
                        viewModel = viewModel,
                        isMusicPlaying = isMusicPlaying,
                        onMusicPlayToggle = { isMusicPlaying = it },
                        accentColor = accentColor,
                        context = context
                    )
                }
            }
        } else {
            // Mobile optimized Portrait Layout
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Central Core HUD
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    JarvisCore(state = speechState, soundLevel = soundLevel)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        StatusLogDisplay(label = "INPUT PATHWAY", value = currentPrompt.ifEmpty { "[Listening for Wake-word 'Hey Jarvis' or Tap]" })
                    }
                }
                
                // State visualizer card
                JarvisStateVisualizer(
                    state = speechState,
                    soundLevel = soundLevel,
                    isProcessing = isProcessing,
                    voiceSensitivity = voiceSensitivity,
                    batteryLevel = batteryLevel,
                    language = selectedLanguage,
                    accentColor = accentColor
                )

                // Output Terminal
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisSurface)
                        .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.15f)), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TerminalHeader(isProcessing = isProcessing, speechState = speechState, accentColor = accentColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            item {
                                Text(
                                    text = currentResponse,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.testTag("jarvis_output_text")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // System accelerators
                SystemAcceleratorsGrid(
                    viewModel = viewModel,
                    isMusicPlaying = isMusicPlaying,
                    onMusicPlayToggle = { isMusicPlaying = it },
                    accentColor = accentColor,
                    context = context
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Input console bar
                InputConsoleBar(
                    manualInputText = manualInputText,
                    onManualInputChange = { manualInputText = it },
                    isTtsEnabled = isTtsEnabled,
                    onToggleTts = { viewModel.isTtsEnabled.value = !isTtsEnabled },
                    speechState = speechState,
                    onMicClick = {
                        if (speechState == JarvisVoiceManager.SpeechState.LISTENING) {
                            viewModel.stopVoiceListening()
                        } else {
                            viewModel.triggerVoiceListening()
                        }
                    },
                    onSendCommand = { cmd ->
                        viewModel.processCommand(cmd, context, isVoice = false)
                    },
                    accentColor = accentColor,
                    context = context
                )
            }
        }
    }
}

@Composable
fun JarvisStateVisualizer(
    state: JarvisVoiceManager.SpeechState,
    soundLevel: Float,
    isProcessing: Boolean,
    voiceSensitivity: Float,
    batteryLevel: Int,
    language: String,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .testTag("state_visualizer_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when (state) {
                            JarvisVoiceManager.SpeechState.LISTENING -> Icons.Filled.Hearing
                            JarvisVoiceManager.SpeechState.PROCESSING -> Icons.Filled.Memory
                            JarvisVoiceManager.SpeechState.SPEAKING -> Icons.Filled.GraphicEq
                            JarvisVoiceManager.SpeechState.ERROR -> Icons.Filled.Warning
                            else -> Icons.Filled.Settings
                        },
                        contentDescription = "Active System State",
                        tint = when (state) {
                            JarvisVoiceManager.SpeechState.LISTENING -> JarvisMagenta
                            JarvisVoiceManager.SpeechState.PROCESSING -> JarvisCyan
                            JarvisVoiceManager.SpeechState.SPEAKING -> JarvisBlue
                            JarvisVoiceManager.SpeechState.ERROR -> Color.Red
                            else -> accentColor
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Text(
                        text = "COGNITIVE PERCEPTION MATRIX",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                val stateText = when (state) {
                    JarvisVoiceManager.SpeechState.LISTENING -> "PERCEIVING SIGNALS"
                    JarvisVoiceManager.SpeechState.PROCESSING -> "PROCESSING COGNITION"
                    JarvisVoiceManager.SpeechState.SPEAKING -> "TRANSMITTING VOCALS"
                    JarvisVoiceManager.SpeechState.ERROR -> "SYSTEM PATHWAY ERROR"
                    else -> "AMBIENT STANDBY"
                }
                
                val stateColor = when (state) {
                    JarvisVoiceManager.SpeechState.LISTENING -> JarvisMagenta
                    JarvisVoiceManager.SpeechState.PROCESSING -> JarvisCyan
                    JarvisVoiceManager.SpeechState.SPEAKING -> JarvisBlue
                    JarvisVoiceManager.SpeechState.ERROR -> Color.Red
                    else -> JarvisTextMuted
                }

                Text(
                    text = "[ $stateText ]",
                    color = stateColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(JarvisSurfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    when (state) {
                        JarvisVoiceManager.SpeechState.LISTENING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VoiceEqualizer(
                                    isListening = true,
                                    isSpeaking = false,
                                    soundLevel = soundLevel,
                                    color = JarvisMagenta
                                )
                                Column {
                                    Text("SIGNAL INGESTION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    val dbLevel = -(75 - (soundLevel * 40).toInt())
                                    Text("Input Amplitude: ${dbLevel} dB", color = JarvisTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                        JarvisVoiceManager.SpeechState.PROCESSING -> {
                            ProcessingMatrixIndicator(color = JarvisCyan)
                        }
                        JarvisVoiceManager.SpeechState.SPEAKING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VoiceEqualizer(
                                    isListening = false,
                                    isSpeaking = true,
                                    soundLevel = soundLevel,
                                    color = JarvisBlue
                                )
                                Column {
                                    Text("VULCAN UPLINK BROADCAST", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Synthesizing audio frequencies...", color = JarvisTextMuted, fontSize = 9.sp)
                                }
                            }
                        }
                        JarvisVoiceManager.SpeechState.ERROR -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "error_strobe")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(500, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "error_alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Red.copy(alpha = alpha))
                                )
                                Column {
                                    Text("COGNITIVE INTERRUPT", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Uplink failed or voice unrecognized.", color = JarvisTextMuted, fontSize = 9.sp)
                                }
                            }
                        }
                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(accentColor.copy(alpha = 0.4f))
                                )
                                Column {
                                    Text("COGNITIVE STANDBY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Neural pathways optimized & ready", color = JarvisTextMuted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(accentColor.copy(alpha = 0.15f)))
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TEMP", color = accentColor, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        val tempVal = when (state) {
                            JarvisVoiceManager.SpeechState.PROCESSING -> "38.4°C"
                            JarvisVoiceManager.SpeechState.SPEAKING -> "36.8°C"
                            else -> "34.1°C"
                        }
                        Text(tempVal, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("UPLINK", color = accentColor, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(if (isProcessing) "SYNC" else "SECURE", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIC SENSITIVITY: ${(voiceSensitivity * 100).toInt()}%",
                    color = JarvisTextMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Text(
                    text = "DIALECT: ${language.uppercase()}",
                    color = JarvisTextMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "CORE STABILITY: NOMINAL",
                    color = JarvisTextMuted,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun VoiceEqualizer(
    isListening: Boolean,
    isSpeaking: Boolean,
    soundLevel: Float,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    
    val barCount = 8
    val animScales = List(barCount) { index ->
        if (isListening) {
            val baseDelay = index * 100
            val animatedScale by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 300 + index * 50, delayMillis = baseDelay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            animatedScale * (0.4f + soundLevel * 0.6f)
        } else if (isSpeaking) {
            val animatedScale by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400 + (index % 3) * 100, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_speaking_$index"
            )
            animatedScale
        } else {
            0.15f
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(32.dp)
    ) {
        animScales.forEach { scale ->
            val height = 32.dp * scale
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun ProcessingMatrixIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing_matrix")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "matrix_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            color = color,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "ACCESSING COGNITIVE SYNAPSES...",
            color = color.copy(alpha = alphaPulse),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TerminalHeader(
    isProcessing: Boolean,
    speechState: JarvisVoiceManager.SpeechState,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "JARVIS OUTPUT TERMINAL",
            color = accentColor.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        if (isProcessing) {
            CircularProgressIndicator(
                color = accentColor,
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (speechState == JarvisVoiceManager.SpeechState.SPEAKING) JarvisBlue else JarvisAmber)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemAcceleratorsGrid(
    viewModel: MainViewModel,
    isMusicPlaying: Boolean,
    onMusicPlayToggle: (Boolean) -> Unit,
    accentColor: Color,
    context: Context
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4
    ) {
        var flashlightState by remember { mutableStateOf(false) }
        
        // Quick flash action
        ActionButtonNode(
            label = if (flashlightState) "Flashlight ON" else "Flashlight",
            icon = if (flashlightState) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
            tint = if (flashlightState) JarvisCyan else JarvisTextMuted,
            onClick = {
                flashlightState = !flashlightState
                viewModel.processCommand(if (flashlightState) "turn on flashlight" else "turn off flashlight", context, isVoice = false)
            }
        )

        // Play simulated synth music action
        ActionButtonNode(
            label = if (isMusicPlaying) "Synthwave: Play" else "Play Music",
            icon = if (isMusicPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            tint = if (isMusicPlaying) JarvisBlue else JarvisTextMuted,
            onClick = {
                val newVal = !isMusicPlaying
                onMusicPlayToggle(newVal)
                if (newVal) {
                    viewModel.processCommand("play some futuristic synthwave music", context, isVoice = false)
                } else {
                    viewModel.stopSpeaking()
                }
            }
        )

        // Open YouTube shortcut
        ActionButtonNode(
            label = "YouTube",
            icon = Icons.Filled.PlayArrow,
            tint = JarvisTextMuted,
            onClick = {
                viewModel.processCommand("open YouTube", context, isVoice = false)
            }
        )

        // Check Mausam shortcut
        ActionButtonNode(
            label = "Mausam (Weather)",
            icon = Icons.Filled.Cloud,
            tint = JarvisCyan,
            onClick = {
                viewModel.processCommand("aaj ka weather batao", context, isVoice = false)
            }
        )

        // Battery diagnostics action button
        val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()
        val isCharging by viewModel.isCharging.collectAsStateWithLifecycle()
        val warningThreshold by viewModel.lowBatteryWarningThreshold.collectAsStateWithLifecycle()
        val batteryStatusColor = if (isCharging) Color.Green else if (batteryLevel <= warningThreshold) Color.Red else accentColor
        ActionButtonNode(
            label = if (isCharging) "Power: $batteryLevel% (⚡)" else "Power: $batteryLevel%",
            icon = Icons.Filled.Power,
            tint = batteryStatusColor,
            onClick = {
                viewModel.checkBatteryStatus(context, forceSpeak = true)
            }
        )
    }
}

@Composable
fun InputConsoleBar(
    manualInputText: String,
    onManualInputChange: (String) -> Unit,
    isTtsEnabled: Boolean,
    onToggleTts: () -> Unit,
    speechState: JarvisVoiceManager.SpeechState,
    onMicClick: () -> Unit,
    onSendCommand: (String) -> Unit,
    accentColor: Color,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = manualInputText,
            onValueChange = onManualInputChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("manual_input_field"),
            placeholder = { Text("Command Jarvis...", color = JarvisTextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = accentColor.copy(alpha = 0.3f),
                focusedContainerColor = JarvisSurface,
                unfocusedContainerColor = JarvisSurface
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            trailingIcon = {
                if (manualInputText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onSendCommand(manualInputText)
                            onManualInputChange("")
                        },
                        modifier = Modifier.testTag("send_manual_input")
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Transmit", tint = accentColor)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (manualInputText.isNotEmpty()) {
                    onSendCommand(manualInputText)
                    onManualInputChange("")
                }
            })
        )

        IconButton(
            onClick = onToggleTts,
            modifier = Modifier
                .size(44.dp)
                .background(JarvisSurface, RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)), RoundedCornerShape(22.dp))
                .testTag("toggle_tts_button")
        ) {
            Icon(
                imageVector = if (isTtsEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeMute,
                contentDescription = "Toggle TTS Feedback",
                tint = if (isTtsEnabled) accentColor else JarvisTextMuted
            )
        }

        val isListening = speechState == JarvisVoiceManager.SpeechState.LISTENING
        IconButton(
            onClick = onMicClick,
            modifier = Modifier
                .size(52.dp)
                .background(
                    color = if (isListening) JarvisMagenta else accentColor,
                    shape = RoundedCornerShape(26.dp)
                )
                .testTag("mic_button")
        ) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Pause else Icons.Filled.Mic,
                contentDescription = "Trigger Speech Recognize",
                tint = if (isListening) Color.White else Color.Black,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// --- TAB 2: VAULTS PANEL (NOTES & REMINDERS) ---
@Composable
fun VaultsTab(viewModel: MainViewModel) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var activeVaultSection by remember { mutableStateOf(0) } // 0 -> Notes, 1 -> Reminders

    var titleInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Vault Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { activeVaultSection = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeVaultSection == 0) JarvisCyan else JarvisSurface
                ),
                border = BorderStroke(1.dp, if (activeVaultSection == 0) JarvisCyan else JarvisCyan.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f).testTag("vault_tab_notes")
            ) {
                Text("MEMO VAULT", color = if (activeVaultSection == 0) Color.Black else JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { activeVaultSection = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeVaultSection == 1) JarvisCyan else JarvisSurface
                ),
                border = BorderStroke(1.dp, if (activeVaultSection == 1) JarvisCyan else JarvisCyan.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f).testTag("vault_tab_reminders")
            ) {
                Text("REMINDER MATRIX", color = if (activeVaultSection == 1) Color.Black else JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        if (activeVaultSection == 0) {
            // Notes Panel
            Column(modifier = Modifier.weight(1f)) {
                // Add Quick Note Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("DEPOSIT NEW MEMO", color = JarvisCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            placeholder = { Text("Memo Title", color = JarvisTextMuted, fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("note_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = contentInput,
                            onValueChange = { contentInput = it },
                            placeholder = { Text("Write something...", color = JarvisTextMuted, fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth().height(80.dp).testTag("note_content_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (contentInput.trim().isNotEmpty()) {
                                    viewModel.addNote(
                                        title = titleInput.ifEmpty { "Jarvis System Memo" },
                                        content = contentInput
                                    )
                                    titleInput = ""
                                    contentInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            modifier = Modifier.align(Alignment.End).height(36.dp).testTag("add_note_button")
                        ) {
                            Text("SAVE MEMO", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Saved notes list
                if (notes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No records found in central vault, Sir.", color = JarvisTextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(notes, key = { it.id }) { note ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceVariant.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title, color = JarvisCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(note.content, color = Color.White, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val dateStr = SimpleDateFormat("HH:mm - dd MMM", Locale.getDefault()).format(Date(note.timestamp))
                                        Text(dateStr, color = JarvisTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    
                                    IconButton(
                                        onClick = { viewModel.deleteNote(note.id) },
                                        modifier = Modifier.testTag("delete_note_${note.id}")
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Purge", tint = JarvisMagenta.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Reminders Panel
            Column(modifier = Modifier.weight(1f)) {
                // Add Quick Reminder Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("SCHEDULE NEW TASK DIRECTIVE", color = JarvisCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = titleInput,
                                onValueChange = { titleInput = it },
                                placeholder = { Text("Task label", color = JarvisTextMuted, fontSize = 12.sp) },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                modifier = Modifier.weight(1.3f).height(48.dp).testTag("reminder_title_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisCyan,
                                    unfocusedBorderColor = JarvisSurfaceVariant
                                )
                            )

                            OutlinedTextField(
                                value = contentInput,
                                onValueChange = { contentInput = it },
                                placeholder = { Text("e.g. 17:00", color = JarvisTextMuted, fontSize = 12.sp) },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                modifier = Modifier.weight(0.7f).height(48.dp).testTag("reminder_time_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisCyan,
                                    unfocusedBorderColor = JarvisSurfaceVariant
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (titleInput.trim().isNotEmpty()) {
                                    viewModel.addReminder(
                                        title = titleInput,
                                        dateTime = contentInput.ifEmpty { "Today, 18:00" }
                                    )
                                    titleInput = ""
                                    contentInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            modifier = Modifier.align(Alignment.End).height(36.dp).testTag("add_reminder_button")
                        ) {
                            Text("SCHEDULE", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Active reminders list
                if (reminders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active scheduled reminders in queue, Sir.", color = JarvisTextMuted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(reminders, key = { it.id }) { r ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (r.isCompleted) JarvisSurfaceVariant.copy(alpha = 0.2f) else JarvisSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, if (r.isCompleted) JarvisTextMuted.copy(alpha = 0.1f) else JarvisCyan.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = r.isCompleted,
                                            onCheckedChange = { isChecked -> viewModel.toggleReminder(r.id, isChecked) },
                                            colors = CheckboxDefaults.colors(checkedColor = JarvisCyan, uncheckedColor = JarvisTextMuted),
                                            modifier = Modifier.testTag("toggle_reminder_${r.id}")
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = r.title,
                                                color = if (r.isCompleted) JarvisTextMuted else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                style = if (r.isCompleted) LocalTextStyle.current.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else LocalTextStyle.current
                                            )
                                            Text(
                                                text = "Trigger at: ${r.dateTime}",
                                                color = JarvisCyan.copy(alpha = 0.7f),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteReminder(r.id) },
                                        modifier = Modifier.testTag("delete_reminder_${r.id}")
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Purge", tint = JarvisMagenta.copy(alpha = 0.6f))
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

// --- TAB 3: SIMULATOR CONTROL DECK ---
@Composable
fun SimulatorTab(viewModel: MainViewModel) {
    var senderName by remember { mutableStateOf("Pepper Potts") }
    var mockMessage by remember { mutableStateOf("Sir, please report to the lab. We have a calibration anomaly with Mark 85.") }

    var callerName by remember { mutableStateOf("Happy Hogan") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisMagenta.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, tint = JarvisMagenta, modifier = Modifier.size(20.dp))
                        Text("CALL RECEIVE & REJECT SIMULATOR", color = JarvisMagenta, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = callerName,
                        onValueChange = { callerName = it },
                        label = { Text("Simulated Caller Label", color = JarvisTextMuted, fontSize = 12.sp) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier.fillMaxWidth().testTag("sim_caller_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisMagenta,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.simulateIncomingCall(callerName) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisMagenta),
                        modifier = Modifier.fillMaxWidth().testTag("simulate_call_button")
                    ) {
                        Text("INJECT SIMULATED CALL SIGNAL", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Message, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                        Text("MESSAGING AI REPLY SIMULATOR", color = JarvisCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = senderName,
                        onValueChange = { senderName = it },
                        label = { Text("Simulated Sender", color = JarvisTextMuted, fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier.fillMaxWidth().testTag("sim_sender_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = mockMessage,
                        onValueChange = { mockMessage = it },
                        label = { Text("Message Body", color = JarvisTextMuted, fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier.fillMaxWidth().height(80.dp).testTag("sim_message_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.simulateIncomingMessage(senderName, mockMessage, "WhatsApp") },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            modifier = Modifier.weight(1.0f).testTag("simulate_whatsapp_button")
                        ) {
                            Text("WHATSAPP MSG", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }

                        Button(
                            onClick = { viewModel.simulateIncomingMessage(senderName, mockMessage, "SMS") },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue),
                            modifier = Modifier.weight(1.0f).testTag("simulate_sms_button")
                        ) {
                            Text("SMS SIGNAL", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- CORE MINIATURE SUBCOMPONENTS ---

@Composable
fun DiagnosticTag(label: String, active: Boolean, activeColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) activeColor.copy(alpha = 0.12f) else Color.Red.copy(alpha = 0.12f))
            .border(1.dp, if (active) activeColor.copy(alpha = 0.4f) else Color.Red.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) activeColor else Color.Red)
            )
            Text(
                text = label,
                color = if (active) activeColor else Color.Red,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun StatusLogDisplay(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(JarvisSurface.copy(alpha = 0.8f))
            .border(1.dp, JarvisCyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(max = 240.dp)
    ) {
        Text(
            text = label,
            color = JarvisCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ActionButtonNode(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisSurface)
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.3f)), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(label, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// --- TAB 4: SYSTEM SETTINGS CONTROL CENTER ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val wakeWord by viewModel.wakeWord.collectAsStateWithLifecycle()
    val voiceSensitivity by viewModel.voiceSensitivity.collectAsStateWithLifecycle()
    val ttsPitch by viewModel.ttsPitch.collectAsStateWithLifecycle()
    val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsStateWithLifecycle()
    val selectedThemeColor by viewModel.selectedThemeColor.collectAsStateWithLifecycle()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsStateWithLifecycle()
    val soundLevel by viewModel.soundLevel.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val isWakeWordDetectionEnabled by viewModel.isWakeWordDetectionEnabled.collectAsStateWithLifecycle()
    val isAwake by viewModel.isAwake.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.language.collectAsStateWithLifecycle()

    val accentColor = when (selectedThemeColor) {
        "Blue" -> JarvisBlue
        "Magenta" -> JarvisMagenta
        "Amber" -> JarvisAmber
        else -> JarvisCyan
    }

    var customWakeWordInput by remember(wakeWord) { mutableStateOf(wakeWord) }
    val isVoiceActive = speechState == JarvisVoiceManager.SpeechState.LISTENING

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. WAKE WORD CUSTOMIZATION ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Text("VOICE WAKE DIRECTIVE", color = accentColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customWakeWordInput,
                        onValueChange = { 
                            customWakeWordInput = it
                            viewModel.setWakeWord(it, context)
                        },
                        label = { Text("Customize Wake Word", color = JarvisTextMuted, fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().testTag("setting_wakeword_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("QUICK DIRECTIVE PRESETS", color = JarvisTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf("Hey Jarvis", "Friday", "Hey Stark", "Computer")
                        presets.forEach { preset ->
                            val isSelected = wakeWord.equals(preset, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else JarvisSurfaceVariant)
                                    .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        customWakeWordInput = preset
                                        viewModel.setWakeWord(preset, context)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("preset_$preset")
                            ) {
                                Text(
                                    text = preset,
                                    color = if (isSelected) accentColor else Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- HANDS-FREE AUTOMATION & LOCK SCREEN ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, (if (isAwake) Color.Green else accentColor).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isWakeWordDetectionEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = null,
                                tint = if (isAwake) Color.Green else accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("HANDS-FREE CORE", color = if (isAwake) Color.Green else accentColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        
                        // State Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background((if (isAwake) Color.Green else if (isWakeWordDetectionEnabled) Color.Yellow else Color.Red).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isAwake) "ACTIVE" else if (isWakeWordDetectionEnabled) "STANDBY" else "DISABLED",
                                color = if (isAwake) Color.Green else if (isWakeWordDetectionEnabled) Color.Yellow else Color.Red,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Allows Jarvis to continuously monitor the wake-word without touching the device. Works on lock screens and in standby mode.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Always-on Wake Detection", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("continuous lock-screen listening", color = JarvisTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Switch(
                            checked = isWakeWordDetectionEnabled,
                            onCheckedChange = { viewModel.setWakeWordDetectionEnabled(it, context) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = if (isAwake) Color.Green else accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = JarvisSurfaceVariant
                            ),
                            modifier = Modifier.testTag("handsfree_switch")
                        )
                    }

                    if (isWakeWordDetectionEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = JarvisSurfaceVariant, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Conversational Status / Timer display
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Conversational Sleep Timeout", color = Color.White, fontSize = 12.sp)
                            Text("60 Seconds", color = if (isAwake) Color.Green else JarvisTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isAwake) {
                                "⚡ Active conversation state enabled! You don't need to repeat the wake word. Jarvis will automatically return to sleep after 1 minute of inactivity."
                            } else {
                                "💤 Currently in standby sleep mode. Speak \"$wakeWord\" hands-free to wake Jarvis up and start commanding."
                            },
                            color = if (isAwake) Color.Green.copy(alpha = 0.85f) else JarvisTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // --- 2. SENSITIVITY ADJUSTMENTS ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Text("MIC CAPTURE & SENSITIVITY", color = accentColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Trigger Threshold", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = String.format(Locale.US, "%.1f dB", voiceSensitivity * 100),
                            color = accentColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = voiceSensitivity,
                        onValueChange = { viewModel.setVoiceSensitivity(it, context) },
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = JarvisSurfaceVariant
                        ),
                        modifier = Modifier.testTag("sensitivity_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic Sound Activation Meter
                    Text("LIVE AUDIO INPUT LEVEL", color = JarvisTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Audio level gauge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(JarvisSurfaceVariant)
                    ) {
                        // Sound level fill
                        val levelWidthFactor = if (isVoiceActive) soundLevel else 0.1f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(levelWidthFactor)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(accentColor, if (levelWidthFactor > voiceSensitivity) Color.Green else accentColor)
                                    )
                                )
                        )

                        // Marker showing the sensitivity setting threshold
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(voiceSensitivity)
                        ) {
                            // Line indicating threshold
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .background(Color.Red)
                                    .align(Alignment.CenterEnd)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quiet", color = JarvisTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("Threshold Marker", color = Color.Red.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("Loud", color = JarvisTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // --- 3. SPEECH FEEDBACK ENGINE ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                            Text("TTS AUDIO ENGINE", color = accentColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = isTtsEnabled,
                            onCheckedChange = { viewModel.toggleTtsEnabled(context) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accentColor,
                                checkedTrackColor = accentColor.copy(alpha = 0.3f),
                                uncheckedThumbColor = JarvisTextMuted,
                                uncheckedTrackColor = JarvisSurfaceVariant
                            ),
                            modifier = Modifier.testTag("tts_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Pitch Slider
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Voice Pitch", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = String.format(Locale.US, "%.2f x", ttsPitch),
                            color = accentColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = ttsPitch,
                        onValueChange = { viewModel.setTtsPitch(it, context) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = JarvisSurfaceVariant
                        ),
                        modifier = Modifier.testTag("pitch_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Speed Rate Slider
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Speech Rate", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = String.format(Locale.US, "%.2f x", ttsSpeechRate),
                            color = accentColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = ttsSpeechRate,
                        onValueChange = { viewModel.setTtsSpeechRate(it, context) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = JarvisSurfaceVariant
                        ),
                        modifier = Modifier.testTag("speech_rate_slider")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.speakAloud("Speech engine test complete. All audio nodes functioning at full capacity, Boss.") },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        enabled = isTtsEnabled,
                        modifier = Modifier.fillMaxWidth().testTag("test_speech_button")
                    ) {
                        Text("TEST JARVIS VOICE SYNTHESIS", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // --- OFFLINE COMMANDS SHOWCASE ---
        item {
            var expandedCategory by remember { mutableStateOf<String?>(null) }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth().testTag("offline_commands_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = "Offline Mode Support",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "OFFLINE COGNITIVE PROTOCOLS",
                            color = accentColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "The following neural subsystems run entirely on local offline hardware, requiring zero external uplink connectivity or cloud-latency.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val categories = listOf(
                        OfflineCategory(
                            id = "critical",
                            title = "STANDBY & SLEEP",
                            icon = Icons.Filled.PowerSettingsNew,
                            description = "Deactivate real-time listeners and mute vocal audio feedback instantly.",
                            examples = listOf("Stop", "Cancel", "Quiet", "Silence", "Sleep", "Chup", "Ruko")
                        ),
                        OfflineCategory(
                            id = "apps",
                            title = "APPLICATION LAUNCHING",
                            icon = Icons.Filled.Launch,
                            description = "Initiate external applications registered within local OS tables.",
                            examples = listOf("Open Chrome", "Launch YouTube", "Kholo Camera")
                        ),
                        OfflineCategory(
                            id = "flashlight",
                            title = "HARDWARE LUMENS",
                            icon = Icons.Filled.Lightbulb,
                            description = "Direct micro-controller access to trigger hardware flashlight beam.",
                            examples = listOf("Flashlight On", "Torch Light Off", "Torch Jalao")
                        ),
                        OfflineCategory(
                            id = "alarm",
                            title = "ALARM SCHEDULING",
                            icon = Icons.Filled.Alarm,
                            description = "Configure temporal alerts or log automated database reminders.",
                            examples = listOf("Set alarm 07:00", "Remind me to wake up at 08:30")
                        ),
                        OfflineCategory(
                            id = "notes",
                            title = "SECURE MEMO STORAGE",
                            icon = Icons.Filled.Create,
                            description = "Commit transcripts directly into secure local SQLite storage vaults.",
                            examples = listOf("Note buy palladium", "Likho Code next app", "Save Note")
                        ),
                        OfflineCategory(
                            id = "call",
                            title = "COMMUNICATIONS LINK",
                            icon = Icons.Filled.Call,
                            description = "Initiate direct phone dialing or pre-draft secure outgoing emails.",
                            examples = listOf("Call 911", "Phone milao", "Send Email")
                        ),
                        OfflineCategory(
                            id = "system",
                            title = "SYSTEM CONFIGURATION",
                            icon = Icons.Filled.Settings,
                            description = "Direct hardware configurations (WiFi, Bluetooth, Brightness).",
                            examples = listOf("Wifi settings", "Open Bluetooth", "Luminance")
                        ),
                        OfflineCategory(
                            id = "power",
                            title = "POWER & TELEMETRY",
                            icon = Icons.Filled.BatteryFull,
                            description = "Query active physical battery percentages and energy state levels.",
                            examples = listOf("Battery status", "Power level", "Toggle Battery Saver")
                        )
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isExpanded = expandedCategory == cat.id
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isExpanded) JarvisSurfaceVariant else JarvisSurfaceVariant.copy(alpha = 0.4f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isExpanded) accentColor.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        expandedCategory = if (isExpanded) null else cat.id
                                    }
                                    .padding(12.dp)
                                    .testTag("offline_cat_${cat.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            tint = if (isExpanded) accentColor else Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = cat.title,
                                            color = if (isExpanded) accentColor else Color.White,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = if (isExpanded) accentColor else JarvisTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.padding(top = 8.dp)) {
                                        Text(
                                            text = cat.description,
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "TRIGGER DIRECTIVES:",
                                            color = accentColor.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            cat.examples.forEach { example ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(JarvisSurface)
                                                        .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.15f)), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "\"$example\"",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
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
        }

        // --- LANGUAGE SELECTION SYSTEM ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Translate, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Text("SYSTEM LANGUAGE TRANSLATOR", color = accentColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Toggle the system's vocal perception and cognitive responses between English, Hindi, and mixed Hinglish dialect.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val languages = listOf("English", "Hindi", "Hinglish")
                        languages.forEach { lang ->
                            val isSelected = selectedLanguage.equals(lang, ignoreCase = true)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else JarvisSurfaceVariant)
                                    .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.setLanguage(lang, context)
                                    }
                                    .padding(vertical = 12.dp)
                                    .testTag("lang_select_$lang")
                            ) {
                                Text(
                                    text = lang.uppercase(),
                                    color = if (isSelected) accentColor else Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. POWER SYSTEM DIAGNOSTICS & SAVER CONFIG ---
        item {
            val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()
            val isCharging by viewModel.isCharging.collectAsStateWithLifecycle()
            val warningThreshold by viewModel.lowBatteryWarningThreshold.collectAsStateWithLifecycle()
            
            val batteryColor = if (isCharging) Color.Green else if (batteryLevel <= warningThreshold) Color.Red else accentColor

            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, batteryColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = batteryColor, modifier = Modifier.size(20.dp))
                            Text("POWER SYSTEM DIAGNOSTICS", color = batteryColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        
                        Text(
                            text = if (isCharging) "⚡ CHARGING" else "DISCHARGING",
                            color = batteryColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Battery level gauge and percentages
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Current Charge Level", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = "$batteryLevel%",
                            color = batteryColor,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Visual Progress bar
                    LinearProgressIndicator(
                        progress = { batteryLevel / 100f },
                        color = batteryColor,
                        trackColor = JarvisSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Warning Threshold configuration
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Low Battery Alarm Threshold", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = "$warningThreshold%",
                            color = batteryColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = warningThreshold.toFloat(),
                        onValueChange = { viewModel.setLowBatteryWarningThreshold(it.toInt(), context) },
                        valueRange = 10f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = batteryColor,
                            activeTrackColor = batteryColor,
                            inactiveTrackColor = JarvisSurfaceVariant
                        ),
                        modifier = Modifier.testTag("battery_threshold_slider")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.checkBatteryStatus(context, forceSpeak = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                            modifier = Modifier.weight(1f).testTag("diagnostic_check_btn")
                        ) {
                            Text("DIAGNOSTIC CHECK", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = { viewModel.processCommand("open battery saver settings", context, isVoice = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = batteryColor),
                            modifier = Modifier.weight(1f).testTag("toggle_saver_btn")
                        ) {
                            Text("TOGGLE BATTERY SAVER", color = Color.Black, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 4. DYNAMIC COSMIC COLOR PALETTE ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Text("CYBER CORE COLOR CODES", color = accentColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val colorsList = listOf("Cyan", "Blue", "Magenta", "Amber")
                        colorsList.forEach { colName ->
                            val colVal = when (colName) {
                                "Blue" -> JarvisBlue
                                "Magenta" -> JarvisMagenta
                                "Amber" -> JarvisAmber
                                else -> JarvisCyan
                            }
                            val isSelected = selectedThemeColor == colName

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { viewModel.setSelectedThemeColor(colName, context) }
                                    .padding(8.dp)
                                    .testTag("theme_select_$colName")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(colVal)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = Color.White,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = colName,
                                    color = if (isSelected) colVal else JarvisTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class OfflineCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val description: String,
    val examples: List<String>
)
