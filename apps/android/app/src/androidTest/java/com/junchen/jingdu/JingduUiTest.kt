package com.junchen.jingdu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JingduUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun emptyLibraryExplainsValueAndImportActionInActiveLocale() {
        composeRule.setContent { JingduApp(AppUiState(), noOpActions()) }
        composeRule.onNodeWithText(context.getString(R.string.app_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_tagline_terminal)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.select_txt)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.select_multiple_txt)).assertIsDisplayed()
    }

    @Test fun readerKeepsPrimaryReadingChromeDiscoverable() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(),
                    pageText = "Chapter 1\nA stable body used for Reader UI smoke verification.",
                    position = 500, length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")), chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithText("Long Novel").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back_to_library)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.chapters)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.start_read_aloud)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.bookmarks)).assertIsDisplayed()
    }

    @Test fun centerTapRestoresReaderChromeAfterAutoHide() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(),
                    pageText = "Chapter 1\nA stable body used for center-tap restoration verification.",
                    position = 500, length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")), chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true, controlsAutoHideMs = 80L),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings)).assertIsDisplayed()
        Thread.sleep(240L)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings)).assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_surface)).performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings)).assertIsDisplayed()
    }

    @Test fun pagedReaderRightTapAlwaysHasAReachableNextPath() {
    var nextCount = 0
    composeRule.setContent {
        JingduApp(
            AppUiState(
                screen = AppScreen.READER,
                currentBook = sampleBook(),
                pageText = "Chapter 1\nA stable body used for real edge-tap paging verification.",
                position = 500,
                length = 10_000,
                chapters = listOf(ChapterModel(0, "Chapter 1")),
                chaptersLoaded = true,
                settings = ReaderSettings(
                    gestureCoachDismissed = true,
                    tapPagingEnabled = false,
                    swipePagingEnabled = false,
                ).withReachablePagedNavigation(),
            ),
            noOpActions().copy(onNavigateNext = { nextCount++ }),
        )
    }
    val surface = composeRule.onNodeWithContentDescription(context.getString(R.string.reader_surface))
    val bounds = surface.fetchSemanticsNode().boundsInRoot
    surface.performTouchInput {
        click(androidx.compose.ui.geometry.Offset(bounds.width * 0.88f, bounds.height * 0.50f))
    }
    composeRule.waitForIdle()
    assertEquals(1, nextCount)
}

    @Test fun quickReadingSettingsStayTouchableAcrossRepeatedStateChanges() {
        var latest = ReaderSettings(gestureCoachDismissed = true)
        composeRule.setContent {
            var settings by remember { mutableStateOf(latest) }
            val actions = noOpActions().copy(onSettingsChanged = { updated -> settings = updated; latest = updated })
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", length = 10_000,
                    panel = ReaderPanel.QUICK_SETTINGS, settings = settings,
                ), actions,
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_quick_settings)).assertIsDisplayed()
        composeRule.onNodeWithText("+").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("+").performClick()
        composeRule.waitForIdle()
        assertEquals(22f, latest.fontSizeSp)
        composeRule.onNodeWithText(context.getString(R.string.reader_mode_continuous)).performClick()
        composeRule.waitForIdle()
        assertEquals(ReaderMode.CONTINUOUS, latest.readingMode)
    }

    @Test fun chapterRowsRemainTouchableWithNativeScrollingPanel() {
        var jumped = -1L
        val chapters = (0 until 30).map { index -> ChapterModel(index * 1000L, "Chapter ${index + 1}") }
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", position = 0, length = 30_000,
                    panel = ReaderPanel.CHAPTERS, chapters = chapters, chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions().copy(onJump = { jumped = it }),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chapter 1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(0L, jumped)
    }

    @Test fun hiddenHotPanelsRemainPhysicallyOffscreen() {
        val chapters = listOf(ChapterModel(0, "Chapter 1"))
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", position = 0, length = 10_000,
                    panel = null, chapters = chapters, chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chapter 1").assertIsNotDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_quick_settings)).assertIsNotDisplayed()
    }

    @Test fun annotationsAreFirstClassLocalReaderAssets() {
        val book = sampleBook()
        val annotation = ReaderAnnotation(
            id = "note-1", bookId = book.id, sourceStart = 100, sourceEnd = 140,
            kind = ReaderAnnotationKind.HIGHLIGHT, style = ReaderHighlightStyle.YELLOW,
            excerpt = "Local highlight", createdAt = 1, updatedAt = 1,
        )
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = book, pageText = "Body", length = 10_000,
                    panel = ReaderPanel.ANNOTATIONS, annotations = listOf(annotation),
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_annotations)).assertIsDisplayed()
        composeRule.onNodeWithText("Local highlight").assertIsDisplayed()
    }

    @Test fun cleanSheetLetsFreeUsersSeeSmartCleanValueBeforePaywallInActiveLocale() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", length = 10_000,
                    panel = ReaderPanel.CLEAN, smartCleanAnalyzed = true,
                    noiseCandidates = listOf(NoiseCandidateModel(94, 326, "promo_repeated", "www.example.com", selected = true)),
                    proUnlocked = false, proPrice = "US$6.99", settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.smart_clean)).assertIsDisplayed()
        composeRule.onNodeWithText("www.example.com").assertIsDisplayed()
    }

    private fun sampleBook() = BookCardModel(
        id = "a".repeat(64), name = "Long Novel.txt", encoding = "UTF-8", sizeBytes = 1024,
        progress = 500, charCount = 10_000, touchedAt = 1, normalizedSha256 = "b".repeat(64),
    )

    private fun noOpActions() = JingduActions(
        onImport = {}, onBatchImport = {}, onOpenBook = {}, onDeleteLibraryBook = {},
        onToggleFavorite = {}, onSetBookTags = { _, _ -> }, onBackToLibrary = {},
        onNavigatePrevious = {}, onNavigateNext = {}, onSeekFraction = {}, onVisibleCharsChanged = {},
        onOpenPanel = {}, onClosePanel = {}, onSearchQueryChanged = {}, onSearch = {}, onJump = {},
        onSyncTtsPosition = {}, onEnsureChapters = {}, onAddBookmark = {}, onDeleteBookmark = {},
        onAddAnnotation = { _, _, _, _, _, _ -> }, onDeleteAnnotation = {}, onImportFont = {},
        onAddRule = { _, _, _ -> }, onDeleteRule = {}, onClearRules = {}, onAnalyzeSmartClean = {},
        onToggleNoiseCandidate = {}, onApplySmartClean = {}, onUndoSmartClean = {},
        onAddGlobalRule = { _, _, _ -> }, onDeleteGlobalRule = {}, onClearGlobalRules = {},
        onInstallRecommendedRules = {}, onExportGlobalRules = {}, onImportGlobalRules = {},
        onUpgradePro = {}, onRestorePro = {}, onExportBackup = {}, onImportBackup = {},
        onToggleCleanPreview = {}, onExportClean = {}, onEncodingSelected = {}, onSettingsChanged = {},
        onToggleTts = {}, onToggleAutoPaging = {}, onSleepTimer = {}, onRequestDeleteCurrent = {},
        onDismissDelete = {}, onConfirmDeleteCurrent = {}, onMessageConsumed = {},
    )
}
