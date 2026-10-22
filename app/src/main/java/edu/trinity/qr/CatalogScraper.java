package edu.trinity.qr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;

public class CatalogScraper {

    public static void main(String[] args) throws Exception {
        try (Scanner scanner = new Scanner(System.in)) {
            // Get university URL
            System.out.print("Enter the university URL: ");
            String startUrl = scanner.nextLine().trim();

            // Validate URL
            if (!startUrl.startsWith("http://") && !startUrl.startsWith("https://")) {
                System.out.println("Error: URL must start with http:// or https://");
                return;
            }

            // Get search term
            System.out.print("Enter the search term: ");
            String searchTerm = scanner.nextLine().trim();

            // Get max depth (optional)
            System.out.print("Enter the max depth (optional, default: 5): ");
            String depthInput = scanner.nextLine().trim();
            int maxDepth = 5; // default
            if (!depthInput.isEmpty()) {
                try {
                    maxDepth = Integer.parseInt(depthInput);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid depth value, using default: 5");
                }
            }

            System.out.println("Starting crawl from: " + startUrl);
            System.out.println("Searching for term: " + searchTerm);
            System.out.println("Max depth: " + maxDepth);

            // Extract domain to stay within the same site
            String domain = extractDomain(startUrl);
            if (domain == null) {
                System.out.println("Error: Could not extract domain from URL");
                return;
            }
            System.out.println("Staying within domain: " + domain);

            // Crawl the site and collect courses from pages containing the search term
            Set<String> visitedUrls = new HashSet<>();
            List<Course> allCourses = new ArrayList<>();
            List<String> pagesWithSearchTerm = new ArrayList<>();

            crawlAndProcess(startUrl, searchTerm, maxDepth, domain, visitedUrls, allCourses, pagesWithSearchTerm);

            // Output results
            System.out.println("\n=== COURSE ANALYSIS RESULTS ===");
            System.out.println("Pages crawled: " + visitedUrls.size());
            System.out.println("Pages containing search term '" + searchTerm + "': " + pagesWithSearchTerm.size());
            System.out.println("Total courses found: " + allCourses.size());

            if (allCourses.isEmpty()) {
                System.out.println("No courses found. Try a different search term or check if the site requires JavaScript.");
                return;
            }

            QRClassifier classifier = new QRClassifier();
            int qrCount = 0;

            for (Course course : allCourses) {
                boolean isQR = classifier.isQuantitative(course);
                if (isQR) {
                    qrCount++;
                }

                System.out.println("\nCourse:");
                System.out.println("  Title: " + course.getTitle());
                System.out.println("  Description: " + course.getDescription());
                System.out.println("  QR Classification: " + (isQR ? "YES" : "NO"));
            }

            System.out.println("\n=== SUMMARY ===");
            System.out.println("Total courses analyzed: " + allCourses.size());
            System.out.println("QR courses identified: " + qrCount);
            System.out.println("Non-QR courses: " + (allCourses.size() - qrCount));
        }
    }

