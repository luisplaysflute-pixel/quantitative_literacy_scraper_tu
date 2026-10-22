package edu.trinity.qr;

public class Course {
    private String title;
    private String description;

    public Course(String t, String d) {
        this.title = t;
        this.description = d;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
