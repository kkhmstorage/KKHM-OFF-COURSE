package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("KKHM Off-Course", appName)
  }

  @Test
  fun `period log entry round trip`() {
    val entry = com.example.data.model.PeriodLogEntry("Monday", 2, "EFFECTIVE", 45, "Notes")
    val decoded = com.example.data.model.PeriodLogEntry.decode(entry.encode())
    assertEquals(entry, decoded)
  }
}
