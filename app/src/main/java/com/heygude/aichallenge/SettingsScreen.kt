package com.heygude.aichallenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.heygude.aichallenge.presentation.SystemPrompt
import com.heygude.aichallenge.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClearHistory: () -> Unit = {},
    viewModel: SettingsViewModel = run {
        val application = LocalContext.current.applicationContext as android.app.Application
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(application) as T
                }
            }
        )
    }
) {
    val allPrompts by viewModel.allPrompts.collectAsState()
    val currentPromptId by viewModel.currentPromptId.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<SystemPrompt?>(null) }
    var currentTemperature by remember { mutableStateOf(temperature) }
    var currentMaxTokens by remember { mutableStateOf(maxTokens.toString()) }
    var modelExpanded by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Get temperature range based on selected model
    val temperatureRange = selectedModel.provider
    val minTemp = temperatureRange.minTemperature
    val maxTemp = temperatureRange.maxTemperature
    
    // Clamp current temperature to valid range when model or temperature changes
    LaunchedEffect(temperature, selectedModel) {
        currentTemperature = temperature.coerceIn(minTemp, maxTemp)
    }
    
    // Update currentMaxTokens when maxTokens changes
    LaunchedEffect(maxTokens) {
        currentMaxTokens = maxTokens.toString()
    }
    
    // Show snackbar for success/error messages
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SettingsViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = androidx.compose.material3.SnackbarDuration.Short
                )
                // Dismiss snackbar and clear state after 1 second
                kotlinx.coroutines.delay(1000)
                snackbarHostState.currentSnackbarData?.dismiss()
                if (viewModel.uiState.value is SettingsViewModel.UiState.Success) {
                    viewModel.clearError()
                }
            }
            is SettingsViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = androidx.compose.material3.SnackbarDuration.Short
                )
                // Dismiss snackbar and clear state after 1 second
                kotlinx.coroutines.delay(500)
                snackbarHostState.currentSnackbarData?.dismiss()
                if (viewModel.uiState.value is SettingsViewModel.UiState.Error) {
                    viewModel.clearError()
                }
            }
            else -> {}
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Model selection
            Text(
                text = stringResource(R.string.gpt_model),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.model),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { modelExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedModel.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            com.heygude.aichallenge.data.yandex.GptModel.entries.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.displayName) },
                                    onClick = {
                                        viewModel.setSelectedModel(model)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Temperature setting
            Text(
                text = stringResource(R.string.temperature),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.temperature_value, currentTemperature),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = currentTemperature.toFloat(),
                        onValueChange = { currentTemperature = it.toDouble().coerceIn(minTemp, maxTemp) },
                        valueRange = minTemp.toFloat()..maxTemp.toFloat(),
                        steps = ((maxTemp - minTemp) / 0.1).toInt() - 1, // 0.1 increments
                        onValueChangeFinished = {
                            viewModel.setTemperature(currentTemperature.coerceIn(minTemp, maxTemp))
                        }
                    )
                    Text(
                        text = stringResource(R.string.temperature_description, minTemp, maxTemp, selectedModel.provider.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Max Tokens setting
            Text(
                text = stringResource(R.string.max_tokens),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = currentMaxTokens,
                        onValueChange = { newValue ->
                            // Only allow digits
                            if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                                currentMaxTokens = newValue
                            }
                        },
                        label = { Text(stringResource(R.string.max_tokens)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        supportingText = {
                            Text(stringResource(R.string.max_tokens_description))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val tokens = currentMaxTokens.toIntOrNull()
                            if (tokens != null && tokens in 1..32000) {
                                viewModel.setMaxTokens(tokens)
                            } else {
                                // Reset to current value if invalid
                                currentMaxTokens = maxTokens.toString()
                            }
                        },
                        enabled = currentMaxTokens.toIntOrNull()?.let { it in 1..32000 } == true && currentMaxTokens.toIntOrNull() != maxTokens
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Clear history button
            Text(
                text = stringResource(R.string.clear_history),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Очистить всю историю переписки",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = onClearHistory,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.clear_history))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.system_prompts),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Add new prompt button
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_new_system_prompt))
            }

            Spacer(modifier = Modifier.height(16.dp))


            // List of prompts
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allPrompts.forEach { prompt ->
                    PromptCard(
                        prompt = prompt,
                        isSelected = prompt.id == currentPromptId,
                        onSelect = { viewModel.setCurrentPrompt(prompt.id) },
                        onEdit = { editingPrompt = prompt },
                        onDelete = { viewModel.deletePrompt(prompt.id) }
                    )
                }
            }
        }
    }
    
    // Add/Edit dialog
    if (showAddDialog || editingPrompt != null) {
        AddEditPromptDialog(
            prompt = editingPrompt,
            onDismiss = {
                showAddDialog = false
                editingPrompt = null
                viewModel.clearError()
            },
            onSave = { name, content ->
                if (editingPrompt != null) {
                    viewModel.updatePrompt(editingPrompt!!.copy(name = name, content = content))
                } else {
                    viewModel.addPrompt(name, content)
                }
                showAddDialog = false
                editingPrompt = null
            }
        )
    }
}

@Composable
fun PromptCard(
    prompt: SystemPrompt,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prompt.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (prompt.isDefault) {
                            Text(
                                text = stringResource(R.string.default_prompt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (prompt.content.length > 100) {
                Text(
                    text = prompt.content.take(100) + "...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = prompt.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddEditPromptDialog(
    prompt: SystemPrompt?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(prompt?.name ?: "") }
    var content by remember { mutableStateOf(prompt?.content ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prompt != null) stringResource(R.string.edit_system_prompt) else stringResource(R.string.add_system_prompt)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.content)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, content) },
                enabled = name.isNotBlank() && content.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

