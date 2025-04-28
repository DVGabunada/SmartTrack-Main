package com.example.smarttrack;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.HashMap;
import java.util.Map;

public class GenerateCode extends AppCompatActivity {

    private TextView titleTextView;
    private TextView roomCodeTextView;
    private ImageView qrCodeImageView;
    private ImageView closeQRButton;

    private FirebaseFirestore firestore;
    private String roomCode;
    private String subjectSection;

    private ProgressDialog progressDialog;  // Define a ProgressDialog

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_code);

        firestore = FirebaseFirestore.getInstance();

        titleTextView = findViewById(R.id.titleTextView);
        roomCodeTextView = findViewById(R.id.roomCodeTextView);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        closeQRButton = findViewById(R.id.closeQRButton);

        // Initialize the ProgressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Processing...");
        progressDialog.setCancelable(false);  // Prevent dismissing while waiting

        // Get data from intent
        roomCode = getIntent().getStringExtra("roomCode");
        subjectSection = getIntent().getStringExtra("subjectSection");

        if (subjectSection != null) {
            titleTextView.setText(subjectSection);
        } else {
            titleTextView.setText("No Subject - Section Provided");
        }

        if (roomCode != null) {
            roomCodeTextView.setText("Code: " + roomCode);
            generateQRCode(roomCode);
            saveQRCodeOpenTime();
        } else {
            roomCodeTextView.setText("No Room Code Provided");
        }

        // Handle closeQRButton click
        closeQRButton.setOnClickListener(v -> closeQRCodeAndNavigate());
    }

    private void generateQRCode(String data) {
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        try {
            BitMatrix bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 500, 500);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            qrCodeImageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void saveQRCodeOpenTime() {
        firestore.collection("rooms")
                .whereEqualTo("roomCode", roomCode)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // Get the correct document using roomCode
                        String roomId = task.getResult().getDocuments().get(0).getId();

                        // Data to update
                        Map<String, Object> qrData = new HashMap<>();
                        qrData.put("status", "open");
                        qrData.put("openTime", Timestamp.now());

                        // Update the specific room
                        firestore.collection("rooms").document(roomId)
                                .update(qrData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("QR", "QR code open time saved to room: " + roomId);
                                })
                                .addOnFailureListener(e -> Log.e("QR", "Error saving QR code open time", e));
                    } else {
                        Log.e("QR", "Room not found for roomCode: " + roomCode);
                    }
                })
                .addOnFailureListener(e -> Log.e("QR", "Error fetching room details", e));
    }

    private void closeQRCodeAndNavigate() {
        // Show the loading dialog immediately
        progressDialog.show();

        firestore.collection("rooms")
                .whereEqualTo("roomCode", roomCode)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String roomId = task.getResult().getDocuments().get(0).getId();

                        // Mark the room as closed in the database
                        Map<String, Object> closeData = new HashMap<>();
                        closeData.put("status", "closed");
                        closeData.put("closeTime", Timestamp.now());

                        firestore.collection("rooms").document(roomId)
                                .update(closeData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("QR", "QR code closed for room: " + roomId);

                                    // Navigate to Admin_Room immediately after updating
                                    Intent intent = new Intent(GenerateCode.this, Admins_Room.class);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> Log.e("QR", "Error closing QR code", e));
                    } else {
                        Log.e("QR", "Room not found for roomCode: " + roomCode);
                    }

                    // Dismiss the progress dialog after Firebase operation is completed
                    progressDialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e("QR", "Error fetching room details", e);
                    progressDialog.dismiss();  // Dismiss the progress dialog even if there is an error
                });
    }

    @Override
    public void onBackPressed() {
        // Show the loading dialog immediately
        progressDialog.show();

        firestore.collection("rooms")
                .whereEqualTo("roomCode", roomCode)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String roomId = task.getResult().getDocuments().get(0).getId();

                        Map<String, Object> closeData = new HashMap<>();
                        closeData.put("status", "closed");
                        closeData.put("closeTime", Timestamp.now());

                        firestore.collection("rooms").document(roomId)
                                .update(closeData)
                                .addOnSuccessListener(aVoid -> {
                                    // After marking it as closed, navigate to Admin_Room
                                    Intent intent = new Intent(GenerateCode.this, Admins_Room.class);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> Log.e("QR", "Error closing QR code", e));
                    }

                    // Dismiss the progress dialog after Firebase operation is completed
                    progressDialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e("QR", "Error fetching room details", e);
                    progressDialog.dismiss();  // Dismiss the progress dialog even if there is an error
                });
    }
}
