package com.example.camerart

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference

class SettingActivity : AppCompatActivity() {
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val prefCapture: ListPreference? = findPreference("pref_capture")
            val prefJpegQuality: SeekBarPreference? = findPreference("pref_jpeg_quality")
            if (prefCapture != null && prefJpegQuality != null) {
                prefCapture.setOnPreferenceChangeListener { _, newValue ->
                    val mode: String = newValue as String
                    when (mode) {
                        resources.getString(R.string.capture_value_latency) -> prefJpegQuality.value = MainActivity.JPEG_QUALITY_LATENCY
                        resources.getString(R.string.capture_value_quality) -> prefJpegQuality.value = MainActivity.JPEG_QUALITY_MAX
                    }

                    //Log.i("PREF CHANGED", mode)
                    true
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }
}