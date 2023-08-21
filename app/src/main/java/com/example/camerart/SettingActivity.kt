package com.example.camerart

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.preference.DropDownPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat

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

            val args = arguments
            if (args != null) {
                val features = CameraFeatures.fromBundle(args.getBundle("features"))
                enablePreference(resources.getString(R.string.flash_key), features.hasFlash)
                enablePreference(resources.getString(R.string.multi_camera_key), features.hasMulti)

                setupCaptureModeAndJPEGQualityPreference()
                setupVideoQualityPreference(features)
                setupImageFormatPreference()
                setupExposurePreference(args)
                setupVideoDurationPreference()
                setupFiltersPreference(args)
                setupQrCodeScannerPreference(args)
            }
        }

        // NOTE(davide): Keep the UI consistent since these two are correlated
        private fun setupCaptureModeAndJPEGQualityPreference() {
            val prefCapture: ListPreference? = findPreference(resources.getString(R.string.capture_key))
            val prefJpegQuality: SeekBarPreference? = findPreference(resources.getString(R.string.jpeg_quality_key))
            if (prefCapture != null && prefJpegQuality != null) {
                prefCapture.setOnPreferenceChangeListener { _, newValue ->
                    val capMode = newValue as String
                    prefJpegQuality.value =
                        if (capMode == resources.getString(R.string.capture_value_quality))
                            MainActivity.JPEG_QUALITY_MAX
                        else
                            MainActivity.JPEG_QUALITY_LATENCY

                    true
                }
            }
        }

        // NOTE(davide): Display only available qualities
        private fun setupVideoQualityPreference(features: CameraFeaturesFromBundle) {
            val pref: ListPreference? = findPreference(resources.getString(R.string.video_quality_key))
            if (pref != null) {
                val qualityNames = features.qualityNames
                pref.entryValues = qualityNames
                pref.entries = qualityNames

                //supportedQualities = qualities
                //supportedResolutions = initVideoResolutions(qualities)

                /*
                pref.summaryProvider =
                    Preference.SummaryProvider<ListPreference> { _ ->
                        lookupQualityResolutionSummary(pref.value)
                    }

                 */
            }
        }

        private fun setupImageFormatPreference() {
            val pref: ListPreference? = findPreference(resources.getString(R.string.image_fmt_key))
            if (pref != null) {
                val availMimes = getAvailableMimes()
                pref.entryValues = availMimes
                pref.entries = availMimes
                pref.summaryProvider =
                    Preference.SummaryProvider<ListPreference> { _ ->
                        val summary = pref.value ?: "Not Selected"
                        summary
                    }
            }
        }

        private fun setupExposurePreference(args: Bundle) {
            val pref: SeekBarPreference? = findPreference(resources.getString(R.string.exposure_key))
            if (pref != null) {
                val expStateBundle = args.getBundle("exposureState")
                if (expStateBundle != null && expStateBundle.getBoolean("supported")) {
                    val exposure = exposureSettingFromBundle(expStateBundle)

                    pref.isEnabled = true
                    pref.min = exposure.min
                    pref.max = exposure.max
                    pref.value = exposure.index
                    pref.seekBarIncrement = exposure.step
                }
            }
        }

        private fun setupVideoDurationPreference() {
            // NOTE(davide): Validate user input. There is a XML attribute that should do that, but
            // it didn't work...
            // TODO(davide): Is there a way to disable the OK button in the dialog box on invalid
            // inputs? Currently we just discard them
            val pref: EditTextPreference? = findPreference(resources.getString(R.string.video_duration_key))
            if (pref != null) {
                pref.setOnPreferenceChangeListener { _, newValue ->
                    var valid = false

                    val duration = newValue as String
                    if (duration.isEmpty()) {
                        valid = true
                    } else {
                        valid = (duration.first() != '0')
                        if (valid) {
                            for (ch in duration.iterator()) {
                                valid = ch.isDigit()
                                if (!valid)
                                    break
                            }
                        }
                    }
                    valid
                }
            }
        }
        private fun setupFiltersPreference(args: Bundle) {
            val pref: DropDownPreference? = findPreference(resources.getString(R.string.filter_key))
            if (pref != null) {
                val isBeefy = args.getBoolean("isBeefy")
                if (!isBeefy) {
                    // NOTE(davide): Don't show expensive filters on weak devices
                    assert(pref.entries.size > 5)
                    assert(pref.entryValues.size > 5)

                    pref.entries = pref.entries.dropLast(EXPENSIVE_FILTER_COUNT).toTypedArray()
                    pref.entryValues = pref.entryValues.dropLast(EXPENSIVE_FILTER_COUNT).toTypedArray()
                }
            }
        }

        private fun setupQrCodeScannerPreference(args: Bundle) {
            val pref: SwitchPreferenceCompat? = findPreference(resources.getString(R.string.qrcode_key))
            if (pref != null) {
                val currMode = args.getInt("cameraMode")
                pref.isChecked = (currMode == MainActivity.MODE_QRCODE_SCANNER)
            }
        }

        /*
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
         */
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
