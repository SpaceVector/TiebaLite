package com.huanchengfly.tieba.post.ui.page.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.AccountManagePageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.BlockSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.CustomSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.HabitSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.LoginPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.MoreSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.OKSignSettingsPageDestination
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.AvatarIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.ContentCardTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.TitleCentredToolbar
import com.huanchengfly.tieba.post.ui.widgets.compose.contentCard
import com.huanchengfly.tieba.post.ui.widgets.compose.contentPageBackground
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.huanchengfly.tieba.post.utils.StringUtil
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Composable
internal fun LeadingIcon(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides ExtendedTheme.colors.primary) {
        content()
        Spacer(modifier = Modifier.width(56.dp))
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NowAccountItem(
    account: Account?,
    modifier: Modifier = Modifier
) {
    val navigator = LocalNavigator.current
    if (account != null) {
        TextPref(
            title = stringResource(id = R.string.title_account_manage),
            summary = stringResource(id = R.string.summary_now_account, account.nameShow ?: account.name),
            enabled = true,
            onClick = { navigator.navigate(AccountManagePageDestination) },
            leadingIcon = {
                LeadingIcon {
                    Avatar(
                        data = StringUtil.getAvatarUrl(account.portrait),
                        size = Sizes.Small,
                        contentDescription = null
                    )
                }
            },
            modifier = modifier,
        )
    } else {
        TextPref(
            title = stringResource(id = R.string.title_account_manage),
            summary = stringResource(id = R.string.summary_not_logged_in),
            enabled = true,
            onClick = { navigator.navigate(LoginPageDestination) },
            leadingIcon = {
                LeadingIcon {
                    AvatarIcon(
                        icon = Icons.Rounded.AccountCircle,
                        size = Sizes.Small,
                        contentDescription = stringResource(id = R.string.title_new_account),
                        color = ExtendedTheme.colors.onChip,
                        backgroundColor = ExtendedTheme.colors.chip,
                    )
                }
            },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Destination
@Composable
fun SettingsPage(
    navigator: DestinationsNavigator,
) {
    ProvideNavigator(navigator = navigator) {
        Scaffold(
            backgroundColor = ExtendedTheme.colors.contentPageBackground,
            contentColor = ExtendedTheme.colors.text,
            topBar = {
                ContentCardTopBar {
                    TitleCentredToolbar(
                        insets = false,
                        title = {
                            Text(
                                text = stringResource(id = R.string.title_settings),
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6
                            )
                        },
                        navigationIcon = {
                            BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                        }
                    )
                }
            },
        ) { paddingValues ->
            Container {
                MyLazyColumn(
                    modifier = Modifier.padding(paddingValues).fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
                    ),
                ) {
                    item {
                        NowAccountItem(
                            account = LocalAccount.current,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .contentCard(),
                        )
                    }
                    item {
                        TextPref(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .contentCard(),
                            textColor = ExtendedTheme.colors.text,
                            title = stringResource(id = R.string.title_block_settings),
                            summary = stringResource(id = R.string.summary_block_settings),
                            leadingIcon = {
                                LeadingIcon {
                                    AvatarIcon(
                                        icon = ImageVector.vectorResource(id = R.drawable.ic_settings_block),
                                        size = Sizes.Small,
                                        contentDescription = null,
                                    )
                                }
                            },
                            darkenOnDisable = false,
                            onClick = { navigator.navigate(BlockSettingsPageDestination) }
                        )
                    }
                    item {
                        TextPref(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .contentCard(),
                            textColor = ExtendedTheme.colors.text,
                            title = stringResource(id = R.string.title_settings_custom),
                            summary = stringResource(id = R.string.summary_settings_custom),
                            leadingIcon = {
                                LeadingIcon {
                                    AvatarIcon(
                                        icon = ImageVector.vectorResource(id = R.drawable.ic_brush_black_24dp),
                                        size = Sizes.Small,
                                        contentDescription = null,
                                    )
                                }
                            },
                            darkenOnDisable = false,
                            onClick = { navigator.navigate(CustomSettingsPageDestination) }
                        )
                    }
                    item {
                        TextPref(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .contentCard(),
                            textColor = ExtendedTheme.colors.text,
                            title = stringResource(id = R.string.title_settings_read_habit),
                            summary = stringResource(id = R.string.summary_settings_habit),
                            leadingIcon = {
                                LeadingIcon {
                                    AvatarIcon(
                                        icon = ImageVector.vectorResource(id = R.drawable.ic_dashboard_customize_black_24),
                                        size = Sizes.Small,
                                        contentDescription = null,
                                    )
                                }
                            },
                            darkenOnDisable = false,
                            onClick = { navigator.navigate(HabitSettingsPageDestination) }
                        )
                    }
                    item {
                        TextPref(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .contentCard(),
                            textColor = ExtendedTheme.colors.text,
                            title = stringResource(id = R.string.title_oksign),
                            summary = stringResource(id = R.string.summary_settings_oksign),
                            leadingIcon = {
                                LeadingIcon {
                                    AvatarIcon(
                                        icon = ImageVector.vectorResource(id = R.drawable.ic_rocket_launch_black_24),
                                        size = Sizes.Small,
                                        contentDescription = null,
                                    )
                                }
                            },
                            darkenOnDisable = false,
                            onClick = {
                                navigator.navigate(OKSignSettingsPageDestination)
                            }
                        )
                    }
                    item {
                        TextPref(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .contentCard(),
                            textColor = ExtendedTheme.colors.text,
                            title = stringResource(id = R.string.title_settings_more),
                            summary = stringResource(id = R.string.summary_settings_more),
                            leadingIcon = {
                                LeadingIcon {
                                    AvatarIcon(
                                        icon = ImageVector.vectorResource(id = R.drawable.ic_more_horiz_black_24),
                                        size = Sizes.Small,
                                        contentDescription = null,
                                    )
                                }
                            },
                            darkenOnDisable = false,
                            onClick = {
                                navigator.navigate(MoreSettingsPageDestination)
                            }
                        )
                    }
                }
            }
        }
    }
}
