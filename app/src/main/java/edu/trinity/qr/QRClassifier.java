package edu.trinity.qr;

import java.util.*;


public class QRClassifier {

    //Core quantitative keywords
    private final List<String> KEYWORDS;

    //Strong indicators of QR/QL courses
    private final List<String> STRONG_INDICATORS;

    //Default constructor with original Trinity-specific keywords
    public QRClassifier() {
        this.KEYWORDS = Arrays.asList(
            "statistics",
            "math",
            "statistical",
            "data",
            "quantitative",
            "analysis",
            "algebra",
            "calculus",
            "probability",
            "modeling",
            "numerical",
            "mathematics",
            "computing",
            "scientific",
            "research methods",
            "measurement"
        );

        this.STRONG_INDICATORS = Arrays.asList(
            "statistical analysis",
            "data analysis",
            "quantitative analysis",
            "quantitative literacy",
            "quantitative reasoning",
            "numerical methods",
            "probability theory",
            "mathematical modeling"
        );
    }

    //Constructor for custom keyword lists
    public QRClassifier(List<String> keywords, List<String> strongIndicators) {
        this.KEYWORDS = keywords != null ? keywords : Arrays.asList();
        this.STRONG_INDICATORS = strongIndicators != null ? strongIndicators : Arrays.asList();
    }

    public boolean isQuantitative(Course course) {
        String text = (course.getTitle() + " " + course.getDescription()).toLowerCase();

        //Strong indicators first
        for(String phrase : STRONG_INDICATORS){
            if(text.contains(phrase)) {
                return true;
            }
        }

        //Keyword matching
        int score = 0;
        for(String keyword : KEYWORDS) {
            if(text.contains(keyword)) {
                score++;
            }
        }

        //Threshold, tweakable
        return score >= 2; //if theres at least 2 keywords
    }
}
