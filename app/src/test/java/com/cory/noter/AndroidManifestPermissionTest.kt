package com.cory.noter

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class AndroidManifestPermissionTest {
    @Test
    fun `manifest requests internet permission for OpenRouter calls`() {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first(File::isFile)
        val usesPermissions = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(manifest)
            .getElementsByTagName("uses-permission")
        val requestedPermissions = (0 until usesPermissions.length).map { index ->
            usesPermissions.item(index).attributes.getNamedItem("android:name").nodeValue
        }

        assertThat(requestedPermissions).contains("android.permission.INTERNET")
    }
}
