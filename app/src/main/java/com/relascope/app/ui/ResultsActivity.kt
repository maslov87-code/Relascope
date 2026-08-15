package com.relascope.app.ui

import android.graphics.Typeface
import android.os.Bundle
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.relascope.app.CsvExporter
import com.relascope.app.SessionStore
import com.relascope.app.Table0Builder
import com.relascope.app.XlsxExporter
import com.relascope.app.databinding.ActivityResultsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultsActivity : AppCompatActivity() {
    private lateinit var b: ActivityResultsBinding
    private var sess: com.relascope.app.Session? = null

    // Системные диалоги "Сохранить как..."
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            contentResolver.openOutputStream(it)?.use { os ->
                sess?.let { s -> CsvExporter.export(os, s) }
                Toast.makeText(this, "CSV успешно сохранен!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val createXlsx = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let {
            contentResolver.openOutputStream(it)?.use { os ->
                sess?.let { s -> XlsxExporter.export(os, s) }
                Toast.makeText(this, "XLSX успешно сохранен!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityResultsBinding.inflate(layoutInflater); setContentView(b.root)

        sess = SessionStore.load(this)
        if (sess == null) { Toast.makeText(this, "Нет данных", Toast.LENGTH_LONG).show(); finish(); return }

        val rows = Table0Builder.rows(sess!!)
        rows.forEachIndexed { ri, row ->
            val tr = TableRow(this)
            row.forEach { v ->
                val tv = TextView(this)
                tv.text = when (v) { null -> ""; is Double -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ','); else -> v.toString() }
                tv.setPadding(8, 6, 8, 6)
                if (ri < 3 || ri == rows.size - 1) tv.setTypeface(null, Typeface.BOLD)
                tr.addView(tv)
            }
            b.table.addView(tr)
        }

        b.btnCsv.setOnClickListener {
            val name = "Таксация_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date()) + ".csv"
            createCsv.launch(name)
        }

        b.btnXlsx.setOnClickListener {
            val name = "Таксация_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date()) + ".xlsx"
            createXlsx.launch(name)
        }

        b.btnNew.setOnClickListener { SessionStore.clear(this); finish() }
    }
}