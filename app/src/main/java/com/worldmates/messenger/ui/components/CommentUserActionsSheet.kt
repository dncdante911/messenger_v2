package com.worldmates.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R

// ══════════════════════════════════════════════════════════════════════════════
// Sealed class — действия в меню пользователя из комментария
// ══════════════════════════════════════════════════════════════════════════════

sealed class CommentUserAction {
    /** Ответить на комментарий */
    object Reply : CommentUserAction()
    /** Открыть профиль */
    object ViewProfile : CommentUserAction()
    /** Написать в личку */
    object SendMessage : CommentUserAction()
    /** Подписаться / отписаться */
    data class Follow(val isFollowing: Boolean) : CommentUserAction()
    /** Добавить в друзья / убрать */
    data class AddFriend(val isFriend: Boolean) : CommentUserAction()
    /** Упомянуть (@username) в поле ввода */
    object Mention : CommentUserAction()
    /** Мьют / анмьют пользователя (скрыть его комментарии) */
    data class MuteUser(val isMuted: Boolean) : CommentUserAction()
    /** Заблокировать / разблокировать */
    data class Block(val isBlocked: Boolean) : CommentUserAction()
    /** Забанить в канале (только для админа) */
    object BanInChannel : CommentUserAction()
    /** Забанить в группе (только для админа) */
    object BanInGroup : CommentUserAction()
    /** Пожаловаться */
    object Report : CommentUserAction()
    /** Скопировать текст комментария */
    object CopyText : CommentUserAction()
}

