package com.manuscript.core.data

import android.content.Context
import com.manuscript.core.data.db.AppDatabase
import com.manuscript.core.net.NotionApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Everything bound to one signed-in Notion account. */
class Session(
    val account: Account,
    val api: NotionApi,
    val cache: ObjectCache,
    db: AppDatabase,
    context: Context,
    val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<ChangeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChangeEvent> = _events

    val users = UserRepository(api, cache, scope)
    val workspace = WorkspaceRepository(api, cache, db, account.id, _events)
    val pages = PageRepository(api, cache, db, account.id, _events)
    val databases = DatabaseRepository(api, cache, _events)
    val comments = CommentRepository(api)
    val uploads = UploadRepository(api, context, account.maxUploadBytes)

    val isDemo: Boolean get() = account.kind == AccountKind.DEMO
    val canListUsers: Boolean get() = !account.isPersonal

    fun emit(event: ChangeEvent) {
        _events.tryEmit(event)
    }
}
