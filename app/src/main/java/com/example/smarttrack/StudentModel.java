package com.example.smarttrack;

public class StudentModel {
    private String studentId;
    private String roomId;
    private String subjectCode;
    private String section;
    private String teacherId;

    // Constructor
    public StudentModel(String studentId, String roomId, String subjectCode, String section, String teacherId) {
        this.studentId = studentId;
        this.roomId = roomId;
        this.subjectCode = subjectCode;
        this.section = section;
        this.teacherId = teacherId;
    }

    // Getters and Setters
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
}
