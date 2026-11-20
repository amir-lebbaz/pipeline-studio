package com.architecture.pipelinestudio

import android.app.Application
import android.content.Context
import com.architecture.pipelinestudio.util.LocaleHelper

class PipelineStudioApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }
}
