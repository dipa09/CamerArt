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
import com.example.camerart.MainActivity.SupportedQuality

class SettingActivity : AppCompatActivity() {
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // NOTE(davide): Keep the UI consistent since these two are correlated
            val prefCapture: ListPreference? = findPreference("pref_capture")
            val prefJpegQuality: SeekBarPreference? = findPreference("pref_jpeg_quality")
            if (prefCapture != null && prefJpegQuality != null) {
                prefCapture.setOnPreferenceChangeListener { _, newValue ->
                    val mode: String = newValue as String
                    when (mode) {
                        resources.getString(R.string.capture_value_latency) -> prefJpegQuality.value = MainActivity.JPEG_QUALITY_LATENCY
                        resources.getString(R.string.capture_value_quality) -> prefJpegQuality.value = MainActivity.JPEG_QUALITY_MAX
                    }

                    true
                }
            }

            // NOTE(davide): Display only available qualities
            val prefVideoQuality: ListPreference? = findPreference("pref_video_quality")
            if (prefVideoQuality != null) {
                val supportedQualities = arguments?.getStringArray("supportedQualities")
                if (supportedQualities != null) {
                    prefVideoQuality.entryValues = supportedQualities
                    prefVideoQuality.entries = supportedQualities
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        // NOTE(davide): Pass arguments from MainActivity to SettingFragment
        val frag = SettingsFragment()
        frag.arguments = intent.extras

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, frag)
            .commit()
    }
}