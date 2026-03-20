package com.worldmates.messenger.ui.notes

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NoteItem
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Notes — Telegram-style personal cloud storage.
 * Text notes are created inline; files via upload (future).
 * Backend: GET/POST/DELETE /api/node/notes
 */
class NotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorldMatesThemedApp {
                NotesScreen(onBack = { finish() })
            }
        }
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class NotesUiState(
    val notes:      List<NoteItem> = emptyList(),
    val isLoading:  Boolean         = false,
    val usedBytes:  Long            = 0L,
    val quotaBytes: Long            = 0L,
    val error:      String?         = null,
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()

    private val api get() = NodeRetrofitClient.api

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val notesResp   = api.listNotes()
                val storageResp = api.getNotesStorage()
                if (notesResp.apiStatus == 200) {
                    _state.value = _state.value.copy(
                        notes      = notesResp.notes,
                        usedBytes  = storageResp.usedBytes,
                        quotaBytes = storageResp.quotaBytes,
                        isLoading  = false,
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = notesResp.errorMessage)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createTextNote(text: String, onDone: () -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val resp = api.createNote(text.trim())
                if (resp.apiStatus == 200 && resp.note != null) {
                    _state.value = _state.value.copy(
                        notes = listOf(resp.note) + _state.value.notes
                    )
                }
            } catch (_: Exception) {}
            onDone()
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            try {
                api.deleteNote(id)
                _state.value = _state.value.copy(
                    notes = _state.value.notes.filter { it.id != id }
                )
            } catch (_: Exception) {}
        }
    }
}

// ── UI ────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesScreen(onBack: () -> Unit, vm: NotesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.notes_title), fontWeight = FontWeight.Bold)
                        if (state.quotaBytes > 0) {
                            val usedMb  = state.usedBytes  / 1024 / 1024
                            val quotaGb = state.quotaBytes / 1024 / 1024 / 1024
                            Text(
                                text  = "${usedMb} MB / ${quotaGb} GB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            // Inline text note composer
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = { inputText = it },
                        placeholder   = { Text(stringResource(R.string.notes_hint)) },
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(24.dp),
                        maxLines      = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick  = {
                            vm.createTextNote(inputText) { inputText = "" }
                        },
                        enabled  = inputText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.notes.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Note,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.notes_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteCard(
                            note     = note,
                            onDelete = { showDeleteConfirm = note.id }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title   = { Text(stringResource(R.string.delete_note_title)) },
            text    = { Text(stringResource(R.string.delete_note_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNote(id)
                    showDeleteConfirm = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun NoteCard(note: NoteItem, onDelete: () -> Unit) {
    val dateStr = remember(note.createdAt) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(note.createdAt * 1000))
    }
    val typeIcon = when (note.type) {
        "image"  -> Icons.Default.Image
        "video"  -> Icons.Default.Movie
        "audio"  -> Icons.Default.Mic
        "file"   -> Icons.Default.AttachFile
        else     -> Icons.Default.Note
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier            = Modifier.padding(12.dp),
            verticalAlignment   = Alignment.Top
        ) {
            Icon(
                imageVector        = typeIcon,
                contentDescription = note.type,
                modifier           = Modifier.size(20.dp).padding(top = 2.dp),
                tint               = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!note.text.isNullOrBlank()) {
                    Text(
                        text     = note.text,
                        style    = MaterialTheme.typography.bodyMedium,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                note.fileName?.let { name ->
                    Text(
                        text  = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val sizeKb = note.fileSize / 1024
                    Text(
                        text  = if (sizeKb > 1024) "${sizeKb/1024} MB" else "$sizeKb KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
