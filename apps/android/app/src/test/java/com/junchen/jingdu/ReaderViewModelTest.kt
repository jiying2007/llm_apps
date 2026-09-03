package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderViewModelTest {
    @Test
    fun pageTurnStateNeverPublishesFullPageAnimationDirection() {
        val viewModel = ReaderViewModel()

        viewModel.replace(AppUiState(pageTurnDirection = 1))
        assertEquals(0, viewModel.state.value.pageTurnDirection)

        viewModel.replace(AppUiState(pageTurnDirection = -1))
        assertEquals(0, viewModel.state.value.pageTurnDirection)
    }

    @Test
    fun pagedPositionStaysAtLastRenderedPageUntilReady() {
        ReaderInteractionRuntime.foregroundPosition = -1L
        ReaderInteractionRuntime.foregroundPosition = 640L

        assertEquals(-1L, ReaderInteractionRuntime.foregroundPosition)

        val session = ReaderSession()
        session.visiblePageChars = 720L

        assertEquals(640L, ReaderInteractionRuntime.foregroundPosition)
        session.reader.close()
    }

    @Test
    fun continuousPositionCommitsAtStateBoundary() {
        ReaderInteractionRuntime.foregroundPosition = -1L
        ReaderInteractionRuntime.foregroundPosition = 960L
        val viewModel = ReaderViewModel()

        viewModel.replace(
            AppUiState(
                screen = AppScreen.READER,
                position = 960L,
                settings = ReaderSettings(readingMode = ReaderMode.CONTINUOUS),
            ),
        )

        assertEquals(960L, ReaderInteractionRuntime.foregroundPosition)
    }
}
