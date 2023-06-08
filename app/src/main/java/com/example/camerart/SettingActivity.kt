package com.example.camerart

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.example.camerart.adapter.SettingAdapter
import com.example.camerart.model.Setting

class SettingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        val settings = listOf<Setting>(
            Setting(R.string.setting_lens_direction),
            Setting(R.string.setting_capture_mode),
            Setting(R.string.setting_flash_mode))

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.adapter = SettingAdapter(this, settings)
        recyclerView.setHasFixedSize(true)
    }
}