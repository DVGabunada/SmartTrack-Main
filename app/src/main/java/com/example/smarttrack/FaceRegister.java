package com.example.smarttrack;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
public class FaceRegister extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String TAG = "FaceRegister";

    private PreviewView previewView;
    private TextView instructionText;
    private ExecutorService cameraExecutor;
    private FirebaseStorage firebaseStorage;
    private StorageReference storageReference;
    private String uid;
    private boolean isImageCaptured = false; // ✅ Prevent multiple captures
    private Interpreter tflite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_recognition);

        uid = getIntent().getStringExtra("uid");

        previewView = findViewById(R.id.previewView);
        instructionText = findViewById(R.id.instructionText);
        cameraExecutor = Executors.newSingleThreadExecutor();

        firebaseStorage = FirebaseStorage.getInstance();
        storageReference = firebaseStorage.getReference();

        try {
            tflite = new Interpreter(loadModelFile());  // ✅ Load TFLite model
            showToast("✅ TensorFlow Lite Model Loaded Successfully!");
        } catch (IOException e) {
            showToast("❌ Error: Failed to load TensorFlow Lite model.");
            e.printStackTrace();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e(TAG, "Error starting camera: ", e);
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (isImageCaptured) {  // ✅ Prevent capturing multiple times
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .build();

            FaceDetector detector = FaceDetection.getClient(options);

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (!faces.isEmpty() && !isImageCaptured) {
                            isImageCaptured = true;
                            updateInstruction("✅ Face detected! Saving image...");
                            captureAndSaveImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees(), faces);
                        } else {
                            updateInstruction("❌ No face detected.");
                        }
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Face detection failed: ", e);
                        imageProxy.close();
                    });
        } else {
            imageProxy.close();
        }
    }

    private void captureAndSaveImage(Image mediaImage, int rotationDegrees, List<Face> faces) {
        Bitmap bitmap = convertMediaImageToBitmap(mediaImage, rotationDegrees);
        if (bitmap == null) {
            showToast("❌ Failed to convert image to Bitmap.");
            return;
        }

        if (faces.isEmpty()) {
            showToast("❌ No face detected, skipping image save.");
            return;
        }

        // Get the first detected face (assuming only one person in the frame)
        Face face = faces.get(0);
        Rect faceBounds = face.getBoundingBox();

        // Crop the face from the image
        Bitmap croppedFace = cropBitmap(bitmap, faceBounds);
        if (croppedFace == null) {
            showToast("❌ Failed to crop face.");
            return;
        }

        // ✅ Extract face embedding from the cropped face
        float[] extractedEmbedding = extractEmbedding(croppedFace);
        if (extractedEmbedding == null) {
            showToast("❌ Failed to extract face embedding.");
            return;
        }

        // Convert cropped face to JPEG format
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        croppedFace.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        byte[] imageData = outputStream.toByteArray();

        // Save the cropped face to Firebase Storage
        String fileName = "face_detections/" + uid + "/profile.jpg";
        StorageReference imageRef = storageReference.child(fileName);

        showToast("✅ Uploading cropped face...");
        UploadTask uploadTask = imageRef.putBytes(imageData);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            imageRef.getDownloadUrl().addOnSuccessListener(asyncUri -> {
                showToast("✅ Upload complete. Saving embedding...");

                new Thread(() -> {
                    try {
                        Tasks.await(saveEmbeddingToFirestore(uid, extractedEmbedding, asyncUri.toString()));

                        runOnUiThread(() -> {
                            showToast("✅ Face Registered Successfully! Closing in 2 seconds...");

                            // ✅ Add a delay before finishing the activity
                            new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 2000);
                        });

                    } catch (Exception e) {
                        runOnUiThread(() -> showToast("❌ Failed to save embedding: " + e.getMessage()));
                    }
                }).start();

            }).addOnFailureListener(e -> showToast("❌ Failed to get download URL."));
        }).addOnFailureListener(e -> showToast("❌ Failed to upload image to Firebase."));
    }


    private Task<Void> saveEmbeddingToFirestore(String userId, float[] embedding, String imageUrl) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        List<Double> embeddingList = new ArrayList<>();
        for (float value : embedding) {
            embeddingList.add((double) value);
        }

        Map<String, Object> userEmbedding = new HashMap<>();
        userEmbedding.put("embedding", embeddingList); // ✅ Save the embedding
        userEmbedding.put("profile", imageUrl); // ✅ Save profile image URL

        return db.collection("face_detections").document(userId).set(userEmbedding);
    }

    private float[] extractEmbedding(Bitmap bitmap) {
        if (tflite == null) {
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
            tflite.run(inputBuffer, output);

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

    private MappedByteBuffer loadModelFile() throws IOException {
        try {
            AssetFileDescriptor fileDescriptor = getAssets().openFd("facenet.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();

            Toast.makeText(FaceRegister.this,"✅ TensorFlow Lite Model Loaded Successfully!", Toast.LENGTH_SHORT).show();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (Exception e) {
            Toast.makeText(FaceRegister.this,"❌ ERROR: Could not load TensorFlow model. ", Toast.LENGTH_SHORT).show();

            return null;
        }
    }

    private Bitmap cropBitmap(Bitmap bitmap, Rect faceBounds) {
        try {
            // Ensure the bounding box is within image limits
            int x = Math.max(faceBounds.left, 0);
            int y = Math.max(faceBounds.top, 0);
            int width = Math.min(faceBounds.width(), bitmap.getWidth() - x);
            int height = Math.min(faceBounds.height(), bitmap.getHeight() - y);

            // Crop the face from the bitmap
            return Bitmap.createBitmap(bitmap, x, y, width, height);
        } catch (Exception e) {
            showToast("❌ Error cropping face.");
            return null;
        }
    }

    private Bitmap convertMediaImageToBitmap(Image mediaImage, int rotationDegrees) {
        if (mediaImage == null) {
            Log.e(TAG, "❌ MediaImage is null");
            return null;
        }

        Image.Plane[] planes = mediaImage.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, mediaImage.getWidth(), mediaImage.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, mediaImage.getWidth(), mediaImage.getHeight()), 90, out);

        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        if (bitmap == null) {
            Log.e(TAG, "❌ Failed to decode Bitmap from byte array");
            return null;
        }

        // Apply rotation if necessary
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        Log.d(TAG, "✅ Bitmap created successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        return bitmap;
    }

    private void debugMessage(String message) {
        Log.d("DEBUG_LOG", message); // ✅ Logs message to Logcat
        showToast(message);
        updateInstruction(message);
    }


    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(FaceRegister.this, message, Toast.LENGTH_SHORT).show());
    }

    private void updateInstruction(String instruction) {
        runOnUiThread(() -> instructionText.setText(instruction));
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}