package com.example.smarttrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPassword extends AppCompatActivity {

    private EditText emailField;
    private Button resetPasswordButton;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();

        // Initialize views
        emailField = findViewById(R.id.emailField);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);

        // Set click listener for the reset password button
        resetPasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailField.getText().toString().trim();

                if (validateEmail(email)) {
                    sendResetPasswordEmail(email);
                } else {
                    Toast.makeText(ForgotPassword.this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean validateEmail(String email) {
        return email != null && !email.isEmpty() && email.contains("@") && email.contains(".");
    }

    private void sendResetPasswordEmail(String email) {
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPassword.this, "Password reset link sent to " + email, Toast.LENGTH_SHORT).show();
                        redirectToLogin();
                    } else {
                        Toast.makeText(ForgotPassword.this, "Failed to send reset email. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void redirectToLogin() {
        Intent intent = new Intent(ForgotPassword.this, Login.class);
        startActivity(intent);
        finish(); // Close this activity
    }
}
