package com.worldmates.messenger.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.BusinessHour
import com.worldmates.messenger.data.model.BusinessHourRequest

private val BizDeep   = Color(0xFF0D1B2A)
private val BizDark   = Color(0xFF1A2942)
private val BizMid    = Color(0xFF243B55)
private val BizAccent = Color(0xFF1E90FF)
private val BizGold   = Color(0xFFFFD166)
private val BizCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessHoursScreen(
    hours:  List<BusinessHour>,
    onSave: (List<BusinessHourRequest>) -> Unit,
    onBack: () -> Unit
) {
    // Mutable state per weekday
    val rows = remember(hours) {
        (0..6).map { d ->
            val h = hours.find { it.weekday == d }
            mutableStateOf(
                Triple(
                    h?.isOpen == 1,
                    h?.openTime ?: "09:00",
                    h?.closeTime ?: "18:00"
                )
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BizDeep)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(BizMid, BizDark)))
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.biz_hours_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(rows) { index, rowState ->
                val (isOpen, openTime, closeTime) = rowState.value
                HoursRow(
                    weekday   = index,
                    isOpen    = isOpen,
                    openTime  = openTime,
                    closeTime = closeTime,
                    onToggle  = { rowState.value = rowState.value.copy(first = it) },
                    onOpenTime  = { rowState.value = rowState.value.copy(second = it) },
                    onCloseTime = { rowState.value = rowState.value.copy(third = it) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = {
                    onSave(rows.mapIndexed { i, s ->
                        BusinessHourRequest(
                            weekday   = i,
                            isOpen    = s.value.first,
                            openTime  = s.value.second,
                            closeTime = s.value.third
                        )
                    })
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = BizAccent)
            ) {
                Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoursRow(
    weekday:     Int,
    isOpen:      Boolean,
    openTime:    String,
    closeTime:   String,
    onToggle:    (Boolean) -> Unit,
    onOpenTime:  (String) -> Unit,
    onCloseTime: (String) -> Unit
) {
    val weekdayNames = listOf(
        stringResource(R.string.biz_weekday_sun),
        stringResource(R.string.biz_weekday_mon),
        stringResource(R.string.biz_weekday_tue),
        stringResource(R.string.biz_weekday_wed),
        stringResource(R.string.biz_weekday_thu),
        stringResource(R.string.biz_weekday_fri),
        stringResource(R.string.biz_weekday_sat)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = BizCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    weekdayNames[weekday],
                    color      = if (isOpen) BizGold else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium,
                    fontSize   = 15.sp,
                    modifier   = Modifier.weight(1f)
                )
                Switch(
                    checked         = isOpen,
                    onCheckedChange = onToggle,
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor  = BizAccent,
                        checkedTrackColor  = BizAccent.copy(alpha = 0.4f)
                    )
                )
            }
            if (isOpen) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(
                        value       = openTime,
                        label       = stringResource(R.string.biz_hours_open),
                        onChange    = onOpenTime,
                        modifier    = Modifier.weight(1f)
                    )
                    TimeField(
                        value       = closeTime,
                        label       = stringResource(R.string.biz_hours_close),
                        onChange    = onCloseTime,
                        modifier    = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value         = value,
        onValueChange = { v ->
            // Keep HH:MM format mask
            val digits = v.filter { it.isDigit() }
            val masked = when {
                digits.length >= 4 -> "${digits.take(2)}:${digits.substring(2, 4)}"
                digits.length >= 2 -> "${digits.take(2)}:${digits.drop(2)}"
                else               -> digits
            }
            onChange(masked)
        },
        label         = { Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)) },
        singleLine    = true,
        modifier      = modifier,
        shape         = RoundedCornerShape(10.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = BizAccent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            focusedTextColor     = Color.White,
            unfocusedTextColor   = Color.White,
            cursorColor          = BizAccent
        )
    )
}
