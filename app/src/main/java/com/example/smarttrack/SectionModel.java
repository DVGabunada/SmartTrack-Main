package com.example.smarttrack;

public class SectionModel {
    private String subjectCode;
    private String section;
    private String teacherId;
    private String studentId;
    private String roomId; // Added field

    // Constructor with roomId
    public SectionModel(String subjectCode, String section, String teacherId, String studentId, String roomId) {
        this.subjectCode = subjectCode;
        this.section = section;
        this.teacherId = teacherId;
        this.studentId = studentId;
        this.roomId = roomId;
    }

    // Original constructor for backward compatibility
    public SectionModel(String subjectCode, String section, String teacherId, String studentId) {
        this.subjectCode = subjectCode;
        this.section = section;
        this.teacherId = teacherId;
        this.studentId = studentId;
    }

    // Default constructor for Firestore
    public SectionModel() {}

    // Getters and Setters
    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
}
