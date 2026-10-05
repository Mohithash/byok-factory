package com.mohithash.byok

import android.app.Application
import androidx.room.Room
import com.mohithash.byok.ai.AiClient
import com.mohithash.byok.data.AppDb
import com.mohithash.byok.data.JsonStore
import com.mohithash.byok.engine.AppSpec
import com.mohithash.byok.engine.Engine
import com.mohithash.byok.engine.UsageStats

class App : Application() {
    lateinit var db: AppDb
    /** Profile, preferences, usage. Included in Android backups. */
    lateinit var store: JsonStore
    /** The AI settings (API key). Excluded from cloud backup and device transfer — see res/xml/backup_rules.xml. */
    lateinit var secrets: JsonStore
    lateinit var spec: AppSpec
    val client = AiClient()
    val engine by lazy { Engine(client, spec) }
    override fun onCreate() {
        super.onCreate()
        spec = AppSpec.load(this)
        db = Room.databaseBuilder(this, AppDb::class.java, "${spec.id}.db").addMigrations(AppDb.MIGRATION_1_2).build()
        store = JsonStore(this)
        secrets = JsonStore(this, SECRETS)
        // v1.0 kept the key alongside the profile; move it to the non-backed-up file once.
        store.raw("ai")?.let { if (secrets.raw("ai") == null) secrets.putRaw("ai", it); store.remove("ai") }
        client.onUsage = { u -> store.update("usage", UsageStats.serializer(), UsageStats()) { it.plus(u.inputTokens, u.outputTokens, System.currentTimeMillis()) } }
    }
    companion object { const val SECRETS = "secrets" }
}
