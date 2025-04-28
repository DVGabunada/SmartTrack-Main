package com.example.smarttrack;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import android.os.Build;
import android.graphics.Color;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.CalendarDay;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Calendar;
import java.util.Collections;


public class Teachers_Calendar extends AppCompatActivity implements EventsAdapter.OnEventActionListener {

    private ImageView roomIcon;
    private ImageView homeIcon;
    private ImageView reportIcon;
    private String uid;
    private MaterialCalendarView calendarView;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView navUsername, navIdNumber;
    private RecyclerView eventRecyclerView;
    private EventsAdapter eventsAdapter;
    private List<Event> eventList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        requestExactAlarmPermission();

        uid = getIntent().getStringExtra("uid");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Calendar");

        roomIcon = findViewById(R.id.roomIcon);
        homeIcon = findViewById(R.id.homeIcon);
        reportIcon = findViewById(R.id.reportIcon);
        calendarView = findViewById(R.id.calendarView);
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
                String url = "https://www.figma.com/proto/C6Qz5vKSZikChpnHm6OtFN/SmartTrack_Manual_Cal?node-id=54626-280&t=X9XqKBQqBTsVNBnL-1&scaling=min-zoom&content-scaling=fixed&page-id=54668%3A48";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });


        // Fetch teacher details
        fetchTeacherDetails(uid);

        // Logout button in navigation drawer
        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Teachers_Calendar.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Navigation buttons
        roomIcon.setOnClickListener(v -> navigateToActivity(Teachers_Room.class));
        homeIcon.setOnClickListener(v -> navigateToActivity(Teachers_Home.class));
        reportIcon.setOnClickListener(v -> navigateToActivity(Teachers_Report.class));

        // Floating action button for adding events
        FloatingActionButton addEventButton = findViewById(R.id.addEventButton);
        addEventButton.setOnClickListener(v -> {
            Intent intent = new Intent(Teachers_Calendar.this, Teachers_CreateEvent.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
        });

        // Initialize RecyclerView
        eventList = new ArrayList<>();
        eventsAdapter = new EventsAdapter(this, eventList, this, uid); // Pass context and listener
        eventRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventRecyclerView.setAdapter(eventsAdapter);

        Calendar currentCalendar = Calendar.getInstance();
        int currentYear = currentCalendar.get(Calendar.YEAR);
        int currentMonth = currentCalendar.get(Calendar.MONTH); // 0-based index

        fetchEvents(currentYear, currentMonth); // Load initial events
        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            if (selected) {
                String selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                        date.getYear(), date.getMonth() + 1, date.getDay()); // Convert to YYYY-MM-DD
                fetchEventsForDate(selectedDate);
            }
        });

        // Ensure Notification Channel is created
        createNotificationChannel();

        // Request Notification Permission if required
        requestNotificationPermission();

        // Highlight today's date and fetch today's events
        CalendarDay today = CalendarDay.today();
        calendarView.setCurrentDate(today);
        calendarView.setDateSelected(today, true);
        String todayDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                today.getYear(), today.getMonth() + 1, today.getDay());
        fetchEventsForDate(todayDate);
    }

    private void fetchEventsForDate(String selectedDate) {
        FirebaseFirestore.getInstance().collection("events")
                .whereEqualTo("teacherId", uid)
                .whereEqualTo("eventDate", selectedDate)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> filteredEvents = new ArrayList<>();

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Event event = document.toObject(Event.class);
                        event.setId(document.getId());
                        filteredEvents.add(event);
                    }

                    Collections.sort(filteredEvents, (e1, e2) -> Long.compare(e1.getEventTimestamp(), e2.getEventTimestamp()));

                    eventList.clear();
                    eventList.addAll(filteredEvents);
                    eventsAdapter.notifyDataSetChanged();

                    if (filteredEvents.isEmpty()) {
                        showNoEventsMessage();
                    } else {
                        hideNoEventsMessage();
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to fetch events for this date", Toast.LENGTH_SHORT).show();
                });
        scheduleRemindersForEvents();
    }

    // Method to show the "No Events" message
    private void showNoEventsMessage() {
        TextView noEventsMessage = findViewById(R.id.noEventsMessage);
        noEventsMessage.setVisibility(View.VISIBLE);
        eventRecyclerView.setVisibility(View.GONE);
    }

    // Method to hide the "No Events" message
    private void hideNoEventsMessage() {
        TextView noEventsMessage = findViewById(R.id.noEventsMessage);
        noEventsMessage.setVisibility(View.GONE);
        eventRecyclerView.setVisibility(View.VISIBLE);
    }

    private void fetchTeacherDetails(String uid) {
        FirebaseFirestore.getInstance().collection("teachers")
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

    private void fetchEvents(int selectedYear, int selectedMonth) {
        FirebaseFirestore.getInstance().collection("events")
                .whereEqualTo("teacherId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    HashSet<CalendarDay> eventDays = new HashSet<>();
                    eventList.clear(); // Clear the event list to avoid displaying all events

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Event event = document.toObject(Event.class);
                        event.setId(document.getId());

                        String eventDate = event.getEventDate();
                        if (eventDate != null && eventDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                            try {
                                String[] dateParts = eventDate.split("-");
                                int eventYear = Integer.parseInt(dateParts[0]);
                                int eventMonth = Integer.parseInt(dateParts[1]) - 1; // Calendar uses 0-based months
                                int eventDay = Integer.parseInt(dateParts[2]);

                                // Add the event day to the calendar decorators
                                eventDays.add(CalendarDay.from(eventYear, eventMonth, eventDay));

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    // Update only the decorators and do not populate the event list
                    addEventIndicatorsToCalendar(eventDays);
                    scheduleRemindersForEvents();

                    // Ensure no events are displayed initially until a date is clicked
                    eventRecyclerView.setVisibility(View.GONE);
                    showNoEventsMessage();

                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                });
    }

    private void fetchRoomsForEvent(String eventId, Event event) {
        FirebaseFirestore.getInstance().collection("events").document(eventId).collection("rooms")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> roomIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        roomIds.add(doc.getString("roomId"));
                    }
                    event.setRooms(roomIds);
                    eventsAdapter.notifyDataSetChanged();
                });
    }

    private void addEventIndicatorsToCalendar(HashSet<CalendarDay> eventDays) {
        calendarView.removeDecorators(); // Clear previous decorators
        calendarView.addDecorator(new EventDecorator(eventDays, Color.RED)); // Add event dots

        // Add today and selected date decorator
        TodayDecorator todayDecorator = new TodayDecorator(this);
        calendarView.addDecorator(todayDecorator);

        calendarView.invalidateDecorators(); // Refresh calendar view
    }

    private void scheduleRemindersForEvents() {
        for (Event event : eventList) {
            long eventTimeMillis = event.getEventTimestamp();
            if (eventTimeMillis == 0) continue; // Skip invalid events

            long oneHourBefore = eventTimeMillis - (1 * 60 * 60 * 1000); // 1 hour before

            scheduleTeacherEventReminder(event, oneHourBefore, "Reminder: " + event.getTitle() + " starts in 1 hour!");
        }
    }


    private void scheduleTeacherEventReminder(Event event, long reminderTimeMillis, String message) {
        if (reminderTimeMillis < System.currentTimeMillis()) return; // Skip past events

        Intent intent = new Intent(this, EventReminderReceiver.class);
        intent.putExtra("eventTitle", "Upcoming Event: " + event.getTitle());
        intent.putExtra("eventMessage", message);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                (event.getId() + message).hashCode(),
                intent,
                flags
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent);
            } else {
                // Alternative method if exact alarms are not allowed
                AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(reminderTimeMillis, pendingIntent);
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent);
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

    private static final int NOTIFICATION_PERMISSION_CODE = 102;

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                System.out.println("Notification permission granted.");
            } else {
                System.out.println("Notification permission denied.");
            }
        }
    }


    private void navigateToActivity(Class<?> activityClass) {
        Intent intent = new Intent(Teachers_Calendar.this, activityClass);
        intent.putExtra("uid", uid);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }
    @Override
    public void onEditEvent(Event event) {
        Intent intent = new Intent(this, Teachers_EditEvent.class);
        intent.putExtra("eventId", event.getId());
        intent.putExtra("eventTitle", event.getTitle());
        intent.putExtra("eventDescription", event.getDescription());
        intent.putExtra("eventLocation", event.getLocation());
        intent.putExtra("eventDate", event.getEventDate());
        intent.putExtra("eventStartTime", event.getStartTime());
        intent.putExtra("eventEndTime", event.getEndTime());
        intent.putExtra("notify", event.isNotify());
        intent.putExtra("wholeDay", event.isWholeDay());

        // Pass the list of selected rooms instead of students
        ArrayList<String> eventRooms = (ArrayList<String>) event.getRooms();
        intent.putStringArrayListExtra("eventRooms", eventRooms);

        startActivity(intent);
    }


    @Override
    public void onDeleteEvent(Event event) {
        FirebaseFirestore.getInstance().collection("events")
                .document(event.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    eventList.remove(event);
                    eventsAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Event deleted successfully.", Toast.LENGTH_SHORT).show();
                    refreshTeacherCalendarDecorators();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete event.", Toast.LENGTH_SHORT).show();
                });
    }
    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        CalendarDay selectedDate = calendarView.getSelectedDate();
        if (selectedDate != null) {
            String selectedDateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                    selectedDate.getYear(), selectedDate.getMonth() + 1, selectedDate.getDay());
            fetchEventsForDate(selectedDateStr);
        } else {
            Calendar currentCalendar = Calendar.getInstance();
            int currentYear = currentCalendar.get(Calendar.YEAR);
            int currentMonth = currentCalendar.get(Calendar.MONTH);
            fetchEvents(currentYear, currentMonth);
        }
        // Refresh the calendar decorators to update indicators
        refreshTeacherCalendarDecorators();
    }

    private void refreshTeacherCalendarDecorators() {
        FirebaseFirestore.getInstance().collection("events")
                .whereEqualTo("teacherId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    HashSet<CalendarDay> eventDays = new HashSet<>();

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Event event = document.toObject(Event.class);
                        event.setId(document.getId());

                        String eventDate = event.getEventDate();
                        if (eventDate != null && eventDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                            try {
                                String[] dateParts = eventDate.split("-");
                                int eventYear = Integer.parseInt(dateParts[0]);
                                int eventMonth = Integer.parseInt(dateParts[1]) - 1; // Calendar uses 0-based months
                                int eventDay = Integer.parseInt(dateParts[2]);

                                // Add the event day to the calendar decorators
                                eventDays.add(CalendarDay.from(eventYear, eventMonth, eventDay));

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    // Update only the decorators and do not populate the event list
                    addEventIndicatorsToCalendar(eventDays);
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                });
    }
}