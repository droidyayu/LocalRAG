package com.ayushig.localrag.demo

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The root view controller of the iOS app: the shared Compose UI. Call `initKoinIos` once
 * before creating it, so the screens can resolve their view models.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
