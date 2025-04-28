package com.example.smarttrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.location.Location;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
public class FaceRecognition extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final float MATCH_THRESHOLD = 0.66f;
    private static final float ALLOWED_RADIUS_METERS = 2000.0f;
    private PreviewView previewView;
    private TextView instructionText;
    private Interpreter tensorFlowLite;
    private Map<String, float[]> databaseEmbeddings;
    private ExecutorService cameraExecutor;
    private boolean isFaceRecognized = false;
    private String roomId;
    private String eventId;
    private double userLongitude;
    private double userLatitude;
    private String location;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_recognition);

        previewView = findViewById(R.id.previewView);
        instructionText = findViewById(R.id.instructionText);
        cameraExecutor = Executors.newSingleThreadExecutor();
        databaseEmbeddings = new HashMap<>();

        try {
            tensorFlowLite = new Interpreter(loadModelFile());
            debugMessage("✅ TensorFlow Lite Model Loaded Successfully!");
        } catch (IOException e) {
            debugMessage("❌ ERROR: Failed to load TensorFlow Lite model");
            tensorFlowLite = null;
        }

        roomId = getIntent().getStringExtra("roomId");
        debugMessage("🔥 Room ID: " + roomId);

        eventId = getIntent().getStringExtra("eventId");
        debugMessage("🔥 Event ID: " + eventId);

        userLongitude = getIntent().getDoubleExtra("longitude", 0);
        userLatitude = getIntent().getDoubleExtra("latitude", 0);
        location = getIntent().getStringExtra("location");

        loadDatabaseEmbeddings();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT) // 🔹 Use FRONT camera
                        .build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                // 🔥 Bind camera lifecycle
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                debugMessage("✅ Camera Started Successfully!");

            } catch (Exception e) {
                debugMessage("❌ Error: Failed to start camera. " + e.getMessage());
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (isFaceRecognized) {
            imageProxy.close();
            return;
        }

        InputImage image;
        try {
            image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        } catch (Exception e) {
            debugMessage("❌ Error processing image.");
            imageProxy.close();
            return;
        }

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        FaceDetector detector = FaceDetection.getClient(options);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty()) {
                        debugMessage("✅ Face detected!");
                        cameraExecutor.execute(() -> processFaceRecognition(imageProxy));
                    } else {
                        debugMessage("❌ No face detected.");
                        imageProxy.close();
                    }
                })
                .addOnFailureListener(e -> {
                    debugMessage("❌ Face detection failed.");
                    imageProxy.close();
                });
    }

    private void processFaceRecognition(ImageProxy imageProxy) {
        Bitmap bitmap = imageProxyToBitmap(imageProxy);
        imageProxy.close();
        if (bitmap == null) {
            debugMessage("❌ Error: Failed to process image.");
            return;
        }

        float[] detectedEmbedding = extractEmbedding(bitmap);
        if (detectedEmbedding == null) {
            debugMessage("❌ Error: Failed to extract face features.");
            return;
        }

        String recognizedUid = matchFace(detectedEmbedding);
        if (recognizedUid != null) {
            if (!isFaceRecognized) {
                isFaceRecognized = true;
                debugMessage("✅ Face recognized! Recording time-in...");
                recordTimeIn(eventId, recognizedUid);
            }
        } else {
            debugMessage("❌ Face not recognized. Try again.");
        }
    }

    private String matchFace(float[] detectedEmbedding) {
        float minDistance = Float.MAX_VALUE;
        String bestMatch = null;

        if (databaseEmbeddings.isEmpty()) {
            debugMessage("❌ No stored face embeddings found in the database.");
            return null;
        }

        for (Map.Entry<String, float[]> entry : databaseEmbeddings.entrySet()) {
            float distance = calculateDistance(detectedEmbedding, entry.getValue());

            debugMessage("🔍 Checking " + entry.getKey() + " | Distance: " + distance);

            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = entry.getKey();
            }
        }

        debugMessage("🔥 Best Match: " + bestMatch + " | Distance: " + minDistance);

        return (minDistance < MATCH_THRESHOLD) ? bestMatch : null;
    }

    private float calculateDistance(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null) {
            debugMessage("❌ Error: One or both embeddings are NULL!");
            return Float.MAX_VALUE;
        }

        if (embedding1.length != embedding2.length) {
            debugMessage("❌ Error: Embeddings have different dimensions! " +
                    "Expected: 128, Found: " + embedding1.length + " and " + embedding2.length);
            return Float.MAX_VALUE;
        }

        float sum = 0;
        for (int i = 0; i < embedding1.length; i++) {
            float diff = embedding1[i] - embedding2[i];
            sum += diff * diff;
        }

        float distance = (float) Math.sqrt(sum);
        debugMessage("📏 Calculated Distance: " + distance);
        return distance;
    }


    private float[] extractEmbedding(Bitmap bitmap) {
        if (tensorFlowLite == null) {
            debugMessage("❌ TensorFlow Lite model is NULL! Cannot extract features.");
            return null;
        }

        if (bitmap == null) {
            debugMessage("❌ Error: Bitmap is NULL! Cannot extract features.");
            return null;
        }

        try {
            debugMessage("ℹ️ Resizing image to 80x80 for FaceNet model...");
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 80, 80, true);
            if (resizedBitmap == null) {
                debugMessage("❌ Error: Failed to resize bitmap.");
                return null;
            }

            debugMessage("ℹ️ Preparing TensorFlow input...");
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(80 * 80 * 3 * 4); // ✅ UINT8 model (1 byte per channel)
            inputBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[80 * 80];
            resizedBitmap.getPixels(intValues, 0, 80, 0, 0, 80, 80);

            for (int pixel : intValues) {
                inputBuffer.put((byte) (pixel & 0xFF));         // Blue
                inputBuffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
                inputBuffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
            }

            debugMessage("ℹ️ Running TensorFlow inference...");
            byte[][] output = new byte[1][512]; // ✅ Ensure output matches expected data type
            tensorFlowLite.run(inputBuffer, output);

            float[] floatOutput = new float[512];
            for (int i = 0; i < 128; i++) {
                floatOutput[i] = (output[0][i] & 0xFF) / 255.0f; // Convert UINT8 to FLOAT32
            }

            debugMessage("✅ Face feature extraction successful!");
            return floatOutput;

        } catch (Exception e) {
            debugMessage("❌ Error: Exception in feature extraction: " + e.getMessage());
            return null;
        }
    }




    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) {
            debugMessage("❌ Error: ImageProxy is NULL");
            return null;
        }

        try {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);

            byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            if (bitmap == null) {
                debugMessage("❌ Error: Failed to convert ImageProxy to Bitmap");
                return null;
            }

            debugMessage("✅ ImageProxy converted to Bitmap successfully!");
            return bitmap;
        } catch (Exception e) {
            debugMessage("❌ Exception in Bitmap conversion: " + e.getMessage());
            return null;
        }
    }

    private void loadDatabaseEmbeddings() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("face_detections").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        debugMessage("❌ No face embeddings found in the database.");
                        return;
                    }

                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        String userId = document.getId();
                        Object embeddingObject = document.get("embedding");

                        if (embeddingObject instanceof List) {
                            List<Double> embeddingList = (List<Double>) embeddingObject;
                            float[] embeddingArray = new float[embeddingList.size()];

                            for (int i = 0; i < embeddingList.size(); i++) {
                                embeddingArray[i] = embeddingList.get(i).floatValue();
                            }

                            databaseEmbeddings.put(userId, embeddingArray);
                            debugMessage("✅ Loaded embedding for user: " + userId);
                        } else {
                            debugMessage("⚠️ No valid embedding found for user: " + userId);
                        }
                    }
                })
                .addOnFailureListener(e -> debugMessage("❌ Error loading embeddings: " + e.getMessage()));
    }



    private MappedByteBuffer loadModelFile() throws IOException {
        try {
            AssetFileDescriptor fileDescriptor = getAssets().openFd("facenet.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();

            debugMessage("✅ TensorFlow Lite Model Loaded Successfully!");
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (Exception e) {
            debugMessage("❌ ERROR: Could not load TensorFlow model. " + e.getMessage());
            return null;
        }
    }

    private void recordTimeIn(String eventId, String recognizedUid) {
        if (eventId == null) {
            debugMessage("❌ Error: Event ID missing.");
            return;
        }

        if (recognizedUid == null) {
            debugMessage("❌ Error: UID missing.");
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("events").document(eventId)
                .get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc.exists()) {
                        String startTimeString = eventDoc.getString("startTime");
                        String eventDateString = eventDoc.getString("eventDate");

                        // Convert event start time from string to Timestamp
                        Timestamp startTime = convertStringToTimestamp(eventDateString, startTimeString);

                        if (startTime != null) {
                            checkRoomMappingForEvent(eventId, recognizedUid, startTime);
                        } else {
                            debugMessage("❌ Error: Failed to parse start time.");
                        }
                    } else {
                        debugMessage("❌ Error: Event not found.");
                    }
                })
                .addOnFailureListener(e -> debugMessage("❌ Error fetching event details."));
    }

    private void checkRoomMappingForEvent(String eventId, String recognizedUid, Timestamp startTime) {
        FirebaseFirestore.getInstance()
                .collection("events").document(eventId)
                .collection("rooms") // Fetch rooms mapped to the event
                .get()
                .addOnSuccessListener(eventRoomsSnapshot -> {
                    if (eventRoomsSnapshot.isEmpty()) {
                        debugMessage("❌ No rooms mapped to this event.");
                        return;
                    }

                    for (DocumentSnapshot eventRoomDoc : eventRoomsSnapshot.getDocuments()) {
                        String mappedRoomId = eventRoomDoc.getId();
                        checkStudentInRoom(eventId, mappedRoomId, recognizedUid, startTime);
                    }
                })
                .addOnFailureListener(e -> debugMessage("❌ Error fetching event rooms."));
    }

    private void checkStudentInRoom(String eventId, String mappedRoomId, String recognizedUid, Timestamp startTime) {
        FirebaseFirestore.getInstance()
                .collection("rooms").document(mappedRoomId)
                .collection("students").document(recognizedUid)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    if (studentDoc.exists()) {
                        debugMessage("✅ Student found in room: " + mappedRoomId + ". Proceeding with attendance.");
                        fetchEventLocation(eventId, mappedRoomId, recognizedUid, startTime);
                    } else {
                        debugMessage("❌ Student is NOT enrolled in room: " + mappedRoomId + ". Skipping.");
                        return;
                    }
                })
                .addOnFailureListener(e -> debugMessage("❌ Error checking student enrollment in rooms."));
    }

    private void fetchEventLocation(String eventId, String roomId, String recognizedUid, Timestamp startTime) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference roomRef = db.collection("events").document(eventId);

        roomRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists() && documentSnapshot.contains("latitude") && documentSnapshot.contains("longitude")) {
                double storedLat = documentSnapshot.getDouble("latitude");
                double storedLng = documentSnapshot.getDouble("longitude");

                boolean isSameLocation = isWithinAllowedRadius(userLatitude, userLongitude, storedLat, storedLng);
                Log.d("LocationCheck", " Status: " + isSameLocation);

                promptForAddressConfirmation(eventId, roomId, recognizedUid, startTime, isSameLocation);
            } else {
                debugMessage("❌ Room location not found!");
            }
        }).addOnFailureListener(e -> debugMessage("❌ Error: Failed to fetch room location. " + e.getMessage()));
    }

    private boolean isWithinAllowedRadius(double userLat, double userLng, double storedLat, double storedLng) {
        // Log the coordinates
        Log.d("LocationCheck", "User coordinates - Latitude: " + userLat + ", Longitude: " + userLng);
        Log.d("LocationCheck", "Stored coordinates - Latitude: " + storedLat + ", Longitude: " + storedLng);

        float[] results = new float[1];
        Location.distanceBetween(userLat, userLng, storedLat, storedLng, results);

        // Optionally log the computed distance
        Log.d("LocationCheck", "Computed distance: " + results[0] + " meters");

        return results[0] <= ALLOWED_RADIUS_METERS;
    }


    private void promptForAddressConfirmation(String eventId, String roomId, String recognizedUid, Timestamp startTime, boolean isSameLocation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.modal, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Get references to modal UI elements
        TextView modalTitle = dialogView.findViewById(R.id.modalTitle);
        TextView modalMessage = dialogView.findViewById(R.id.modalMessage);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button okayButton = dialogView.findViewById(R.id.okayButton);

        // Set modal title and message
        modalTitle.setText("Confirm Address");
        modalMessage.setText("Detected Address:\n" + location);

        cancelButton.setOnClickListener(v -> {
            debugMessage("❌ Attendance recording cancelled.");
            goToHome();
            dialog.dismiss();
        });

        okayButton.setOnClickListener(v -> {
            Timestamp timeIn = Timestamp.now();
            String status = determineStatus(timeIn, startTime);
            saveAttendanceData(eventId, roomId, recognizedUid, timeIn, status, isSameLocation);
            dialog.dismiss();
        });

        dialog.show();
    }


    private void saveAttendanceData(String eventId, String roomId, String recognizedUid, Timestamp timeIn, String status, boolean isSameLocation) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        Map<String, Object> studentData = new HashMap<>();
        studentData.put("studentId", recognizedUid);
        studentData.put("roomId", roomId);

        db.collection("events").document(eventId)
                .collection("rooms").document(roomId)
                .collection("students").document(recognizedUid)
                .set(studentData)
                .addOnSuccessListener(aVoid -> debugMessage("✅ Student document updated with ID: " + recognizedUid))
                .addOnFailureListener(e -> debugMessage("❌ Error updating student document: " + e.getMessage()));

        Map<String, Object> attendanceData = new HashMap<>();
        attendanceData.put("timeIn", timeIn);
        attendanceData.put("isSameLocationTimeIn", isSameLocation);
        attendanceData.put("statusTimeIn", status);
        attendanceData.put("locationTimeIn", location);

        db.collection("events").document(eventId)
                .collection("rooms").document(roomId)
                .collection("students").document(recognizedUid)
                .collection("attendance").document(currentDate)
                .set(attendanceData)
                .addOnSuccessListener(documentReference -> {
                    debugMessage("✅ Time In recorded successfully with status: " + status + " at location: " + location);
                    goToHome();
                })
                .addOnFailureListener(e -> debugMessage("❌ Error: Time In failed. " + e.getMessage()));
    }


    private String determineStatus(Timestamp timeIn, Timestamp startTime) {
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startTime.toDate());

        Calendar timeInCal = Calendar.getInstance();
        timeInCal.setTime(timeIn.toDate());

        // Compare only HOUR and MINUTE
        int startHour = startCal.get(Calendar.HOUR_OF_DAY);
        int startMinute = startCal.get(Calendar.MINUTE);

        int timeInHour = timeInCal.get(Calendar.HOUR_OF_DAY);
        int timeInMinute = timeInCal.get(Calendar.MINUTE);

        if (timeInHour < startHour || (timeInHour == startHour && timeInMinute <= startMinute)) {
            return "On Time"; // Time in is earlier or exactly at the start time
        } else {
            return "Late"; // Time in is after the start time
        }
    }

    private Timestamp convertStringToTimestamp(String eventDate, String startTime) {
        try {
            String dateTimeString = eventDate + " " + startTime; // Example: "2025-02-14 12:00"
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date parsedDate = dateFormat.parse(dateTimeString);

            return new Timestamp(parsedDate);
        } catch (Exception e) {
            debugMessage("❌ Error parsing date: " + e.getMessage());
            return null;
        }
    }


    private void debugMessage(String message) {
        Log.d("DEBUG_LOG", message); // ✅ Logs message to Logcat
        showToast(message);
        updateInstruction(message);
    }


    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(FaceRecognition.this, message, Toast.LENGTH_SHORT).show());
    }

    private void updateInstruction(String instruction) {
        runOnUiThread(() -> instructionText.setText(instruction));
    }

    private void goToHome() {
        startActivity(new Intent(FaceRecognition.this, Students_Home.class));
        finish();
    }

}