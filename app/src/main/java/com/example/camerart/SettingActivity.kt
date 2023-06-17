package com.example.camerart

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.preference.EditTextPreference
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

            val prefExposure: SeekBarPreference? = findPreference("pref_exposure")
            if (prefExposure != null) {
                val expStateBundle = arguments?.getBundle("exposureState")
                if (expStateBundle != null) {
                    if (expStateBundle.getBoolean("supported")) {
                        val exposure = exposureSettingFromBundle(expStateBundle)

                        prefExposure.isEnabled = true
                        prefExposure.min = exposure.min
                        prefExposure.max = exposure.max
                        prefExposure.value = exposure.index
                        prefExposure.seekBarIncrement = exposure.step
                    }
                }
            }

            // NOTE(davide): Validate user input. There is a XML attribute that should do that, but
            // it didn't work...
            val prefVideoDuration: EditTextPreference? = findPreference("pref_video_duration")
            if (prefVideoDuration != null) {
                prefVideoDuration.setOnPreferenceChangeListener { _, newValue ->
                    var valid = false
                    val duration = newValue as String
                    val iter = duration.iterator()
                    for (ch in iter) {
                        valid = (ch.isDigit() ||
                                (iter.hasNext() &&
                                        (ch.lowercaseChar() == 's' || ch.lowercaseChar() == 'm' || ch.lowercaseChar() == 'h')))
                        if (!valid)
                            break
                    }
                    valid
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