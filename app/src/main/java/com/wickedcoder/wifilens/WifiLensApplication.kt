package com.wickedcoder.wifilens

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt's component root. Referenced from `AndroidManifest.xml`. */
@HiltAndroidApp
class WifiLensApplication : Application()
