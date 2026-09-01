@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.junchen.jingdu

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Chapters is already kept composed and measured off-screen for the Reader session. Avoid
 * competing idle precompose/premeasure work when the live list first becomes visible or scrolls.
 */
private object ReaderChapterNoPrefetchStrategy : LazyListPrefetchStrategy {
    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) = Unit
    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) = Unit
    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit
}

@Composable
internal fun rememberReaderChapterListState(): LazyListState =
    remember { LazyListState(prefetchStrategy = ReaderChapterNoPrefetchStrategy) }
