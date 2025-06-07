package com.anubhav.placemyorder

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anubhav.placemyorder.ui.theme.PlaceMyOrderTheme
import com.anubhav.placemyorder.ui.theme.blue_10
import com.anubhav.placemyorder.ui.theme.red_10
import com.anubhav.placemyorder.ui.theme.white_10
import com.anubhav.placemyorder.ui.theme.white_20
import com.anubhav.placemyorder.ui.theme.white_30
import com.anubhav.placemyorder.ui.theme.white_50
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlaceMyOrderTheme {

                val isUserSpeaking = remember { mutableStateOf(false) }
                val isMicOn = remember {
                    mutableStateOf(false)
                }
                val context = LocalContext.current
                val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
                val coroutineScope = rememberCoroutineScope()
                val audioHelper = remember {
                    AudioCaptureHelper(
                        onSpeechStart = {
                            Log.d("AudioHelper", "🎙️ Speaking...")
                            isUserSpeaking.value = true
                        },
                        onSpeechEnd = {
                            Log.d("AudioHelper", "🔇 Silence")
                            isUserSpeaking.value = false
                        },
                        onSegmentUpload = { delta ->
                            //data chunk available for upload . Data is in byte array format
                            Log.d("AudioHelper", "Data available for uploading.")
                            coroutineScope.launch {
                                Log.d("Audio Data", delta.toString())
                            }
                        }
                    )
                }

                LaunchedEffect(Unit) {
                    permissionState.launchPermissionRequest()
                }

                DisposableEffect(Unit) {
                    onDispose {
                        audioHelper.stopListening()
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(white_20)
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .background(white_20),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        MicToggleButton(
                            isRecording = isMicOn,
                            isUserSpeaking = isUserSpeaking
                        ) {
                            if (permissionState.status.isGranted) {
                                if (isMicOn.value) {
                                    audioHelper.stopListening()
                                    isMicOn.value = false
                                    isUserSpeaking.value = false
                                } else {
                                    audioHelper.startListening()
                                    isMicOn.value = true
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(52.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MicToggleButton(
    isRecording: MutableState<Boolean>,
    isUserSpeaking: MutableState<Boolean>,
    onToggle: () -> Unit,
) {
    // Ripple animation values when recording
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val icon = if (isRecording.value) Icons.Default.Stop else Icons.Default.Mic
    val iconTint = if (isRecording.value) red_10 else white_10

    val innerBackground = if (isRecording.value) white_30 else blue_10
    val outerBackground = if (isRecording.value) red_10 else white_50

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(100.dp)
    ) {
        if (isUserSpeaking.value) {
            Box(
                modifier = Modifier
                    .size(44.dp * pulseScale)
                    .clip(CircleShape)
                    .background(outerBackground.copy(alpha = pulseAlpha))
            )
        }

        // Main circular button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (isRecording.value) 44.dp else 64.dp)
                .clip(CircleShape)
                .background(outerBackground)
                .padding(4.dp)
                .clip(CircleShape)
                .background(innerBackground)
                .clickable { onToggle() }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (isRecording.value) "Stop" else "Mic",
                tint = iconTint,
                modifier = Modifier.size(if (isRecording.value) 24.dp else 34.dp)
            )
        }
    }
}