    /**
     * Crawls the site starting from the given URL, up to the specified depth.
     * For each page that contains the search term, extracts course information.
     *
     * @param url           The URL to crawl
     * @param searchTerm    The term to search for in page content
     * @param maxDepth      Maximum crawling depth
     * @param domain        Domain to stay within (to avoid crawling external sites)
     * @param visitedUrls   Set of already visited URLs (to prevent cycles)
     * @param allCourses    List to collect all found courses
     * @param pagesWithSearchTerm List to collect URLs that contain the search term
     */
    private static void crawlAndProcess(String url, String searchTerm, int maxDepth, String domain,
                                       Set<String> visitedUrls, List<Course> allCourses,
                                       List<String> pagesWithSearchTerm) throws Exception {
        // Check if we've exceeded max depth or already visited this URL
        if (maxDepth < 0 || !visitedUrls.add(url)) {
            return;
        }

        System.out.println("Crawling (depth " + maxDepth + "): " + url);

        // Fetch the page (try Jsoup first, fall back to HtmlUnit with JS if needed)
        Document doc = fetchPage(url);
        if (doc == null) {
            System.out.println("  Failed to fetch page");
            return;
        }

        // Check if this page contains the search term
        boolean containsSearchTerm = doc.text().toLowerCase().contains(searchTerm.toLowerCase());
        if (containsSearchTerm) {
            pagesWithSearchTerm.add(url);
            System.out.println("  Found search term in page");
        }

        // Extract courses from this page
        List<Course> courses = parseCourses(doc);
        if (!courses.isEmpty()) {
            System.out.println("  Found " + courses.size() + " courses on this page");
            allCourses.addAll(courses);
        }

        // If we've reached max depth, don't crawl further
        if (maxDepth == 0) {
            return;
        }

        // Find and crawl all links on this page
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String nextUrl = link.absUrl("href");

            // Skip if URL is empty or invalid
            if (nextUrl == null || nextUrl.isEmpty()) {
                continue;
            }

            // Stay within the same domain
            if (!isSameDomain(nextUrl, domain)) {
                continue;
            }

            // Recursively crawl the link
            crawlAndProcess(nextUrl, searchTerm, maxDepth - 1, domain, visitedUrls, allCourses, pagesWithSearchTerm);
        }
    }

    /**
     * Fetches a page using HtmlUnit with JavaScript enabled to handle WAF challenges.
     *
     * @param url The URL to fetch
     * @return The document, or null if fetching failed
     */
    private static Document fetchPage(String url) {
        return fetchWithHtmlUnit(url);
    }

    /**
     * Fetches a page using Jsoup with browser-like headers to avoid WAF challenges.
     * First visits the domain root to establish session if needed.
     *
     * @param url The URL to fetch
     * @return The document, or null if fetching failed
     */
    private static Document fetchWithJsoup(String url) {
        try {
            // First, try to visit the domain root to establish any necessary cookies
            String domain = extractDomain(url);
            if (domain != null) {
                String rootUrl = "https://" + domain;
                if (!url.equals(rootUrl)) {
                    // Visit root first to get cookies
                    visitRootForCookies(rootUrl);
                }
            }

            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .referrer("https://www.google.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .followRedirects(true)
                    .timeout(15000)
                    .execute();

            if (response.statusCode() == 200) {
                System.out.println("  Fetched with Jsoup (status: " + response.statusCode() + ")");
                return response.parse();
            } else if (response.statusCode() == 202) {
                System.out.println("  Jsoup returned status: 202 (WAF challenge)");
                // Try to see if we can get past it by examining the response
                String responseBody = response.body();
                System.out.println("  Response body length: " + (responseBody != null ? responseBody.length() : 0));
                if (responseBody != null && responseBody.length() > 100) {
                    System.out.println("  Response body preview: " + responseBody.substring(0, Math.min(200, responseBody.length())));
                    // Try to parse it anyway - maybe there's useful content
                    Document doc = Jsoup.parse(responseBody);
                    if (isValidResponse(doc)) {
                        System.out.println("  Challenge response contained usable content");
                        return doc;
                    }
                }
                // Try to extract and use cookies
                Map<String, String> cookies = response.cookies();
                if (!cookies.isEmpty()) {
                    System.out.println("  Got cookies: " + cookies.keySet());
                    // Try again with these cookies
                    return fetchWithJsoupAndCookies(url, cookies);
                }
                return null;
            } else {
                System.out.println("  Jsoup returned status: " + response.statusCode());
                return null;
            }
        } catch (Exception e) {
            System.out.println("  Jsoup fetch failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Visits the domain root to establish cookies for subsequent requests.
     *
     * @param rootUrl The domain root URL (e.g., https://example.com)
     */
    private static void visitRootForCookies(String rootUrl) {
        try {
            Connection.Response response = Jsoup.connect(rootUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .referrer("https://www.google.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .followRedirects(true)
                    .timeout(10000)
                    .execute();

            if (response.statusCode() == 200) {
                System.out.println("  Visited root for cookies (status: " + response.statusCode() + ")");
            } else {
                System.out.println("  Root visit returned status: " + response.statusCode());
            }
        } catch (Exception e) {
            System.out.println("  Root visit failed: " + e.getMessage());
        }
    }

    /**
     * Fetches a page using Jsoup with specific cookies.
     *
     * @param url    The URL to fetch
     * @param cookies Cookies to include in the request
     * @return The document, or null if fetching failed
     */
    private static Document fetchWithJsoupAndCookies(String url, Map<String, String> cookies) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .referrer("https://www.google.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .cookies(cookies)
                    .followRedirects(true)
                    .timeout(15000)
                    .execute();

            if (response.statusCode() == 200) {
                System.out.println("  Fetched with Jsoup+cookies (status: " + response.statusCode() + ")");
                return response.parse();
            } else {
                System.out.println("  Jsoup+cookies returned status: " + response.statusCode());
                return null;
            }
        } catch (Exception e) {
            System.out.println("  Jsoup+cookies fetch failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetches a page using HtmlUnit with JavaScript enabled.
     *
     * @param url The URL to fetch
     * @return The document, or null if fetching failed
     */
    private static Document fetchWithHtmlUnit(String url) {
        WebClient webClient = null;
        try {
            webClient = new WebClient(BrowserVersion.CHROME);
            webClient.getOptions().setJavaScriptEnabled(true);
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setTimeout(10000);

            HtmlPage page = webClient.getPage(url);
            if (page.getWebResponse().getStatusCode() == 200) {
                System.out.println("  Fetched with HtmlUnit (status: " + page.getWebResponse().getStatusCode() + ")");
                String html = page.asXml();
                return Jsoup.parse(html);
            } else {
                System.out.println("  HtmlUnit returned status: " + page.getWebResponse().getStatusCode());
                return null;
            }
        } catch (Exception e) {
            System.out.println("  HtmlUnit fetch failed: " + e.getMessage());
            return null;
        } finally {
            if (webClient != null) {
                webClient.close();
            }
        }
    }

    /**
     * Checks if a response from Jsoup appears to be valid (not a challenge/empty response).
     *
     * @param doc The document to check
     * @return true if the response appears valid
     */
    private static boolean isValidResponse(Document doc) {
        if (doc == null) {
            return false;
        }

        // Check for empty or nearly empty content
        String text = doc.text().trim();
        if (text.isEmpty() || text.length() < 50) {
            return false;
        }

        // Check for common challenge indicators
        String html = doc.html().toLowerCase();
        if (html.contains("challenge") ||
            html.contains("waf") ||
            html.contains("cloudflare") ||
            html.contains("please enable javascript") ||
            html.contains("checking your browser")) {
            return false;
        }

        return true;
    }

    /**
     * Extracts the domain from a URL.
     *
     * @param url The URL to extract domain from
     * @return The domain, or null if extraction failed
     */
    private static String extractDomain(String url) {
        try {
            int protocolEnd = url.indexOf("://");
            if (protocolEnd == -1) {
                return null;
            }

            int domainStart = protocolEnd + 3;
            int domainEnd = url.indexOf("/", domainStart);
            if (domainEnd == -1) {
                domainEnd = url.length();
            }

            return url.substring(domainStart, domainEnd);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if a URL is within the same domain.
     *
     * @param url   The URL to check
     * @param domain The domain to check against
     * @return true if the URL is within the same domain
     */
    private static boolean isSameDomain(String url, String domain) {
        try {
            String urlDomain = extractDomain(url);
            return urlDomain != null && urlDomain.equals(domain);
        } catch (Exception e) {
            return false;
        }
    }

    public static ArrayList<Course> parseCourses(Document doc) {
        ArrayList<Course> courses = new ArrayList<Course>();

        if (doc == null) {
            return courses;
        }

        // Try multiple selectors in order - use first one that returns results
        String[] CourseSelectors = {
            "div.courseblock",                    // Common pattern
            "div.course",                         // Simple pattern
            "div.course-item",                    // Another common pattern
            "div.catalog-entry",                  // Catalog-specific
            "section.course",                     // Semantic HTML
            "article.course",                     // Semantic HTML
            "li.course",                          // List items
            "tr.course",                          // Table rows
            ".course",                            // Class-based
            "[class*='course']",                  // Any class containing 'course'
            "[itemtype*='course']",               // Schema.org Course
            "[typeof*='course']",                 // RDFa Course
            ".search-result",                     // Search result items
            ".result-item",                       // Generic result items
            ".listing-item",                      // Listing items
            ".card"                               // Card-based layouts
        };

        Elements blocks = new Elements();
        boolean foundBlocks = false;

        for(String selector : CourseSelectors) {
            blocks = doc.select(selector);
            if (!blocks.isEmpty()) {
                System.out.println("    Using course selector: " + selector);
                foundBlocks = true;
                break;
            }
        }

        // If no specific course selectors work, try to find heading-like elements that might be courses
        if (!foundBlocks) {
            System.out.println("    No specific course selectors matched, trying heuristic approach");
            // Look for elements that contain both a title-like heading and some descriptive text
            Elements headingElements = doc.select("h1, h2, h3, h4, h5, h6");
            for (Element heading : headingElements) {
                // Check if this heading looks like a course title
                String headingText = heading.text().toLowerCase();
                if (headingText.length() >= 3 &&
                    (headingText.matches(".*[0-9].*") ||  // Contains numbers (like course codes)
                     headingText.matches(".*[a-zA-Z]{2,}[0-9]+.*") ||  // Like CS101, MATH201
                     headingText.contains("course") ||
                     headingText.contains("class"))) {

                    String title = heading.text().trim();

                    // Try to get description from various sources
                    String desc = "";

                    // 1. Try next siblings (paragraphs or divs)
                    Elements nextSiblings = heading.nextElementSiblings();
                    if (!nextSiblings.isEmpty()) {
                        StringBuilder descBuilder = new StringBuilder();
                        int count = 0;
                        for (Element sib : nextSiblings) {
                            // Stop at next heading or after getting enough text
                            if (sib.tagName().matches("h[1-6]")) break;
                            String sibText = sib.text().trim();
                            if (!sibText.isEmpty()) {
                                descBuilder.append(sibText).append(" ");
                                count++;
                                if (count >= 3) break;  // Limit description length
                            }
                        }
                        desc = descBuilder.toString().trim();
                    }

                    // 2. If no good description from siblings, look in parent container
                    if (desc.isEmpty()) {
                        Element parent = heading.parent();
                        if (parent != null) {
                            // Get text from parent, excluding the heading text itself
                            String parentText = parent.text().trim();
                            String headingTextExact = heading.text().trim();
                            if (parentText.length() > headingTextExact.length()) {
                                desc = parentText.substring(headingTextExact.length()).trim();
                            }
                        }
                    }

                    // Only add if we have both title and description
                    if (!title.isEmpty() && !desc.isEmpty()) {
                        courses.add(new Course(title, desc));
                    }
                }
            }
        }

        // Process the blocks we found with selectors
        for(Element block : blocks) {
            String title = extractCourseTitle(block);
            String desc = extractCourseDescription(block);

            // Only add if we have both title and description
            if (!title.isEmpty() && !desc.isEmpty()) {
                courses.add(new Course(title, desc));
            }
        }

        return courses;
    }

    /**
     * Extracts the course title from an element using common patterns.
     *
     * @param element The element to extract title from
     * @return The course title, or empty string if not found
     */
    private static String extractCourseTitle(Element element) {
        // Try common title selectors
        String[] titleSelectors = {
            "h3", "h2", "h1", "h4", "h5", "h6",
            ".course-title", ".title", ".coursename_big",
            ".course-name", ".moduletitle", ".ntitle",
            "[class*='title']", "[class*='course']"
        };

        for (String selector : titleSelectors) {
            Element titleElem = element.selectFirst(selector);
            if (titleElem != null) {
                String title = titleElem.text().trim();
                if (!title.isEmpty() && title.length() >= 3 && title.length() <= 200) {
                    return title;
                }
            }
        }

        // If no specific title found, use the element's text if it looks like a title
        String text = element.text().trim();
        if (text.length() >= 3 && text.length() <= 100 &&
            !text.contains("\n") &&
            (text.matches(".*[0-9].*") || text.matches(".*[A-Z]{2,}.*"))) {
            return text;
        }

        return "";
    }

    /**
     * Extracts the course description from an element using common patterns.
     *
     * @param element The element to extract description from
     * @return The course description, or empty string if not found
     */
    private static String extractCourseDescription(Element element) {
        // Try common description selectors
        String[] descSelectors = {
            "p", ".course-desc", ".description", ".desc",
            ".coursedetail", ".content", ".info", ".details",
            "[class*='desc']", "[class*='description']", "[class*='detail']"
        };

        for (String selector : descSelectors) {
            Element descElem = element.selectFirst(selector);
            if (descElem != null) {
                String desc = descElem.text().trim();
                if (!desc.isEmpty() && desc.length() >= 10 && desc.length() <= 1000) {
                    return desc;
                }
            }
        }

        // Try getting all text and cleaning it up
        String text = element.text().trim();
        if (text.length() >= 10 && text.length() <= 2000) {
            // Remove extra whitespace and newlines
            text = text.replaceAll("\\s+", " ").trim();
            return text;
        }

        return "";
    }
}
