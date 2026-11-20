package com.architecture.pipelinestudio.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.architecture.pipelinestudio.databinding.ActivityTheoryBinding
import com.architecture.pipelinestudio.util.LocaleHelper

class TheoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTheoryBinding

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTheoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