// ══════════════════════════════════════════════════════════════════════════════
// Bottom sheet
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Компактный bottom sheet с действиями над пользователем в контексте комментария.
 *
 * @param userId      ID пользователя, чей комментарий
 * @param username    Имя пользователя
 * @param avatar      URL аватара (может быть null)
 * @param isOwnComment  true — это мой комментарий (скрываем Reply/Follow/Block/Ban/Report)
 * @param isFollowing   уже подписан на пользователя
 * @param isFriend      уже в друзьях
 * @param isMuted       пользователь замьючен
 * @param isBlocked     пользователь заблокирован
 * @param isAdmin       текущий юзер — администратор (показывать Ban)
 * @param context       "channel" | "group" | "story" — определяет контекст бана
 * @param commentText   текст комментария (для CopyText)
 * @param onDismiss     закрыть шторку
 * @param onAction      обработчик действия
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentUserActionsSheet(
    userId: Long,
    username: String,
    avatar: String?,
    isOwnComment: Boolean,
    isFollowing: Boolean = false,
    isFriend: Boolean = false,
    isMuted: Boolean = false,
    isBlocked: Boolean = false,
    isAdmin: Boolean = false,
    context: String = "story",       // "channel" | "group" | "story"
    commentText: String = "",
    onDismiss: () -> Unit,
    onAction: (CommentUserAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // ── Шапка: аватар + имя ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!avatar.isNullOrEmpty()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = username.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = username,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.comment_user_actions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Действия ─────────────────────────────────────────────────────

            // Ответить — для чужих комментариев
            if (!isOwnComment) {
                ActionRow(
                    icon = Icons.Outlined.Reply,
                    label = stringResource(R.string.reply),
                    onClick = { onAction(CommentUserAction.Reply); onDismiss() }
                )
            }

            // Просмотр профиля — всегда
            ActionRow(
                icon = Icons.Outlined.Person,
                label = stringResource(R.string.view_profile),
                onClick = { onAction(CommentUserAction.ViewProfile); onDismiss() }
            )

            // Написать — для чужих
            if (!isOwnComment) {
                ActionRow(
                    icon = Icons.Outlined.Chat,
                    label = stringResource(R.string.send_message),
                    onClick = { onAction(CommentUserAction.SendMessage); onDismiss() }
                )
            }

            // Упомянуть — для чужих
            if (!isOwnComment) {
                ActionRow(
                    icon = Icons.Outlined.AlternateEmail,
                    label = stringResource(R.string.mention_in_reply),
                    onClick = { onAction(CommentUserAction.Mention); onDismiss() }
                )
            }

            // Подписаться / отписаться
            if (!isOwnComment) {
                ActionRow(
                    icon = if (isFollowing) Icons.Outlined.PersonRemove else Icons.Outlined.PersonAdd,
                    label = stringResource(if (isFollowing) R.string.unfollow else R.string.follow),
                    onClick = { onAction(CommentUserAction.Follow(isFollowing)); onDismiss() }
                )
            }

            // Добавить в друзья
            if (!isOwnComment && !isFriend) {
                ActionRow(
                    icon = Icons.Outlined.GroupAdd,
                    label = stringResource(R.string.add_friend),
                    onClick = { onAction(CommentUserAction.AddFriend(isFriend)); onDismiss() }
                )
            }

            // Скопировать текст — всегда
            if (commentText.isNotEmpty()) {
                ActionRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = stringResource(R.string.copy_text),
                    onClick = { onAction(CommentUserAction.CopyText); onDismiss() }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            // Мьют / анмьют — для чужих
            if (!isOwnComment) {
                ActionRow(
                    icon = if (isMuted) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    label = stringResource(if (isMuted) R.string.unmute_user else R.string.mute_user),
                    onClick = { onAction(CommentUserAction.MuteUser(isMuted)); onDismiss() }
                )
            }

            // Заблокировать / разблокировать — для чужих
            if (!isOwnComment) {
                ActionRow(
                    icon = if (isBlocked) Icons.Outlined.LockOpen else Icons.Outlined.Block,
                    label = stringResource(if (isBlocked) R.string.unblock_user else R.string.block_user),
                    isDestructive = !isBlocked,
                    onClick = { onAction(CommentUserAction.Block(isBlocked)); onDismiss() }
                )
            }

            // Бан в канале / группе — только для админа, для чужих
            if (isAdmin && !isOwnComment) {
                when (context) {
                    "channel" -> ActionRow(
                        icon = Icons.Outlined.GppBad,
                        label = stringResource(R.string.ban_in_channel),
                        isDestructive = true,
                        onClick = { onAction(CommentUserAction.BanInChannel); onDismiss() }
                    )
                    "group" -> ActionRow(
                        icon = Icons.Outlined.GppBad,
                        label = stringResource(R.string.ban_in_group),
                        isDestructive = true,
                        onClick = { onAction(CommentUserAction.BanInGroup); onDismiss() }
                    )
                }
            }

            // Пожаловаться — для чужих
            if (!isOwnComment) {
                ActionRow(
                    icon = Icons.Outlined.Flag,
                    label = stringResource(R.string.report_user),
                    isDestructive = true,
                    onClick = { onAction(CommentUserAction.Report); onDismiss() }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Визуальный блок цитаты ответа — используется в CommentItem / PremiumCommentItem
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Компактная визуальная цитата ответа. Отображается ВЫШЕ текста комментария.
 *
 * Дизайн намеренно отличается от Telegram:
 *  - закруглённая «таблетка» с лёгким градиентом от primary
 *  - слева иконка ↩ + кружок с начальной буквой автора
 *  - жирный username и срезанный текст в одну строку
 *
 * @param replyToUsername    имя автора цитируемого комментария
 * @param replyToText        текст цитируемого комментария
 * @param onTap              опциональный клик для перехода к цитате (можно null)
 */
@Composable
fun CommentReplyQuote(
    replyToUsername: String,
    replyToText: String,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val initial = replyToUsername.take(1).uppercase()
    val bgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ↩ иконка
        Icon(
            imageVector = Icons.Outlined.Reply,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(13.dp)
        )

        // Кружок с начальной буквой
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Имя + текст
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = replyToUsername,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1
            )
            Text(
                text = "·",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = replyToText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDestructive)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}
