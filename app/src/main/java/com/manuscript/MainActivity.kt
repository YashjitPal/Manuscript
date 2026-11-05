package com.manuscript

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.manuscript.core.SessionState
import com.manuscript.ui.navigation.Tab
import com.manuscript.ui.shell.ManuscriptRoot
import com.manuscript.ui.shell.ShellAction
import com.manuscript.ui.shell.ShellActions

class MainActivity : ComponentActivity() {

    private val container get() = ManuscriptApp.container

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { container.state.value == SessionState.Loading }
        if (savedInstanceState == null) handleIntent(intent)
        setContent { ManuscriptRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (container.handleOAuthRedirect(data)) return
                if (data.scheme == "manuscript") return
                ShellActions.post(ShellAction.OpenLink(data.toString()))
            }
            Intent.ACTION_SEND -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return
                ShellActions.post(ShellAction.ShareText(text, intent.getStringExtra(Intent.EXTRA_SUBJECT)))
            }
            ACTION_NEW_PAGE -> ShellActions.post(ShellAction.NewPage)
            ACTION_QUICK_NOTE -> ShellActions.post(ShellAction.QuickNote)
            ACTION_SEARCH -> ShellActions.post(ShellAction.Search)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.isCtrlPressed && !event.isAltPressed) {
            val action = when (event.keyCode) {
                KeyEvent.KEYCODE_P -> ShellAction.Search
                KeyEvent.KEYCODE_N -> if (event.isShiftPressed) ShellAction.NewDatabase else ShellAction.NewPage
                KeyEvent.KEYCODE_BACKSLASH -> ShellAction.ToggleSidebar
                KeyEvent.KEYCODE_COMMA -> ShellAction.Settings
                KeyEvent.KEYCODE_LEFT_BRACKET -> ShellAction.Back
                KeyEvent.KEYCODE_L -> if (event.isShiftPressed) ShellAction.ToggleTheme else null
                KeyEvent.KEYCODE_1 -> ShellAction.SelectTab(Tab.HOME)
                KeyEvent.KEYCODE_2 -> ShellAction.SelectTab(Tab.SEARCH)
                KeyEvent.KEYCODE_3 -> ShellAction.SelectTab(Tab.LIBRARY)
                KeyEvent.KEYCODE_4 -> ShellAction.SelectTab(Tab.ACTIVITY)
                else -> null
            }
            if (action != null && container.state.value is SessionState.Active) {
                ShellActions.post(action)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val unhandledSearch = keyCode == KeyEvent.KEYCODE_K && event.isCtrlPressed
        val unhandledBack = keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.isAltPressed
        if ((unhandledSearch || unhandledBack) && container.state.value is SessionState.Active) {
            ShellActions.post(if (unhandledSearch) ShellAction.Search else ShellAction.Back)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        val ctrl = KeyEvent.META_CTRL_ON
        data += KeyboardShortcutGroup(
            getString(R.string.app_name),
            listOf(
                KeyboardShortcutInfo("Search", KeyEvent.KEYCODE_P, ctrl),
                KeyboardShortcutInfo("New page", KeyEvent.KEYCODE_N, ctrl),
                KeyboardShortcutInfo("New database", KeyEvent.KEYCODE_N, ctrl or KeyEvent.META_SHIFT_ON),
                KeyboardShortcutInfo("Toggle sidebar", KeyEvent.KEYCODE_BACKSLASH, ctrl),
                KeyboardShortcutInfo("Go back", KeyEvent.KEYCODE_LEFT_BRACKET, ctrl),
                KeyboardShortcutInfo("Settings", KeyEvent.KEYCODE_COMMA, ctrl),
                KeyboardShortcutInfo("Toggle dark mode", KeyEvent.KEYCODE_L, ctrl or KeyEvent.META_SHIFT_ON),
                KeyboardShortcutInfo("Home", KeyEvent.KEYCODE_1, ctrl),
                KeyboardShortcutInfo("Search tab", KeyEvent.KEYCODE_2, ctrl),
                KeyboardShortcutInfo("Library", KeyEvent.KEYCODE_3, ctrl),
                KeyboardShortcutInfo("Updates", KeyEvent.KEYCODE_4, ctrl),
            ),
        )
        data += KeyboardShortcutGroup(
            "Editing",
            listOf(
                KeyboardShortcutInfo("Bold", KeyEvent.KEYCODE_B, ctrl),
                KeyboardShortcutInfo("Italic", KeyEvent.KEYCODE_I, ctrl),
                KeyboardShortcutInfo("Underline", KeyEvent.KEYCODE_U, ctrl),
                KeyboardShortcutInfo("Strikethrough", KeyEvent.KEYCODE_X, ctrl or KeyEvent.META_SHIFT_ON),
                KeyboardShortcutInfo("Inline code", KeyEvent.KEYCODE_E, ctrl),
                KeyboardShortcutInfo("Add link", KeyEvent.KEYCODE_K, ctrl),
                KeyboardShortcutInfo("Find in page", KeyEvent.KEYCODE_F, ctrl),
            ),
        )
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
    }

    companion object {
        const val ACTION_NEW_PAGE = "com.manuscript.action.NEW_PAGE"
        const val ACTION_QUICK_NOTE = "com.manuscript.action.QUICK_NOTE"
        const val ACTION_SEARCH = "com.manuscript.action.SEARCH"
    }
}
