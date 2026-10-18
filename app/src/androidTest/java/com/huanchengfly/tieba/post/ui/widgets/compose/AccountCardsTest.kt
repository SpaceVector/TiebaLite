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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptionsBuilder
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.MessageListBean
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.AboutPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.AppThemePageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.HistoryPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SubPostsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadStorePageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.UserProfilePageDestination
import com.huanchengfly.tieba.post.ui.page.main.LocalMainNavigationBottomPadding
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.AtMeListViewModel
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.MessageItemData
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsListPage
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsListUiState
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsListViewModel
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsType
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.ReplyMeListViewModel
import com.huanchengfly.tieba.post.ui.page.main.user.UserPage
import com.huanchengfly.tieba.post.ui.page.main.user.UserUiState
import com.huanchengfly.tieba.post.ui.page.main.user.UserViewModel
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class AccountCardsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun notificationsKeepMessageQuoteAndAuthorNavigationInBothThemes() {
        val dark = mutableStateOf(false)
        val type = mutableStateOf(NotificationsType.ReplyMe)
        val navigator = RecordingNavigator()
        val first = MessageListBean.MessageInfoBean(
            isFloor = "0", title = "通知对应的主题", content = "收到一条新的回复。",
            replyer = MessageListBean.ReplyerInfoBean(id = "-17401901", nameShow = "消息作者一"),
            threadId = "-17401910", postId = "-17401911", time = "0",
        )
        val second = MessageListBean.MessageInfoBean(
            isFloor = "1", title = "提到你的主题", content = "楼中楼也有人回复了你。",
            quoteContent = "被引用的原回复", quotePid = "-17401923",
            replyer = MessageListBean.ReplyerInfoBean(id = "-17401902", nameShow = "消息作者二"),
            threadId = "-17401920", postId = "-17401921", time = "0",
        )
        val initial = NotificationsListUiState(
            isRefreshing = false, hasMore = false,
            data = persistentListOf(MessageItemData(first, false), MessageItemData(second, false)),
        )
        val models = mapOf<NotificationsType, NotificationsListViewModel>(
            NotificationsType.ReplyMe to ReplyMeListViewModel().apply { initialized = true },
            NotificationsType.AtMe to AtMeListViewModel().apply { initialized = true },
        )
        val states = models.mapValues { (_, model) -> seed(model, initial) }
        compose.setContent {
            Preview(dark.value, navigator) {
                NotificationsListPage(type.value, models.getValue(type.value))
            }
        }
        val measurements = mutableListOf<String>()
        for (variant in NotificationsType.values()) {
            for (night in listOf(false, true)) {
                compose.runOnIdle { type.value = variant; dark.value = night }
                val mode = "${variant.name}-${if (night) "dark" else "light"}"
                savePreview("notifications-$mode")
                val card = compose.onNodeWithText(first.content!!)
                val cardImage = card.captureToImage().asAndroidBitmap()
                val y = with(compose.density) { 2.dp.roundToPx() }
                assertEquals(colors(night).contentCardBackground.toArgb(), cardImage.getPixel(cardImage.width / 2, y))
                assertEquals(with(compose.density) { 336.dp.toPx() }, cardImage.width.toFloat(), 1f)
                val quote = compose.onNodeWithText(if (variant == NotificationsType.ReplyMe) second.quoteContent!! else second.title!!)
                val quoteImage = quote.captureToImage().asAndroidBitmap()
                val quoteFill = quoteImage.getPixel(quoteImage.width / 2, y)
                assertEquals(colors(night).contentInsetBackground.toArgb(), quoteFill)
                card.performClick()
                assertEquals(ThreadPageDestination(threadId = -17401910L, postId = -17401911L).route, navigator.routes.last())
                // UserHeader opens the profile from the avatar, not the nickname.
                card.performTouchInput {
                    click(Offset((16.dp + Sizes.Small / 2).toPx(), (12.dp + Sizes.Small / 2).toPx()))
                }
                assertEquals(UserProfilePageDestination(-17401901L).route, navigator.routes.last())
                compose.onNodeWithText(second.content!!).performClick()
                assertEquals(SubPostsPageDestination(threadId = -17401920L, subPostId = -17401921L, loadFromSubPost = true).route, navigator.routes.last())
                quote.performClick()
                assertEquals(SubPostsPageDestination(threadId = -17401920L, postId = -17401923L, loadFromSubPost = true).route, navigator.routes.last())
                measurements += "$mode: cardWidth=${cardImage.width}, cardFill=${colors(night).contentCardBackground.toArgb()}, quoteFill=$quoteFill; 4 navigation routes verified"
            }
        }
        compose.runOnIdle { states.values.forEach { it.value = initial.copy(data = persistentListOf()) } }
        compose.onNodeWithText(first.content!!).assertDoesNotExist()
        savePreview("notifications-empty-dark")
        writeMeasurements("notifications", measurements)
        assertEquals(16, navigator.routes.size)
    }

    @Test
    fun userCardsKeepProfileMenusSwitchAndBottomClearanceInBothThemes() {
        val dark = mutableStateOf(false)
        val navigator = RecordingNavigator()
        val account = Account(
            uid = "-17401930", name = "卡片化体验者", intro = "个人信息与常用功能分区展示。",
            concernNum = "37", fansNum = "128", postNum = "256",
        )
        val model = UserViewModel()
        val initial = UserUiState(account = account)
        seed(model, initial)
        compose.setContent { Preview(dark.value, navigator) { UserPage(model) } }
        val measurements = mutableListOf<String>()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10000f) }
            val mode = if (night) "dark" else "light"
            savePreview("user-$mode")
            val profile = compose.onNodeWithText(account.name)
            val bitmap = profile.captureToImage().asAndroidBitmap()
            val y = with(compose.density) { 2.dp.roundToPx() }
            assertEquals(colors(night).contentCardBackground.toArgb(), bitmap.getPixel(bitmap.width / 2, y))
            compose.onNodeWithText("37").assertIsDisplayed()
            compose.onNodeWithText("128").assertIsDisplayed()
            compose.onNodeWithText("256").assertIsDisplayed()
            compose.onNode(isToggleable()).assertIsDisplayed()
            profile.performClick()
            assertEquals(UserProfilePageDestination(account.uid.toLong()).route, navigator.routes.last())
            val menus = listOf(
                R.string.title_my_collect to ThreadStorePageDestination.route,
                R.string.title_history to HistoryPageDestination.route,
                R.string.title_theme to AppThemePageDestination.route,
                R.string.my_info_settings to SettingsPageDestination.route,
                R.string.my_info_about to AboutPageDestination.route,
            )
            for ((label, route) in menus) {
                compose.onNodeWithText(context.getString(label)).performScrollTo().performClick()
                assertEquals(route, navigator.routes.last())
            }
            compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
            val about = compose.onNodeWithText(context.getString(R.string.my_info_about)).fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
            val clearance = viewport.bottom - about.bottom
            assertTrue("Last menu must clear the floating navigation area", clearance >= with(compose.density) { 80.dp.toPx() } - 1f)
            measurements += "$mode: profileFill=${colors(night).contentCardBackground.toArgb()}, bottomClearancePx=$clearance; 6 navigation routes verified"
        }
        writeMeasurements("user", measurements)
        assertEquals(12, navigator.routes.size)
        assertEquals(initial, model.uiState.value)
    }

    @Test
    fun userLoggedOutAndLoadingCardsKeepMenusInBothThemes() {
        val dark = mutableStateOf(false)
        val model = UserViewModel()
        val state = seed(model, UserUiState())
        compose.setContent { Preview(dark.value) { UserPage(model) } }
        val measurements = mutableListOf<String>()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; state.value = UserUiState() }
            val mode = if (night) "dark" else "light"
            compose.onNodeWithText(context.getString(R.string.tip_login)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.title_my_collect)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.my_info_service_center)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.title_history)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.my_info_settings)).assertIsDisplayed()
            savePreview("user-logged-out-$mode")
            compose.runOnIdle { state.value = UserUiState(isLoading = true) }
            compose.onNodeWithText(context.getString(R.string.tip_login)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.text_stat_fans)).assertIsDisplayed()
            savePreview("user-loading-$mode")
            val fans = compose.onNodeWithText(context.getString(R.string.text_stat_fans)).fetchSemanticsNode().boundsInRoot
            val preview = compose.onNodeWithTag("preview")
            val bounds = preview.fetchSemanticsNode().boundsInRoot
            val bitmap = preview.captureToImage().asAndroidBitmap()
            val fill = bitmap.getPixel((fans.center.x - bounds.left).toInt(), (fans.center.y - bounds.top).toInt())
            assertEquals(colors(night).contentInsetBackground.toArgb(), fill)
            measurements += "$mode: loadingStatsFill=$fill, matches inset background"
        }
        writeMeasurements("loading", measurements)
    }

    private fun <S : UiState> seed(model: BaseViewModel<*, *, *, *>, state: S): MutableStateFlow<S> {
        val flow = MutableStateFlow(state)
        BaseViewModel::class.java.getDeclaredField("uiState").apply {
            isAccessible = true
            set(model, flow)
        }
        return flow
    }

    private class RecordingNavigator : DestinationsNavigator by EmptyDestinationsNavigator {
        val routes = mutableListOf<String>()
        override fun navigate(direction: Direction, onlyIfResumed: Boolean, builder: NavOptionsBuilder.() -> Unit) {
            routes += direction.route
        }
    }

    @Composable
    private fun Preview(dark: Boolean, navigator: DestinationsNavigator = EmptyDestinationsNavigator, content: @Composable () -> Unit) {
        val palette = colors(dark)
        CompositionLocalProvider(
            LocalExtendedColors provides palette, LocalAccount provides null,
            LocalMainNavigationBottomPadding provides 80.dp,
        ) {
            MaterialTheme(colors = getColorPalette(dark, palette)) {
                ProvideNavigator(navigator) {
                    ProvideContentColor(palette.text) {
                        Box(Modifier.width(360.dp).height(720.dp).background(palette.contentPageBackground).testTag("preview")) { content() }
                    }
                }
            }
        }
    }

    private fun savePreview(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        SystemClock.sleep(800)
        val bitmap = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "account-notifications-cards-v6").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun writeMeasurements(name: String, values: List<String>) {
        File(context.getExternalFilesDir(null), "account-notifications-cards-v6/$name-measurements.txt")
            .writeText(values.joinToString("\n", postfix = "\n"))
    }

    private fun colors(dark: Boolean): ExtendedColors {
        val text = if (dark) Color(0xFFEAEAEA) else Color(0xFF202020)
        val background = if (dark) Color(0xFF101010) else Color.White
        val card = if (dark) Color(0xFF202020) else Color.White
        val inset = if (dark) Color(0xFF2A2A2A) else Color(0xFFF0F1F3)
        return ExtendedColors(
            theme = "test", isNightMode = dark,
            primary = Color(0xFF16A99B), accent = Color(0xFF16A99B), onPrimary = Color.White, onAccent = Color.White,
            text = text, textSecondary = text.copy(alpha = 0.65f), textDisabled = text.copy(alpha = 0.35f),
            background = background, card = card, floorCard = inset, chip = inset, onChip = text,
            topBar = background, topBarSurface = inset, onTopBar = text, onTopBarSurface = text,
            onTopBarSecondary = text, onTopBarActive = text, bottomBar = background,
            bottomBarSurface = inset, onBottomBarSurface = text, windowBackground = background,
            divider = inset, shadow = Color.Transparent, placeholder = inset, indicator = inset, unselected = text,
        )
    }
}
