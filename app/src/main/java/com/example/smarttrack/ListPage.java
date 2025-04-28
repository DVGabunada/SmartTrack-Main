package com.example.smarttrack;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class ListPage extends AppCompatActivity {
    private FirebaseFirestore db;
    private String listTitle;
    private LinearLayout containerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list);

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());

        listTitle = getIntent().getStringExtra("title");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText(listTitle);

        // 🔹 Find the LinearLayout container where we'll add the views dynamically
        containerLayout = findViewById(R.id.containerLayout);

        db = FirebaseFirestore.getInstance();
        fetchDataFromFirestore();
    }

    private void fetchDataFromFirestore() {
        String collectionPath = "";

        if (listTitle.equals("Teachers")) {
            collectionPath = "teachers";
        } else if (listTitle.equals("Students")) {
            collectionPath = "students";
        } else if (listTitle.equals("Teacher Rooms")) {
            collectionPath = "rooms";
        }

        if (!collectionPath.isEmpty()) {
            db.collection(collectionPath)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        containerLayout.removeAllViews(); // 🔥 Clear previous views

                        for (QueryDocumentSnapshot document : querySnapshot) {
                            String title = "";
                            String subtitle = "";
                            String content = "";

                            if (listTitle.equals("Teachers") || listTitle.equals("Students")) {
                                String firstName = document.getString("firstName");
                                String lastName = document.getString("lastName");
                                String idNumber = document.getString("idNumber");
                                String email = document.getString("email");

                                title = firstName + " " + lastName;
                                subtitle = idNumber;
                                content = email;
                            } else if (listTitle.equals("Teacher Rooms")) {
                                String subjectName = document.getString("subjectName");
                                String section = document.getString("section");

                                Timestamp startTime = document.getTimestamp("startTime");
                                Timestamp endTime = document.getTimestamp("endTime");

                                String formattedTime = formatTimestamp(startTime) + " - " + formatTimestamp(endTime);

                                title = subjectName;
                                subtitle = section;
                                content = formattedTime;
                            }

                            addCardView(title, subtitle, content);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("FirestoreError", "❌ Error fetching data: " + e.getMessage());
                        Toast.makeText(ListPage.this, "Failed to fetch data", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // 🔥 Convert Firestore Timestamp to Readable String
    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            return sdf.format(timestamp.toDate());
        }
        return "N/A";
    }

    // 🔥 Dynamically Add CardView to `LinearLayout`
    private void addCardView(String title, String subtitle, String content) {
        View cardView = LayoutInflater.from(this).inflate(R.layout.card, containerLayout, false);

        TextView txtIcon = cardView.findViewById(R.id.txtIcon);
        TextView txtTitle = cardView.findViewById(R.id.title);
        TextView txtSubtitle = cardView.findViewById(R.id.subtitle);
        TextView txtContent = cardView.findViewById(R.id.content);

        txtIcon.setText(title.substring(0, 1).toUpperCase()); // 🔥 First Letter as Icon
        txtTitle.setText(title);
        txtSubtitle.setText(subtitle);
        txtContent.setText(content);

        containerLayout.addView(cardView); // 🔥 Add to `LinearLayout`
    }
}
