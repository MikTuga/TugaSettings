package com.miktuga.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.miktuga.design.settings.TugaSetting
import com.miktuga.design.settings.TugaSettingsClient
import com.miktuga.design.settings.UnitsDistanceValue
import com.miktuga.design.settings.UnitsSpeedValue
import com.miktuga.design.settings.UnitsTempValue

class SettingsActivity : AppCompatActivity() {

    private lateinit var segSpeedKmh: AppCompatButton
    private lateinit var segSpeedMph: AppCompatButton
    private lateinit var segTempC: AppCompatButton
    private lateinit var segTempF: AppCompatButton
    private lateinit var segDistM: AppCompatButton
    private lateinit var segDistFt: AppCompatButton

    private lateinit var textUsbMount: TextView
    private lateinit var textMusic: TextView
    private lateinit var textReports: TextView

    private lateinit var switchAutoUpdate: SwitchCompat

    private var pendingPathSetting: TugaSetting<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        segSpeedKmh = findViewById(R.id.segSpeedKmh)
        segSpeedMph = findViewById(R.id.segSpeedMph)
        segTempC = findViewById(R.id.segTempC)
        segTempF = findViewById(R.id.segTempF)
        segDistM = findViewById(R.id.segDistM)
        segDistFt = findViewById(R.id.segDistFt)

        textUsbMount = findViewById(R.id.textUsbMount)
        textMusic = findViewById(R.id.textMusic)
        textReports = findViewById(R.id.textReports)

        switchAutoUpdate = findViewById(R.id.switchAutoUpdate)

        segSpeedKmh.setOnClickListener {
            TugaSettingsClient.set(this, TugaSetting.UnitsSpeed, UnitsSpeedValue.KMH); render()
        }
        segSpeedMph.setOnClickListener {
            TugaSettingsClient.set(this, TugaSetting.UnitsSpeed, UnitsSpeedValue.MPH); render()
        }
        segTempC.setOnClickListener {
            TugaSettingsClient.set(this, TugaSetting.UnitsTemp, UnitsTempValue.CELSIUS); render()
        }
        segTempF.setOnClickListener {
            TugaSettingsClient.set(this, TugaSetting.UnitsTemp, UnitsTempValue.FAHRENHEIT); render()
        }
        segDistM.setOnClickListener {
            TugaSettingsClient.set(this, TugaSetting.UnitsDistance, UnitsDistanceValue.METERS); render()
        }
        segDistFt.setOnClickListener {
            TugaSettingsClient.set(this, TugaSetting.UnitsDistance, UnitsDistanceValue.FEET); render()
        }

        findViewById<View>(R.id.btnUsbMount).setOnClickListener { launchPicker(TugaSetting.UsbMountPath) }
        findViewById<View>(R.id.btnMusic).setOnClickListener { launchPicker(TugaSetting.MusicFolder) }
        findViewById<View>(R.id.btnReports).setOnClickListener { launchPicker(TugaSetting.ReportsFolder) }

        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            TugaSettingsClient.set(this, TugaSetting.AutoUpdateCheck, isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val speed = TugaSettingsClient.get(this, TugaSetting.UnitsSpeed)
        applySegmented(segSpeedKmh, speed == UnitsSpeedValue.KMH)
        applySegmented(segSpeedMph, speed == UnitsSpeedValue.MPH)

        val temp = TugaSettingsClient.get(this, TugaSetting.UnitsTemp)
        applySegmented(segTempC, temp == UnitsTempValue.CELSIUS)
        applySegmented(segTempF, temp == UnitsTempValue.FAHRENHEIT)

        val dist = TugaSettingsClient.get(this, TugaSetting.UnitsDistance)
        applySegmented(segDistM, dist == UnitsDistanceValue.METERS)
        applySegmented(segDistFt, dist == UnitsDistanceValue.FEET)

        textUsbMount.text = TugaSettingsClient.get(this, TugaSetting.UsbMountPath)
        textMusic.text = TugaSettingsClient.get(this, TugaSetting.MusicFolder)
        textReports.text = TugaSettingsClient.get(this, TugaSetting.ReportsFolder)

        switchAutoUpdate.setOnCheckedChangeListener(null)
        switchAutoUpdate.isChecked = TugaSettingsClient.get(this, TugaSetting.AutoUpdateCheck)
        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            TugaSettingsClient.set(this, TugaSetting.AutoUpdateCheck, isChecked)
        }
    }

    private fun applySegmented(btn: AppCompatButton, selected: Boolean) {
        if (selected) {
            btn.setBackgroundResource(R.drawable.segment_selected)
            btn.setTextColor(0xFFFFFFFF.toInt())
        } else {
            btn.setBackgroundResource(R.drawable.segment_unselected)
            btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun launchPicker(setting: TugaSetting<String>) {
        pendingPathSetting = setting
        val intent = Intent(this, FolderPickerActivity::class.java)
        intent.putExtra(FolderPickerActivity.EXTRA_INITIAL_PATH, TugaSettingsClient.get(this, setting))
        startActivityForResult(intent, REQ_PICK_FOLDER)
    }

    @Deprecated("Activity result API replaces this on newer SDKs; kept for compileSdk 34 / targetSdk 28.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_FOLDER && resultCode == Activity.RESULT_OK && data != null) {
            val picked = data.getStringExtra(FolderPickerActivity.RESULT_PATH)
            val setting = pendingPathSetting
            if (picked != null && setting != null) {
                TugaSettingsClient.set(this, setting, picked)
                render()
            }
        }
        pendingPathSetting = null
    }

    companion object {
        private const val REQ_PICK_FOLDER = 9001
    }
}
