package com.cangshuo.toolbox.core.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Shared page transition spec. All page-level navigation uses these helpers so the app keeps one
 * motion language; individual screens must not define their own enter/exit animations.
 */
object ToolboxMotion {
    const val PAGE_ENTER_DURATION_MS = 260
    const val PAGE_EXIT_DURATION_MS = 200
    const val TAB_DURATION_MS = 200

    /** Entering page slides in by a tenth of the pane width. */
    private const val ENTER_SLIDE_DIVISOR = 10

    /** Covered page drifts a little toward the start side for depth. */
    private const val EXIT_DRIFT_DIVISOR = 20

    /** New page: fade in while sliding in from the end. */
    fun pageEnter(): EnterTransition = fadeIn(
        animationSpec = tween(PAGE_ENTER_DURATION_MS, easing = LinearOutSlowInEasing),
    ) + slideInHorizontally(
        animationSpec = tween(PAGE_ENTER_DURATION_MS, easing = LinearOutSlowInEasing),
        initialOffsetX = { width -> width / ENTER_SLIDE_DIVISOR },
    )

    /** Covered page: fade out while drifting slightly to the start side. */
    fun pageExit(): ExitTransition = fadeOut(
        animationSpec = tween(PAGE_EXIT_DURATION_MS, easing = FastOutLinearInEasing),
    ) + slideOutHorizontally(
        animationSpec = tween(PAGE_EXIT_DURATION_MS, easing = FastOutLinearInEasing),
        targetOffsetX = { width -> -width / EXIT_DRIFT_DIVISOR },
    )

    /** Revealed page when returning: fades back in from its drifted position. */
    fun pagePopEnter(): EnterTransition = fadeIn(
        animationSpec = tween(PAGE_ENTER_DURATION_MS, easing = LinearOutSlowInEasing),
    ) + slideInHorizontally(
        animationSpec = tween(PAGE_ENTER_DURATION_MS, easing = LinearOutSlowInEasing),
        initialOffsetX = { width -> -width / EXIT_DRIFT_DIVISOR },
    )

    /** Current page when returning: fade out while sliding out to the end. */
    fun pagePopExit(): ExitTransition = fadeOut(
        animationSpec = tween(PAGE_EXIT_DURATION_MS, easing = FastOutLinearInEasing),
    ) + slideOutHorizontally(
        animationSpec = tween(PAGE_EXIT_DURATION_MS, easing = FastOutLinearInEasing),
        targetOffsetX = { width -> width / ENTER_SLIDE_DIVISOR },
    )

    /** Forward navigation: tool detail or search covers the current page. */
    fun forward(): ContentTransform = pageEnter() togetherWith pageExit()

    /** Back navigation from a forward page. */
    fun backward(): ContentTransform = pagePopEnter() togetherWith pagePopExit()

    /** Bottom navigation tabs: crossfade without directional movement. */
    fun tab(): ContentTransform = fadeIn(
        animationSpec = tween(TAB_DURATION_MS, easing = LinearOutSlowInEasing),
    ) togetherWith fadeOut(
        animationSpec = tween(TAB_DURATION_MS, easing = FastOutLinearInEasing),
    )
}
