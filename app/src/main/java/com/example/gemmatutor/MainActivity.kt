package com.example.gemmatutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gemmatutor.ui.theme.GemmaTutorTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

const val START_SCREEN = "start_screen"
const val CHAT_SCREEN = "chat_screen"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GemmaTutorTheme {
                Scaffold(
                    topBar = { AppBar() }
                ) { innerPadding ->
                    ComposableMain(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ComposableMain(modifier: Modifier = Modifier) {
    val permissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO
    )

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        if (permissionState.status.isGranted) {
            GemmaTutor()
        } else {
            PermissionRequest(permissionState)
        }
    }
}

@Composable
fun GemmaTutor() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = START_SCREEN
    ) {
        composable(START_SCREEN) {
            LoadingRoute(
                onModelLoaded = {
                    navController.navigate(CHAT_SCREEN) {
                        popUpTo(START_SCREEN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(CHAT_SCREEN) {
            ChatRoute()
        }
    }
}

// TopAppBar is marked as experimental in Material 3
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Text(
                text = stringResource(R.string.disclaimer),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequest(permissionState: PermissionState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "please grant RECORD_AUDIO permission.",
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        )
        Button(
            onClick = { permissionState.launchPermissionRequest() },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        ) {
            Text("Request permission")
        }
    }
}
