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
    const val PAGE_ENTER_DURATION_MS = 240
    const val PAGE_EXIT_DURATION_MS = 180
    const val TAB_DURATION_MS = 180

    private const val SLIDE_DIVISOR = 8

    /** New page: fade in while sliding in from the end. */
    fun pageEnter(): EnterTransition = fadeIn(
        animationSpec = tween(PAGE_ENTER_DURATION_MS, easing = LinearOutSlowInEasing),
    ) + slideInHorizontally(
        animationSpec = tween(PAGE_ENTER_DURATION_MS, easing = LinearOutSlowInEasing),
        initialOffsetX = { width -> width / SLIDE_DIVISOR },
    )

    /** Covered page: fade out without directional movement. */
    fun pageExit(): ExitTransition = fadeOut(
        animationSpec = tween(PAGE_EXIT_DURATION_MS, easing = FastOutLinearInEasing),
    )

    /** Revealed page when returning: fade in. */
    fun pagePopEnter(): EnterTransition = fadeIn(
        animationSpec = tween(PAGE_ENTER_DURATION_MS, easing = LinearOutSlowInEasing),
    )

    /** Current page when returning: fade out while sliding out to the end. */
    fun pagePopExit(): ExitTransition = fadeOut(
        animationSpec = tween(PAGE_EXIT_DURATION_MS, easing = FastOutLinearInEasing),
    ) + slideOutHorizontally(
        animationSpec = tween(PAGE_ENTER_DURATION_MS, easing = LinearOutSlowInEasing),
        targetOffsetX = { width -> width / SLIDE_DIVISOR },
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
