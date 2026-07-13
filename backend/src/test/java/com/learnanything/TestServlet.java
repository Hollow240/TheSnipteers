package com.learnanything;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TestServlet {
    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Error: GEMINI_API_KEY environment variable is not set");
            System.exit(1);
        }

        String topic = "Java Servlets";
        System.out.println("Testing Gemini API call for topic: " + topic);

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

            String prompt = "Generate a structured learning path for learning: " + escapeJson(topic) + 
                    ". You must return a JSON object containing a list of nodes and links. Schema: { \"nodes\": [{ \"id\": \"string\", \"name\": \"string\", \"description\": \"string\" }], \"links\": [{ \"source\": \"string\", \"target\": \"string\" }] }";

            String jsonPayload = "{"
                    + "\"contents\":[{"
                    + "\"parts\":[{"
                    + "\"text\":\"" + escapeJson(prompt) + "\""
                    + "}]"
                    + "}],"
                    + "\"generationConfig\":{"
                    + "\"responseMimeType\":\"application/json\""
                    + "}"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(geminiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("Response Status Code: " + response.statusCode());
            if (response.statusCode() != 200) {
                System.out.println("Error body: " + response.body());
                System.exit(1);
            }

            String responseBody = response.body();
            String dagJson = extractGeminiText(responseBody);

            System.out.println("\nExtracted DAG JSON:\n" + dagJson);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String unescapeJson(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '"') { sb.append('"'); i++; }
                else if (next == '\\') { sb.append('\\'); i++; }
                else if (next == '/') { sb.append('/'); i++; }
                else if (next == 'b') { sb.append('\b'); i++; }
                else if (next == 'f') { sb.append('\f'); i++; }
                else if (next == 'n') { sb.append('\n'); i++; }
                else if (next == 'r') { sb.append('\r'); i++; }
                else if (next == 't') { sb.append('\t'); i++; }
                else { sb.append(c); }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractGeminiText(String responseBody) {
        int textStart = responseBody.indexOf("\"text\"");
        if (textStart == -1) return null;
        int firstQuote = responseBody.indexOf("\"", textStart + 6);
        if (firstQuote == -1) return null;
        int start = firstQuote + 1;
        
        int end = start;
        while (end < responseBody.length()) {
            if (responseBody.charAt(end) == '"') {
                int backslashes = 0;
                for (int i = end - 1; i >= start; i--) {
                    if (responseBody.charAt(i) == '\\') backslashes++;
                    else break;
                }
                if (backslashes % 2 == 0) {
                    break;
                }
            }
            end++;
        }
        
        if (end >= responseBody.length()) return null;
        return unescapeJson(responseBody.substring(start, end));
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
