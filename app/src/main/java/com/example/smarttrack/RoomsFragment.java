package com.example.smarttrack;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RoomsFragment extends Fragment {

    private String roomType; // "teacher" or "admin"
    private LinearLayout roomsLayout;
    private Map<String, Map<String, String>> originalRooms = new HashMap<>();

    // Updated newInstance method to accept room data
    public static RoomsFragment newInstance(String roomType, Map<String, Map<String, String>> roomDetails){

    RoomsFragment fragment = new RoomsFragment();
        Bundle args = new Bundle();
        args.putString("roomType", roomType);
        args.putSerializable("roomDetails", (Serializable) roomDetails);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rooms, container, false);
        roomsLayout = view.findViewById(R.id.roomsLayout);

        if (getArguments() != null) {
            roomType = getArguments().getString("roomType");
            originalRooms = (Map<String, Map<String, String>>) getArguments().getSerializable("roomDetails");
        }

        // Display rooms based on the current room type (admin or teacher)
        displayRooms(getContext(), roomType);

        return view;
    }

    /**
     * Displays rooms based on the specified room type.
     * Admin rooms display only rooms with an adminId.
     * Teacher rooms display only rooms with a teacherId.
     */
    private void displayRooms(Context context, String roomType) {
        if (originalRooms != null && !originalRooms.isEmpty()) { // Ensure rooms exist
            Log.d("RoomsFragment", "Displaying Rooms for Type: " + roomType + " | Total Rooms: " + originalRooms.size());

            for (Map.Entry<String, Map<String, String>> entry : originalRooms.entrySet()) {
                String roomCode = entry.getKey();
                Map<String, String> detailsMap = entry.getValue();

                String adminId = detailsMap.get("adminId");
                String teacherId = detailsMap.get("teacherId");

                boolean isAdminRoom = adminId != null && !adminId.trim().isEmpty();
                boolean isTeacherRoom = teacherId != null && !teacherId.trim().isEmpty();

                if (roomType.equals("admin") && isAdminRoom) {
                    createRoomCard(context, roomCode, detailsMap);
                    Log.d("RoomsFragment", "Admin Room Displayed: " + roomCode + " | Admin ID: " + adminId);
                } else if (roomType.equals("teacher") && isTeacherRoom) {
                    createRoomCard(context, roomCode, detailsMap);
                    Log.d("RoomsFragment", "Teacher Room Displayed: " + roomCode + " | Teacher ID: " + teacherId);
                } else {
                    Log.d("RoomsFragment", "Room Skipped: " + roomCode + " | Type: " + roomType);
                }
            }
        } else {
            Log.d("RoomsFragment", "No rooms to display for type: " + roomType);
        }
    }

    /**
     * Creates a CardView for a specific room and adds it to the UI.
     */
    private void createRoomCard(Context context, String roomCode, Map<String, String> detailsMap) {
        String subjectCode = detailsMap.get("subjectCode");
        String section = detailsMap.get("section");
        String startTime = detailsMap.get("startTime");
        String endTime = detailsMap.get("endTime");
        boolean isActive = Boolean.parseBoolean(detailsMap.get("isActive"));

        CardView cardView = new CardView(context);
        cardView.setRadius(20f);
        cardView.setCardElevation(10f);
        cardView.setUseCompatPadding(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 20);
        cardView.setLayoutParams(cardParams);

        RelativeLayout cardLayout = new RelativeLayout(context);
        cardLayout.setPadding(30, 30, 30, 30);

        View statusIndicator = new View(context);
        RelativeLayout.LayoutParams statusParams = new RelativeLayout.LayoutParams(30, 30);
        statusParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        statusParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        statusParams.setMargins(0, 20, 20, 0);
        statusIndicator.setLayoutParams(statusParams);
        statusIndicator.setBackgroundColor(isActive ? Color.GREEN : Color.RED);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_VERTICAL);
        RelativeLayout.LayoutParams contentParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        contentParams.addRule(RelativeLayout.CENTER_VERTICAL);
        contentLayout.setLayoutParams(contentParams);

        TextView iconView = new TextView(context);
        iconView.setText(String.valueOf(subjectCode.charAt(0)).toUpperCase());
        iconView.setTextSize(30);
        iconView.setTypeface(Typeface.DEFAULT_BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextColor(Color.WHITE);
        iconView.setBackgroundResource(R.drawable.rounded_black_bg);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(120, 120);
        iconParams.setMargins(0, 0, 40, 0);
        iconView.setLayoutParams(iconParams);

        TextView detailsView = new TextView(context);
        String detailsText = subjectCode + "\n" + section + "\n" + startTime + " - " + endTime;
        detailsView.setText(detailsText);
        detailsView.setTextSize(18);
        detailsView.setTypeface(Typeface.DEFAULT_BOLD);
        detailsView.setTextColor(Color.BLACK);

        String creatorInfo = detailsMap.get("creatorInfo");
        TextView createdByView = new TextView(context);
        createdByView.setText(creatorInfo);
        createdByView.setTextSize(16);
        createdByView.setTypeface(Typeface.DEFAULT);
        createdByView.setTextColor(Color.GRAY);

        contentLayout.addView(iconView);
        contentLayout.addView(detailsView);
        contentLayout.addView(createdByView);

        cardLayout.addView(statusIndicator);
        cardLayout.addView(contentLayout);

        cardView.addView(cardLayout);

        cardView.setOnClickListener(v -> {
            Log.d("RoomsFragment", "Card clicked: " + roomCode);
        });

        roomsLayout.addView(cardView);
    }
}
