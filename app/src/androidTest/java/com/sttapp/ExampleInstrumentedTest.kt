package com.sttapp

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device smoke test: verifies the app boots to a valid target context.
 *
 * Run locally on a device/emulator with `./gradlew connectedDebugAndroidTest`.
 * Note: the release APK is arm64-v8a-only, so on-CI device smoke tests use
 * Firebase Test Lab **physical** (arm64) devices — see docs/TESTING.md.
 * A plain x86_64 GitHub-hosted emulator cannot install this APK.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun appPackageResolves() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context)
        assertEquals("com.sttapp", context.packageName)
    }
}
