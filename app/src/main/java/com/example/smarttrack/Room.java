package com.example.smarttrack;

public class Room {
    private String roomCode;
    private String subjectCode;
    private String section;

    public Room(String roomCode, String subjectCode, String section) {
        this.roomCode = roomCode;
        this.subjectCode = subjectCode;
        this.section = section;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public String getSection() {
        return section;
    }
}
