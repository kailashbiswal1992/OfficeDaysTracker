package com.example.officedaystracker

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : ComponentActivity() {

    private val attendancePrefs by lazy {
        getSharedPreferences("attendance", MODE_PRIVATE)
    }

    private var displayedMonth = Calendar.getInstance()

    private lateinit var monthTitle: TextView
    private lateinit var calendarGrid: GridLayout
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        displayedMonth.set(Calendar.DAY_OF_MONTH, 1)
        setContentView(R.layout.activity_calendar)

        monthTitle = findViewById(R.id.tvMonthTitle)
        calendarGrid = findViewById(R.id.calendarGrid)
        progressText = findViewById(R.id.tvProgress)

        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)

        btnPrev.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            refreshCalendar()
        }
        btnNext.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            refreshCalendar()
        }

        refreshCalendar()
    }

    private fun refreshCalendar() {
        calendarGrid.removeAllViews()

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthTitle.text = monthFormat.format(displayedMonth.time)

        updateQuarterProgress()

        val days = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        for (day in days) {
            val header = TextView(this).apply {
                text = day
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
                setTextColor(ContextCompat.getColor(this@CalendarActivity, android.R.color.darker_gray))
            }

            calendarGrid.addView(
                header,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = dpToPx(40)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            )
        }

        val firstDay = displayedMonth.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)

        val startingDay = firstDay.get(Calendar.DAY_OF_WEEK) - 1
        val maxDay = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 0 until startingDay) addEmptyCell()

        val today = Calendar.getInstance()

        for (day in 1..maxDay) {
            val date = displayedMonth.clone() as Calendar
            date.set(Calendar.DAY_OF_MONTH, day)

            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.time)
            val attended = attendancePrefs.getBoolean(dateKey, false)
            val isToday = date.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    date.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

            addDayCell(day, dateKey, attended, isToday)
        }
    }

    private fun addEmptyCell() {
        val cell = View(this)
        calendarGrid.addView(
            cell,
            GridLayout.LayoutParams().apply {
                width = 0
                height = dpToPx(48)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
        )
    }

    private fun addDayCell(day: Int, dateKey: String, attended: Boolean, isToday: Boolean) {
        // Build a vertical cell: day number + small dot
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = dpToPx(64)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }

        val tvDay = TextView(this).apply {
            text = day.toString()
            textSize = 16f
            gravity = Gravity.CENTER
        }

        val dot = View(this).apply {
            val size = dpToPx(6)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = dpToPx(6)
            }
        }

        // background/selection
        if (isToday) {
            container.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(2), ContextCompat.getColor(this@CalendarActivity, R.color.primary))
                setColor(ContextCompat.getColor(this@CalendarActivity, android.R.color.transparent))
            }
        }

        if (attended) {
            dot.visibility = View.VISIBLE
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@CalendarActivity, R.color.tile_attended))
            }
            tvDay.setTextColor(ContextCompat.getColor(this@CalendarActivity, android.R.color.black))
        } else {
            dot.visibility = View.INVISIBLE
            tvDay.setTextColor(ContextCompat.getColor(this@CalendarActivity, android.R.color.darker_gray))
        }

        container.addView(tvDay)
        container.addView(dot)

        // toggle attendance on tap
        container.setOnClickListener {
            val currently = attendancePrefs.getBoolean(dateKey, false)
            attendancePrefs.edit().putBoolean(dateKey, !currently).apply()
            // refresh the current month view to reflect the change
            refreshCalendar()
        }

        calendarGrid.addView(container)
    }

    private fun updateQuarterProgress() {
        val year = displayedMonth.get(Calendar.YEAR)
        val month = displayedMonth.get(Calendar.MONTH)
        val quarterStart = (month / 3) * 3

        var officeDays = 0

        for (m in quarterStart until quarterStart + 3) {
            val calendar = Calendar.getInstance()
            calendar.set(year, m, 1)
            val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (day in 1..maxDay) {
                calendar.set(Calendar.DAY_OF_MONTH, day)
                val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
                if (attendancePrefs.getBoolean(key, false)) officeDays++
            }
        }

        val quarter = (quarterStart / 3) + 1
        progressText.text = "Q$quarter $year  •  Office Days: $officeDays / 24"
    }

    private fun dpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}
