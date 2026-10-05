package com.mohithash.byok.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mohithash.byok.App
import com.mohithash.byok.MainActivity
import com.mohithash.byok.ai.AiSettings
import com.mohithash.byok.engine.Prefs
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Walks the real app (MainActivity → Nav → every screen) on Robolectric against a fake Claude endpoint, and saves
 * screenshots to build/screens/ so the UI can be eyeballed without a device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class UiSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var server: HttpServer
    private val bodies = ConcurrentLinkedQueue<String>()
    private val replies = ConcurrentLinkedQueue<String>()
    private lateinit var app: App
    private val out = File("build/screens").apply { mkdirs() }

    private val doc = """{"title":"Golden fried rice","summary":"Three dinners from **what you have**, ready in 25 minutes.","sections":[
        {"kind":"cards","heading":"Ideas","text":"","items":[],"cards":[{"title":"Egg fried rice","body":"Day-old rice, eggs, spring onion.","meta":"20 min · 0 missing"},{"title":"Shakshuka","body":"Eggs poached in *spiced* tomato.","meta":"25 min · 1 missing"}],"rows":[],"kv":[]},
        {"kind":"steps","heading":"Method","text":"","items":["Heat a wok until smoking.","Scramble the eggs, set aside.","Fry rice 3 min, return eggs, season."],"cards":[],"rows":[],"kv":[]},
        {"kind":"checklist","heading":"Shopping","text":"","items":["Spring onions","Soy sauce","Frozen peas"],"cards":[],"rows":[],"kv":[]},
        {"kind":"table","heading":"Nutrition","text":"","items":[],"cards":[],"rows":[["Dish","kcal","Protein"],["Fried rice","520","18 g"],["Shakshuka","410","22 g"]],"kv":[]},
        {"kind":"kv","heading":"At a glance","text":"","items":[],"cards":[],"rows":[],"kv":[{"k":"Time","v":"25 min"},{"k":"Cost","v":"£3"}]},
        {"kind":"callout","heading":"Tip","text":"Cold rice fries best — warm rice turns mushy.","items":[],"cards":[],"rows":[],"kv":[]},
        {"kind":"quote","heading":"Say it","text":"Dinner in twenty!","items":[],"cards":[],"rows":[],"kv":[]}],
        "tags":["quick","budget"],"followups":["Make it vegan","What about lunch?"]}"""

    private fun reply(json: String) = """{"model":"claude-opus-5-5","stop_reason":"end_turn","usage":{"input_tokens":900,"output_tokens":600},"content":[{"type":"text","text":${JsonPrimitive(json)}}]}"""

    @Before fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            bodies += ex.requestBody.readBytes().decodeToString()
            val b = (replies.poll() ?: """{"error":{"message":"no reply"}}""").toByteArray()
            Thread.sleep(150)
            runCatching { ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) } }
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        app = ApplicationProvider.getApplicationContext()
        runBlocking { app.db.results().deleteAll() }
        app.store.set("onboarded", Boolean.serializer(), false)
        app.store.set("prefs", Prefs.serializer(), Prefs())
        app.secrets.set("ai", AiSettings.serializer(), AiSettings(apiKey = "sk-test", baseUrl = "http://127.0.0.1:${server.address.port}"))
    }

    @After fun tearDown() { server.stop(0) }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(text: String, timeout: Long = 15_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun walkThroughEveryScreen() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitText("Get started")
        shot("01-onboarding")
        rule.onNodeWithText("Get started").performScrollTo().performClick()

        waitText("Tools")
        shot("02-home")

        // Tool form → generate
        rule.onNodeWithText("What can I cook?").performScrollTo().performClick()
        waitText("Suggest recipes")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("eggs, rice, half an onion")
        shot("03-tool-form")
        replies += reply(doc)
        rule.onNodeWithText("Suggest recipes").performScrollTo().performClick()
        waitText("Golden fried rice")
        shot("04-result-top")
        rule.onNodeWithText("Spring onions", substring = true).performScrollTo()
        shot("05-result-middle")

        // Follow-up joins the thread
        replies += reply(doc.replace("Golden fried rice", "Vegan fried rice"))
        rule.onAllNodes(hasText("Make it vegan")).onFirst().performScrollTo().performClick()
        waitText("Vegan fried rice")
        waitText("In this thread")
        shot("06-follow-up-thread")
        assertTrue(bodies.last().contains("\"role\":\"assistant\""))

        // Back to home: recent list
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("Recent")
        shot("07-home-recent")

        // History
        rule.onNodeWithContentDescription("History").performClick()
        waitText("Search saved results")
        shot("08-history")

        // Settings
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("Tools")
        rule.onNodeWithContentDescription("Settings").performClick()
        waitText("Standing instructions")
        shot("09-settings-top")
        rule.onNodeWithText("Export backup").performScrollTo()
        shot("10-settings-data")

        // Dark theme applies immediately
        app.store.set("prefs", Prefs.serializer(), Prefs(theme = "dark"))
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("Tools")
        shot("11-home-dark")
        scenario.close()
    }

    @Test fun sharedTextShowsPickerAndPrefillsTool() {
        app.store.set("onboarded", Boolean.serializer(), true)
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Leftover roast chicken and two potatoes")
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        waitText("Pick a tool for it")
        shot("12-share-banner")
        rule.onAllNodesWithText("Rescue leftovers").onFirst().performScrollTo().performClick()
        waitText("Leftover roast chicken and two potatoes")
        shot("13-share-prefilled")
        scenario.close()
    }

    @Test fun errorCardOffersRetryAndSettings() {
        app.store.set("onboarded", Boolean.serializer(), true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitText("Tools")
        rule.onNodeWithText("Rescue leftovers").performScrollTo().performClick()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("rice")
        replies += """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        rule.onNodeWithText("Rescue").performScrollTo().performClick()
        waitText("invalid x-api-key")
        shot("14-error")
        assertTrue(rule.onAllNodesWithText("Try again").fetchSemanticsNodes().isNotEmpty())
        scenario.close()
    }

    @Test fun darkThemeHeroesStayReadable() {
        app.store.set("prefs", Prefs.serializer(), Prefs(theme = "dark"))
        app.secrets.set("ai", AiSettings.serializer(), AiSettings())
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitText("Get started")
        shot("15-onboarding-dark")
        scenario.close()
        app.store.set("onboarded", Boolean.serializer(), true)
        val home = ActivityScenario.launch(MainActivity::class.java)
        waitText("Add your AI key")
        shot("16-home-dark-setup-hero")
        home.close()
    }
}
