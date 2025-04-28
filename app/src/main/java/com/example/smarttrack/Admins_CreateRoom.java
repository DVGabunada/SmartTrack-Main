package com.example.smarttrack;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Admins_CreateRoom extends AppCompatActivity {

    private EditText subjectNameField, subjectCodeField, sectionField;
    private EditText startDateField, endDateField, startTimeField, endTimeField, numberOfStudentsField;
    private Button createRoomButton;
    private Button backButton;
    private FirebaseFirestore firestore;
    private Button mondayButton, tuesdayButton, wednesdayButton, thursdayButton, fridayButton, saturdayButton, sundayButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        // Initialize Firestore
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
        createRoomButton = findViewById(R.id.createRoomButton);
        backButton = findViewById(R.id.backButton);
        mondayButton = findViewById(R.id.mondayButton);
        tuesdayButton = findViewById(R.id.tuesdayButton);
        wednesdayButton = findViewById(R.id.wednesdayButton);
        thursdayButton = findViewById(R.id.thursdayButton);
        fridayButton = findViewById(R.id.fridayButton);
        saturdayButton = findViewById(R.id.saturdayButton);
        sundayButton = findViewById(R.id.sundayButton);

        // Enable/Disable the Create Room Button
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                validateFields();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        };

        // Add text change listener to all fields
        subjectNameField.addTextChangedListener(textWatcher);
        subjectCodeField.addTextChangedListener(textWatcher);
        sectionField.addTextChangedListener(textWatcher);
        startDateField.addTextChangedListener(textWatcher);
        endDateField.addTextChangedListener(textWatcher);
        startTimeField.addTextChangedListener(textWatcher);
        endTimeField.addTextChangedListener(textWatcher);
        numberOfStudentsField.addTextChangedListener(textWatcher);

        setupDayButton(mondayButton, "Monday");
        setupDayButton(tuesdayButton, "Tuesday");
        setupDayButton(wednesdayButton, "Wednesday");
        setupDayButton(thursdayButton, "Thursday");
        setupDayButton(fridayButton, "Friday");
        setupDayButton(saturdayButton, "Saturday");
        setupDayButton(sundayButton, "Sunday");


        // Back Button
        backButton.setOnClickListener(v -> finish());


        // Date Pickers
        startDateField.setOnClickListener(v -> showDatePicker(startDateField));
        endDateField.setOnClickListener(v -> showDatePicker(endDateField));

        // Time Pickers
        startTimeField.setOnClickListener(v -> showTimePicker(startTimeField));
        endTimeField.setOnClickListener(v -> showTimePicker(endTimeField));

        createRoomButton.setOnClickListener(v -> {
            String subjectName = subjectNameField.getText().toString().trim();
            String subjectCode = subjectCodeField.getText().toString().trim();
            String section = sectionField.getText().toString().trim();
            String startDate = startDateField.getText().toString().trim();
            String endDate = endDateField.getText().toString().trim();
            String startTime = startTimeField.getText().toString().trim();
            String endTime = endTimeField.getText().toString().trim();
            String numberOfStudents = numberOfStudentsField.getText().toString().trim();
            List<String> activeDays = getActiveDays();

            if (subjectName.isEmpty()) {
                Toast.makeText(this, "Please enter Subject Name", Toast.LENGTH_SHORT).show();
            } else if (subjectCode.isEmpty()) {
                Toast.makeText(this, "Please enter Subject Code", Toast.LENGTH_SHORT).show();
            } else if (section.isEmpty()) {
                Toast.makeText(this, "Please enter Section", Toast.LENGTH_SHORT).show();
            } else if (startDate.isEmpty()) {
                Toast.makeText(this, "Please select the Start Date", Toast.LENGTH_SHORT).show();
            } else if (endDate.isEmpty()) {
                Toast.makeText(this, "Please select the End Date", Toast.LENGTH_SHORT).show();
            } else if (startTime.isEmpty()) {
                Toast.makeText(this, "Please select the Start Time", Toast.LENGTH_SHORT).show();
            } else if (endTime.isEmpty()) {
                Toast.makeText(this, "Please select the End Time", Toast.LENGTH_SHORT).show();
            } else if (numberOfStudents.isEmpty()) {
                Toast.makeText(this, "Please enter the number of students", Toast.LENGTH_SHORT).show();
            } else if (activeDays.isEmpty()) {
                Toast.makeText(this, "Please select at least one weekday", Toast.LENGTH_SHORT).show();
            } else {
                checkRoomExistence();
            }
        });

    }
        private void setupDayButton(Button button, String dayName) {
        button.setOnClickListener(v -> {
            if (button.getTag() != null && button.getTag().equals("active")) {
                button.setBackgroundResource(R.drawable.btn_default_normal);
                button.setTag(null);
            } else {
                button.setBackgroundResource(R.drawable.btn_gold);
                button.setTag("active");
            }
        });
    }


    private void showDatePicker(EditText field) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> field.setText(dayOfMonth + "/" + (month + 1) + "/" + year),
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000); // Disable past dates
        datePickerDialog.show();
    }

    private void showTimePicker(EditText field) {
        Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String time = String.format("%02d:%02d", hourOfDay, minute);
            field.setText(time);
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void validateFields() {
        // Enable button only if all fields are filled
        boolean isFormValid = !subjectNameField.getText().toString().isEmpty() &&
                !subjectCodeField.getText().toString().isEmpty() &&
                !sectionField.getText().toString().isEmpty() &&
                !startDateField.getText().toString().isEmpty() &&
                !endDateField.getText().toString().isEmpty() &&
                !startTimeField.getText().toString().isEmpty() &&
                !endTimeField.getText().toString().isEmpty() &&
                !numberOfStudentsField.getText().toString().isEmpty();

       createRoomButton.setEnabled(isFormValid);
    }

    private void checkRoomExistence() {
        List<String> activeDays = getActiveDays();

        if (subjectNameField.getText().toString().trim().isEmpty() ||
                subjectCodeField.getText().toString().trim().isEmpty() ||
                sectionField.getText().toString().trim().isEmpty() ||
                startDateField.getText().toString().trim().isEmpty() ||
                endDateField.getText().toString().trim().isEmpty() ||
                startTimeField.getText().toString().trim().isEmpty() ||
                endTimeField.getText().toString().trim().isEmpty() ||
                numberOfStudentsField.getText().toString().trim().isEmpty() ||
                activeDays.isEmpty()) {

            Toast.makeText(this, "Please fill in all required fields and select at least one weekday.", Toast.LENGTH_SHORT).show();
            return;
        }

        // If all fields are valid, continue checking for duplicates
        String subjectCode = subjectCodeField.getText().toString().trim().replaceAll("\\s+", "").toLowerCase();
        String section = sectionField.getText().toString().trim().replaceAll("\\s+", "").toLowerCase();

        firestore.collection("rooms")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (var document : queryDocumentSnapshots) {
                        String existingSubjectCode = document.getString("subjectCode");
                        String existingSection = document.getString("section");

                        if (existingSubjectCode != null && existingSection != null) {
                            existingSubjectCode = existingSubjectCode.trim().replaceAll("\\s+", "").toLowerCase();
                            existingSection = existingSection.trim().replaceAll("\\s+", "").toLowerCase();

                            if (existingSubjectCode.equals(subjectCode) && existingSection.equals(section)) {
                                Toast.makeText(this, "This room already exists!", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                    }
                    createRoom(); // Proceed if no duplicate found
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error checking room: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }



    private void createRoom() {
        String subjectName = subjectNameField.getText().toString().trim();
        String subjectCode = subjectCodeField.getText().toString().trim();
        String section = sectionField.getText().toString().trim();
        String startDate = startDateField.getText().toString().trim();
        String endDate = endDateField.getText().toString().trim();
        String startTime = startTimeField.getText().toString().trim();
        String endTime = endTimeField.getText().toString().trim();
        String numberOfStudents = numberOfStudentsField.getText().toString().trim();

        List<String> activeDays = getActiveDays();

        if (subjectName.isEmpty() || subjectCode.isEmpty() || section.isEmpty() ||
                startDate.isEmpty() || endDate.isEmpty() || startTime.isEmpty() ||
                endTime.isEmpty() || numberOfStudents.isEmpty() || activeDays.isEmpty()) {

            Toast.makeText(this, "Please fill in all fields and select at least one weekday.", Toast.LENGTH_SHORT).show();
            return;
        }

        Date startDateObject = getDateFromString(startDate + " " + startTime);
        Date endDateObject = getDateFromString(endDate + " " + endTime);

        String roomCode = "ROOM" + (int) (Math.random() * 10000);
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("subjectName", subjectName);
        roomData.put("subjectCode", subjectCode);
        roomData.put("section", section);
        roomData.put("startDate", startDate);
        roomData.put("endDate", endDate);
        roomData.put("startTime", startDateObject);
        roomData.put("endTime", endDateObject);
        roomData.put("numberOfStudents", numberOfStudents);
        roomData.put("roomCode", roomCode);
        roomData.put("adminId", FirebaseAuth.getInstance().getCurrentUser().getUid());
        roomData.put("schedule", activeDays);

        firestore.collection("rooms").add(roomData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Room Created Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error creating room: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }


    private List<String> getActiveDays() {
        List<String> days = new ArrayList<>();
        if (mondayButton.getTag() != null && mondayButton.getTag().equals("active"))
            days.add("Monday");
        if (tuesdayButton.getTag() != null && tuesdayButton.getTag().equals("active"))
            days.add("Tuesday");
        if (wednesdayButton.getTag() != null && wednesdayButton.getTag().equals("active"))
            days.add("Wednesday");
        if (thursdayButton.getTag() != null && thursdayButton.getTag().equals("active"))
            days.add("Thursday");
        if (fridayButton.getTag() != null && fridayButton.getTag().equals("active"))
            days.add("Friday");
        if (saturdayButton.getTag() != null && saturdayButton.getTag().equals("active"))
            days.add("Saturday");
        if (sundayButton.getTag() != null && sundayButton.getTag().equals("active"))
            days.add("Sunday");
        return days;
    }


    private Date getDateFromString(String dateString) {
        try {
            // Combine date and time to convert to Date object
            String[] dateParts = dateString.split(" ");
            String[] dateElements = dateParts[0].split("/");
            String[] timeElements = dateParts[1].split(":");

            int day = Integer.parseInt(dateElements[0]);
            int month = Integer.parseInt(dateElements[1]) - 1; // months are 0-based
            int year = Integer.parseInt(dateElements[2]);
            int hour = Integer.parseInt(timeElements[0]);
            int minute = Integer.parseInt(timeElements[1]);

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, hour, minute, 0);
            return calendar.getTime();
        } catch (Exception e) {
            Toast.makeText(this, "Invalid date/time format", Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}
