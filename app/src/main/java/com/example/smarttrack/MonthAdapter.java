package com.example.smarttrack;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.*;

public class MonthAdapter extends RecyclerView.Adapter<MonthAdapter.MonthViewHolder> {
    private static final String TAG = "MonthAdapter";
    private List<String> months;
    private Context context;
    private FirebaseFirestore db;
    private String uid;
    private String roomId;

    public MonthAdapter(List<String> months, Context context, String uid, String roomId) {
        this.months = months;
        this.context = context;
        this.uid = uid;
        this.roomId = roomId;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_month, parent, false);
        return new MonthViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
        String month = months.get(position);
        holder.monthName.setText(month);

        holder.monthName.setOnClickListener(v -> {
            if (holder.attendanceDetails.getVisibility() == View.GONE) {
                holder.attendanceDetails.setVisibility(View.VISIBLE);
                fetchAttendanceForMonth(month, holder, position);
            } else {
                holder.attendanceDetails.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public int getItemCount() {
        return months.size();
    }

    public static class MonthViewHolder extends RecyclerView.ViewHolder {
        TextView monthName, presentLabel, lateLabel, absentLabel;
        View attendanceDetails;

        public MonthViewHolder(@NonNull View itemView) {
            super(itemView);
            monthName = itemView.findViewById(R.id.monthName);
            presentLabel = itemView.findViewById(R.id.presentLabel);
            lateLabel = itemView.findViewById(R.id.lateLabel);
            absentLabel = itemView.findViewById(R.id.absentLabel);
            attendanceDetails = itemView.findViewById(R.id.attendanceDetails);
        }
    }

    private void fetchAttendanceForMonth(String month, MonthViewHolder holder, int monthIndex) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        // Determine the start and end of the selected month
        calendar.set(Calendar.MONTH, monthIndex);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date startOfMonth = calendar.getTime();

        calendar.add(Calendar.MONTH, 1);
        Date startOfNextMonth = calendar.getTime();

        // Get today's date
        Calendar today = Calendar.getInstance();
        String todayFormatted = dateFormat.format(today.getTime());

        // Query Firestore for attendance in the selected month
        db.collection("rooms").document(roomId)
                .collection("students").document(uid)
                .collection("attendance")
                .whereGreaterThanOrEqualTo("date", new Timestamp(startOfMonth))
                .whereLessThan("date", new Timestamp(startOfNextMonth))
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int totalPresent = 0, totalLate = 0;
                    Set<String> attendedDays = new HashSet<>();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Timestamp date = document.getTimestamp("date");
                        String status = document.getString("status");

                        if (date != null) {
                            String formattedDate = dateFormat.format(date.toDate());
                            attendedDays.add(formattedDate);
                        }

                        if (status != null) {
                            if (status.equalsIgnoreCase("Present")) {
                                totalPresent++;
                            } else if (status.equalsIgnoreCase("Late")) {
                                totalLate++;
                            }
                        }
                    }

                    // Get the number of workdays in this month (excluding weekends)
                    int workDays = getWorkdaysInMonth(monthIndex);

                    int totalAbsent;
                    if (monthIndex > today.get(Calendar.MONTH)) {
                        // Future months → No absences yet
                        totalAbsent = 0;
                    } else if (monthIndex == today.get(Calendar.MONTH)) {
                        // Current month → Count only past days
                        int pastWorkdays = getWorkdaysUntilToday();
                        totalAbsent = pastWorkdays - attendedDays.size();
                    } else {
                        // Past months → Full workdays count
                        totalAbsent = workDays - attendedDays.size();
                    }

                    holder.presentLabel.setText("Presents: " + totalPresent);
                    holder.lateLabel.setText("Lates: " + totalLate);
                    holder.absentLabel.setText("Absents: " + Math.max(0, totalAbsent));

                    Log.d(TAG, "✅ Monthly Stats for " + month + " -> Present: " + totalPresent + ", Late: " + totalLate + ", Absent: " + totalAbsent);
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to fetch attendance", e));
    }

    private int getWorkdaysInMonth(int monthIndex) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MONTH, monthIndex);
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        int workDays = 0;
        for (int day = 1; day <= daysInMonth; day++) {
            calendar.set(Calendar.DAY_OF_MONTH, day);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                workDays++;
            }
        }
        return workDays;
    }

    private int getWorkdaysUntilToday() {
        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DAY_OF_MONTH);

        int workDays = 0;
        for (int day = 1; day <= today; day++) {
            calendar.set(Calendar.DAY_OF_MONTH, day);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                workDays++;
            }
        }
        return workDays;
    }
}
