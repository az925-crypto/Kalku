package com.zaaaam.kalku

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.zaaaam.kalku.data.SettingsRepo
import com.zaaaam.kalku.fs.VaultRepo

class KalkuApp : Application() {

    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
    }
}

/** Hand-rolled DI: one instance per process. */
class Container(context: Context) {
    val appContext: Context = context.applicationContext

    val db by lazy {
        // No destructive fallback on purpose: silently wiping the vault index is
        // never acceptable; schema changes must ship with explicit migrations.
        Room.databaseBuilder(appContext, com.zaaaam.kalku.data.KalkuDatabase::class.java, "kalku.db")
            .build()
    }

    val settings by lazy { SettingsRepo(appContext) }

    val repo by lazy { VaultRepo(appContext, db) }
}
