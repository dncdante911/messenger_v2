package com.worldmates.messenger.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bottom sheet for configuring search filters:
 *  – Message type (All / Text / Media / Sticker)
 *  – Date range (From / To) with Material3 DatePickerDialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterSheet(
    current:   SearchFilters,
    onApply:   (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var msgType  by remember { mutableStateOf(current.msgType)  }
    var dateFrom by remember { mutableStateOf(current.dateFrom) }
    var dateTo   by remember { mutableStateOf(current.dateTo)   }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text       = stringResource(R.string.search_filter_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // ── Message type ──────────────────────────────────────────────────
            Text(
                text  = stringResource(R.string.search_filter_type_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                listOf(
                    MsgTypeFilter.ALL     to R.string.search_filter_type_all,
                    MsgTypeFilter.TEXT    to R.string.search_filter_type_text,
                    MsgTypeFilter.MEDIA   to R.string.search_filter_type_media,
                    MsgTypeFilter.STICKER to R.string.search_filter_type_sticker,
                ).forEach { (type, labelRes) ->
                    FilterChip(
                        selected = msgType == type,
                        onClick  = { msgType = type },
                        label    = { Text(stringResource(labelRes)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Date range ────────────────────────────────────────────────────
            Text(
                text  = stringResource(R.string.search_filter_date_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                // From date
                OutlinedButton(
                    onClick  = { showFromPicker = true },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text     = dateFrom?.let { dateFmt.format(Date(it * 1000)) }
                            ?: stringResource(R.string.search_filter_date_from),
                        style    = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                // To date
                OutlinedButton(
                    onClick  = { showToPicker = true },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text     = dateTo?.let { dateFmt.format(Date(it * 1000)) }
                            ?: stringResource(R.string.search_filter_date_to),
                        style    = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            // Clear date range
            if (dateFrom != null || dateTo != null) {
                TextButton(
                    onClick  = { dateFrom = null; dateTo = null },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.search_filter_date_clear))
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick  = {
                        msgType  = MsgTypeFilter.ALL
                        dateFrom = null
                        dateTo   = null
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.search_filter_reset))
                }
                Button(
                    onClick  = {
                        onApply(
                            SearchFilters(
                                dateFrom = dateFrom,
                                dateTo   = dateTo,
                                fromId   = current.fromId,
                                msgType  = msgType,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.search_filter_apply))
                }
            }
        }
    }

    // ── Date picker dialogs ───────────────────────────────────────────────────

    if (showFromPicker) {
        DatePickerModal(
            initialMillis = dateFrom?.times(1000L),
            onDateSelected = { ms ->
                dateFrom = ms?.div(1000L)
                showFromPicker = false
            },
            onDismiss = { showFromPicker = false },
        )
    }

    if (showToPicker) {
        DatePickerModal(
            initialMillis = dateTo?.times(1000L),
            onDateSelected = { ms ->
                dateTo = ms?.div(1000L)
                showToPicker = false
            },
            onDismiss = { showToPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initialMillis:   Long?,
    onDateSelected:  (Long?) -> Unit,
    onDismiss:       () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton    = {
            TextButton(onClick = { onDateSelected(state.selectedDateMillis) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}
