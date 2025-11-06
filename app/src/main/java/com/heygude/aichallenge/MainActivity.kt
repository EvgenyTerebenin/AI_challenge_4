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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.heygude.aichallenge.ui.theme.AIChallengeTheme

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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val itemsInReverse = messages.sortedByDescending { it.timestampMs }
            items(itemsInReverse, key = { it.id }) { message ->
                val isUser = message.isUser
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) Color(0xFFDCF8C6) else Color(0xFFFFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                    ) {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(12.dp),
                            color = Color.Black
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                singleLine = true,
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
            Button(
                onClick = {
                    if (prompt.isNotBlank()) {
                        vm.sendPrompt(prompt)
                        prompt = ""
                        keyboardController?.hide()
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Send")
            }
        }

        when (val state = uiState) {
            is AIAgentViewModel.UiState.Loading -> Text("Generating...", modifier = Modifier.padding(horizontal = 12.dp))
            else -> {}
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