package com.cory.noter

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class AndroidManifestPermissionTest {
    @Test
    fun `manifest requests internet permission for OpenRouter calls`() {
        val requestedPermissions = requestedManifestPermissions()

        assertThat(requestedPermissions).contains("android.permission.INTERNET")
    }

    @Test
    fun `manifest requests record audio permission for voice capture`() {
        val requestedPermissions = requestedManifestPermissions()

        assertThat(requestedPermissions).contains("android.permission.RECORD_AUDIO")
    }

    @Test
    fun `manifest declares speech recognition service query for system STT discovery`() {
        val queriedIntentActions = queriedManifestIntentActions()

        assertThat(queriedIntentActions).contains("android.speech.RecognitionService")
    }

    private fun requestedManifestPermissions(): List<String> {
        val usesPermissions = parseManifest()
            .getElementsByTagName("uses-permission")
        val requestedPermissions = (0 until usesPermissions.length).map { index ->
            usesPermissions.item(index).attributes.getNamedItem("android:name").nodeValue
        }
        return requestedPermissions
    }

    private fun queriedManifestIntentActions(): List<String> {
        val queries = parseManifest().getElementsByTagName("queries")
        return (0 until queries.length).flatMap { queryIndex ->
            val queryElement = queries.item(queryIndex) as Element
            val actions = queryElement.getElementsByTagName("action")
            (0 until actions.length).map { actionIndex ->
                actions.item(actionIndex).attributes.getNamedItem("android:name").nodeValue
            }
        }
    }

    private fun parseManifest(): Document {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first(File::isFile)
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(manifest)
    }
}
