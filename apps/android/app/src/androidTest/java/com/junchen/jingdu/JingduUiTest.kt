package com.junchen.jingdu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class JingduUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyLibraryExplainsValueAndImportActionInActiveLocale() {
        composeRule.setContent {
            JingduApp(
                state = AppUiState(),
                actions = noOpActions(),
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.app_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_tagline)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.select_txt)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.select_multiple_txt)).assertIsDisplayed()
    }

    @Test
    fun readerKeepsPrimaryNavigationDiscoverableInActiveLocale() {
        val book = sampleBook()
        composeRule.setContent {
            JingduApp(
                state = AppUiState(
                    screen = AppScreen.READER,
                    currentBook = book,
                    pageText = "Chapter 1\nA stable body used for reader UI smoke verification.",
                    position = 500,
                    length = 10_000,
                ),
                actions = noOpActions(),
            )
        }

        composeRule.onNodeWithText("Long Novel").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back_to_library)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.full_text_search)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.chapters)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_progress)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.start_read_aloud)).assertIsDisplayed()
    }

    @Test
    fun cleanSheetLetsFreeUsersSeeSmartCleanValueBeforePaywallInActiveLocale() {
        composeRule.setContent {
            JingduApp(
                state = AppUiState(
                    screen = AppScreen.READER,
                    currentBook = sampleBook(),
                    pageText = "Body",
                    length = 10_000,
                    panel = ReaderPanel.CLEAN,
                    smartCleanAnalyzed = true,
                    noiseCandidates = listOf(
                        NoiseCandidateModel(94, 326, "promo_repeated", "www.example.com"),
                    ),
                    proUnlocked = false,
                    proPrice = "US$6.99",
                ),
                actions = noOpActions(),
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.smart_clean)).assertIsDisplayed()
        composeRule.onNodeWithText("www.example.com").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.unlock_pro_apply, context.getString(R.string.price_suffix, "US$6.99"))).assertIsDisplayed()
    }

    private fun sampleBook() = BookCardModel(
        id = "a".repeat(64),
        name = "Long Novel.txt",
        encoding = "UTF-8",
        sizeBytes = 1024,
        progress = 500,
        charCount = 10_000,
        touchedAt = 1,
        normalizedSha256 = "b".repeat(64),
    )

    private fun noOpActions() = JingduActions(
        onImport = {},
        onBatchImport = {},
        onOpenBook = {},
        onDeleteLibraryBook = {},
        onBackToLibrary = {},
        onNavigatePrevious = {},
        onNavigateNext = {},
        onSeekFraction = {},
        onVisibleCharsChanged = {},
        onOpenPanel = {},
        onClosePanel = {},
        onSearchQueryChanged = {},
        onSearch = {},
        onJump = {},
        onAddBookmark = {},
        onDeleteBookmark = {},
        onAddRule = { _, _, _ -> },
        onDeleteRule = {},
        onClearRules = {},
        onAnalyzeSmartClean = {},
        onToggleNoiseCandidate = {},
        onApplySmartClean = {},
        onAddGlobalRule = { _, _, _ -> },
        onDeleteGlobalRule = {},
        onClearGlobalRules = {},
        onInstallRecommendedRules = {},
        onExportGlobalRules = {},
        onImportGlobalRules = {},
        onUpgradePro = {},
        onRestorePro = {},
        onExportBackup = {},
        onImportBackup = {},
        onToggleCleanPreview = {},
        onExportClean = {},
        onEncodingSelected = {},
        onSettingsChanged = {},
        onToggleTts = {},
        onToggleAutoPaging = {},
        onSleepTimer = {},
        onRequestDeleteCurrent = {},
        onDismissDelete = {},
        onConfirmDeleteCurrent = {},
        onMessageConsumed = {},
    )
}
