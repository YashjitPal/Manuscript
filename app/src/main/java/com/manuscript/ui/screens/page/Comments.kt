package com.manuscript.ui.screens.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.manuscript.core.data.Session
import com.manuscript.core.model.NComment
import com.manuscript.core.model.plainText
import com.manuscript.core.model.toRich
import com.manuscript.core.net.NotionException
import com.manuscript.core.net.userMessage
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.EmptyState
import com.manuscript.ui.components.LocalLinkHandler
import com.manuscript.ui.components.RichTextView
import com.manuscript.ui.components.UserAvatar
import com.manuscript.ui.sessionViewModel
import com.manuscript.ui.theme.LocalHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Discussion(val id: String, val comments: List<NComment>)

class CommentsViewModel(private val session: Session, private val targetId: String, private val isBlock: Boolean) : ViewModel() {
    data class UiState(
        val discussions: List<Discussion> = emptyList(),
        val loading: Boolean = true,
        val sending: Boolean = false,
        val error: String? = null,
        val notPermitted: Boolean = false,
    )

    private fun Throwable.isMissingCapability() = this is NotionException.Api && (code == "restricted_resource" || status == 403)

    private fun Throwable.commentError(): String =
        if (isMissingCapability()) "This connection isn't allowed to add or change comments. Turn on “Insert comments” for it in Notion's developer portal."
        else userMessage()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val users = session.cache.users

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.discussions.isEmpty(), error = null) }
            runCatching { session.comments.list(targetId) }
                .onSuccess { comments ->
                    session.users.ensure(comments.map { it.createdBy })
                    _state.update {
                        it.copy(
                            loading = false,
                            discussions = comments.groupBy { c -> c.discussionId }
                                .map { (id, list) -> Discussion(id, list.sortedBy { c -> c.createdTime }) }
                                .sortedByDescending { d -> d.comments.lastOrNull()?.createdTime },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        if (e.isMissingCapability()) it.copy(loading = false, notPermitted = true, error = null)
                        else it.copy(loading = false, error = e.userMessage())
                    }
                }
        }
    }

    fun send(text: String, discussionId: String?) {
        if (text.isBlank()) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (discussionId != null) session.comments.create(text.trim().toRich(), discussionId = discussionId)
                else if (isBlock) session.comments.create(text.trim().toRich(), blockId = targetId)
                else session.comments.create(text.trim().toRich(), pageId = targetId)
            }.onSuccess { load() }.onFailure { e -> _state.update { it.copy(error = e.commentError()) } }
            _state.update { it.copy(sending = false) }
        }
    }

    fun edit(comment: NComment, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { session.comments.update(comment.id, text.trim().toRich()) }
                .onSuccess { load() }.onFailure { e -> _state.update { it.copy(error = e.commentError()) } }
        }
    }

    fun delete(comment: NComment) {
        viewModelScope.launch {
            runCatching { session.comments.delete(comment.id) }
                .onSuccess { load() }.onFailure { e -> _state.update { it.copy(error = e.commentError()) } }
        }
    }
}

@Composable
fun CommentsPanel(targetId: String, isBlock: Boolean, title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val vm = sessionViewModel(key = "comments-$targetId") { CommentsViewModel(it, targetId, isBlock) }
    val state by vm.state.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val account = LocalSession.current.account
    val me = account.userId
    var draft by remember(targetId) { mutableStateOf("") }
    var replyTo by remember(targetId) { mutableStateOf<Discussion?>(null) }
    var editing by remember(targetId) { mutableStateOf<NComment?>(null) }
    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Comments", style = MaterialTheme.typography.titleLargeEmphasized)
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
        }
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                state.notPermitted -> EmptyState(
                    Icons.Rounded.Lock,
                    "Comments are turned off",
                    if (account.isPersonal) "Your personal access token isn't allowed to read comments in this workspace."
                    else "This Notion connection isn't allowed to read comments. In Notion's developer portal, open the connection's Capabilities and turn on “Read comments” and “Insert comments”.",
                    polygon = MaterialShapes.Cookie4Sided,
                )
                state.discussions.isEmpty() -> EmptyState(Icons.Rounded.Forum, "No comments yet", "Start a discussion about this ${if (isBlock) "block" else "page"}.", polygon = MaterialShapes.Puffy)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.discussions, key = { it.id }) { discussion ->
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                discussion.comments.forEach { comment ->
                                    CommentItem(
                                        comment = comment,
                                        author = users[comment.createdBy],
                                        mine = comment.createdBy != null && (comment.createdBy == me || comment.createdBy == LocalSession.current.account.botId),
                                        onEdit = { editing = comment; draft = comment.rich.plainText() },
                                        onDelete = { haptics.reject(); vm.delete(comment) },
                                    )
                                }
                                TextButton(onClick = { replyTo = discussion; editing = null }, modifier = Modifier.padding(start = 8.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reply")
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(replyTo != null || editing != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (editing != null) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (editing != null) "Editing comment" else "Replying to ${users[replyTo?.comments?.firstOrNull()?.createdBy]?.displayName ?: "discussion"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { replyTo = null; editing = null; draft = "" }) { Icon(Icons.Rounded.Close, "Cancel") }
            }
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (!state.notPermitted) Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(if (replyTo != null) "Reply…" else "Add a comment…") },
                shape = RoundedCornerShape(28.dp),
                maxLines = 5,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val target = editing
                    if (target != null) vm.edit(target, draft) else vm.send(draft, replyTo?.id)
                    haptics.confirm()
                    draft = ""
                    editing = null
                    replyTo = null
                },
                enabled = draft.isNotBlank() && !state.sending,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(52.dp),
            ) {
                if (state.sending) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.AutoMirrored.Rounded.Send, "Send")
            }
        }
    }
}

@Composable
private fun CommentItem(comment: NComment, author: com.manuscript.core.model.NUser?, mine: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val links = LocalLinkHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { if (mine) menu = true })
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        UserAvatar(author, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.displayName ?: author?.displayName ?: "Someone", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(Fmt.relative(comment.createdTime), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (comment.lastEditedTime != null && comment.lastEditedTime != comment.createdTime) {
                    Text(" · edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(2.dp))
            RichTextView(comment.rich, MaterialTheme.typography.bodyLarge)
            comment.attachments.forEach { file ->
                TextButton(onClick = { links.openUrl(file.url) }) {
                    Icon(Icons.Rounded.AttachFile, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(file.name?.replaceFirstChar { it.uppercase() } ?: "Attachment")
                }
            }
        }
        Box {
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
