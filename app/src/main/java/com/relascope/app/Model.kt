package com.relascope.app

enum class Species(val ru: String) { SOSNA("Сосна"), BEREZA("Берёза"), LISTVENNICA("Лиственница"), EL("Ель"), KEDR("Кедр") }
enum class Grade(val ru: String) { DELOVOE("деловых"), DROVYANOE("дровяных") }
enum class Category(val ru: String) { PURE("Чистые (≥80%)"), MIXED("Смешанные (≤70%)") }
enum class Density(val ru: String) { D09("0,9–1,0"), D06("0,6–0,8"), D03("0,3–0,5") }

class Plot(val weight: Double) {
    val counts = mutableMapOf<Species, MutableMap<Grade, Int>>()
    fun add(s: Species, g: Grade) { counts.getOrPut(s) { mutableMapOf() }.merge(g, 1) { a, b -> a + b } }
    fun undoLast(): Boolean {
        for (s in counts.keys) for (g in Grade.values()) {
            val v = counts[s]?.get(g) ?: 0
            if (v > 0) { counts[s]!![g] = v - 1; return true }
        }
        return false
    }
    fun get(s: Species, g: Grade) = counts[s]?.get(g) ?: 0
    fun total() = counts.values.sumOf { it.values.sum() }
}

class Session(val areaHa: Double, val category: Category, val density: Density, val required: Int) {
    val plots = mutableListOf<Plot>()
    var openPlot = false
    var finished = false
}

object Table1 {
    private val PURE = arrayOf(intArrayOf(3,4,5,6,7), intArrayOf(3,5,7,8,11), intArrayOf(5,7,8,12,13))
    private val MIXED = arrayOf(intArrayOf(3,5,6,8,9), intArrayOf(5,6,8,11,12), intArrayOf(6,8,10,13,16))
    /** null => площадь < 3 га, учёт не проводится */
    fun plots(cat: Category, den: Density, area: Double): Int? {
        if (area < 3.0) return null
        val col = when { area < 6 -> 0; area < 11 -> 1; area < 16 -> 2; area < 26 -> 3; else -> 4 }
        val row = when (den) { Density.D09 -> 0; Density.D06 -> 1; Density.D03 -> 2 }
        return (if (cat == Category.PURE) PURE else MIXED)[row][col]
    }
}
