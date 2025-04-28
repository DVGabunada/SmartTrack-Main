package com.example.smarttrack;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Admins_Report extends AppCompatActivity {
    private static final String TAG = "Admins_Report";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView navUsername, navIdNumber;

    private TextView tvStartDate, tvEndDate;
    private Button btnFilter;
    private RecyclerView roomsRecyclerView;
    private RoomsAdapter roomsAdapter;

    // For the bottom nav
    private ImageView homeIcon, roomIcon, scheduleIcon;

    // For search and sort
    private EditText searchField;
    private ImageView sortButton;
    private boolean isAscending = false;
    private List<RoomItem> originalRoomList = new ArrayList<>();

    // Date range strings
    private String selectedStartDate = null;
    private String selectedEndDate = null;

    // Admin UID (if needed)
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        // Get UID (if passed from previous activity)
        uid = getIntent().getStringExtra("uid");
        if (uid == null) {
            uid = FirebaseAuth.getInstance().getUid();
        }

        setupToolbar();
        setupDrawerAndNav();
        setupDateRangeFilter();
        setupRecyclerView();
        setupBottomNavigation();
        setupSearchAndSort(); // Setup search and sort functionality

        // Optionally fetch admin user details for the nav header
        fetchAdminDetails(uid);


        TextView manualLink = findViewById(R.id.manualLink);

        manualLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://www.figma.com/proto/Jm2THxINfHdGGAZCXLuMUr/REPORTTTTTTTTTTTTTTTTTS?node-id=1-172&t=f1LzElqKVIzdVkTN-1&scaling=min-zoom&content-scaling=fixed&page-id=0%3A1";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });


    }



    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Reports (Admin)");
    }

    private void setupDrawerAndNav() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);

        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        Button logoutButton = navigationView.findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Admins_Report.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void setupDateRangeFilter() {
        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate = findViewById(R.id.tvEndDate);
        btnFilter = findViewById(R.id.btnFilter);

        tvStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        tvEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        // When Filter is clicked, load ALL rooms in Firestore with a loading dialog
        btnFilter.setOnClickListener(v -> {
            if (selectedStartDate == null || selectedEndDate == null) {
                Toast.makeText(this, "Please select both start and end date.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show a loading (progress) dialog
            ProgressDialog progressDialog = new ProgressDialog(Admins_Report.this);
            progressDialog.setMessage("Loading rooms...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            loadAllRooms(progressDialog);
        });
    }

    private void showDatePickerDialog(boolean isStart) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);  // 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (DatePicker view, int selectedYear, int selectedMonth, int selectedDay) -> {
                    String dateStr = selectedYear + "-" + (selectedMonth + 1) + "-" + selectedDay;
                    if (isStart) {
                        selectedStartDate = dateStr;
                        tvStartDate.setText(dateStr);
                    } else {
                        selectedEndDate = dateStr;
                        tvEndDate.setText(dateStr);
                    }
                },
                year,
                month,
                day
        );
        datePickerDialog.show();
    }

    private void setupRecyclerView() {
        roomsRecyclerView = findViewById(R.id.roomsRecyclerView);
        roomsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomsAdapter = new RoomsAdapter(new ArrayList<>());
        roomsRecyclerView.setAdapter(roomsAdapter);
    }

    /**
     * Query ALL rooms from Firestore. Once the rooms are fetched, we dismiss the progress dialog.
     */
    private void loadAllRooms(ProgressDialog progressDialog) {
        FirebaseFirestore.getInstance()
                .collection("rooms")
                .get()
                .addOnCompleteListener(task -> {
                    // Dismiss the progress dialog once data is loaded or fails
                    progressDialog.dismiss();

                    if (!task.isSuccessful() || task.getResult() == null) {
                        Toast.makeText(this, "Error loading rooms", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error fetching rooms", task.getException());
                        return;
                    }

                    List<RoomItem> roomList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String roomId = doc.getId();
                        String subjectCode = doc.getString("subjectCode");
                        String section = doc.getString("section");

                        // Check if the doc has teacherId or adminId
                        String teacherId = doc.getString("teacherId");
                        String adminId = doc.getString("adminId");

                        String creatorId = null;
                        if (teacherId != null) {
                            creatorId = teacherId;
                        } else if (adminId != null) {
                            creatorId = adminId;
                        }

                        if (creatorId == null) {
                            creatorId = "UnknownCreator";
                        }

                        if (subjectCode == null) subjectCode = "UnknownSubject";
                        if (section == null) section = "UnknownSection";

                        roomList.add(new RoomItem(roomId, subjectCode, section, creatorId));
                    }

                    // Save the full list for filtering and sorting
                    originalRoomList = new ArrayList<>(roomList);
                    // Update the adapter with the complete list
                    roomsAdapter.setRooms(roomList);
                });
    }

    private void setupBottomNavigation() {
        homeIcon = findViewById(R.id.homeIcon);
        roomIcon = findViewById(R.id.roomIcon);
        scheduleIcon = findViewById(R.id.scheduleIcon);

        homeIcon.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Report.this, Admins_Home.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        roomIcon.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Report.this, Admins_Room.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        scheduleIcon.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Report.this, Admins_Calendar.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }

    /**
     * Sets up search and sort functionality.
     */
    private void setupSearchAndSort() {
        searchField = findViewById(R.id.searchField);
        sortButton = findViewById(R.id.sortButton);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRooms(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        sortButton.setOnClickListener(v -> sortRooms());
    }

    /**
     * Filters the room list based on subject code, section, room ID, creator ID, or creator name.
     */
    private void filterRooms(String query) {
        query = query.trim().toLowerCase();
        List<RoomItem> filteredList = new ArrayList<>();
        if (query.isEmpty()) {
            filteredList.addAll(originalRoomList);
        } else {
            for (RoomItem room : originalRoomList) {
                if ((room.subjectCode != null && room.subjectCode.toLowerCase().contains(query)) ||
                        (room.section != null && room.section.toLowerCase().contains(query)) ||
                        (room.roomId != null && room.roomId.toLowerCase().contains(query)) ||
                        (room.creatorId != null && room.creatorId.toLowerCase().contains(query)) ||
                        (room.creatorName != null && room.creatorName.toLowerCase().contains(query))) {
                    filteredList.add(room);
                }
            }
        }
        roomsAdapter.setRooms(filteredList);
    }

    /**
     * Sorts the room list by subject code in ascending or descending order.
     */
    private void sortRooms() {
        isAscending = !isAscending;
        String query = searchField.getText().toString().trim().toLowerCase();
        List<RoomItem> listToSort = new ArrayList<>();
        if (query.isEmpty()) {
            listToSort.addAll(originalRoomList);
        } else {
            // Use the filtered list if a search query is active
            for (RoomItem room : originalRoomList) {
                if ((room.subjectCode != null && room.subjectCode.toLowerCase().contains(query)) ||
                        (room.section != null && room.section.toLowerCase().contains(query)) ||
                        (room.roomId != null && room.roomId.toLowerCase().contains(query)) ||
                        (room.creatorId != null && room.creatorId.toLowerCase().contains(query)) ||
                        (room.creatorName != null && room.creatorName.toLowerCase().contains(query))) {
                    listToSort.add(room);
                }
            }
        }
        Collections.sort(listToSort, new Comparator<RoomItem>() {
            @Override
            public int compare(RoomItem r1, RoomItem r2) {
                String s1 = (r1.subjectCode == null) ? "" : r1.subjectCode;
                String s2 = (r2.subjectCode == null) ? "" : r2.subjectCode;
                return isAscending ? s1.compareToIgnoreCase(s2) : s2.compareToIgnoreCase(s1);
            }
        });
        roomsAdapter.setRooms(listToSort);
    }

    /**
     * Fetch admin user details for the navigation drawer (from the "administrator" collection).
     */
    private void fetchAdminDetails(String uid) {
        FirebaseFirestore.getInstance().collection("administrator")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String idNumber = document.getString("idNumber");

                        navUsername.setText((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : ""));
                        navIdNumber.setText(idNumber != null ? idNumber : "");
                    } else {
                        // If no doc found, log it (common cause: mismatch between UID and doc ID)
                        navUsername.setText("Admin not found");
                        navIdNumber.setText("");
                        Log.e(TAG, "No administrator document found for UID: " + uid);
                    }
                })
                .addOnFailureListener(e -> {
                    navUsername.setText("Error fetching details");
                    navIdNumber.setText("");
                    Log.e(TAG, "Error fetching admin details", e);
                });
    }

    // ----------------------------------------------------------------------
    // RoomsAdapter (inner class)
    // ----------------------------------------------------------------------
    private class RoomsAdapter extends RecyclerView.Adapter<RoomsAdapter.RoomViewHolder> {
        private List<RoomItem> rooms;

        public RoomsAdapter(List<RoomItem> rooms) {
            this.rooms = rooms;
        }

        public void setRooms(List<RoomItem> updatedList) {
            this.rooms = updatedList;
            notifyDataSetChanged();
        }

        @Override
        public RoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_room, parent, false);
            return new RoomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RoomViewHolder holder, int position) {
            RoomItem roomItem = rooms.get(position);
            // Show subject code and section
            String display = roomItem.subjectCode + " - " + roomItem.section;
            holder.tvRoomTitle.setText(display);

            // Show a temporary text until the name is fetched
            holder.tvRoomCreator.setText("Created by: Loading...");

            // Asynchronously fetch the creator's name by checking both collections
            fetchAndSetCreatorName(roomItem, holder.tvRoomCreator);

            // On item click, go to an AttendanceSummary screen (adjust as needed)
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Admins_Report.this, AttendanceSummary.class);
                intent.putExtra("roomId", roomItem.roomId);
                intent.putExtra("subjectCode", roomItem.subjectCode);
                intent.putExtra("section", roomItem.section);
                intent.putExtra("startDate", selectedStartDate);
                intent.putExtra("endDate", selectedEndDate);
                startActivity(intent);
            });
        }

        /**
         * Tries to fetch the creator's name by:
         *  1) Checking the "teachers" collection,
         *  2) If not found, checking the "administrator" collection.
         */
        private void fetchAndSetCreatorName(RoomItem roomItem, TextView tvRoomCreator) {
            // If we have no real ID, just show "Unknown"
            if (roomItem.creatorId == null || "UnknownCreator".equals(roomItem.creatorId)) {
                tvRoomCreator.setText("Created by: Unknown");
                return;
            }

            // First, try teachers
            FirebaseFirestore.getInstance()
                    .collection("teachers")
                    .document(roomItem.creatorId)
                    .get()
                    .addOnSuccessListener(teacherSnap -> {
                        if (teacherSnap.exists()) {
                            // Found in teachers
                            String fullName = buildFullName(
                                    teacherSnap.getString("firstName"),
                                    teacherSnap.getString("middleName"),
                                    teacherSnap.getString("lastName")
                            );
                            roomItem.creatorName = fullName;
                            tvRoomCreator.setText("Created by: " + fullName);
                        } else {
                            // Not found in teachers; try administrator
                            FirebaseFirestore.getInstance()
                                    .collection("administrator")
                                    .document(roomItem.creatorId)
                                    .get()
                                    .addOnSuccessListener(adminSnap -> {
                                        if (adminSnap.exists()) {
                                            String fullName = buildFullName(
                                                    adminSnap.getString("firstName"),
                                                    adminSnap.getString("middleName"),
                                                    adminSnap.getString("lastName")
                                            );
                                            roomItem.creatorName = fullName;
                                            tvRoomCreator.setText("Created by: " + fullName);
                                        } else {
                                            // Not found in either collection
                                            tvRoomCreator.setText("Created by: Unknown");
                                            Log.e(TAG, "No matching doc in teacher or admin for ID: " + roomItem.creatorId);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        tvRoomCreator.setText("Created by: Unknown");
                                        Log.e(TAG, "Error fetching admin doc for ID: " + roomItem.creatorId, e);
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        tvRoomCreator.setText("Created by: Unknown");
                        Log.e(TAG, "Error fetching teacher doc for ID: " + roomItem.creatorId, e);
                    });
        }

        /**
         * Helper method to build a full name string from first, middle, last.
         */
        private String buildFullName(String first, String middle, String last) {
            StringBuilder sb = new StringBuilder();
            if (first != null && !first.isEmpty()) sb.append(first).append(" ");
            if (middle != null && !middle.isEmpty()) sb.append(middle).append(" ");
            if (last != null && !last.isEmpty()) sb.append(last);
            return sb.toString().trim();
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }

        class RoomViewHolder extends RecyclerView.ViewHolder {
            TextView tvRoomTitle, tvRoomCreator;

            RoomViewHolder(View itemView) {
                super(itemView);
                tvRoomTitle = itemView.findViewById(R.id.tvRoomTitle);
                tvRoomCreator = itemView.findViewById(R.id.tvRoomCreator);
            }
        }
    }

    // ----------------------------------------------------------------------
    // Updated RoomItem model class with only creatorId and creatorName
    // ----------------------------------------------------------------------
    private static class RoomItem {
        String roomId;
        String subjectCode;
        String section;
        String creatorId;   // ID that might be in teachers or administrator
        String creatorName; // will be filled once we fetch from Firestore

        RoomItem(String roomId, String subjectCode, String section, String creatorId) {
            this.roomId = roomId;
            this.subjectCode = subjectCode;
            this.section = section;
            this.creatorId = creatorId;
            this.creatorName = null;
        }
    }
}
