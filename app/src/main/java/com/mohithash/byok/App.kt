package com.mohithash.byok

import android.app.Application
import androidx.room.Room
import com.mohithash.byok.ai.AiClient
import com.mohithash.byok.data.AppDb
import com.mohithash.byok.data.JsonStore
import com.mohithash.byok.engine.AppSpec
import com.mohithash.byok.engine.Engine

class App : Application() {
    lateinit var db: AppDb
    lateinit var store: JsonStore
    lateinit var spec: AppSpec
    val client = AiClient()
    val engine by lazy { Engine(client, spec) }
    override fun onCreate() {
        super.onCreate()
        spec = AppSpec.load(this)
        db = Room.databaseBuilder(this, AppDb::class.java, "${spec.id}.db").build()
        store = JsonStore(this)
    }
}
