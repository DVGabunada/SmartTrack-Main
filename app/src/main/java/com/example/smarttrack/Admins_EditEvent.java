package com.example.smarttrack;

import static com.example.smarttrack.Teacher_Map.LOCATION_REQUEST_CODE;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.location.Geocoder;
import android.location.Address;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;


public class Admins_EditEvent extends AppCompatActivity {
    private String eventId;
    private FirebaseFirestore db;
    private double selectedLatitude;
    private double selectedLongitude;

    private EditText eventTitleField, eventDescField, locationField;
    private TextView selectedDateTextView, selectedRoomsTextView;
    private Button selectDateButton, startTimePicker, endTimePicker, editEventButton, eventBackButton, selectRoomsButton;
    private CheckBox notifyCheckBox, wholeDayCheckBox;

    private List<String> selectedRoomIds = new ArrayList<>();
    private List<String> subjectNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_event);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        eventTitleField = findViewById(R.id.eventTitleField);
        eventDescField = findViewById(R.id.eventDescField);
        locationField = findViewById(R.id.locationField);
        selectedRoomsTextView = findViewById(R.id.selectedRoomsTextView);
        selectRoomsButton = findViewById(R.id.selectRoomsButton);
        selectedDateTextView = findViewById(R.id.selectedDateTextView);
        selectDateButton = findViewById(R.id.selectDateButton);
        startTimePicker = findViewById(R.id.eventStartTimeField);
        endTimePicker = findViewById(R.id.eventEndTimeField);
        notifyCheckBox = findViewById(R.id.notifycheckBox);
        wholeDayCheckBox = findViewById(R.id.wholeDaycheckBox);
        editEventButton = findViewById(R.id.editEventButton);
        eventBackButton = findViewById(R.id.eventBackButton);

        // Get eventId from Intent
        eventId = getIntent().getStringExtra("eventId");
        selectedRoomIds = getIntent().getStringArrayListExtra("eventRooms");
        if (selectedRoomIds == null) {
            selectedRoomIds = new ArrayList<>();
        }


        // Fetch event details from Firestore
        fetchEventDetails();

        // Add a TextWatcher to the locationField
        locationField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                // Do nothing before text is changed
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                String location = charSequence.toString().trim();
                if (!location.isEmpty()) {
                    // Call method to get latitude and longitude from location
                    getLocationCoordinates(location);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // Do nothing after text is changed
            }
        });

        // Handle editing the event
        editEventButton.setOnClickListener(v -> updateEventInFirestore());

        // Set listeners for date and time pickers
        selectDateButton.setOnClickListener(v -> showDatePickerDialog());
        startTimePicker.setOnClickListener(v -> showTimePickerDialog(startTimePicker));
        endTimePicker.setOnClickListener(v -> showTimePickerDialog(endTimePicker));

        // Handle room selection
        selectRoomsButton.setOnClickListener(v -> showRoomSelectionDialog());

        // Handle back button click
        eventBackButton.setOnClickListener(v -> finish());

        // Set up location field to open the map
        locationField.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_EditEvent.this, Teacher_Map.class);
            startActivityForResult(intent, LOCATION_REQUEST_CODE);
        });

        startTimePicker.setOnClickListener(v -> {
            if (wholeDayCheckBox.isChecked()) {
                Toast.makeText(Admins_EditEvent.this, "Time start cannot be changed because Whole Day is selected", Toast.LENGTH_SHORT).show();
            } else {
                showTimePickerDialog(startTimePicker);
            }
        });

        endTimePicker.setOnClickListener(v -> {
            if (wholeDayCheckBox.isChecked()) {
                Toast.makeText(Admins_EditEvent.this, "Time end cannot be changed because Whole Day is selected", Toast.LENGTH_SHORT).show();
            } else {
                showTimePickerDialog(endTimePicker);
            }
        });

    }

    private void fetchEventDetails() {
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Populate fields with fetched data
                        eventTitleField.setText(documentSnapshot.getString("title"));
                        eventDescField.setText(documentSnapshot.getString("description"));
                        locationField.setText(documentSnapshot.getString("location"));

                        // Fetch latitude and longitude
                        double latitude = documentSnapshot.getDouble("latitude");
                        double longitude = documentSnapshot.getDouble("longitude");

                        selectedLatitude = latitude;
                        selectedLongitude = longitude;

                        String eventDate = documentSnapshot.getString("eventDate");
                        selectedDateTextView.setText("Selected Date: " + eventDate);

                        startTimePicker.setText(documentSnapshot.getString("startTime"));
                        endTimePicker.setText(documentSnapshot.getString("endTime"));
                        notifyCheckBox.setChecked(documentSnapshot.getBoolean("notify"));
                        wholeDayCheckBox.setChecked(documentSnapshot.getBoolean("wholeDay"));

                        // Fetch selected rooms from sub-collection
                        fetchRoomsFromSubcollection(eventId);
                    } else {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch event details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void getLocationCoordinates(String location) {
        // Use Geocoder to convert location into latitude and longitude
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        try {
            List<Address> addresses = geocoder.getFromLocationName(location, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                selectedLatitude = address.getLatitude();
                selectedLongitude = address.getLongitude();

            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error getting location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchRoomsFromSubcollection(String eventId) {
        db.collection("events").document(eventId).collection("rooms")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    selectedRoomIds.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        selectedRoomIds.add(doc.getString("roomId"));
                    }
                    fetchRoomNames(); // Fetch room names and update the selectedRoomsTextView
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load selected rooms", Toast.LENGTH_SHORT).show();
                });
    }

    private Map<String, String> roomIdToNameMap = new HashMap<>(); // Room ID -> Room Name

    private void fetchRoomNames() {
        db.collection("rooms")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    subjectNames.clear();
                    roomIdToNameMap.clear();

                    Calendar today = Calendar.getInstance();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String subjectName = doc.getString("subjectName");
                        String roomCode = doc.getString("roomCode");
                        String roomId = doc.getId();
                        String creatorId = doc.getString("teacherId");
                        String endDate = doc.getString("endDate"); // Get the end date from Firestore

                        if (subjectName != null && roomCode != null && endDate != null) {
                            try {
                                // Parse end date and check if it is on or after today
                                Calendar roomEndDate = Calendar.getInstance();
                                String[] dateParts = endDate.split("/");
                                int day = Integer.parseInt(dateParts[0]);
                                int month = Integer.parseInt(dateParts[1]) - 1; // 0-based month
                                int year = Integer.parseInt(dateParts[2]);

                                roomEndDate.set(year, month, day);
                                if (!roomEndDate.before(today)) {
                                    if (creatorId == null || creatorId.isEmpty()) {
                                        // Room with no creator ID, assume admin-created
                                        String fullRoomName = subjectName + " - " + roomCode + " (Created by Admin)";
                                        subjectNames.add(fullRoomName);
                                        roomIdToNameMap.put(roomId, fullRoomName);
                                    } else {
                                        // Check if the creator is an admin
                                        db.collection("administrator")
                                                .document(creatorId)
                                                .get()
                                                .addOnSuccessListener(adminDoc -> {
                                                    if (adminDoc.exists()) {
                                                        // Room created by an admin
                                                        String fullRoomName = subjectName + " - " + roomCode + " (Created by Admin)";
                                                        subjectNames.add(fullRoomName);
                                                        roomIdToNameMap.put(roomId, fullRoomName);
                                                    } else {
                                                        // Fetch the creator's name from the teachers' collection
                                                        db.collection("teachers")
                                                                .document(creatorId)
                                                                .get()
                                                                .addOnSuccessListener(document -> {
                                                                    if (document.exists()) {
                                                                        String creatorName = document.getString("firstName") + " " + document.getString("lastName");
                                                                        String fullRoomName = subjectName + " - " + roomCode + " (Created by: " + creatorName + ")";
                                                                        subjectNames.add(fullRoomName);
                                                                        roomIdToNameMap.put(roomId, fullRoomName);
                                                                    }
                                                                    updateSelectedRoomsUI();
                                                                })
                                                                .addOnFailureListener(e -> {
                                                                    Toast.makeText(this, "Failed to load teacher's name.", Toast.LENGTH_SHORT).show();
                                                                });
                                                    }
                                                    updateSelectedRoomsUI();
                                                })
                                                .addOnFailureListener(e -> {
                                                    Toast.makeText(this, "Failed to verify admin status.", Toast.LENGTH_SHORT).show();
                                                });
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    updateSelectedRoomsUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load room names", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOCATION_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String selectedLocation = data.getStringExtra("selectedLocation");
            double newLatitude = data.getDoubleExtra("latitude", 0.0);
            double newLongitude = data.getDoubleExtra("longitude", 0.0);
            if (selectedLocation != null) {
                locationField.setText(selectedLocation);
            }
            // Update selectedLatitude and selectedLongitude
            selectedLatitude = newLatitude;
            selectedLongitude = newLongitude;
        }
    }
    private void showRoomSelectionDialog() {
        boolean[] checkedRooms = new boolean[subjectNames.size() + 1];
        List<String> selectedSubjectNames = new ArrayList<>();
        List<String> selectedRoomIdsList = new ArrayList<>(selectedRoomIds); // Clone list

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Rooms");

        // Add "Select All" option at the top
        String[] roomOptions = new String[subjectNames.size() + 1];
        roomOptions[0] = "Select All"; // First option is "Select All"

        // Pre-check only rooms that are already selected for the event
        for (int i = 0; i < subjectNames.size(); i++) {
            roomOptions[i + 1] = subjectNames.get(i);
            String roomId = getRoomIdByName(subjectNames.get(i));
            if (selectedRoomIds.contains(roomId)) {
                checkedRooms[i + 1] = true; // Pre-check the room
                selectedSubjectNames.add(subjectNames.get(i)); // Add to the displayed selected rooms
            }
        }

        builder.setMultiChoiceItems(roomOptions, checkedRooms, (dialog, which, isChecked) -> {
            if (which == 0) { // "Select All" was clicked
                for (int i = 1; i < checkedRooms.length; i++) {
                    checkedRooms[i] = isChecked;
                    ((AlertDialog) dialog).getListView().setItemChecked(i, isChecked);

                    String roomName = roomOptions[i];
                    String roomId = getRoomIdByName(roomName);

                    if (isChecked) {
                        if (!selectedSubjectNames.contains(roomName)) {
                            selectedSubjectNames.add(roomName);
                        }
                        if (!selectedRoomIdsList.contains(roomId)) {
                            selectedRoomIdsList.add(roomId);
                        }
                    } else {
                        selectedSubjectNames.clear();
                        selectedRoomIdsList.clear();
                    }
                }
            } else {
                checkedRooms[which] = isChecked;
                String roomName = roomOptions[which];
                String roomId = getRoomIdByName(roomName);

                if (isChecked) {
                    selectedSubjectNames.add(roomName);
                    selectedRoomIdsList.add(roomId);
                } else {
                    selectedSubjectNames.remove(roomName);
                    selectedRoomIdsList.remove(roomId);
                    checkedRooms[0] = false;
                    ((AlertDialog) dialog).getListView().setItemChecked(0, false);
                }
            }
        });

        builder.setPositiveButton("OK", (dialog, which) -> {
            selectedRoomsTextView.setText(selectedSubjectNames.isEmpty() ? "No rooms selected" : String.join(", ", selectedSubjectNames));
            selectedRoomIds = new ArrayList<>(selectedRoomIdsList);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    private String getRoomIdByName(String roomName) {
        for (Map.Entry<String, String> entry : roomIdToNameMap.entrySet()) {
            if (entry.getValue().equals(roomName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void updateEventInFirestore() {
        String updatedTitle = eventTitleField.getText().toString().trim();
        String updatedDescription = eventDescField.getText().toString().trim();
        String updatedLocation = locationField.getText().toString().trim();
        String updatedDate = selectedDateTextView.getText().toString().replace("Selected Date: ", "").trim();
        String updatedStartTime = startTimePicker.getText().toString().trim();
        String updatedEndTime = endTimePicker.getText().toString().trim();
        boolean updatedNotify = notifyCheckBox.isChecked();
        boolean updatedWholeDay = wholeDayCheckBox.isChecked();

        if (updatedTitle.isEmpty() || updatedDescription.isEmpty() || updatedLocation.isEmpty() || updatedDate.isEmpty()) {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("title", updatedTitle);
        eventData.put("description", updatedDescription);
        eventData.put("location", updatedLocation);
        eventData.put("latitude", selectedLatitude);
        eventData.put("longitude", selectedLongitude);
        eventData.put("eventDate", updatedDate);
        eventData.put("startTime", updatedStartTime);
        eventData.put("endTime", updatedEndTime);
        eventData.put("notify", updatedNotify);
        eventData.put("wholeDay", updatedWholeDay);

        db.collection("events").document(eventId)
                .update(eventData)
                .addOnSuccessListener(aVoid -> {
                    updateRoomsSubcollection(eventId); // Update room selection in sub-collection
                    Toast.makeText(this, "Event updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    private void updateRoomsSubcollection(String eventId) {
        db.collection("events").document(eventId).collection("rooms")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Delete existing rooms
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        db.collection("events").document(eventId).collection("rooms").document(doc.getId()).delete();
                    }

                    // Add updated rooms
                    for (String roomId : selectedRoomIds) {
                        Map<String, Object> roomData = new HashMap<>();
                        roomData.put("roomId", roomId);
                        db.collection("events").document(eventId).collection("rooms")
                                .document(roomId)
                                .set(roomData);
                    }
                });
    }


    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Create a Calendar instance for the selected date
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(selectedYear, selectedMonth, selectedDay);

                    // Prevent selecting past dates
                    if (selectedDate.before(calendar)) {
                        Toast.makeText(this, "Cannot select a past date", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Update the selected date
                    selectedDateTextView.setText(String.format(Locale.getDefault(), "Selected Date: %04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay));
                },
                year, month, day
        );

        // Prevent selecting past dates in the DatePicker
        datePickerDialog.getDatePicker().setMinDate(calendar.getTimeInMillis());

        datePickerDialog.show();
    }

    private void showTimePickerDialog(Button timeButton) {
        int defaultHour, defaultMinute;
        String currentTimeText = timeButton.getText().toString().trim();
        if (!currentTimeText.isEmpty() && currentTimeText.contains(":")) {
            String[] timeParts = currentTimeText.split(":");
            defaultHour = Integer.parseInt(timeParts[0]);
            defaultMinute = Integer.parseInt(timeParts[1]);
        } else {
            Calendar calendar = Calendar.getInstance();
            defaultHour = calendar.get(Calendar.HOUR_OF_DAY);
            defaultMinute = calendar.get(Calendar.MINUTE);
        }

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);

                    if (timeButton == endTimePicker) {
                        String startTime = startTimePicker.getText().toString();
                        if (!startTime.isEmpty()) {
                            String[] startParts = startTime.split(":");
                            int startHour = Integer.parseInt(startParts[0]);
                            int startMinute = Integer.parseInt(startParts[1]);

                            if (selectedHour < startHour || (selectedHour == startHour && selectedMinute < startMinute)) {
                                Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                    }
                    timeButton.setText(formattedTime);
                },
                defaultHour, defaultMinute, false
        );
        timePickerDialog.show();
    }

    private void updateSelectedRoomsUI() {
        List<String> selectedSubjectNames = new ArrayList<>();

        for (String roomId : selectedRoomIds) {
            if (roomIdToNameMap.containsKey(roomId)) {
                selectedSubjectNames.add(roomIdToNameMap.get(roomId));
            }
        }

        selectedRoomsTextView.setText(selectedSubjectNames.isEmpty() ? "No rooms selected" : String.join(", ", selectedSubjectNames));
    }
}

