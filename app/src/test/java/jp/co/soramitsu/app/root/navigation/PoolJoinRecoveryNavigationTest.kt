package jp.co.soramitsu.app.root.navigation

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import jp.co.soramitsu.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PoolJoinRecoveryNavigationTest {
    @Test fun `standalone recovery route uses its own destination without join arguments`() {
        val root = mock<NavController>()
        Navigator(mock()).apply { attach(root, mock<AppCompatActivity>()) }.openPoolJoinRecovery()
        verify(root).navigate(R.id.poolJoinRecoveryFragment, null, null)
    }

    @Test fun `both production graphs register recovery with no draft or payment arguments`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/res/navigation").isDirectory }
        for (name in listOf("bottom_nav_graph.xml", "main_nav_graph.xml")) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, "app/src/main/res/navigation/$name"))
            val dialogs = doc.getElementsByTagName("dialog")
            val found = (0 until dialogs.length).map { dialogs.item(it) as org.w3c.dom.Element }
                .single { it.getAttribute("android:id") == "@+id/poolJoinRecoveryFragment" }
            assertEquals("jp.co.soramitsu.staking.impl.presentation.confirm.pool.join.PoolJoinRecoveryFragment", found.getAttribute("android:name"))
            assertEquals(0, found.getElementsByTagName("argument").length)
            if (name == "bottom_nav_graph.xml") {
                assertEquals("@+id/defiGraph", (found.parentNode as org.w3c.dom.Element).getAttribute("android:id"))
            } else assertTrue(found.parentNode === doc.documentElement)
        }
    }
}
