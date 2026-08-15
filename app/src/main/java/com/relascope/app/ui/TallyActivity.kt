package com.relascope.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.relascope.app.*
import com.relascope.app.databinding.ActivityTallyBinding
import java.util.concurrent.Executors

class TallyViewModel(app: android.app.Application) : AndroidViewModel(app) {
    val session: Session = SessionStore.load(app) ?: throw IllegalStateException("Нет сессии")
    fun save() = SessionStore.save(getApplication(), session)
}

class TallyActivity : AppCompatActivity() {
    private lateinit var b: ActivityTallyBinding
    private lateinit var vm: TallyViewModel
    private val detector = HeuristicTrunkDetector()
    private val exec = Executors.newSingleThreadExecutor()
    private var paused = false
    private var lastRaw = SignalState.NONE
    private var rawCount = 0
    private var stable = SignalState.NONE

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (!CalibrationStore.has(this)) { Toast.makeText(this, "Сначала выполните калибровку", Toast.LENGTH_LONG).show(); finish(); return }
        b = ActivityTallyBinding.inflate(layoutInflater); setContentView(b.root)
        vm = ViewModelProvider(this)[TallyViewModel::class.java]
        b.overlay.k = CalibrationStore.k(this)
        b.btnCount.isEnabled = false
        b.btnCount.setOnClickListener { showSpeciesDialog() }
        b.btnFinishPlot.setOnClickListener { finishPlot() }
        b.btnFinishAll.setOnClickListener {
            vm.session.finished = true; vm.save()
            startActivity(Intent(this, ResultsActivity::class.java)); finish()
        }
        updateInfo()
        if (!vm.session.openPlot) offerPlotType()
        bindCamera()
    }

    private fun offerPlotType() {
        if (vm.session.plots.size >= vm.session.required) { updateButtons(); return }
        AlertDialog.Builder(this).setTitle("Площадка ${vm.session.plots.size + 1} из ${vm.session.required}")
            .setItems(arrayOf("Полная (1, 360°)", "Половинная (0,5, 180°)")) { _, w ->
                vm.session.plots.add(Plot(if (w == 0) 1.0 else 0.5)); vm.session.openPlot = true; vm.save(); updateInfo()
            }.setCancelable(false).show()
    }

    private fun finishPlot() {
        vm.session.openPlot = false; vm.save()
        updateInfo()
        if (vm.session.plots.size >= vm.session.required) {
            Toast.makeText(this, "Все площадки завершены — нажмите ЗАВЕРШИТЬ ПЕРЕЧЕТ ЛЕСОСЕКИ", Toast.LENGTH_LONG).show()
        } else offerPlotType()
    }

    private fun updateButtons() {
        b.btnFinishAll.isEnabled = vm.session.plots.size >= vm.session.required && !vm.session.openPlot
        b.btnCount.isEnabled = vm.session.openPlot && stable == SignalState.COUNT && !paused
    }

    private fun updateInfo() {
        val p = vm.session.plots.lastOrNull()
        b.tvPlotInfo.text = "Площадка: ${vm.session.plots.size}/${vm.session.required}" +
                (if (p != null && vm.session.openPlot) " (вес ${if (p.weight == 1.0) "1" else "0,5"}), деревьев: ${p.total()}" else "")
        updateButtons()
    }

    private fun bindCamera() {
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val provider = fut.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(b.preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(exec, { image ->
                if (!paused && vm.session.openPlot) {
                    val k = CalibrationStore.k(this)
                    val f = YuvGray.from(image)
                    val notch = (k * f.w).toDouble()
                    val st = detector.detect(f.data, f.w, f.h, f.w / 2, f.h / 2, notch)
                    rawCount = if (st == lastRaw) rawCount + 1 else 1
                    lastRaw = st
                    if (rawCount >= 6 && st != stable) {
                        stable = st
                        runOnUiThread {
                            b.overlay.state = stable; b.overlay.invalidate()
                            if (stable == SignalState.COUNT) vibrate()
                            updateButtons()
                        }
                    }
                }
                image.close()
            })
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, mainExecutor)
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(150)
    }

    private fun showSpeciesDialog() {
        paused = true
        b.btnCount.isEnabled = false
        val items = Species.values().flatMap { sp -> Grade.values().map { g -> "${sp.ru} — ${g.ru}" } }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Порода и категория дерева:")
            .setItems(items) { _, i ->
                val sp = Species.values()[i / 2]; val g = Grade.values()[i % 2]
                vm.session.plots.last().add(sp, g); vm.save(); updateInfo()
                resetDetection()
            }
            .setOnCancelListener { resetDetection() }
            .show()
    }

    private fun resetDetection() {
        paused = false; stable = SignalState.NONE; lastRaw = SignalState.NONE; rawCount = 0
        b.overlay.state = SignalState.NONE; b.overlay.invalidate(); updateButtons()
    }
}