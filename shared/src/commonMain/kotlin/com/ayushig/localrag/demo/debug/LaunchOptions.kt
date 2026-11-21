package com.ayushig.localrag.demo.debug

/**
 * Which generation engine the app boots. Chosen by a launch argument, acceptance runs only:
 * without the argument the mode is null and the real on-device model is used exactly as before.
 *
 * - null or "model": the real model (default).
 * - "scripted": the scripted model, answering at once.
 * - "scripted-slow": the scripted model with a long delay, so a test can cancel mid-turn.
 */
expect fun launchEngineMode(): String?

const val ENGINE_MODE_SCRIPTED = "scripted"
const val ENGINE_MODE_SCRIPTED_SLOW = "scripted-slow"

/** Delay for the "scripted-slow" launch mode. */
const val SCRIPTED_SLOW_MILLIS = 6000L

/** The scripted engine's delay, or null when the real model stays in charge. */
fun scriptedDelayMillis(mode: String?): Long? = when (mode) {
    ENGINE_MODE_SCRIPTED -> 0L
    ENGINE_MODE_SCRIPTED_SLOW -> SCRIPTED_SLOW_MILLIS
    else -> null
}
