package com.example.smarttrack;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class Announcement {
    private String id;
    private String announcement_title;
    private String announcement_message;
    private com.google.firebase.Timestamp timestamp;
    private String roomCode;
    private String teacherUid;
    private String teacherName;


    public Announcement() {}

    public Announcement(String id, String announcement_title, String announcement_message,String uid, Timestamp timestamp, String teacherUid, String teacherName){
        this.id = id;
        this.announcement_title = announcement_title;
        this.announcement_message = announcement_message;
        this.timestamp = timestamp;
        this.teacherUid = teacherUid;
        this.teacherName = teacherName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnnouncement_title() {
        return announcement_title;
    }

    public void setAnnouncement_title(String announcement_title) {
        this.announcement_title = announcement_title;
    }

    public String getAnnouncement_message() {
        return announcement_message;
    }

    public void setAnnouncement_message(String announcement_message) {
        this.announcement_message = announcement_message;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getTeacherUid() {
        return teacherUid;
    }

    public void setTeacherUid(String teacherUid) {
        this.teacherUid = teacherUid;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public String getFormattedTimestamp() {
        if (timestamp != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());
            return sdf.format(timestamp.toDate());
        }
        return "";
    }



}
