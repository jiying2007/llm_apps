package com.junchen.jingdu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Hot-path overload for the reader bottom scrubber. The signature intentionally matches the
 * ReaderScreenV3 call that does not provide valueRange/steps, so advanced settings continue to use
 * Material3 Slider while page/continuous position updates use the fixed-cost Canvas implementation.
 */
@Composable
internal fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ReaderLinearSlider(
        value = value,
        valueRange = 0f..1f,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = { onValueChangeFinished?.invoke() },
    )
}
