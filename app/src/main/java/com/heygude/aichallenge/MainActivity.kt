package com.heygude.aichallenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.heygude.aichallenge.ui.theme.AIChallengeTheme

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
        enableEdgeToEdge()
        setContent {
            AIChallengeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AIAgentScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

}

@Composable
fun AIAgentScreen(modifier: Modifier = Modifier, vm: AIAgentViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()
    var prompt by remember { mutableStateOf("") }
    var showJsonMessages by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val messages by vm.messages.collectAsState()
    val listState = rememberLazyListState()

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
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
                        modifier = Modifier.fillMaxWidth(0.75f),
                        tonalElevation = 1.dp
                    ) {
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
                    }
                }
            }
        }
        // Generating indicator - fixed height to prevent layout shifts
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp), // Fixed height to prevent layout shifts
            color = if (uiState is AIAgentViewModel.UiState.Loading) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                Color.Transparent
            }
        ) {
            AnimatedVisibility(
                visible = uiState is AIAgentViewModel.UiState.Loading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Generating...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    modifier = Modifier.weight(1f),
                    placeholder = { 
                        Text(
                            "Message",
                            style = MaterialTheme.typography.bodyLarge
                        ) 
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
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
                        contentDescription = "Send",
                        tint = if (prompt.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = showJsonMessages,
                onCheckedChange = { showJsonMessages = it }
            )
            Text(
                text = "Show messages in JSON",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
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