package com.relascope.app.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import com.relascope.app.CalibrationEngine
import com.relascope.app.CalibrationStore
import com.relascope.app.databinding.ActivityCalibrationBinding
import java.io.File
import java.util.concurrent.Executors

class CalibrationActivity : AppCompatActivity() {
    private lateinit var b: ActivityCalibrationBinding
    private var capture: ImageCapture? = null
    private var guess: CalibrationEngine.Guess? = null
    private val exec = Executors.newSingleThreadExecutor()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityCalibrationBinding.inflate(layoutInflater); setContentView(b.root)
        b.calibView.visibility = View.GONE   // ИСПРАВЛЕНО: пока нет фото, показываем только камеру
        b.sbLeft.max = 100; b.sbRight.max = 100; b.sbLeft.progress = 50; b.sbRight.progress = 50
        val seek = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = applyOffsets()
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        b.sbLeft.setOnSeekBarChangeListener(seek); b.sbRight.setOnSeekBarChangeListener(seek)
        b.btnCapture.setOnClickListener { takePhoto() }
        b.btnRetake.setOnClickListener {
            // ИСПРАВЛЕНО: полностью убираем старый кадр и включаем живой превью
            guess = null
            b.calibView.bmp = null
            b.calibView.invalidate()
            b.calibView.visibility = View.GONE
            b.preview.visibility = View.VISIBLE
        }
        b.btnConfirm.setOnClickListener {
            val g = guess ?: return@setOnClickListener Toast.makeText(this, "Сначала сделайте фото", Toast.LENGTH_LONG).show()
            val k = (b.calibView.right - b.calibView.left + 1) / g.frameW.toFloat()
            CalibrationStore.save(this, k)
            Toast.makeText(this, "Калибровка сохранена: k=$k", Toast.LENGTH_LONG).show()
            finish()
        }
        bind()
    }

    private fun bind() {
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val provider = fut.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(b.preview.surfaceProvider) }
            capture = ImageCapture.Builder().build()
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        }, mainExecutor)
    }

    private fun takePhoto() {
        val c = capture ?: return
        val out = File(cacheDir, "calib.jpg")
        c.takePicture(ImageCapture.OutputFileOptions.Builder(out).build(), exec, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) = runOnUiThread {
                val bmp = BitmapFactory.decodeFile(out.absolutePath)
                guess = CalibrationEngine.findNotch(bmp)
                if (guess == null) { Toast.makeText(this@CalibrationActivity, "Реласкоп не найден на фото", Toast.LENGTH_LONG).show(); return@runOnUiThread }
                b.preview.visibility = View.GONE
                b.calibView.visibility = View.VISIBLE   // показываем фото с линиями только после распознавания
                b.calibView.bmp = bmp
                applyOffsets()
            }
            override fun onError(e: ImageCaptureException) = runOnUiThread { Toast.makeText(this@CalibrationActivity, e.message, Toast.LENGTH_LONG).show() }
        })
    }

    private fun applyOffsets() {
        val g = guess ?: return
        val step = g.frameW / 500f
        b.calibView.left = g.left + (b.sbLeft.progress - 50) * step
        b.calibView.right = g.right + (b.sbRight.progress - 50) * step
        b.calibView.lineY = g.y.toFloat()
        b.calibView.invalidate()
    }
}