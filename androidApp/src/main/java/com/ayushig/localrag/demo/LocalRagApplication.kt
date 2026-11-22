package com.ayushig.localrag.demo

import android.app.Application
import com.ayushig.localrag.demo.di.androidModule
import com.ayushig.localrag.demo.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LocalRagApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LocalRagApplication)
            modules(sharedModule, androidModule)
        }
    }
}
