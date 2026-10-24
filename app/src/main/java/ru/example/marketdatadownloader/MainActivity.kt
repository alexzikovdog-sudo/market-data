package ru.example.marketdatadownloader

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.io.OutputStreamWriter
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var exchange: Spinner
    private lateinit var ticker: EditText
    private lateinit var timeframe: Spinner
    private lateinit var period: Spinner
    private lateinit var startDate: EditText
    private lateinit var endDate: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var download: Button
    private var csvResult: String? = null
    private var csvName = "market_data.csv"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15,23,42)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 26, 22, 24)
            setBackgroundColor(Color.rgb(248,250,252))
        }
        scroll.addView(layout)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "📈 Market Data Downloader"
            textSize = 25f; setTextColor(Color.rgb(15,23,42)); gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        layout.addView(title)
        val subtitle = TextView(this).apply {
            text = "Исторические свечи MOEX и OKX → CSV"
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.rgb(100,116,139))
            setPadding(0,0,0,22)
        }
        layout.addView(subtitle)

        exchange = spinner(layout, "Биржа", listOf("MOEX — акции", "MOEX — фьючерсы", "OKX"))
        ticker = edit(layout, "Тикер / торговая пара", "SBER, BR-11.26 или BTC-USDT")
        timeframe = spinner(layout, "Таймфрейм", listOf("1 минута", "5 минут", "15 минут", "1 час", "4 часа", "1 день"))
        period = spinner(layout, "Период", listOf("1 месяц", "3 месяца", "6 месяцев", "1 год", "Вся история", "Свой период"))
        startDate = edit(layout, "Начало (для своего периода)", date(-30))
        endDate = edit(layout, "Конец (для своего периода)", date(0))
        val hint = TextView(this).apply {
            text = "Даты: ГГГГ-ММ-ДД. OKX: BTC-USDT или BTC/USDT. MOEX фьючерсы используют рынок forts."
            textSize = 12f; setTextColor(Color.DKGRAY); setPadding(0,8,0,14)
        }
        layout.addView(hint)
        download = Button(this).apply { text = "⬇ Скачать CSV"; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(37,99,235)) }
        layout.addView(download)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; visibility = View.GONE }
        layout.addView(progress)
        status = TextView(this).apply { textSize = 14f; setTextColor(Color.rgb(51,65,85)); setPadding(0,14,0,10) }
        layout.addView(status)
        val save = Button(this).apply { text = "💾 Сохранить полученный CSV"; isEnabled = false }
        layout.addView(save)
        download.setOnClickListener { startDownload() }
        save.setOnClickListener {
            if (csvResult != null) {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, csvName)
                }
                startActivityForResult(intent, 42)
            }
        }
        saveButton = save
    }
    private lateinit var saveButton: Button

    private fun spinner(parent: LinearLayout, label: String, items: List<String>): Spinner {
        parent.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(Color.rgb(51,65,85)); setPadding(0,10,0,4) })
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        parent.addView(s)
        return s
    }
    private fun edit(parent: LinearLayout, label: String, hintText: String): EditText {
        parent.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(Color.rgb(51,65,85)); setPadding(0,10,0,4) })
        return EditText(this).also { it.hint = hintText; it.setSingleLine(true); it.setText(hintText.takeIf { x -> label.startsWith("Начало") || label.startsWith("Конец") } ?: ""); parent.addView(it) }
    }
    private fun date(days: Int): String {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }
    private fun timeframeValue(): String = when(timeframe.selectedItemPosition) {
        0 -> "1m"; 1 -> "5m"; 2 -> "15m"; 3 -> "1H"; 4 -> "4H"; else -> "1D"
    }
    private fun parseDate(text: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(text.trim())!!.time
    private fun startDownload() {
        val symbol = ticker.text.toString().trim()
        if (symbol.isBlank()) { status.text = "Введите тикер."; return }
        val end: Long
        val start: Long
        try {
            end = if (period.selectedItemPosition == 5) parseDate(endDate.text.toString()) + 86_399_999L else System.currentTimeMillis()
            start = when(period.selectedItemPosition) {
                0 -> end - 30L*86400000; 1 -> end - 90L*86400000; 2 -> end - 180L*86400000
                3 -> end - 365L*86400000; 4 -> if (exchange.selectedItemPosition == 2) parseDate("2017-01-01") else parseDate("2016-01-01")
                else -> parseDate(startDate.text.toString())
            }
            if (start >= end) throw IllegalArgumentException("Начало должно быть раньше конца.")
        } catch (e: Exception) { status.text = "Проверьте даты: используйте ГГГГ-ММ-ДД."; return }
        download.isEnabled = false; saveButton.isEnabled = false
        progress.visibility = View.VISIBLE; progress.progress = 5; status.text = "Подключение к бирже…"
        thread {
            try {
                val csv = if (exchange.selectedItemPosition == 2) fetchOkx(symbol, timeframeValue(), start, end) else fetchMoex(symbol, timeframe.selectedItemPosition, start, end, exchange.selectedItemPosition == 1)
                runOnUiThread {
                    csvResult = csv
                    csvName = "market_${symbol.replace("/", "_").replace("-", "_")}_${timeframeValue()}_${dateFromMillis(start)}_${dateFromMillis(end)}.csv"
                    progress.progress = 100
                    status.text = "Готово: ${(csv.lines().size - 2).coerceAtLeast(0)} свечей. Нажмите «Сохранить CSV» и выберите папку."
                    saveButton.isEnabled = true; download.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Ошибка: ${e.message ?: "не удалось загрузить данные"}"; progress.progress = 0; download.isEnabled = true }
            }
        }
    }
    private fun dateFromMillis(ms: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))
    private fun request(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "MarketDataDownloader/1.0")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw Exception("HTTP $code: ${body.take(180)}")
            return body
        } finally { conn.disconnect() }
    }
    private fun fetchOkx(input: String, tf: String, start: Long, end: Long): String {
        val symbol = input.trim().replace("/", "-").uppercase(Locale.ROOT)
        val bar = when(tf) { "1m"->"1m"; "5m"->"5m"; "15m"->"15m"; "1H"->"1H"; "4H"->"4H"; else->"1D" }
        val all = sortedMapOf<Long, List<String>>()
        var before = end
        var loops = 0
        while (before > start && loops++ < 1000) {
            val url = "https://www.okx.com/api/v5/market/history-candles?instId=${enc(symbol)}&bar=$bar&after=$before&limit=300"
            val root = JSONObject(request(url))
            if (root.optString("code") != "0") throw Exception(root.optString("msg", "OKX API error"))
            val arr = root.getJSONArray("data")
            if (arr.length() == 0) break
            var oldest = Long.MAX_VALUE
            for (i in 0 until arr.length()) {
                val row = arr.getJSONArray(i); val ts = row.getString(0).toLong()
                oldest = minOf(oldest, ts)
                if (ts in start..end) all[ts] = listOf(ts.toString(), row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5))
            }
            if (oldest == Long.MAX_VALUE || oldest >= before) break
            before = oldest
            runOnUiThread { progress.progress = (10 + ((end - before).toDouble() / (end - start).coerceAtLeast(1) * 80)).toInt().coerceIn(10,90); status.text = "OKX: получено ${all.size} свечей…" }
            if (arr.length() < 2) break
        }
        if (all.isEmpty()) throw Exception("OKX не вернула свечи для $symbol. Проверьте формат пары.")
        return buildString { append("timestamp,open,high,low,close,volume\\n"); all.values.forEach { append(it.joinToString(",")).append('\\n') } }
    }
    private fun fetchMoex(symbol: String, tfIndex: Int, start: Long, end: Long, futures: Boolean): String {
        val engine = if (futures) "futures" else "stock"
        val market = if (futures) "forts" else "shares"
        val interval = when(tfIndex) { 0 -> 1; 1 -> 5; 2 -> 10; 3 -> 60; 4 -> 24; else -> 24 }
        val all = sortedMapOf<String, List<String>>()
        var startIndex = 0
        var loops = 0
        val from = dateFromMillis(start); val till = dateFromMillis(end)
        while (loops++ < 1000) {
            val url = "https://iss.moex.com/iss/engines/$engine/markets/$market/securities/${enc(symbol.uppercase(Locale.ROOT))}/candles.json?from=$from&till=$till&interval=$interval&start=$startIndex"
            val root = JSONObject(request(url))
            val candles = root.optJSONObject("candles") ?: throw Exception("Ответ MOEX без блока candles")
            val cols = candles.getJSONArray("columns"); val rows = candles.getJSONArray("data")
            if (rows.length() == 0) break
            val ix = (0 until cols.length()).associate { cols.getString(it) to it }
            val needed = listOf("begin","open","high","low","close","volume")
            if (!ix.keys.containsAll(needed)) throw Exception("Неожиданный формат свечей MOEX")
            for (i in 0 until rows.length()) {
                val r = rows.getJSONArray(i)
                val ts = r.getString(ix["begin"]!!)
                val values = listOf(ts, r.optString(ix["open"]!!), r.optString(ix["high"]!!), r.optString(ix["low"]!!), r.optString(ix["close"]!!), r.optString(ix["volume"]!!))
                all[ts] = values
            }
            startIndex += rows.length()
            runOnUiThread { progress.progress = (10 + (startIndex % 80)).coerceIn(10,90); status.text = "MOEX: получено ${all.size} свечей…" }
            if (rows.length() < 500) break
        }
        if (all.isEmpty()) throw Exception("MOEX не вернула свечи. Проверьте тикер, рынок и доступность истории.")
        return buildString { append("timestamp,open,high,low,close,volume\\n"); all.values.forEach { append(it.joinToString(",")).append('\\n') } }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == Activity.RESULT_OK && data?.data != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.use { it.write((csvResult ?: "").toByteArray(Charsets.UTF_8)) }
                status.text = "CSV сохранён: $csvName"
            } catch (e: Exception) { status.text = "Не удалось сохранить файл: ${e.message}" }
        }
    }
}
