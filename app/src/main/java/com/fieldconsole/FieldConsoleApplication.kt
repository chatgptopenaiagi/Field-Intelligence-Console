package com.fieldconsole

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * FieldConsoleApplication — Hilt-enabled Application class.
 *
 * The @HiltAndroidApp annotation triggers Hilt's code generation and
 * sets up the application-level dependency injection component.
 */
@HiltAndroidApp
class FieldConsoleApplication : Application()
