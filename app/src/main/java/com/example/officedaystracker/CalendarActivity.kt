package com.example.officedaystracker

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
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

        buildScreen()
        refreshCalendar()
    }

    private fun buildScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Office Attendance"
            textSize = 26f
            gravity = Gravity.CENTER
        }

        progressText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val previous = Button(this).apply {
            text = "‹"
            textSize = 24f
            setOnClickListener {
                displayedMonth.add(Calendar.MONTH, -1)
                refreshCalendar()
            }
        }

        monthTitle = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
        }

        val next = Button(this).apply {
            text = "›"
            textSize = 24f
            setOnClickListener {
                displayedMonth.add(Calendar.MONTH, 1)
                refreshCalendar()
            }
        }

        navigation.addView(
            previous,
            LinearLayout.LayoutParams(70, 60)
        )

        navigation.addView(
            monthTitle,
            LinearLayout.LayoutParams(
                0,
                60,
                1f
            )
        )

        navigation.addView(
            next,
            LinearLayout.LayoutParams(70, 60)
        )

        calendarGrid = GridLayout(this).apply {
            columnCount = 7
            rowCount = 7
        }

        val legend = TextView(this).apply {
            text = "● Office day     ○ Not attended"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 10)
        }

        root.addView(title)
        root.addView(progressText)
        root.addView(navigation)
        root.addView(calendarGrid)
        root.addView(legend)

        setContentView(root)
    }

    private fun refreshCalendar() {

        calendarGrid.removeAllViews()

        val monthFormat = SimpleDateFormat(
            "MMMM yyyy",
            Locale.getDefault()
        )

        monthTitle.text = monthFormat.format(displayedMonth.time)

        updateQuarterProgress()

        val days = arrayOf(
            "SUN",
            "MON",
            "TUE",
            "WED",
            "THU",
            "FRI",
            "SAT"
        )

        for (day in days) {
            val header = TextView(this).apply {
                text = day
                textSize = 12f
                gravity = Gravity.CENTER
            }

            calendarGrid.addView(
                header,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = 50
                    columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
                    )
                }
            )
        }

        val firstDay = displayedMonth.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)

        val startingDay = firstDay.get(Calendar.DAY_OF_WEEK) - 1
        val maxDay = displayedMonth.getActualMaximum(
            Calendar.DAY_OF_MONTH
        )

        for (i in 0 until startingDay) {
            addEmptyCell()
        }

        val today = Calendar.getInstance()

        for (day in 1..maxDay) {

            val date = displayedMonth.clone() as Calendar
            date.set(Calendar.DAY_OF_MONTH, day)

            val dateKey = SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).format(date.time)

            val attended =
                attendancePrefs.getBoolean(dateKey, false)

            val isToday =
                date.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                date.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

            addDayCell(
                day,
                attended,
                isToday
            )
        }
    }

    private fun addEmptyCell() {

        val cell = TextView(this)

        calendarGrid.addView(
            cell,
            GridLayout.LayoutParams().apply {
                width = 0
                height = 70
                columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )
            }
        )
    }

    private fun addDayCell(
        day: Int,
        attended: Boolean,
        isToday: Boolean
    ) {

        val cell = TextView(this).apply {
            text = if (attended) {
                "✓\n$day"
            } else {
                day.toString()
            }

            textSize = if (attended) 14f else 16f
            gravity = Gravity.CENTER
            setPadding(2, 2, 2, 2)

            if (attended) {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(46, 125, 50))
            } else if (isToday) {
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.LTGRAY)
            }
        }

        calendarGrid.addView(
            cell,
            GridLayout.LayoutParams().apply {
                width = 0
                height = 70
                columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )
                setMargins(2, 2, 2, 2)
            }
        )
    }

    private fun updateQuarterProgress() {

        val year = displayedMonth.get(Calendar.YEAR)
        val month = displayedMonth.get(Calendar.MONTH)

        val quarterStart = (month / 3) * 3

        var officeDays = 0

        for (m in quarterStart until quarterStart + 3) {

            val calendar = Calendar.getInstance()
            calendar.set(year, m, 1)

            val maxDay =
                calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            for (day in 1..maxDay) {

                calendar.set(Calendar.DAY_OF_MONTH, day)

                val key = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                ).format(calendar.time)

                if (attendancePrefs.getBoolean(key, false)) {
                    officeDays++
                }
            }
        }

        val quarter = (quarterStart / 3) + 1

        progressText.text =
            "Q$quarter $year  •  Office Days: $officeDays / 24"
    }
}
