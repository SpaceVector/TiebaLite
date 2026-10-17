package com.huanchengfly.tieba.post.ui.page.reply

import android.graphics.Bitmap
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as NativeDialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.google.accompanist.navigation.material.bottomSheet
import com.huanchengfly.tieba.post.LocalDestination
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.models.database.Draft
import com.huanchengfly.tieba.post.rememberBottomSheetNavigator
import com.huanchengfly.tieba.post.toMD5
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.page.destinations.ReplyPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SubPostsSheetPageDestination
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostItemData
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsContent
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsUiState
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsViewModel
import com.huanchengfly.tieba.post.ui.widgets.compose.BaseDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.FloatingReplyBar
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalFloatingSheetBackdrop
import com.huanchengfly.tieba.post.ui.widgets.compose.ProvideContentColor
import com.huanchengfly.tieba.post.ui.widgets.compose.glassBackdropSource
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberGlassBackdrop
import com.huanchengfly.tieba.post.ui.widgets.compose.dialogs.getDialogWindow
import com.huanchengfly.tieba.post.utils.EmoticonManager
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.litepal.LitePal
import java.io.File

class FloatingReplyComposerTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun threadEditorKeepsTextDraftAndToolsInsideFloatingGlass() {
        val dark = mutableStateOf(true)
        val visible = mutableStateOf(true)
        val model = ReplyViewModel()
        val threadId = -17401701L
        val hash = "${threadId}_null_null".toMD5()
        LitePal.deleteAll(Draft::class.java, "hash = ?", hash)
        try {
            Log.i("FloatingReplyTest", "thread editor: set content")
            compose.setContent {
                Preview(dark.value) {
                    if (visible.value) ThreadReplySheet(model, threadId)
                }
            }
            compose.onNodeWithText(label(R.string.tip_reply_thread)).performClick()
            dismissWarningIfPresent()
            Log.i("FloatingReplyTest", "thread editor: warning dismissed")
            assertKeyboardShownWithoutEditorTap("thread")
            val send = compose.onNodeWithContentDescription(label(R.string.send_reply))
            send.assertIsNotEnabled()
            onView(withId(R.id.edit_text)).perform(replaceText("仅用于界面验证，不发送"))
            send.assertIsEnabled()
            compose.waitUntil(5000) { draft(hash)?.content == "仅用于界面验证，不发送" }
            savePreview("reply-editor-dark")
            Log.i("FloatingReplyTest", "thread editor: draft and dark preview verified")
            var insertionPoint = 0
            onView(withId(R.id.edit_text)).check { view, error ->
                if (error != null) throw error
                insertionPoint = (view as EditText).selectionStart
            }

            compose.onNodeWithContentDescription(label(R.string.insert_emotions)).performClick()
            compose.waitUntil(3000) { model.uiState.value.replyPanelType == ReplyPanelType.EMOJI }
            assertKeyboardHiddenForPanel()
            compose.onNodeWithContentDescription(label(R.string.insert_emotions)).performClick()
            assertKeyboardShownWithoutEditorTap("thread-from-emoji")
            compose.onNodeWithContentDescription(label(R.string.insert_emotions)).performClick()
            compose.waitUntil(3000) { model.uiState.value.replyPanelType == ReplyPanelType.EMOJI }
            val emoticon = EmoticonManager.getAllEmoticon().first { it.name.isNotEmpty() }
            compose.onNodeWithContentDescription(context.getString(R.string.emoticon, emoticon.name)).performClick()
            val initialText = "仅用于界面验证，不发送"
            val expected = initialText.substring(0, insertionPoint) + "#(${emoticon.name})" + initialText.substring(insertionPoint)
            onView(withId(R.id.edit_text)).check(matches(withText(expected)))
            savePreview("reply-emoji-dark")

            compose.onNodeWithContentDescription(label(R.string.insert_photo)).performClick()
            compose.onNodeWithText(label(R.string.origin_image)).assertIsDisplayed()
            compose.runOnIdle { dark.value = false }
            savePreview("reply-image-light")
            Log.i("FloatingReplyTest", "thread editor: emoji and image tools verified")
            Espresso.pressBack()
            compose.waitUntil(3000) { model.uiState.value.replyPanelType == ReplyPanelType.NONE }
            onView(withId(R.id.edit_text)).check(matches(withText(expected)))
            compose.waitUntil(5000) { draft(hash)?.content == expected }
            compose.onNodeWithContentDescription(label(R.string.btn_close)).performClick()
            compose.onNodeWithContentDescription(label(R.string.send_reply)).assertDoesNotExist()
            assertEquals(expected, draft(hash)?.content)
            compose.onNodeWithText(label(R.string.tip_reply_thread)).performClick()
            dismissWarningIfPresent()
            assertKeyboardShownWithoutEditorTap("thread-reopen")
            onView(withId(R.id.edit_text)).check(matches(withText(expected)))
            compose.onNodeWithContentDescription(label(R.string.btn_close)).performClick()
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            LitePal.deleteAll(Draft::class.java, "hash = ?", hash)
        }
    }

    @Test
    fun subPostDialogReusesFloatingEditorAndKeepsReplyTarget() {
        val visible = mutableStateOf(true)
        val showEditor = mutableStateOf(false)
        val threadId = -17401702L
        val postId = -2L
        val subPostId = -3L
        val hash = "${threadId}_${postId}_${subPostId}".toMD5()
        val model = ReplyViewModel()
        LitePal.deleteAll(Draft::class.java, "hash = ?", hash)
        try {
            Log.i("FloatingReplyTest", "subpost dialog: set content")
            compose.setContent {
                Preview(false) {
                    if (visible.value) FloatingReplyBar(onClick = { showEditor.value = true })
                    if (visible.value && showEditor.value) {
                        val dialog = rememberDialogState()
                        androidx.compose.runtime.LaunchedEffect(Unit) { dialog.show() }
                        BaseDialog(dialogState = dialog, imePadding = false) {
                            Box(Modifier.testTag("dialogEditor")) {
                                ReplyPageContent(
                                    viewModel = model, onBack = { showEditor.value = false },
                                    forumId = -1L, forumName = "界面测试", threadId = threadId,
                                    postId = postId, subPostId = subPostId, replyUserName = "测试用户",
                                    tbs = "TEST", isDialog = true,
                                )
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText(label(R.string.tip_reply_thread)).performClick()
            dismissWarningIfPresent()
            Log.i("FloatingReplyTest", "subpost dialog: warning dismissed")
            assertKeyboardShownWithoutEditorTap("subpost")
            compose.onNodeWithContentDescription(label(R.string.insert_photo)).assertDoesNotExist()
            onView(withId(R.id.edit_text)).check(matches(withHint(context.getString(R.string.hint_reply, "测试用户"))))
            onView(withId(R.id.edit_text)).perform(replaceText("楼中楼回复样式验证"))
            compose.onNodeWithContentDescription(label(R.string.send_reply)).assertIsEnabled()
            savePreview("subpost-reply-light", "dialogEditor")
            Log.i("FloatingReplyTest", "subpost dialog: target, text and preview verified")
            compose.onNodeWithContentDescription(label(R.string.btn_close)).performClick()
            compose.onNodeWithContentDescription(label(R.string.send_reply)).assertDoesNotExist()
            compose.onNodeWithText(label(R.string.tip_reply_thread)).performClick()
            dismissWarningIfPresent()
            assertKeyboardShownWithoutEditorTap("subpost-reopen")
            compose.onNodeWithContentDescription(label(R.string.btn_close)).performClick()
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            LitePal.deleteAll(Draft::class.java, "hash = ?", hash)
        }
    }

    @OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class)
    @Test
    fun subPostsSheetReplyBarShowsKeyboardWithoutEditorTap() {
        val visible = mutableStateOf(true)
        val threadId = -17401801L
        val postId = -17401802L
        val hash = "${threadId}_${postId}_null".toMD5()
        val model = SubPostsViewModel().apply { initialized = true }
        // Seed only the screen's data. Keep its real reply callback, dialog and editor;
        // no account persistence, network load or send is needed for this regression.
        BaseViewModel::class.java.getDeclaredField("uiState").apply {
            isAccessible = true
            set(model, MutableStateFlow(SubPostsUiState(
                forum = SimpleForum(id = -1L, name = "界面测试").wrapImmutable(),
                subPosts = persistentListOf(SubPostItemData(
                    subPost = SubPostList(id = -17401803L).wrapImmutable(),
                    subPostContentRenders = persistentListOf(),
                    blocked = false,
                )),
                totalCount = 1,
                hasMore = false,
            )))
        }
        LitePal.deleteAll(Draft::class.java, "hash = ?", hash)
        try {
            compose.setContent {
                Preview(true) {
                    if (visible.value) {
                        val storeOwner = remember {
                            object : ViewModelStoreOwner {
                                override val viewModelStore = ViewModelStore()
                            }
                        }
                        DisposableEffect(storeOwner) {
                            onDispose { storeOwner.viewModelStore.clear() }
                        }
                        val sheetNavigator = rememberBottomSheetNavigator(skipHalfExpanded = true)
                        val navController = rememberNavController(sheetNavigator)
                        ModalBottomSheetLayout(
                            bottomSheetNavigator = sheetNavigator,
                            sheetBackgroundColor = Color.Transparent,
                            sheetElevation = 0.dp,
                        ) {
                            NavHost(navController, startDestination = "thread", modifier = Modifier.fillMaxSize()) {
                                composable("thread") {
                                    Button(onClick = { navController.navigate("subposts") }) {
                                        Text("打开楼中楼")
                                    }
                                }
                                bottomSheet("subposts") {
                                    CompositionLocalProvider(
                                        LocalViewModelStoreOwner provides storeOwner,
                                        LocalAccount provides Account(uid = "-17401804", name = "界面测试"),
                                        LocalDestination provides SubPostsSheetPageDestination,
                                    ) {
                                        ProvideNavigator(EmptyDestinationsNavigator) {
                                            SubPostsContent(
                                                viewModel = model, forumId = -1L,
                                                threadId = threadId, postId = postId, isSheet = true,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText("打开楼中楼").performClick()
            compose.onNodeWithText(label(R.string.tip_reply_thread)).performClick()
            dismissWarningIfPresent()
            assertKeyboardShownWithoutEditorTap("subposts-sheet")
            compose.onNodeWithContentDescription(label(R.string.insert_photo)).assertDoesNotExist()
            compose.onNodeWithContentDescription(label(R.string.insert_emotions)).performClick()
            assertKeyboardHiddenForPanel()
            val close = hasContentDescription(label(R.string.btn_close)) and hasAnyAncestor(isDialog())
            compose.onNode(close).performClick()
            compose.onNodeWithContentDescription(label(R.string.send_reply)).assertDoesNotExist()
            compose.onNodeWithText(label(R.string.tip_reply_thread)).performClick()
            dismissWarningIfPresent()
            assertKeyboardShownWithoutEditorTap("subposts-sheet-reopen")
            compose.onNode(close).performClick()
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            LitePal.deleteAll(Draft::class.java, "hash = ?", hash)
        }
    }

    @OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class)
    @Composable
    private fun ThreadReplySheet(model: ReplyViewModel, threadId: Long) {
        val sheetNavigator = rememberBottomSheetNavigator(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            skipHalfExpanded = true,
        )
        val navController = rememberNavController(sheetNavigator)
        ModalBottomSheetLayout(
            bottomSheetNavigator = sheetNavigator,
            sheetBackgroundColor = Color.Transparent,
            sheetElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.18f),
        ) {
            NavHost(navController, startDestination = "thread", modifier = Modifier.fillMaxSize()) {
                composable("thread") {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        FloatingReplyBar(onClick = { navController.navigate("reply") })
                    }
                }
                bottomSheet("reply") {
                    ReplyPageContent(
                        viewModel = model, onBack = { navController.popBackStack() },
                        forumId = -1L, forumName = "界面测试", threadId = threadId, tbs = "TEST",
                    )
                }
            }
        }
    }

    @Test
    fun delayedDialogWindowFocusStillShowsKeyboardWithoutEditorTap() {
        val visible = mutableStateOf(true)
        val allowWindowFocus = mutableStateOf(false)
        var dialogWindow: Window? = null
        val model = ReplyViewModel()
        val threadId = -17401805L
        val hash = "${threadId}_-2_null".toMD5()
        LitePal.deleteAll(Draft::class.java, "hash = ?", hash)
        try {
            compose.setContent {
                Preview(true) {
                    if (visible.value) NativeDialog(
                        onDismissRequest = { visible.value = false },
                        properties = DialogProperties(decorFitsSystemWindows = false),
                    ) {
                        val window = getDialogWindow()
                        val focusAllowed = allowWindowFocus.value
                        SideEffect {
                            dialogWindow = window
                            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                            // Delay only this test dialog's focus until its editor has been attached.
                            // This models the real reply window receiving focus after composition.
                            if (focusAllowed) window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                            else window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            ReplyPageContent(
                                viewModel = model, onBack = { visible.value = false },
                                forumId = -1L, forumName = "界面测试", threadId = threadId,
                                postId = -2L, tbs = "TEST", isDialog = true,
                            )
                        }
                    }
                }
            }
            dismissWarningIfPresent()
            compose.runOnIdle {
                val editor = dialogWindow?.decorView?.findViewById<EditText>(R.id.edit_text)
                assertNotNull(editor)
                assertTrue(editor!!.isAttachedToWindow)
                Log.i("FloatingReplyTest", "delayed dialog before focus: attached=${editor.isAttachedToWindow} windowFocus=${editor.hasWindowFocus()} editorFocus=${editor.hasFocus()}")
                allowWindowFocus.value = true
            }
            compose.waitUntil(5000) {
                var focused = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    focused = dialogWindow?.decorView?.hasWindowFocus() == true
                }
                focused
            }
            assertKeyboardShownWithoutEditorTap("delayed-dialog")
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            LitePal.deleteAll(Draft::class.java, "hash = ?", hash)
        }
    }

    @Composable
    private fun Preview(dark: Boolean, content: @Composable () -> Unit) {
        val background = if (dark) Color(0xFF121212) else Color(0xFFF8F8F8)
        val text = if (dark) Color.White else Color(0xFF202020)
        val colors = ExtendedColors("test", dark, primary = Color(0xFF26B6A6), accent = Color(0xFF26B6A6),
            text = text, textSecondary = text.copy(alpha = 0.65f), background = background,
            windowBackground = background, bottomBar = background, divider = text.copy(alpha = 0.08f))
        MaterialTheme(colors = if (dark) darkColors() else lightColors()) {
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(LocalExtendedColors provides colors,
                LocalFloatingSheetBackdrop provides backdrop, LocalDestination provides ReplyPageDestination) {
                ProvideContentColor(text) {
                    Box(Modifier.fillMaxSize().background(background).testTag("preview")) {
                        Column(Modifier.fillMaxSize().glassBackdropSource(backdrop, background)) {
                            repeat(8) { index ->
                                Text("${index + 1} 楼 · 回复输入面板预览", color = text, modifier = Modifier.padding(16.dp))
                                Box(Modifier.fillMaxWidth().height(64.dp).background(
                                    if (index % 2 == 0) Color(0xFF26B6A6) else Color(0xFFE0A764)))
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) { content() }
                    }
                }
            }
        }
    }

    private fun dismissWarningIfPresent() {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label(R.string.btn_continue_reply))
        if (runCatching { node.fetchSemanticsNode() }.isSuccess) node.performClick()
        compose.waitForIdle()
    }

    private fun draft(hash: String) = LitePal.where("hash = ?", hash).findFirst(Draft::class.java)
    private fun assertKeyboardShownWithoutEditorTap(entry: String) {
        var editor: EditText? = null
        onView(withId(R.id.edit_text)).check { view, error ->
            if (error != null) throw error
            editor = view as EditText
        }
        var ready = false
        var details = ""
        try {
            compose.waitUntil(5000) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val view = editor!!
                    val ime = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    ready = view.hasFocus() && ime
                    details = "entry=$entry attached=${view.isAttachedToWindow} windowFocus=${view.hasWindowFocus()} editorFocus=${view.hasFocus()} imeVisible=$ime"
                }
                ready
            }
        } finally {
            Log.i("FloatingReplyTest", details)
            val directory = File(context.getExternalFilesDir(null), "reply-ime-autofocus").apply { mkdirs() }
            File(directory, "ime-$entry.txt").writeText(details)
        }
    }

    private fun assertKeyboardHiddenForPanel() {
        var editor: EditText? = null
        onView(withId(R.id.edit_text)).check { view, error ->
            if (error != null) throw error
            editor = view as EditText
        }
        compose.waitUntil(5000) {
            var hidden = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                hidden = ViewCompat.getRootWindowInsets(editor!!)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == false
            }
            hidden
        }
    }
    private fun label(id: Int) = context.getString(id)
    private fun savePreview(name: String, tag: String = "preview") {
        val directory = File(context.getExternalFilesDir(null), "thread-bottom-bar-expand").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
