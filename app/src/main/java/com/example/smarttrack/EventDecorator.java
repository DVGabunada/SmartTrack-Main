package com.example.smarttrack;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.spans.DotSpan;
import java.util.HashSet;

public class EventDecorator implements DayViewDecorator {
    private final HashSet<CalendarDay> eventDays;
    private final int color;

    public EventDecorator(HashSet<CalendarDay> eventDays, int color) {
        this.eventDays = new HashSet<>(eventDays);
        this.color = color; // Color for the dot
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return eventDays.contains(day);
    }

    @Override
    public void decorate(DayViewFacade view) {
        view.addSpan(new DotSpan(8, color)); // Adds a small dot indicator
    }
}