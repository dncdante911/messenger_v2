package com.worldmates.messenger.ui.messages.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.network.Poll
import com.worldmates.messenger.network.PollOption

/**
 * Poll message bubble for group chats and channels.
 * Shows question, options with vote bars, total vote count.
 */
@Composable
fun PollMessageComponent(
    poll: Poll,
    isMine: Boolean,
    canClose: Boolean = false,
    onVote: (optionId: Long) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isMine)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val contentColor = if (isMine)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    val hasVoted = poll.options.any { it.isVoted }
    val showResults = hasVoted || poll.isClosed

    Surface(
        modifier = modifier.widthIn(min = 200.dp, max = 320.dp),
        shape = RoundedCornerShape(16.dp),
        color = bubbleColor,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (poll.isClosed) Icons.Default.Lock else Icons.Default.HowToVote,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (poll.isClosed) stringResource(R.string.poll_closed)
                           else stringResource(R.string.poll_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                if (poll.isAnonymous) {
                    Text(
                        text = stringResource(R.string.poll_anonymous),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.5f)
                    )
                }
            }

            // Question
            Text(
                text = poll.question,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            // Options
            poll.options.forEach { option ->
                PollOptionRow(
                    option = option,
                    showResults = showResults,
                    isClosed = poll.isClosed,
                    contentColor = contentColor,
                    onVote = { if (!hasVoted && !poll.isClosed) onVote(option.id) }
                )
            }

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.poll_votes_count, poll.totalVotes),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
                if (canClose && !poll.isClosed) {
                    TextButton(
                        onClick = onClose,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.poll_close),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PollOptionRow(
    option: PollOption,
    showResults: Boolean,
    isClosed: Boolean,
    contentColor: Color,
    onVote: () -> Unit
) {
    val animatedPercent by animateFloatAsState(
        targetValue = if (showResults) option.percent / 100f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "poll_bar"
    )

    // Fill alpha is kept low so text remains readable at any fill width
    val fillAlpha = if (option.isVoted) 0.18f else 0.10f
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = fillAlpha)
    val borderColor = if (option.isVoted)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    else
        contentColor.copy(alpha = 0.18f)
    val accentColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(contentColor.copy(alpha = 0.06f))
            .then(
                if (!showResults && !isClosed)
                    Modifier.clickable(onClick = onVote)
                else Modifier
            )
            .border(
                width = if (option.isVoted) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Transparent progress fill — never obscures text
        if (showResults && animatedPercent > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedPercent)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fillColor)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (option.isVoted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = option.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (option.isVoted) accentColor else contentColor,
                    fontWeight = if (option.isVoted) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showResults) {
                Text(
                    text = "${option.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (option.isVoted) FontWeight.Bold else FontWeight.Medium,
                    color = if (option.isVoted) accentColor else contentColor.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Dialog to create a new poll.
 */
@Composable
fun CreatePollDialog(
    onDismiss: () -> Unit,
    onCreate: (question: String, options: List<String>, isAnonymous: Boolean, allowsMultiple: Boolean) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var isAnonymous by remember { mutableStateOf(true) }
    var allowsMultiple by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.poll_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.poll_question_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.poll_options_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                options.forEachIndexed { index, optionText ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = optionText,
                            onValueChange = { newVal ->
                                options = options.toMutableList().also { it[index] = newVal }
                            },
                            label = { Text(stringResource(R.string.poll_option_n, index + 1)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (options.size > 2) {
                            IconButton(onClick = {
                                options = options.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.poll_remove_option)
                                )
                            }
                        }
                    }
                }

                if (options.size < 10) {
                    TextButton(onClick = { options = options + "" }) {
                        Text(stringResource(R.string.poll_add_option))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.poll_anonymous_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.poll_multiple_answers),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = allowsMultiple, onCheckedChange = { allowsMultiple = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val filteredOptions = options.filter { it.isNotBlank() }
                    if (question.isNotBlank() && filteredOptions.size >= 2) {
                        onCreate(question.trim(), filteredOptions, isAnonymous, allowsMultiple)
                        onDismiss()
                    }
                },
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2
            ) {
                Text(stringResource(R.string.poll_create_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
