package com.example.smarttrack;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;

import com.google.firebase.auth.FirebaseAuth;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.CalendarDay;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;

public class Students_Calendar extends AppCompatActivity {

    private ImageView roomIcon, homeIcon, reportIcon;
    private String uid;
    private MaterialCalendarView studentCalendarView;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView navUsername, navIdNumber;
    private RecyclerView eventRecyclerView;
    private Students_EventsAdapter eventsAdapter;
    private List<Event> eventList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar_student);

        uid = getIntent().getStringExtra("uid");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Calendar");

        roomIcon = findViewById(R.id.roomIcon);
        homeIcon = findViewById(R.id.homeIcon);
        reportIcon = findViewById(R.id.reportIcon);
        studentCalendarView = findViewById(R.id.studentCalendarView);
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);
        eventRecyclerView = findViewById(R.id.eventRecyclerView);

        roomIcon.setClickable(true);
        reportIcon.setClickable(true);
        homeIcon.setClickable(true);

        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        TextView manualLink = findViewById(R.id.manualLink);

        manualLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://www.figma.com/proto/C6Qz5vKSZikChpnHm6OtFN/SmartTrack_Manual_Cal?node-id=54626-577&t=qEiJZ5FpGPn1zyVw-1&scaling=min-zoom&content-scaling=fixed&page-id=54668%3A49";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });


        // Logout button in navigation drawer
        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Students_Calendar.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Navigation buttons
        roomIcon.setOnClickListener(v -> navigateToActivity(Students_Room.class));
        homeIcon.setOnClickListener(v -> navigateToActivity(Students_Home.class));
        reportIcon.setOnClickListener(v -> navigateToActivity(Students_Report.class));

        // Fetch student details
        fetchStudentDetails(uid);


        // Initialize RecyclerView
        eventList = new ArrayList<>();
        eventsAdapter = new Students_EventsAdapter(this, eventList, event -> {
            // Navigate to Students_EventDetails
            Intent intent = new Intent(Students_Calendar.this, Students_EventDetails.class);
            intent.putExtra("eventId", event.getId()); // Pass event ID
            startActivity(intent);
        });
        eventRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventRecyclerView.setAdapter(eventsAdapter);


        // Fetch events from Firestore
        // Initialize with the current month
        Calendar currentCalendar = Calendar.getInstance();
        int currentYear = currentCalendar.get(Calendar.YEAR);
        int currentMonth = currentCalendar.get(Calendar.MONTH); // 0-based index
        fetchEvents(currentYear, currentMonth);

        // Add a date click listener to fetch events only for the selected day:
        studentCalendarView.setOnDateChangedListener((widget, date, selected) -> {
            int selectedYear = date.getYear();
            int selectedMonth = date.getMonth();
            int selectedDay = date.getDay();
            // Fetch events for the specific day
            fetchEventsForDay(selectedYear, selectedMonth, selectedDay);
        });

        // Ensure Notification Channel is created
        createNotificationChannel();

        requestNotificationPermission();

        // Apply the TodayDecorator immediately
        TodayDecorator todayDecorator = new TodayDecorator(this);
        studentCalendarView.addDecorator(todayDecorator);

        // Set the selected date to today initially
        todayDecorator.setSelectedDate(CalendarDay.today());
        studentCalendarView.invalidateDecorators(); // Ensure the decorator is applied

        // Highlight today's date and fetch today's events
        CalendarDay today = CalendarDay.today();
        studentCalendarView.setDateSelected(today, true);
        fetchEventsForDay(today.getYear(), today.getMonth(), today.getDay());
    }

    private void fetchStudentDetails(String uid) {
        FirebaseFirestore.getInstance().collection("students")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String idNumber = document.getString("idNumber");
                        navUsername.setText(firstName + " " + lastName);
                        navIdNumber.setText(idNumber);
                    }
                })
                .addOnFailureListener(e -> {
                    navUsername.setText("Error fetching details");
                    navIdNumber.setText("");
                });
    }

    private void navigateToActivity(Class<?> activityClass) {
        Intent intent = new Intent(Students_Calendar.this, activityClass);
        intent.putExtra("uid", uid);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void fetchEvents(int selectedYear, int selectedMonth) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<String> studentRooms = new ArrayList<>();

        // Hide the event list initially and show "No events" message
        eventRecyclerView.setVisibility(View.GONE);
        showNoEventsMessage();

        // Fetch all rooms the student is part of
        db.collection("rooms")
                .get()
                .addOnSuccessListener(roomsSnapshot -> {
                    for (QueryDocumentSnapshot roomDoc : roomsSnapshot) {
                        String roomId = roomDoc.getId();
                        db.collection("rooms").document(roomId).collection("students").document(uid)
                                .get()
                                .addOnSuccessListener(studentDoc -> {
                                    if (studentDoc.exists()) {
                                        studentRooms.add(roomId);
                                    }
                                });
                    }
                })
                .addOnCompleteListener(task -> {
                    // Fetch event dates for calendar decorators only
                    db.collection("events").get().addOnSuccessListener(eventsSnapshot -> {
                        HashSet<CalendarDay> eventDays = new HashSet<>();

                        for (QueryDocumentSnapshot eventDoc : eventsSnapshot) {
                            String eventDate = eventDoc.getString("eventDate");

                            if (eventDate != null) {
                                try {
                                    String[] dateParts = eventDate.split("-");
                                    int eventYear = Integer.parseInt(dateParts[0]);
                                    int eventMonth = Integer.parseInt(dateParts[1]) - 1; // 0-based month
                                    int eventDay = Integer.parseInt(dateParts[2]);

                                    String eventId = eventDoc.getId();

                                    db.collection("events").document(eventId).collection("rooms").get()
                                            .addOnSuccessListener(roomSnapshots -> {
                                                List<String> eventRooms = new ArrayList<>();
                                                for (QueryDocumentSnapshot roomDoc : roomSnapshots) {
                                                    eventRooms.add(roomDoc.getString("roomId"));
                                                }

                                                for (String studentRoom : studentRooms) {
                                                    if (eventRooms.contains(studentRoom)) {
                                                        eventDays.add(CalendarDay.from(eventYear, eventMonth, eventDay));
                                                        break;
                                                    }
                                                }

                                                // Add only decorators without modifying event list
                                                addEventIndicatorsToCalendar(eventDays);
                                            });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                });
    }


    // Fetch events for a specific day
    private void fetchEventsForDay(int selectedYear, int selectedMonth, int selectedDay) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<String> studentRooms = new ArrayList<>();

        // Get the list of rooms the student is part of
        db.collection("rooms")
                .get()
                .addOnSuccessListener(roomsSnapshot -> {
                    for (QueryDocumentSnapshot roomDoc : roomsSnapshot) {
                        String roomId = roomDoc.getId();
                        db.collection("rooms")
                                .document(roomId)
                                .collection("students")
                                .document(uid)
                                .get()
                                .addOnSuccessListener(studentDoc -> {
                                    if (studentDoc.exists()) {
                                        studentRooms.add(roomId);
                                    }
                                });
                    }
                })
                .addOnCompleteListener(task -> {
                    // Once the rooms are fetched, query events for that specific day
                    fetchFilteredEventsForDay(studentRooms, selectedYear, selectedMonth, selectedDay);
                });
    }

    private void fetchFilteredEventsForDay(List<String> studentRooms, int selectedYear, int selectedMonth, int selectedDay) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        eventList.clear(); // Clear previous events to avoid duplicates

        db.collection("events").get().addOnSuccessListener(eventsSnapshot -> {
            List<Event> filteredEvents = new ArrayList<>(); // Temporary list to store events

            for (QueryDocumentSnapshot eventDoc : eventsSnapshot) {
                Event event = eventDoc.toObject(Event.class);
                event.setId(eventDoc.getId());
                String eventDate = eventDoc.getString("eventDate");

                if (eventDate != null) {
                    try {
                        // Parse the event date (assumes format "YYYY-MM-DD")
                        String[] dateParts = eventDate.split("-");
                        int eventYear = Integer.parseInt(dateParts[0]);
                        int eventMonth = Integer.parseInt(dateParts[1]) - 1; // Convert to 0-based month
                        int eventDay = Integer.parseInt(dateParts[2]);

                        // Only include events matching the clicked day
                        if (eventYear == selectedYear && eventMonth == selectedMonth && eventDay == selectedDay) {
                            String eventId = eventDoc.getId();
                            db.collection("events").document(eventId).collection("rooms").get()
                                    .addOnSuccessListener(roomSnapshots -> {
                                        List<String> eventRooms = new ArrayList<>();
                                        for (QueryDocumentSnapshot roomDoc : roomSnapshots) {
                                            eventRooms.add(roomDoc.getString("roomId"));
                                        }
                                        for (String studentRoom : studentRooms) {
                                            if (eventRooms.contains(studentRoom)) {
                                                filteredEvents.add(event);
                                                break; // Prevent duplicate additions
                                            }
                                        }

                                        // Sort events by start time
                                        Collections.sort(filteredEvents, Comparator.comparingLong(Event::getEventTimestamp));

                                        eventList.clear();
                                        eventList.addAll(filteredEvents);
                                        eventsAdapter.notifyDataSetChanged();

                                        if (filteredEvents.isEmpty()) {
                                            showNoEventsMessage();
                                        } else {
                                            hideNoEventsMessage();
                                            scheduleRemindersForEvents(filteredEvents);
                                        }
                                    });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (filteredEvents.isEmpty()) {
                showNoEventsMessage();
                eventList.clear();
                eventsAdapter.notifyDataSetChanged();
            }
        });

    }


    private void showNoEventsMessage() {
        TextView noEventsMessage = findViewById(R.id.noEventsMessageS);
        noEventsMessage.setText("No events for this date.");
        noEventsMessage.setVisibility(View.VISIBLE);
        eventRecyclerView.setVisibility(View.GONE);
    }

    private void hideNoEventsMessage() {
        TextView noEventsMessage = findViewById(R.id.noEventsMessageS);
        noEventsMessage.setVisibility(View.GONE);
        eventRecyclerView.setVisibility(View.VISIBLE);
    }

    private void fetchFilteredEventsForRooms(List<String> studentRooms) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        HashSet<String> addedEventIds = new HashSet<>(); // Prevent duplicate events
        HashSet<CalendarDay> eventDays = new HashSet<>();
        List<Event> newOrEditedEvents = new ArrayList<>();

        db.collection("events").get().addOnSuccessListener(eventsSnapshot -> {
            eventList.clear();

            for (QueryDocumentSnapshot eventDoc : eventsSnapshot) {
                Event event = eventDoc.toObject(Event.class);
                event.setId(eventDoc.getId());
                String eventDate = eventDoc.getString("eventDate");
                boolean notify = Boolean.TRUE.equals(eventDoc.getBoolean("notify"));

                if (eventDate != null) {
                    try {
                        String[] dateParts = eventDate.split("-");
                        int eventYear = Integer.parseInt(dateParts[0]);
                        int eventMonth = Integer.parseInt(dateParts[1]) - 1; // Calendar uses 0-based months
                        int eventDay = Integer.parseInt(dateParts[2]);

                        String eventId = eventDoc.getId();

                        db.collection("events").document(eventId).collection("rooms").get()
                                .addOnSuccessListener(roomSnapshots -> {
                                    List<String> eventRooms = new ArrayList<>();
                                    for (QueryDocumentSnapshot roomDoc : roomSnapshots) {
                                        eventRooms.add(roomDoc.getString("roomId"));
                                    }

                                    for (String studentRoom : studentRooms) {
                                        if (eventRooms.contains(studentRoom) && !addedEventIds.contains(eventId)) {
                                            eventList.add(event);
                                            eventDays.add(CalendarDay.from(eventYear, eventMonth, eventDay));
                                            addedEventIds.add(eventId);

                                            if (notify) {
                                                newOrEditedEvents.add(event);
                                            }
                                            break;
                                        }
                                    }

                                    // Update UI once all events are processed
                                    eventsAdapter.notifyDataSetChanged();
                                    addEventIndicatorsToCalendar(eventDays);
                                    scheduleRemindersForEvents(newOrEditedEvents);
                                });

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void addEventIndicatorsToCalendar(HashSet<CalendarDay> eventDays) {
        // Clear previous decorators to avoid duplication
        studentCalendarView.removeDecorators();

        // Add event dots for all days with events
        studentCalendarView.addDecorator(new EventDecorator(eventDays, Color.RED));

        // Initialize and add the TodayDecorator
        TodayDecorator todayDecorator = new TodayDecorator(this);
        studentCalendarView.addDecorator(todayDecorator);

        // Set the date change listener to handle selected date highlighting
        studentCalendarView.setOnDateChangedListener((widget, date, selected) -> {
            if (selected) {
                fetchEventsForDay(date.getYear(), date.getMonth(), date.getDay());

                // Update the selected date in the decorator and refresh
                todayDecorator.setSelectedDate(date);
                studentCalendarView.invalidateDecorators(); // Refresh to apply the decorator
            }
        });
    }


    private void scheduleEventReminder(Event event, long reminderTimeMillis, String message) {
        if (reminderTimeMillis < System.currentTimeMillis()) return; // Skip past events

        Intent intent = new Intent(this, EventReminderReceiver.class);
        intent.putExtra("eventTitle", "Upcoming Event: " + event.getTitle());
        intent.putExtra("eventMessage", message);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                event.getId().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE // ✅ FIXED
        );


        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent);
    }


    private void scheduleRemindersForEvents(List<Event> events) {
        for (Event event : events) {
            long eventTimeMillis = event.getEventTimestamp();
            if (eventTimeMillis == 0) continue; // Skip invalid events

            long oneHourBefore = eventTimeMillis - (1 * 60 * 60 * 1000); // 1 hour before

            scheduleEventReminder(event, oneHourBefore, "Reminder: " + event.getTitle() + " starts in 1 hour!");
        }
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "event_reminder",
                    "Event Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh events based on the currently selected date, or all if none is selected
        CalendarDay selectedDate = studentCalendarView.getSelectedDate();
        if (selectedDate != null) {
            fetchEventsForDay(selectedDate.getYear(), selectedDate.getMonth(), selectedDate.getDay());
        } else {
            Calendar currentCalendar = Calendar.getInstance();
            int currentYear = currentCalendar.get(Calendar.YEAR);
            int currentMonth = currentCalendar.get(Calendar.MONTH);
            fetchEvents(currentYear, currentMonth);
        }
        // Refresh the calendar decorators to update the indicators
        refreshStudentCalendarDecorators();
    }

    private void refreshStudentCalendarDecorators() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<String> studentRooms = new ArrayList<>();

        db.collection("rooms")
                .get()
                .addOnSuccessListener(roomsSnapshot -> {
                    for (QueryDocumentSnapshot roomDoc : roomsSnapshot) {
                        String roomId = roomDoc.getId();
                        db.collection("rooms").document(roomId).collection("students").document(uid)
                                .get()
                                .addOnSuccessListener(studentDoc -> {
                                    if (studentDoc.exists()) {
                                        studentRooms.add(roomId);
                                    }
                                });
                    }
                })
                .addOnCompleteListener(task -> {
                    // Fetch event dates for calendar decorators only
                    db.collection("events").get().addOnSuccessListener(eventsSnapshot -> {
                        HashSet<CalendarDay> eventDays = new HashSet<>();

                        for (QueryDocumentSnapshot eventDoc : eventsSnapshot) {
                            String eventDate = eventDoc.getString("eventDate");

                            if (eventDate != null) {
                                try {
                                    String[] dateParts = eventDate.split("-");
                                    int eventYear = Integer.parseInt(dateParts[0]);
                                    int eventMonth = Integer.parseInt(dateParts[1]) - 1; // 0-based month
                                    int eventDay = Integer.parseInt(dateParts[2]);

                                    String eventId = eventDoc.getId();

                                    db.collection("events").document(eventId).collection("rooms").get()
                                            .addOnSuccessListener(roomSnapshots -> {
                                                List<String> eventRooms = new ArrayList<>();
                                                for (QueryDocumentSnapshot roomDoc : roomSnapshots) {
                                                    eventRooms.add(roomDoc.getString("roomId"));
                                                }

                                                for (String studentRoom : studentRooms) {
                                                    if (eventRooms.contains(studentRoom)) {
                                                        eventDays.add(CalendarDay.from(eventYear, eventMonth, eventDay));
                                                        break;
                                                    }
                                                }
                                                // Add only decorators without modifying event list
                                                addEventIndicatorsToCalendar(eventDays);
                                            });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                });
    }
}