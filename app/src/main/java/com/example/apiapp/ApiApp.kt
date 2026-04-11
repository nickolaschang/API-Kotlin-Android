package com.example.apiapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for the whole app.
 *
 * The `@HiltAndroidApp` annotation is what bootstraps Hilt's dependency
 * injection graph — it generates the base Hilt components that every
 * `@AndroidEntryPoint` activity and `@HiltViewModel` feeds off of.
 *
 * Registered in `AndroidManifest.xml` via `android:name=".ApiApp"`.
 */
@HiltAndroidApp
class ApiApp : Application()
