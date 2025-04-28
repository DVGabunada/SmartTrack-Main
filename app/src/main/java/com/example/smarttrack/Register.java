package com.example.smarttrack;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class Register extends AppCompatActivity {

    private EditText emailField, firstNameField, middleNameField, lastNameField, idNumberField, passwordField, confirmPasswordField;
    private EditText courseYearField, dobField, homeAddressField, cityAddressField, contactNumberField;
    private Spinner genderSpinner;
    private Button signUpButton;
    private TextView welcomeText, subtitleText;
    private View logoImageView;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private TextView alreadyHaveAccount;

    private boolean isNextClicked = false;
    private boolean isTeacher = false; // Default to false, set based on intent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_register);

        // Initialize Firebase services
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Initialize views
        initializeViews();

        // Get userType from Intent (default: student)
        String userType = getIntent().getStringExtra("userType");
        if (userType == null || userType.isEmpty()) {
            userType = "student"; // Default to student if no userType is provided
        }
        isTeacher = userType.equalsIgnoreCase("teacher");

        // Update subtitle text dynamically
        subtitleText.setText("Register as " + capitalizeFirstLetter(userType));

        alreadyHaveAccount.setOnClickListener(v -> navigateToLogin());

        dobField.setOnClickListener(v -> showDatePicker());

        // Hide student-specific fields if registering a teacher
        if (isTeacher) {
            toggleStudentFields(View.GONE);
            signUpButton.setText("REGISTER TEACHER");
        } else {
            signUpButton.setText("NEXT");
        }

        // Handle sign-up button click
        signUpButton.setOnClickListener(v -> {
            if (isTeacher) {
                if (validateInitialInputs()) {
                    registerTeacher();
                }
            } else {
                if (!isNextClicked) {
                    if (validateInitialInputs()) {
                        toggleInitialFields(View.GONE);
                        toggleStudentFields(View.VISIBLE);
                        welcomeText.setText("Student Information");
                        isNextClicked = true;
                    }
                } else if (validateStudentInputs()) {
                    registerStudent();
                }
            }
        });

        contactNumberField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && TextUtils.isEmpty(contactNumberField.getText().toString())) {
                contactNumberField.setText("+63");
                contactNumberField.setSelection(contactNumberField.getText().length());
            }
        });
    }

    private void initializeViews() {
        emailField = findViewById(R.id.emailField);
        firstNameField = findViewById(R.id.firstNameField);
        middleNameField = findViewById(R.id.middleNameField);
        lastNameField = findViewById(R.id.lastNameField);
        idNumberField = findViewById(R.id.idNumberField);
        passwordField = findViewById(R.id.passwordField);
        confirmPasswordField = findViewById(R.id.confirmPasswordField);
        courseYearField = findViewById(R.id.courseYearField);
        dobField = findViewById(R.id.dobPicker);
        homeAddressField = findViewById(R.id.homeAddressField);
        cityAddressField = findViewById(R.id.cityAddressField);
        contactNumberField = findViewById(R.id.contactNumberField);
        genderSpinner = findViewById(R.id.genderSpinner);
        signUpButton = findViewById(R.id.signUpButton);
        welcomeText = findViewById(R.id.welcomeText);
        subtitleText = findViewById(R.id.subtitleText);
        logoImageView = findViewById(R.id.logoImageView);
        alreadyHaveAccount = findViewById(R.id.alreadyHaveAccount);
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
            String date = dayOfMonth + "/" + (month1 + 1) + "/" + year1;
            dobField.setText(date);
        }, year, month, day);

        datePickerDialog.show();
    }

    private void toggleInitialFields(int visibility) {
        emailField.setVisibility(visibility);
        firstNameField.setVisibility(visibility);
        middleNameField.setVisibility(visibility);
        lastNameField.setVisibility(visibility);
        idNumberField.setVisibility(visibility);
        passwordField.setVisibility(visibility);
        confirmPasswordField.setVisibility(visibility);
    }

    private void toggleStudentFields(int visibility) {
        courseYearField.setVisibility(visibility);
        genderSpinner.setVisibility(visibility);
        dobField.setVisibility(visibility);
        homeAddressField.setVisibility(visibility);
        cityAddressField.setVisibility(visibility);
        contactNumberField.setVisibility(visibility);
    }

    private boolean validateInitialInputs() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();
        String idNumber = idNumberField.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!email.endsWith("@cit.edu")) {
            Toast.makeText(this, "Email must be @cit.edu domain!", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!isValidPassword(password)) {
            Toast.makeText(this, "Password must be at least 8 characters, include 1 uppercase, 1 number, and 1 special character.", Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private boolean isValidPassword(String password) {
        String passwordPattern = "^(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])(?=.*\\d).{8,}$";
        return password.matches(passwordPattern);
    }

    private boolean validateStudentInputs() {
        String contactNumber = contactNumberField.getText().toString().trim();

        if (TextUtils.isEmpty(courseYearField.getText().toString()) ||
                TextUtils.isEmpty(homeAddressField.getText().toString()) ||
                TextUtils.isEmpty(cityAddressField.getText().toString()) ||
                TextUtils.isEmpty(contactNumberField.getText().toString())) {
            Toast.makeText(this, "All student fields are required!", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!isValidContactNumber(contactNumber)) {
            Toast.makeText(this, "Invalid Contact Number! Format must be +639XXXXXXXXXX.", Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private boolean isValidContactNumber(String contactNumber) {
        String contactPattern = "^\\+63\\d{10}$";
        return contactNumber.matches(contactPattern);
    }

    private void registerTeacher() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        sendEmailVerification(() -> saveTeacherToFirestore());
                    } else {
                        Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveTeacherToFirestore() {
        String uid = firebaseAuth.getCurrentUser().getUid();

        Map<String, Object> teacherData = new HashMap<>();
        teacherData.put("email", emailField.getText().toString());
        teacherData.put("firstName", firstNameField.getText().toString());
        teacherData.put("middleName", middleNameField.getText().toString());
        teacherData.put("lastName", lastNameField.getText().toString());
        teacherData.put("idNumber", idNumberField.getText().toString());
        teacherData.put("isConfirmedByAdmin", true);

        firestore.collection("teachers").document(uid).set(teacherData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Teacher registered. Verification email sent.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void registerStudent() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        sendEmailVerification(() -> saveStudentToFirestore());
                    } else {
                        Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendEmailVerification(Runnable onSuccess) {
        firebaseAuth.getCurrentUser().sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        onSuccess.run();
                    } else {
                        Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveStudentToFirestore() {
        String uid = firebaseAuth.getCurrentUser().getUid();

        Map<String, Object> studentData = new HashMap<>();
        studentData.put("email", emailField.getText().toString());
        studentData.put("firstName", firstNameField.getText().toString());
        studentData.put("middleName", middleNameField.getText().toString());
        studentData.put("lastName", lastNameField.getText().toString());
        studentData.put("idNumber", idNumberField.getText().toString());

        firestore.collection("students").document(uid).set(studentData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Student registered successfully.", Toast.LENGTH_LONG).show();
                    finish(); // Go back to the previous screen or close registration
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void navigateToFaceRecognition(String documentId) {
        Intent intent = new Intent(this, FaceRegister.class);
        intent.putExtra("documentId", documentId);
        startActivity(intent);
        finish();
    }

    private void navigateToLogin() {
        startActivity(new Intent(this, Login.class));
        finish();
    }

    private String capitalizeFirstLetter(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }
}
