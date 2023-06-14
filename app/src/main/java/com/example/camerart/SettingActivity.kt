package com.example.camerart

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference

class SettingActivity : AppCompatActivity() {
    class SettingsFragment : PreferenceFragmentCompat() {
        var supportedQualities: Array<String>? = null
        var supportedResolutions: Array<String>? = null

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
                supportedQualities = arguments?.getStringArray("supportedQualities")
                supportedResolutions = arguments?.getStringArray("supportedResolutions")
                if (supportedQualities != null) {
                    prefVideoQuality.entryValues = supportedQualities
                    prefVideoQuality.entries = supportedQualities
                }

                prefVideoQuality.summaryProvider = Preference.SummaryProvider<ListPreference> { _ ->
                    lookupQualityResolutionSummary(prefVideoQuality.value)
                }
            }

            // NOTE(davide): Display available image formats
            val prefImageFormat: ListPreference? = findPreference("pref_image_format")
            if (prefImageFormat != null) {
                val formats = arguments?.getIntArray("supportedImageFormats")
                if (formats != null) {
                    val names = Array<CharSequence>(formats.size) {""}

                    val mimes = MainActivity.Mime.values()
                    for (i in formats.indices) {
                        names[i] = mimes[i].name
                    }

                    prefImageFormat.entryValues = names
                    prefImageFormat.entries = names
                }

                prefImageFormat.summaryProvider = Preference.SummaryProvider<ListPreference> { _ ->
                    val summary = prefImageFormat.value ?: "Not Selected"
                    summary
                }
            }
        }

        private fun lookupQualityResolutionSummary(qualityName: String?): String {
            var result = "Not selected"
            if (qualityName != null && supportedQualities != null && supportedResolutions != null) {
                assert(supportedResolutions!!.size == supportedQualities!!.size)
                for (i in supportedQualities!!.indices) {
                    if (qualityName == supportedQualities!![i]) {
                        result = supportedResolutions!![i]
                        break
                    }
                }
            }

            return result
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