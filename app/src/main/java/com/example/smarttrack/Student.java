package com.example.smarttrack;

public class Student {
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;

    // Constructor
    public Student(String firstName, String middleName, String lastName, String email) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.email = email;
    }

    // Getters
    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    // Optional: Full name method
    public String getFullName() {
        return firstName + " " + middleName + " " + lastName;
    }
}
