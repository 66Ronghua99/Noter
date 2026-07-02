package com.cory.noter.calendar

import android.Manifest
import android.content.ContentProvider
import android.content.Context
import android.content.ContextWrapper
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class CalendarSourceTest {
    @Test
    fun `provider query failure returns missing permission result`() = runTest {
        val context = GrantingCalendarPermissionContext(ApplicationProvider.getApplicationContext())
        ShadowContentResolver.registerProviderInternal(
            CalendarContract.AUTHORITY,
            ThrowingCalendarProvider(),
        )

        val result = AndroidCalendarSource(context).loadCalendars()

        assertThat(result).isEqualTo(CalendarSourceResult.MissingPermission)
    }

    private class ThrowingCalendarProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            throw SecurityException("calendar provider rejected query")
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private class GrantingCalendarPermissionContext(
        base: Context,
    ) : ContextWrapper(base) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            when (permission) {
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                -> PackageManager.PERMISSION_GRANTED
                else -> super.checkPermission(permission, pid, uid)
            }
    }
}
