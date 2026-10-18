package com.huanchengfly.tieba.post.ui.widgets.compose

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavOptionsBuilder
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.MessageListBean
import com.huanchengfly.tieba.post.api.models.protos.Agree
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.ui.common.TextContentRender
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.ListPref
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.AboutPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.BlockListPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SubPostsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadPageDestination
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.MessageItemData
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsListPage
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsListUiState
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsType
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.ReplyMeListViewModel
import com.huanchengfly.tieba.post.ui.page.settings.about.AboutPage
import com.huanchengfly.tieba.post.ui.page.settings.account.AccountManagePage
import com.huanchengfly.tieba.post.ui.page.settings.block.BlockSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.block.blocklist.BlockListPage
import com.huanchengfly.tieba.post.ui.page.settings.block.blocklist.BlockListUiState
import com.huanchengfly.tieba.post.ui.page.settings.block.blocklist.BlockListViewModel
import com.huanchengfly.tieba.post.ui.page.settings.custom.CustomSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.habit.HabitSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.more.MoreSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.oksign.OKSignSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.theme.AppThemePage
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostItemData
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsPage
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsUiState
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsViewModel
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** V11 only: actual settings children and the concrete notification's standalone floor page. */
class CardDetailsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private var reportDirectoryName = "card-details-v11"

    @After
    fun clearFixtures() = compose.runOnUiThread { owner.viewModelStore.clear() }

    @Test
    fun settingsChildrenUseCardsInBothThemesWithoutChangingPreferences() {
        val before = runBlocking { context.dataStore.data.first().asMap() }
        val dark = mutableStateOf(false)
        val page = mutableStateOf(0)
        val navigator = RecordingNavigator()
        val blocks = BlockListViewModel()
        val initial = BlockListUiState(isLoading = false,
            blackList = listOf(Block(category = Block.CATEGORY_BLACK_LIST, keywords = "[\"黑名单预览\"]")),
            whiteList = listOf(Block(category = Block.CATEGORY_WHITE_LIST, keywords = "[\"白名单预览\"]")))
        seed(blocks, initial)
        val titles = listOf(R.string.title_block_settings, R.string.title_settings_custom,
            R.string.title_settings_read_habit, R.string.title_oksign, R.string.title_settings_more,
            R.string.title_account_manage, R.string.title_block_list, R.string.title_theme, R.string.title_about)
        val first = listOf(R.string.title_block_list, R.string.title_custom_font_size,
            R.string.title_settings_image_load_type, R.string.title_oksign_slow_mode,
            R.string.title_use_webview, R.string.title_new_account)
        val last = listOf(R.string.settings_block_video, R.string.title_lift_up_bottom_bar,
            R.string.title_do_not_use_photo_picker, R.string.title_ignore_battery_optimization,
            R.string.title_about)
        compose.setContent {
            Preview(dark.value, navigator, if (page.value == 5) 420 else 720) {
                key(page.value, dark.value) {
                    when (page.value) {
                        0 -> BlockSettingsPage(navigator)
                        1 -> CustomSettingsPage(navigator)
                        2 -> HabitSettingsPage(navigator)
                        3 -> OKSignSettingsPage(navigator)
                        4 -> MoreSettingsPage(navigator)
                        5 -> AccountManagePage(navigator)
                        6 -> BlockListPage(blocks, navigator)
                        7 -> AppThemePage(navigator)
                        8 -> AboutPage(navigator)
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            for (index in titles.indices) {
                compose.runOnIdle { page.value = index }
                // Scroll past the user-configurable wallpaper tile before capturing theme previews.
                if (index == 7) verticalList().performScrollToIndex(2)
                val image = save("settings-$index-${mode(night)}")
                verifyCard(context.getString(titles[index]), night, image)
                when (index) {
                    in 0..5 -> {
                        verifyCard(context.getString(first[index]), night, image)
                        if (index < last.size) {
                            val label = context.getString(last[index])
                            verticalList().performScrollToNode(hasText(label))
                            verifyCard(label, night, save("settings-$index-${mode(night)}-last"))
                        }
                        if (index == 0) {
                            verticalList().performScrollToIndex(0)
                            compose.onNodeWithText(context.getString(R.string.title_block_list)).performClick()
                            assertEquals(BlockListPageDestination.route, navigator.routes.last())
                        }
                        if (index == 4) {
                            compose.onNodeWithText(context.getString(R.string.title_about)).performClick()
                            assertEquals(AboutPageDestination.route, navigator.routes.last())
                        }
                        if (index == 5) compose.onNodeWithText(context.getString(R.string.title_switch_account)).assertHasNoClickAction()
                    }
                    6 -> {
                        verifyCard("黑名单预览", night, image)
                        verifyCard(context.getString(R.string.title_add_black), night, image)
                        compose.onNodeWithText(context.getString(R.string.title_white_list)).performClick().assertIsSelected()
                        verifyCard("白名单预览", night, save("settings-whitelist-${mode(night)}"))
                    }
                    7 -> verifyCard(context.resources.getStringArray(R.array.themeNames)[0], night, image)
                    8 -> verifyCard(context.getString(R.string.source_code), night, image)
                }
                compose.onNodeWithContentDescription(context.getString(R.string.button_back)).performClick()
            }
        }
        assertEquals(18, navigator.backs)
        assertEquals(initial, blocks.uiState.value)
        assertEquals("Settings preview must not write user preferences", before, runBlocking { context.dataStore.data.first().asMap() })
        record("settings", "Both themes: six actual preference screens, last items, nested black/white list, theme rows, about; routes/back preserved; no preference, account, cache, icon or theme changes.")
    }

    @Test
    fun concreteReplyOpensCardedFloorPageAndCountScrollsAway() {
        val dark = mutableStateOf(false)
        val detail = mutableStateOf(false)
        val navigator = RecordingNavigator()
        val messages = ReplyMeListViewModel().apply { initialized = true }
        val messageState = NotificationsListUiState(isRefreshing = false, hasMore = false,
            data = persistentListOf(MessageItemData(MessageListBean.MessageInfoBean(
                isFloor = "1", threadId = "-17402701", postId = "-17402703", content = "具体回复入口", time = "0"), blocked = false)))
        seed(messages, messageState)
        val model = SubPostsViewModel().apply { initialized = true }
        val author = User(id = -17402704L, nameShow = "主楼作者", level_id = 8)
        val initial = SubPostsUiState(hasMore = false, totalCount = 12,
            forum = SimpleForum(id = -17402705L, name = "卡片预览").wrapImmutable(),
            thread = ThreadInfo(id = -17402701L, author = author).wrapImmutable(),
            post = Post(id = -17402702L, floor = 7, author = author).wrapImmutable(),
            postContentRenders = persistentListOf(TextContentRender("主楼正文预览")),
            subPosts = (1..12).map { index -> SubPostItemData(
                subPost = SubPostList(id = -17402703L - index,
                    author = User(id = -17402800L - index, nameShow = "回复作者$index", level_id = index),
                    agree = Agree(diffAgreeNum = 10L + index)).wrapImmutable(),
                subPostContentRenders = persistentListOf(TextContentRender("楼中楼回复$index：" + "内容随列表滚动。".repeat(6))),
                blocked = false,
            ) }.toImmutableList())
        seed(model, initial)
        compose.setContent {
            Preview(dark.value, navigator) {
                key(detail.value, dark.value) {
                    if (detail.value) SubPostsPage(navigator, threadId = -17402701L, postId = -17402702L,
                        subPostId = -17402703L, loadFromSubPost = true, viewModel = model)
                    else NotificationsListPage(NotificationsType.ReplyMe, messages)
                }
            }
        }
        val title = context.getString(R.string.title_sub_posts, 7)
        val count = context.getString(R.string.title_sub_posts_header, 12)
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; detail.value = false }
            compose.onNodeWithText("具体回复入口").performClick()
            assertEquals(SubPostsPageDestination(threadId = -17402701L, subPostId = -17402703L, loadFromSubPost = true).route, navigator.routes.last())
            compose.runOnIdle { detail.value = true }
            val image = save("message-floor-${mode(night)}")
            for (label in listOf(title, "主楼正文预览", count)) verifyCard(label, night, image)
            verifyCard("回复作者1", night, image, substring = true)
            // Likes belong to the same surface, not a legacy opaque button patch.
            verifyCard("11", night, image)
            verticalList().performScrollToIndex(8)
            compose.onNodeWithText(count).assertIsNotDisplayed()
            compose.onNodeWithText(title).assertIsDisplayed()
            save("message-floor-${mode(night)}-scrolled")
            compose.onNodeWithContentDescription(context.getString(R.string.btn_open_origin_thread)).performClick()
            assertEquals(ThreadPageDestination(forumId = 0L, threadId = -17402701L, postId = -17402702L).route, navigator.routes.last())
            compose.onNodeWithContentDescription(context.getString(R.string.btn_close)).performClick()
        }
        assertEquals(2, navigator.backs)
        assertEquals(initial, model.uiState.value)
        assertEquals(messageState, messages.uiState.value)
        record("message-floor", "Both themes: actual concrete message route, standalone floor header, parent/reply cards, integrated likes; reply count scrolls away; origin/back preserved; no APIs or reply/IME actions.")
    }

    @OptIn(androidx.compose.material.ExperimentalMaterialApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test
    fun imageLoadingChoiceDialogMatchesCardsAndKeepsSelectionAndCancel() {
        reportDirectoryName = "settings-choice-cards-v12"
        val before = runBlocking { context.dataStore.data.first().asMap() }
        val dark = mutableStateOf(false)
        val scene = mutableStateOf(0)
        val navigator = RecordingNavigator()
        val job = SupervisorJob()
        val file = File(context.cacheDir, "v12-choice-${SystemClock.uptimeMillis()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val preferenceKey = stringPreferencesKey("image_load_type")
        val title = context.getString(R.string.title_settings_image_load_type)
        val entries = linkedMapOf("0" to context.getString(R.string.title_image_load_type_smart_origin),
            "1" to context.getString(R.string.title_image_load_type_smart_load),
            "2" to context.getString(R.string.title_image_load_type_all_origin),
            "3" to context.getString(R.string.title_image_load_type_all_no))
        val changes = mutableListOf<String>()
        try {
            compose.setContent {
                Preview(dark.value, navigator) {
                    key(scene.value, dark.value) {
                        when (scene.value) {
                            0 -> HabitSettingsPage(navigator)
                            1 -> PrefsScreen(store, dividerThickness = 0.dp) {
                                prefsItem { ListPref(key = "image_load_type", title = title, entries = entries,
                                    defaultValue = "0", useSelectedAsSummary = true, onValueChange = { changes += it }) }
                            }
                        }
                    }
                }
            }
            for (night in listOf(false, true)) {
                compose.runOnIdle { scene.value = 0; dark.value = night }
                compose.onNodeWithText(title).performClick()
                val actual = runBlocking { context.dataStore.data.first()[preferenceKey] } ?: "0"
                verifyChoiceDialog(night, title, entries, actual, "image-load-${mode(night)}")
                dialogText(context.getString(R.string.button_cancel)).performClick()
                awaitChoiceDismissed()
                compose.onNodeWithText(title).performClick()
                pressBack()
                awaitChoiceDismissed()
                assertTrue("Actual user preferences must not change on cancel/back", before == runBlocking { context.dataStore.data.first().asMap() })

                // Exercise writes only in a temporary DataStore, never the user's image-loading preference.
                runBlocking { store.edit { it[preferenceKey] = "0" } }
                compose.runOnIdle { scene.value = 1 }
                compose.onNodeWithText(title).performClick()
                dialogText(entries.getValue("2")).performClick()
                awaitChoiceDismissed()
                compose.waitUntil(3000) { runBlocking { store.data.first()[preferenceKey] } == "2" }
                compose.onNodeWithText(entries.getValue("2")).assertIsDisplayed()
                compose.onNodeWithText(title).performClick()
                verifyChoiceDialog(night, title, entries, "2", "image-load-selected-${mode(night)}")
                dialogText(context.getString(R.string.button_cancel)).performClick()
                awaitChoiceDismissed()
                assertEquals("2", runBlocking { store.data.first()[preferenceKey] })
            }
            assertEquals(listOf("2", "2"), changes)
            assertTrue("Actual user preferences remain unchanged", before == runBlocking { context.dataStore.data.first().asMap() })
            record("image-load", "Both themes: actual settings -> image loading card dialog, four rounded choices, readable selected/unselected text, >=48dp targets; cancel/back preserve actual preferences. Isolated temporary DataStore: select -> write correct key -> update summary -> dismiss -> reopen checked choice; no real theme/icon/account/image preference writes.")
        } finally {
            compose.runOnIdle { scene.value = 2 }
            compose.waitForIdle()
            job.cancel()
            runBlocking { job.join() }
            file.delete()
            assertTrue("Temporary preference fixture cleaned", !file.exists())
        }
    }

    private fun dialogText(label: String, merged: Boolean = false) =
        compose.onNode(hasText(label) and hasAnyAncestor(isDialog()), useUnmergedTree = !merged)

    private fun awaitChoiceDismissed() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(3000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }

    private fun verifyChoiceDialog(dark: Boolean, title: String, entries: Map<String, String>, selected: String, name: String) {
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle(); SystemClock.sleep(500)
        val root = compose.onNode(isDialog())
        val image = root.captureToImage().asAndroidBitmap()
        File(outputDirectory(), "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val origin = root.fetchSemanticsNode().boundsInRoot
        val colors = colors(dark)
        for ((value, label) in mapOf("title" to title) + entries) {
            val node = dialogText(label).assertIsDisplayed()
            val y = (node.fetchSemanticsNode().boundsInRoot.center.y - origin.top).toInt()
            assertEquals("Card panel fill", colors.contentCardBackground.toArgb(), image.getPixel(px(20f).toInt(), y))
            val background = if (value == "title") colors.contentCardBackground
                else if (value == selected) colors.text.copy(alpha = 0.1f).compositeOver(colors.contentInsetBackground)
                else colors.contentInsetBackground
            if (value != "title") {
                val actual = image.getPixel(px(32f).toInt(), y)
                for (shift in listOf(0, 8, 16, 24)) assertTrue("$label option fill", kotlin.math.abs(((actual ushr shift) and 255) - ((background.toArgb() ushr shift) and 255)) <= 1)
                assertTrue("$label touch target >=48dp", dialogText(label, merged = true).fetchSemanticsNode().boundsInRoot.height >= px(48f) - 1)
            }
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val foreground = layouts.single().layoutInput.style.color.compositeOver(background)
            val contrast = (maxOf(foreground.luminance(), background.luminance()) + 0.05f) / (minOf(foreground.luminance(), background.luminance()) + 0.05f)
            assertTrue("$label contrast=$contrast", contrast >= 4.5f)
            File(outputDirectory(), "dialog-pixel-measurements.txt").appendText("${mode(dark)} $name; label=$label; contrast=$contrast; minContrast=4.5\n")
        }
        val checked = compose.onNodeWithContentDescription(context.getString(R.string.desc_checked)).fetchSemanticsNode().boundsInRoot
        val selectedBounds = dialogText(entries.getValue(selected), merged = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Checkmark belongs to persisted selection", checked.center.y in selectedBounds.top..selectedBounds.bottom)
    }

    private fun <S : UiState> seed(model: BaseViewModel<*, *, *, *>, state: S) {
        BaseViewModel::class.java.getDeclaredField("uiState").apply { isAccessible = true; set(model, MutableStateFlow(state)) }
        compose.runOnUiThread { owner.viewModelStore.put(model.javaClass.name, model) }
    }

    private fun verticalList() = compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
    private fun mode(dark: Boolean) = if (dark) "dark" else "light"
    private fun px(dp: Float) = with(compose.density) { dp.dp.toPx() }
    private fun outputDirectory() = File(context.getExternalFilesDir(null), reportDirectoryName).apply { mkdirs() }
    private fun record(name: String, text: String) = File(outputDirectory(), "$name-measurements.txt").writeText(text + "\n")
    private fun save(name: String): Bitmap {
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle(); SystemClock.sleep(500)
        val bitmap = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        File(outputDirectory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun verifyCard(label: String, dark: Boolean, bitmap: Bitmap, substring: Boolean = false) {
        val node = compose.onNodeWithText(label, substring = substring, useUnmergedTree = true).assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val origin = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        val y = (bounds.center.y - origin.top).toInt()
        val fill = bitmap.getPixel(px(24f).toInt(), y)
        val gutter = bitmap.getPixel(px(2f).toInt(), y)
        assertEquals("$label card fill", colors(dark).contentCardBackground.toArgb(), fill)
        assertEquals("$label page gutter", colors(dark).contentPageBackground.toArgb(), gutter)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foreground = layouts.single().layoutInput.style.color.compositeOver(colors(dark).contentCardBackground)
        val background = colors(dark).contentCardBackground
        val contrast = (maxOf(foreground.luminance(), background.luminance()) + 0.05f) /
            (minOf(foreground.luminance(), background.luminance()) + 0.05f)
        val enabled = !compose.onNodeWithText(label, substring = substring).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        if (enabled) assertTrue("$label contrast=$contrast", contrast >= 4.5f)
        File(outputDirectory(), "pixel-measurements.txt").appendText("${mode(dark)} label=$label; card=$fill; gutter=$gutter; foreground=${foreground.toArgb()}; contrast=$contrast; enabled=$enabled; enabledMinContrast=4.5\n")
    }

    private class RecordingNavigator : DestinationsNavigator by EmptyDestinationsNavigator {
        val routes = mutableListOf<String>()
        var backs = 0
        override fun navigate(direction: Direction, onlyIfResumed: Boolean, builder: NavOptionsBuilder.() -> Unit) { routes += direction.route }
        override fun navigateUp(): Boolean { backs++; return true }
    }

    @Composable
    private fun Preview(dark: Boolean, navigator: DestinationsNavigator, height: Int = 720, content: @Composable () -> Unit) {
        val palette = colors(dark)
        CompositionLocalProvider(LocalExtendedColors provides palette, LocalAccount provides null, LocalViewModelStoreOwner provides owner) {
            MaterialTheme(colors = getColorPalette(dark, palette)) {
                ProvideNavigator(navigator) {
                    ProvideContentColor(palette.text) {
                        Box(Modifier.width(400.dp).height(height.dp).background(palette.contentPageBackground).testTag("preview")) { content() }
                    }
                }
            }
        }
    }

    private fun colors(dark: Boolean): ExtendedColors {
        val text = if (dark) Color(0xFFEAEAEA) else Color(0xFF202020)
        val background = if (dark) Color(0xFF101010) else Color.White
        val card = if (dark) Color(0xFF202020) else Color.White
        val inset = if (dark) Color(0xFF2A2A2A) else Color(0xFFF0F1F3)
        return ExtendedColors(theme = "test", isNightMode = dark,
            primary = Color(0xFF16A99B), accent = Color(0xFF16A99B), onPrimary = Color.White, onAccent = Color.White,
            text = text, textSecondary = text.copy(alpha = 0.65f), textDisabled = text.copy(alpha = 0.35f),
            background = background, card = card, floorCard = inset, chip = inset, onChip = text,
            topBar = Color(0xFF087F78), topBarSurface = inset, onTopBar = Color.White, onTopBarSurface = Color.White,
            onTopBarSecondary = Color.White, onTopBarActive = Color.White, bottomBar = background,
            bottomBarSurface = inset, onBottomBarSurface = text, windowBackground = background,
            divider = inset, shadow = Color.Transparent, placeholder = inset, indicator = inset, unselected = text)
    }
}
