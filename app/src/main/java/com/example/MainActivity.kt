package com.example

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StrobeApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun StrobeApp(modifier: Modifier = Modifier) {
    var frequency by remember { mutableFloatStateOf(0f) }
    var isResumed by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val cameraManager = remember { 
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager 
    }
    
    val cameraId = remember {
        try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            Log.e("StrobeApp", "Error checking camera capabilities", e)
            null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, cameraId) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = (event == Lifecycle.Event.ON_RESUME)
            // Turn off camera unconditionally when paused/stopped
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                if (cameraId != null) {
                    try {
                        cameraManager.setTorchMode(cameraId, false)
                    } catch (e: Exception) {
                        Log.e("StrobeApp", "Error turning off flashlight on pause", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (cameraId != null) {
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (e: Exception) {
                    Log.e("StrobeApp", "Error turning off flashlight on dispose", e)
                }
            }
        }
    }

    LaunchedEffect(frequency, isResumed) {
        if (cameraId == null) return@LaunchedEffect
        
        try {
            if (!isResumed || frequency <= 0f) {
                cameraManager.setTorchMode(cameraId, false)
            } else {
                val freq = frequency.coerceIn(0.1f, 10f)
                val halfPeriod = (1000f / freq / 2f).toLong()
                
                while (isActive) {
                    cameraManager.setTorchMode(cameraId, true)
                    delay(halfPeriod)
                    cameraManager.setTorchMode(cameraId, false)
                    delay(halfPeriod)
                }
            }
        } catch (e: Exception) {
            Log.e("StrobeApp", "Error operating flashlight", e)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Villogás frekvencia",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = String.format("%.1f Hz", frequency),
            style = MaterialTheme.typography.displayLarge,
            color = if (frequency > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Slider(
            value = frequency,
            onValueChange = { frequency = it },
            valueRange = 0f..10f,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Állítsd be a csúszkát 0 és 10 Hz között a villogás sebességének változtatásához.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (cameraId == null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Kamera vaku nem található ezen az eszközön.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
