package com.heygude.aichallenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.heygude.aichallenge.BuildConfig
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.heygude.aichallenge.ui.theme.AIChallengeTheme
import com.heygude.aichallenge.data.yandex.GptModel
import androidx.compose.ui.res.stringResource
import com.heygude.aichallenge.R
import timber.log.Timber

@Serializable
private data class ApiResponse(
    val status: String? = null,
    val data: ResponseData? = null,
    val error: ErrorData? = null
)

@Serializable
private data class ResponseData(
    val text: String? = null,
    val metadata: JsonObject? = null
)

@Serializable
private data class ErrorData(
    val code: String? = null,
    val message: String? = null,
    val details: JsonObject? = null
)

private val jsonParser = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Extracts text from JSON response if possible, otherwise returns original text
 */
private fun extractTextFromJson(jsonText: String): String {
    return try {
        val response = jsonParser.decodeFromString<ApiResponse>(jsonText)
        response.data?.text ?: jsonText
    } catch (e: Exception) {
        // If parsing fails, return original text
        jsonText
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        enableEdgeToEdge()
        setContent {
            AIChallengeTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val activity = context as? ComponentActivity
                val application = context.applicationContext as android.app.Application
                // Create shared ViewModel at Activity level
                val aiAgentViewModel: AIAgentViewModel = if (activity != null) {
                    viewModel(
                        viewModelStoreOwner = activity,
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return AIAgentViewModel(application) as T
                            }
                        }
                    )
                } else {
                    // Fallback if activity is not available
                    viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return AIAgentViewModel(application) as T
                            }
                        }
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            AIAgentScreen(
                                modifier = Modifier.padding(innerPadding),
                                vm = aiAgentViewModel,
                                onSettingsClick = { navController.navigate("settings") }
                            )
                        }
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBackClick = { navController.popBackStack() },
                            onClearHistory = { aiAgentViewModel.clearHistory() }
                        )
                    }
                }
            }
        }
    }

}

@Composable
fun AIAgentScreen(
    modifier: Modifier = Modifier,
    vm: AIAgentViewModel = run {
        val application = LocalContext.current.applicationContext as android.app.Application
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return AIAgentViewModel(application) as T
                }
            }
        )
    },
    onSettingsClick: () -> Unit = {}
) {
    val uiState by vm.uiState.collectAsState()
    var prompt by remember { mutableStateOf("") }
    var showJsonMessages by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val messages by vm.messages.collectAsState()
    val listState = rememberLazyListState()
    val selectedModel by vm.selectedModel.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val messageLabel = stringResource(R.string.message)
    val textCopiedMessage = stringResource(R.string.text_copied_to_clipboard)

    // Auto-scroll to top (index 0) when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // Small delay to ensure the item is laid out before scrolling
            delay(50)
            listState.scrollToItem(0)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val itemsInReverse = messages.sortedByDescending { it.timestampMs }
            items(itemsInReverse, key = { it.id }) { message ->
                val isUser = message.isUser
                val displayText = if (!isUser && !showJsonMessages) {
                    // Extract text from JSON for server messages when checkbox is disabled
                    extractTextFromJson(message.text)
                } else {
                    // Show full text/JSON for user messages or when checkbox is enabled
                    message.text
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    // Title
                    Text(
                        text = if (isUser) {
                            stringResource(R.string.user)
                        } else {
                            val modelName = message.model?.displayName ?: stringResource(R.string.ai)
                            if (message.isSummary) {
                                "📝 Резюме ($modelName)"
                            } else {
                                modelName
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Message bubble
                    Surface(
                        shape = if (isUser) {
                            RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                        } else {
                            RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                        },
                        color = if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(messageLabel, displayText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, textCopiedMessage, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                        tonalElevation = 1.dp
                    ) {
                        Column {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                            // Token information
                            if (isUser && message.requestTokens != null) {
                                // Show request tokens for user messages
                                Text(
                                    text = "Request tokens: ${message.requestTokens}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            } else if (!isUser && (message.requestTokens != null || message.responseTokens != null || message.responseTimeMs != null || message.costUsd != null)) {
                                // Show token info for AI messages
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    if (message.requestTokens != null) {
                                        Text(
                                            text = "Request tokens: ${message.requestTokens}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    if (message.responseTokens != null) {
                                        Text(
                                            text = "Response tokens: ${message.responseTokens}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    if (message.responseTimeMs != null) {
                                        val seconds = message.responseTimeMs / 1000.0
                                        Text(
                                            text = "Response time: ${String.format("%.2f", seconds)}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    if (message.costUsd != null) {
                                        Text(
                                            text = "Cost: $${String.format("%.6f", message.costUsd)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 200.dp),
                    placeholder = { 
                        Text(
                            stringResource(R.string.message),
                            style = MaterialTheme.typography.bodyLarge
                        ) 
                    },
                    maxLines = 5,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    singleLine = false,
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (prompt.isNotBlank()) {
                                vm.sendPrompt(prompt)
                                prompt = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                )
                if (uiState is AIAgentViewModel.UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (prompt.isNotBlank()) {
                                vm.sendPrompt(prompt)
                                prompt = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier,
                        enabled = prompt.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = if (prompt.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
        }

        // Model selection dropdown
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.model),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedModel.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        GptModel.entries.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName) },
                                onClick = {
                                    vm.setSelectedModel(model)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showJsonMessages,
                    onCheckedChange = { showJsonMessages = it }
                )
                Text(
                    text = stringResource(R.string.show_messages_in_json),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AIAgentScreenPreview() {
    AIChallengeTheme {
        AIAgentScreen()
    }
}