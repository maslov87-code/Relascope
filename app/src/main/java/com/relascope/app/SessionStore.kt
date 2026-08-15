package com.relascope.app

import android.content.Context
import org.json.JSONObject
import java.io.File

object SessionStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "session.json")
    fun save(ctx: Context, s: Session) {
        val o = JSONObject().put("area", s.areaHa).put("cat", s.category.name).put("den", s.density.name)
            .put("req", s.required).put("open", s.openPlot).put("fin", s.finished)
            .put("plots", org.json.JSONArray(s.plots.map { p ->
                JSONObject().put("w", p.weight).put("c", JSONObject(p.counts.mapValues { e ->
                    JSONObject(e.value.mapKeys { g -> g.key.name })
                }.mapKeys { e -> e.key.name }))
            }))
        file(ctx).writeText(o.toString())
    }
    fun load(ctx: Context): Session? {
        val f = file(ctx); if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val s = Session(o.getDouble("area"), Category.valueOf(o.getString("cat")), Density.valueOf(o.getString("den")), o.getInt("req"))
            s.openPlot = o.optBoolean("open"); s.finished = o.optBoolean("fin")
            val arr = o.optJSONArray("plots")
            if (arr != null) for (i in 0 until arr.length()) {
                val p = Plot(arr.getJSONObject(i).getDouble("w"))
                val c = arr.getJSONObject(i).optJSONObject("c")
                if (c != null) for (sk in c.keys()) {
                    val sp = Species.valueOf(sk); val g = c.getJSONObject(sk)
                    for (gk in g.keys()) p.counts.getOrPut(sp) { mutableMapOf() }[Grade.valueOf(gk)] = g.getInt(gk)
                }
                s.plots.add(p)
            }
            s
        } catch (e: Exception) { null }
    }
    fun clear(ctx: Context) = file(ctx).delete()
}
