package com.example.flashlight

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var switch: SwitchMaterial
    private lateinit var onOffButton: MaterialButton
    private var camera: Camera? = null
    private var isOn: Boolean = false
    private var strobeOn: Boolean = false
    private var parameters: Camera.Parameters? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switch = findViewById(R.id.stroboSwitch)
        onOffButton = findViewById(R.id.onOffButton)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            //если есть такая фича, смотрим наличие разрешения
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                //если нет, запрашиваем
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 22)
            } else {
                camera = Camera.open()
            }
        } else {
            //иначе показываем ошибку
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.no_flash_title))
                .setMessage(getString(R.string.no_flash_message))
                .setPositiveButton(getString(R.string.exit)) { _, _ ->
                    finish()
                }
                .setIcon(R.drawable.ic_round_warning_24)
                .show()
        }

        // если фонарь работает, то при выключении переключателя выключить
        switch.setOnCheckedChangeListener { _, _ ->
            strobeOn = !strobeOn
            if (isOn) flashOff()
        }
    }

    fun flashOnOff(view: View) {
        if (!isOn) {
            //включаем
            flashOn()
        } else {
            //выключаем
            flashOff()
        }
    }

    // включение фонаря в отдельном потоке, чтобы не блокировать интерфейс
    private fun flashOn() {
        CoroutineScope(Dispatchers.Default).launch {
            camera?.let { c ->
                parameters = c.parameters
                parameters?.let {

                    // проверяем возможные способы включения фонаря в телефоне
                    val supportedFlashModes = it.supportedFlashModes
                    when {
                        supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_TORCH) == true -> {
                            it.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                        }
                        supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_ON) == true -> {
                            it.flashMode = Camera.Parameters.FLASH_MODE_ON
                        }
                        else -> {
                            camera = null
                        }
                    }
                }
                camera?.let {
                    isOn = true

                    // если включен переключатель, включаем стробоскоп
                    if (strobeOn) {
                        val flashMode = parameters?.flashMode
                        while (isOn) {
                            parameters?.flashMode = flashMode
                            it.parameters = parameters
                            it.startPreview()
                            it.setPreviewTexture(SurfaceTexture(0))
                            Thread.sleep(100)
                            parameters?.flashMode = Camera.Parameters.FLASH_MODE_OFF
                            it.parameters = parameters
                            it.startPreview()
                        }
                    } else {
                        it.parameters = parameters
                        it.startPreview()
                        it.setPreviewTexture(SurfaceTexture(0))
                    }
                }
            }
        }
    }

    // выключаем
    private fun flashOff() {
        camera?.let {
            parameters?.flashMode = Camera.Parameters.FLASH_MODE_OFF
            it.parameters = parameters
            it.stopPreview()
            isOn = false
        }
    }

    override fun onStop() {
        super.onStop()
        releaseCamera()
    }

    override fun onPause() {
        super.onPause()
        releaseCamera()
    }

    override fun onResume() {
        super.onResume()
        if (camera == null) {
            camera = Camera.open()
        }
    }

    private fun releaseCamera() {
        camera?.let {
            it.release()
            camera = null
        }
    }
}