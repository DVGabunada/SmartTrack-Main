package com.example.smarttrack;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;

public class TodayDecorator implements DayViewDecorator {

    private final CalendarDay today;
    private final Drawable solidMaroonDrawable; // Solid maroon circle
    private final Drawable outlinedMaroonDrawable; // Outlined maroon circle
    private final int whiteColor;
    private CalendarDay selectedDate;

    public TodayDecorator(Context context) {
        this.today = CalendarDay.today();
        this.solidMaroonDrawable = ContextCompat.getDrawable(context, R.drawable.current_date_highlight); // Solid maroon circle
        this.outlinedMaroonDrawable = ContextCompat.getDrawable(context, R.drawable.date_highlight); // Outlined circle
        this.whiteColor = ContextCompat.getColor(context, android.R.color.white);
        this.selectedDate = null; // No date selected initially
    }

    public void setSelectedDate(CalendarDay selectedDate) {
        this.selectedDate = selectedDate;
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        // Decorate today or if a non-today date is selected
        return day != null && (day.equals(today) || (selectedDate != null && !day.equals(today) && day.equals(selectedDate)));
    }

    @Override
    public void decorate(DayViewFacade view) {
        if (selectedDate != null && selectedDate.equals(today)) {
            // Solid maroon circle for today's date
            view.setBackgroundDrawable(solidMaroonDrawable);
            view.addSpan(new ForegroundColorSpan(whiteColor)); // White text
            view.addSpan(new StyleSpan(Typeface.BOLD)); // Bold text
        } else {
            // Outlined maroon circle for selected non-today dates
            view.setBackgroundDrawable(outlinedMaroonDrawable);
            view.addSpan(new StyleSpan(Typeface.BOLD)); // Bold text
        }
    }
}
