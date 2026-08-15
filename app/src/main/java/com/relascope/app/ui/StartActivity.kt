package com.relascope.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.relascope.app.*
import com.relascope.app.databinding.ActivityStartBinding

class StartActivity : AppCompatActivity() {
    private lateinit var b: ActivityStartBinding
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityStartBinding.inflate(layoutInflater); setContentView(b.root)
        b.spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Category.values().map { it.ru })
        b.spDensity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Density.values().map { it.ru })
        b.btnCalib.setOnClickListener { startActivity(Intent(this, CalibrationActivity::class.java)) }
        b.btnStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1); return@setOnClickListener
            }
            val area = b.etArea.text.toString().replace(',', '.').toDoubleOrNull()
            if (area == null) { toast("Введите площадь"); return@setOnClickListener }
            val req = Table1.plots(Category.values()[b.spCategory.selectedItemPosition], Density.values()[b.spDensity.selectedItemPosition], area)
            if (req == null) { toast("Учёт на лесосеках менее 3 га не проводится"); return@setOnClickListener }
            SessionStore.clear(this)
            SessionStore.save(this, Session(area, Category.values()[b.spCategory.selectedItemPosition], Density.values()[b.spDensity.selectedItemPosition], req))
            startActivity(Intent(this, TallyActivity::class.java))
        }
        b.btnContinue.setOnClickListener { startActivity(Intent(this, TallyActivity::class.java)) }
        b.btnResults.setOnClickListener { startActivity(Intent(this, ResultsActivity::class.java)) }
    }
    override fun onResume() {
        super.onResume()
        val sess = SessionStore.load(this)
        b.btnContinue.isEnabled = sess != null && !sess.finished
        b.btnResults.isEnabled = sess != null && sess.plots.isNotEmpty()
        b.tvCalib.text = if (CalibrationStore.has(this)) "Калибровка: ${CalibrationStore.model(this)}" else "Калибровка не выполнена!"
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
