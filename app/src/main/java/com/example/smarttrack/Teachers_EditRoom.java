package com.example.smarttrack;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class Teachers_EditRoom extends AppCompatActivity {

    private static final String TAG = "Teachers_EditRoom";

    private EditText subjectNameField, subjectCodeField, sectionField;
    private EditText startDateField, endDateField, startTimeField, endTimeField, numberOfStudentsField;
    private Button updateRoomButton, backButton;
    private FirebaseFirestore firestore;
    private Button mondayButton, tuesdayButton, wednesdayButton, thursdayButton, fridayButton, saturdayButton, sundayButton;
    private String roomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_room);

        firestore = FirebaseFirestore.getInstance();

        // Initialize views
        subjectNameField = findViewById(R.id.subjectNameField);
        subjectCodeField = findViewById(R.id.subjectCodeField);
        sectionField = findViewById(R.id.sectionField);
        startDateField = findViewById(R.id.startDateField);
        endDateField = findViewById(R.id.endDateField);
        startTimeField = findViewById(R.id.startTimeField);
        endTimeField = findViewById(R.id.endTimeField);
        numberOfStudentsField = findViewById(R.id.numberOfStudentsField);
        updateRoomButton = findViewById(R.id.createRoomButton);
        backButton = findViewById(R.id.backButton);
        mondayButton = findViewById(R.id.mondayButton);
        tuesdayButton = findViewById(R.id.tuesdayButton);
        wednesdayButton = findViewById(R.id.wednesdayButton);
        thursdayButton = findViewById(R.id.thursdayButton);
        fridayButton = findViewById(R.id.fridayButton);
        saturdayButton = findViewById(R.id.saturdayButton);
        sundayButton = findViewById(R.id.sundayButton);

        updateRoomButton.setText("Update Room");
        updateRoomButton.setEnabled(true);

        roomId = getIntent().getStringExtra("roomId");

        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Error: Room ID missing!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fetchRoomData(roomId);

      //  backButton.setOnClickListener(v -> {
       //     Intent intent = new Intent(Teachers_EditRoom.this, Teachers_Room.class);
        //    intent.putExtra("uid", FirebaseAuth.getInstance().getCurrentUser().getUid());
        //    startActivity(intent);
       //     finish();
      //  });

        backButton.setOnClickListener(v -> finish());

        startDateField.setOnClickListener(v -> showDatePicker(startDateField));
        endDateField.setOnClickListener(v -> showDatePicker(endDateField));
        startTimeField.setOnClickListener(v -> showTimePicker(startTimeField));
        endTimeField.setOnClickListener(v -> showTimePicker(endTimeField));

        updateRoomButton.setOnClickListener(v -> updateRoom());

        setupDayButtons();
    }

    private void fetchRoomData(String roomId) {
        DocumentReference roomRef = firestore.collection("rooms").document(roomId);
        roomRef.get().addOnSuccessListener(document -> {
            if (document.exists()) {
                subjectNameField.setText(document.getString("subjectName"));
                subjectCodeField.setText(document.getString("subjectCode"));
                sectionField.setText(document.getString("section"));
                startDateField.setText(document.getString("startDate"));
                endDateField.setText(document.getString("endDate"));
                numberOfStudentsField.setText(document.getString("numberOfStudents"));

                Timestamp startTime = document.getTimestamp("startTime");
                Timestamp endTime = document.getTimestamp("endTime");

                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                if (startTime != null) startTimeField.setText(timeFormat.format(startTime.toDate()));
                if (endTime != null) endTimeField.setText(timeFormat.format(endTime.toDate()));

                // Ensure schedule is stored as a List<String>
                Object scheduleObject = document.get("schedule");
                if (scheduleObject instanceof List) {
                    List<String> schedule = (List<String>) scheduleObject;
                    for (String day : schedule) {
                        highlightButton(getButtonByDay(day));
                    }
                } else {
                    Log.e(TAG, "Invalid schedule format in Firestore");
                }
            } else {
                Toast.makeText(this, "Room not found!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error fetching room data", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error fetching room data", e);
        });
    }

    private void setupDayButtons() {
        setupDayButton(mondayButton, "Monday");
        setupDayButton(tuesdayButton, "Tuesday");
        setupDayButton(wednesdayButton, "Wednesday");
        setupDayButton(thursdayButton, "Thursday");
        setupDayButton(fridayButton, "Friday");
        setupDayButton(saturdayButton, "Saturday");
        setupDayButton(sundayButton, "Sunday");
    }

    private void setupDayButton(Button button, String dayName) {
        button.setOnClickListener(v -> {
            if ("active".equals(button.getTag())) {
                button.setBackgroundResource(R.drawable.btn_default_normal);
                button.setTag(null);
            } else {
                button.setBackgroundResource(R.drawable.btn_gold);
                button.setTag("active");
            }
        });
    }

    private void highlightButton(Button button) {
        if (button != null) {
            button.setBackgroundResource(R.drawable.btn_gold);
            button.setTag("active");
        }
    }

    private Button getButtonByDay(String dayName) {
        switch (dayName) {
            case "Monday": return mondayButton;
            case "Tuesday": return tuesdayButton;
            case "Wednesday": return wednesdayButton;
            case "Thursday": return thursdayButton;
            case "Friday": return fridayButton;
            case "Saturday": return saturdayButton;
            case "Sunday": return sundayButton;
            default: return null;
        }
    }

    private void showDatePicker(EditText field) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> field.setText(dayOfMonth + "/" + (month + 1) + "/" + year),
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void showTimePicker(EditText field) {
        Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
            field.setText(time);
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void updateRoom() {
        String subjectName = subjectNameField.getText().toString().trim();
        String subjectCode = subjectCodeField.getText().toString().trim();
        String section = sectionField.getText().toString().trim();
        String startDate = startDateField.getText().toString().trim();
        String endDate = endDateField.getText().toString().trim();
        String startTime = startTimeField.getText().toString().trim();
        String endTime = endTimeField.getText().toString().trim();
        String numberOfStudents = numberOfStudentsField.getText().toString().trim();
        List<String> selectedDays = getActiveDays();

        if (subjectName.isEmpty()) {
            Toast.makeText(this, "Please enter Subject Name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (subjectCode.isEmpty()) {
            Toast.makeText(this, "Please enter Subject Code", Toast.LENGTH_SHORT).show();
            return;
        }
        if (section.isEmpty()) {
            Toast.makeText(this, "Please enter Section", Toast.LENGTH_SHORT).show();
            return;
        }

        if (startDate.isEmpty()) {
            Toast.makeText(this, "Please select the start date", Toast.LENGTH_SHORT).show();
            return;
        }

        if (endDate.isEmpty()) {
            Toast.makeText(this, "Please select the end date", Toast.LENGTH_SHORT).show();
            return;
        }

        if (startTime.isEmpty()) {
            Toast.makeText(this, "Please select the start time", Toast.LENGTH_SHORT).show();
            return;
        }

        if (endTime.isEmpty()) {
            Toast.makeText(this, "Please select the end time", Toast.LENGTH_SHORT).show();
            return;
        }

        if (numberOfStudents.isEmpty()) {
            Toast.makeText(this, "Please enter the number of students in your class", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDays.isEmpty()) {
            Toast.makeText(this, "Please select at least one weekday", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if subjectCode and section already exist in a different room
        firestore.collection("rooms")
                .whereEqualTo("subjectCode", subjectCode)
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean roomExists = false;
                    for (var doc : queryDocumentSnapshots) {
                        if (!doc.getId().equals(roomId)) {
                            roomExists = true;
                            break;
                        }
                    }

                    if (roomExists) {
                        Toast.makeText(this, "This room already exists", Toast.LENGTH_SHORT).show();
                    } else {
                        // Proceed to update the room
                        Map<String, Object> updatedData = new HashMap<>();
                        updatedData.put("subjectName", subjectName);
                        updatedData.put("subjectCode", subjectCode);
                        updatedData.put("section", section);
                        updatedData.put("startDate", startDate);
                        updatedData.put("endDate", endDate);
                        updatedData.put("startTime", convertTimeToTimestamp(startTime));
                        updatedData.put("endTime", convertTimeToTimestamp(endTime));
                        updatedData.put("numberOfStudents", numberOfStudents);
                        updatedData.put("schedule", selectedDays);

                        firestore.collection("rooms").document(roomId)
                                .update(updatedData)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Room updated successfully!", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error updating room in Firestore", e);
                                    Toast.makeText(this, "Error updating room", Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking for existing room", e);
                    Toast.makeText(this, "Failed to check for duplicate room", Toast.LENGTH_SHORT).show();
                });
    }

    private Timestamp convertTimeToTimestamp(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date date = sdf.parse(timeStr);
            return new Timestamp(date);
        } catch (Exception e) {
            return null;
        }
    }


    private List<String> getActiveDays() {
        List<String> days = new ArrayList<>();
        if ("active".equals(mondayButton.getTag())) days.add("Monday");
        if ("active".equals(tuesdayButton.getTag())) days.add("Tuesday");
        if ("active".equals(wednesdayButton.getTag())) days.add("Wednesday");
        if ("active".equals(thursdayButton.getTag())) days.add("Thursday");
        if ("active".equals(fridayButton.getTag())) days.add("Friday");
        if ("active".equals(saturdayButton.getTag())) days.add("Saturday");
        if ("active".equals(sundayButton.getTag())) days.add("Sunday");

        return days;
    }
}
