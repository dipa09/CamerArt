package com.example.camerart

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference

class SettingActivity : AppCompatActivity() {
    class SettingsFragment : PreferenceFragmentCompat() {
        var supportedQualities: Array<String>? = null
        var supportedResolutions: Array<String>? = null

        private fun enablePreference(name: String, state: Boolean) {
            val pref: Preference? = findPreference(name)
            if (pref != null) {
                pref.isEnabled = state
            }
        }
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val featuresBundle = arguments?.getBundle("features")
            val features = if (featuresBundle != null)
                cameraFeaturesFromBundle(featuresBundle)
            else
                CameraFeatures(false, false, false)

            enablePreference(resources.getString(R.string.flash_key), features.hasFlash)
            enablePreference(resources.getString(R.string.multi_camera_key), features.hasMulti)

            // NOTE(davide): Keep the UI consistent since these two are correlated
            val prefCapture: ListPreference? = findPreference(resources.getString(R.string.capture_key))
            val prefJpegQuality: SeekBarPreference? = findPreference(resources.getString(R.string.jpeg_quality_key))
            if (prefCapture != null && prefJpegQuality != null) {
                prefCapture.setOnPreferenceChangeListener { _, newValue ->
                    val capMode = newValue as String
                    prefJpegQuality.value = if (capMode == resources.getString(R.string.capture_value_quality))
                        MainActivity.JPEG_QUALITY_MAX
                    else
                        MainActivity.JPEG_QUALITY_LATENCY

                    true
                }
            }

            // NOTE(davide): Display only available qualities
            val prefVideoQuality: ListPreference? = findPreference(resources.getString(R.string.video_quality_key))
            if (prefVideoQuality != null) {
                val qualities = arguments?.getStringArray("supportedQualities")
                if (qualities != null) {
                    prefVideoQuality.entryValues = qualities
                    prefVideoQuality.entries = qualities

                    supportedQualities = qualities
                    supportedResolutions = initVideoResolutions(qualities)
                }

                prefVideoQuality.summaryProvider = Preference.SummaryProvider<ListPreference> { _ ->
                    lookupQualityResolutionSummary(prefVideoQuality.value)
                }
            }

            val prefImageFormat: ListPreference? =
                findPreference(resources.getString(R.string.image_fmt_key))
            if (prefImageFormat != null) {
                val availMimes = getAvailableMimes()
                prefImageFormat.entryValues = availMimes
                prefImageFormat.entries = availMimes
                prefImageFormat.summaryProvider = Preference.SummaryProvider<ListPreference> { _ ->
                    val summary = prefImageFormat.value ?: "Not Selected"
                    summary
                }
            }

            val prefExposure: SeekBarPreference? = findPreference(resources.getString(R.string.exposure_key))
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
            // TODO(davide): Is there a way to disable the OK button in the dialog box on invalid
            // inputs? Currently we just discard them
            val prefVideoDuration: EditTextPreference? = findPreference(resources.getString(R.string.video_duration_key))
            if (prefVideoDuration != null) {
                prefVideoDuration.setOnPreferenceChangeListener { _, newValue ->
                    val duration = newValue as String
                    var valid = (duration.first() != '0')
                    if (valid) {
                        for (ch in duration.iterator()) {
                            valid = ch.isDigit()
                            if (!valid)
                                break
                        }
                    }
                    valid
                }
            }
        }

        private fun initVideoResolutions(qualities: Array<String>): Array<String>  {
            val resolutions = Array<String>(qualities.size){""}
            for (i in qualities.indices)  {
                val quality = qualities[i]

                var prefix: String = ""
                var p: Int = 0
                when (quality)  {
                    "SD" -> { p = 480 }
                    "HD" -> { p = 720 }
                    "FHD" -> { p = 1080; prefix = "Full " }
                    "UHD" -> { p = 2160; prefix = "4K ultra " }
                }

                resolutions[i] = prefix + quality + " ${p}p"
            }

            return resolutions
        }

        private fun lookupQualityResolutionSummary(qualityName: String?): String  {
            var result = "Not selected"

            val resolutions = supportedResolutions
            val qualities = supportedQualities
            if (qualityName != null && resolutions != null && qualities != null)  {
                assert(resolutions.size == qualities.size)

                for (i in qualities.indices) {
                    if (qualityName == qualities[i])  {
                        result = resolutions[i]
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

fun getAvailableMimes(): Array<CharSequence> {
    val availMimes = ArrayList<CharSequence>(3)

    val mimeMap = MimeTypeMap.getSingleton()
    if (mimeMap.hasMimeType(MainActivity.MIME_TYPE_JPEG))
        availMimes.add(MainActivity.MIME_TYPE_JPEG)
    if (mimeMap.hasMimeType(MainActivity.MIME_TYPE_PNG))
        availMimes.add(MainActivity.MIME_TYPE_PNG)
    if (Build.VERSION.SDK_INT >= MainActivity.MIN_VERSION_FOR_WEBP &&
            mimeMap.hasMimeType(MainActivity.MIME_TYPE_WEBP))
        availMimes.add(MainActivity.MIME_TYPE_WEBP)

    return availMimes.toTypedArray()
}
