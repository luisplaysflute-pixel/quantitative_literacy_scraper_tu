package edu.trinity.qr;

import java.io.IOException;
import java.util.ArrayList;

import org.jsoup.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.Scanner;

public class CatalogScraper {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter a search term: ");
        String searchTerm = scanner.nextLine();
        String url = "https://www.trinity.edu";
        //crawler(1, url, new ArrayList<String>());
        Search(url, searchTerm, new ArrayList<String>());
    }

    private static void crawler(int level, String url, ArrayList<String> visited) {
        if(level <= 5){
            Document doc = request(url, visited);

            if(doc != null){
                for(Element link : doc.select("a[href]")){
                    String nextLink = link.absUrl("href");
                    if(visited.contains(nextLink) == false){
                        crawler(level++, nextLink, visited);
                    }
                }
            }
        }
    }

    private static Document request(String url, ArrayList<String> v){
        try {
            Connection con = Jsoup.connect(url);
            Document doc = con.get();

            if(con.response().statusCode() == 200){
                System.out.println("Link: " + url);
                System.out.println(doc.title());
                v.add(url);

                return doc;
            }
            return null;
        }catch (IOException e) {
            return null;
        }
    }
    private static void Search(String url, String searchTerm, ArrayList<String> visited) {
        Document doc = request(url, visited);
        if(doc != null) {
            if(doc.text().toLowerCase().contains(searchTerm.toLowerCase())){
                System.out.println("Found the term: " + searchTerm + " at " + url);
            }
        }
    }
}
