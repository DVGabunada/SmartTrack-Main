package com.example.smarttrack;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Arrays;
import java.util.List;

public class MonthlyAttendanceActivity extends AppCompatActivity {
    private static final String TAG = "MonthlyAttendance";
    private RecyclerView monthRecyclerView;
    private String uid;
    private String roomId = "kU4jmZG16GWdmTOZC5ci"; // This should be dynamically set based on the logged-in user

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_attendance);

        // Get user ID from intent or FirebaseAuth
        uid = getIntent().getStringExtra("uid");
        if (uid == null) {
            uid = FirebaseAuth.getInstance().getUid();
        }

        if (uid == null) {
            Log.e(TAG, "No UID available.");
            finish();
            return;
        }

        // Initialize RecyclerView
        monthRecyclerView = findViewById(R.id.monthRecyclerView);
        monthRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // List of months
        List<String> months = Arrays.asList(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
        );

        // Set the adapter
        MonthAdapter adapter = new MonthAdapter(months, this, uid, roomId);
        monthRecyclerView.setAdapter(adapter);
    }
}
