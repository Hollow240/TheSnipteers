package com.learnanything;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@WebServlet(name = "GetLessonServlet", urlPatterns = {"/api/get-lesson"})
public class GetLessonServlet extends HttpServlet {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp);
        // Read JSON body
        StringBuilder sb = new StringBuilder();
        String line;
        try (java.io.BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();

        String topic = extractJsonField(body, "topic");
        String nodeTitle = extractJsonField(body, "nodeTitle");
        String nodeDescription = extractJsonField(body, "nodeDescription");

        if (topic == null || nodeTitle == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"Missing 'topic' or 'nodeTitle' parameter in request body\"}");
            return;
        }

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getenv("GOOGLE_API_KEY");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getProperty("GEMINI_API_KEY");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getProperty("GOOGLE_API_KEY");
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"GOOGLE_API_KEY or GEMINI_API_KEY environment variable is not set\"}");
            return;
        }

        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        String prompt = "You are a world-class tutor using the Feynman Technique. Explain the sub-topic '" + escapeJson(nodeTitle) + 
                "' within the overall course '" + escapeJson(topic) + "'. " +
                (nodeDescription != null && !nodeDescription.trim().isEmpty() ? "Description: " + escapeJson(nodeDescription) + ". " : "") + "\n\n" +
                "Structure your response in Markdown with exactly these sections:\n" +
                "1. The Hook (1 paragraph) - Connect it to everyday life.\n" +
                "2. The Core Concept (2-3 paragraphs) - Explain in plain English.\n" +
                "3. The Mental Model (1 paragraph) - Provide an analogy.\n" +
                "4. Active Recall (3 bullet points) - Questions for the user.";

        String jsonPayload = "{"
                + "\"contents\":[{"
                + "\"parts\":[{"
                + "\"text\":\"" + escapeJson(prompt) + "\""
                + "}]"
                + "}]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(geminiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(20))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                resp.setStatus(response.statusCode());
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\": \"Gemini API returned error: " + escapeJson(response.body()) + "\"}");
                return;
            }

            String responseBody = response.body();
            String lessonMarkdown = extractGeminiText(responseBody);

            if (lessonMarkdown == null) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\": \"Failed to parse response from Gemini API\"}");
                return;
            }

            // Return lesson markdown
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/markdown; charset=UTF-8");
            resp.getWriter().write(lessonMarkdown);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"Request interrupted: " + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"Request failed: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ponytail: extract simple field value from JSON string without a parsing dependency
    private static String extractJsonField(String body, String fieldName) {
        int keyIndex = body.indexOf("\"" + fieldName + "\"");
        if (keyIndex == -1) return null;
        int firstQuote = body.indexOf("\"", keyIndex + fieldName.length() + 2);
        if (firstQuote == -1) return null;
        int start = firstQuote + 1;
        int end = start;
        while (end < body.length()) {
            if (body.charAt(end) == '"' && body.charAt(end - 1) != '\\') {
                break;
            }
            end++;
        }
        if (end >= body.length()) return null;
        return unescapeJson(body.substring(start, end));
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
