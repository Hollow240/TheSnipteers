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

@WebServlet(name = "GeneratePathServlet", urlPatterns = {"/api/generate-path"})
public class GeneratePathServlet extends HttpServlet {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp);
        String topic = req.getParameter("q");
        if (topic == null || topic.trim().isEmpty()) {
            topic = req.getParameter("topic");
        }

        if (topic == null || topic.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"Missing 'q' or 'topic' parameter\"}");
            return;
        }

        handleTopicRequest(topic, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp);
        // Read the body of the request
        StringBuilder sb = new StringBuilder();
        String line;
        try (java.io.BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();

        // Extract the topic value from {"topic": "value"}
        String topic = null;
        int topicKeyIndex = body.indexOf("\"topic\"");
        if (topicKeyIndex != -1) {
            int firstQuote = body.indexOf("\"", topicKeyIndex + 7);
            if (firstQuote != -1) {
                int start = firstQuote + 1;
                int end = start;
                while (end < body.length()) {
                    if (body.charAt(end) == '"' && body.charAt(end - 1) != '\\') {
                        break;
                    }
                    end++;
                }
                if (end < body.length()) {
                    topic = unescapeJson(body.substring(start, end));
                }
            }
        }

        if (topic == null || topic.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"Missing or invalid 'topic' in JSON payload\"}");
            return;
        }

        handleTopicRequest(topic, resp);
    }

    private void handleTopicRequest(String topic, HttpServletResponse resp) throws IOException {
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

        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

        // ponytail: demand a deep DAG with explicit tier counts so the skill tree renders with real depth
        String prompt = "You are a world-class curriculum designer. Generate a comprehensive, detailed learning path DAG (Directed Acyclic Graph) for mastering: " + escapeJson(topic) + ".\n\n" +
                "STRICT REQUIREMENTS:\n" +
                "1. Generate EXACTLY 12-15 distinct nodes.\n" +
                "2. Organize them into 3 explicit tiers:\n" +
                "   - Tier 1 BEGINNER (4-5 nodes): Foundational concepts, zero prior knowledge required.\n" +
                "   - Tier 2 INTERMEDIATE (4-5 nodes): Core techniques, requires Tier 1 completion.\n" +
                "   - Tier 3 ADVANCED (4-5 nodes): Expert-level mastery, requires Tier 2 completion.\n" +
                "3. Each node must have a short, specific name (2-4 words max) and a one-sentence description.\n" +
                "4. Links must reflect real conceptual prerequisites — not just sequential numbering.\n" +
                "5. Every node must be reachable from at least one root node.\n\n" +
                "Return ONLY a valid JSON object, no markdown fences, no commentary:\n" +
                "{ \"nodes\": [{ \"id\": \"string\", \"name\": \"string\", \"description\": \"string\" }], \"links\": [{ \"source\": \"string\", \"target\": \"string\" }] }";

        String jsonPayload = "{"
                + "\"contents\":[{"
                + "\"parts\":[{"
                + "\"text\":\"" + escapeJson(prompt) + "\""
                + "}]"
                + "}],"
                + "\"generationConfig\":{"
                + "\"responseMimeType\":\"application/json\","
                + "\"maxOutputTokens\":4000"
                + "}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(geminiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(60)) // ponytail: 60s for expanded 12-15 node DAG generation
                .build();

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Content-Type", "text/event-stream");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setCharacterEncoding("UTF-8");

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                resp.setStatus(response.statusCode());
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\": \"Gemini API returned error: " + escapeJson(response.body()) + "\"}");
                return;
            }

            String responseBody = response.body();
            String curriculumJson = cleanJson(extractGeminiText(responseBody));

            if (curriculumJson == null) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\": \"Failed to parse response from Gemini API\"}");
                return;
            }

            // Write the full JSON payload back to the browser
            resp.getWriter().write(curriculumJson);
            resp.getWriter().flush();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resp.getWriter().write("{\"error\": \"Request interrupted: " + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"error\": \"Request failed: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ponytail: simple manual JSON unescaper to avoid heavy parsing libraries and classpath issues
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

    // ponytail: extract the JSON text string directly from Gemini response without Jackson to keep classpath zero-dependency
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

    // ponytail: simple json escaping helper
    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String cleanJson(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    private static class StreamUnescaper {
        private final StringBuilder pending = new StringBuilder();

        public String feed(String input) {
            StringBuilder result = new StringBuilder();
            int i = 0;
            String text = input;
            if (pending.length() > 0) {
                text = pending.toString() + input;
                pending.setLength(0);
            }

            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\') {
                    if (i + 1 >= text.length()) {
                        pending.append(c);
                        break;
                    }
                    char next = text.charAt(i + 1);
                    if (next == 'u') {
                        if (i + 5 >= text.length()) {
                            pending.append(text.substring(i));
                            break;
                        }
                        String hex = text.substring(i + 2, i + 6);
                        try {
                            int code = Integer.parseInt(hex, 16);
                            result.append((char) code);
                        } catch (NumberFormatException e) {
                            result.append("\\u").append(hex);
                        }
                        i += 6;
                    } else {
                        if (next == '"') result.append('"');
                        else if (next == '\\') result.append('\\');
                        else if (next == '/') result.append('/');
                        else if (next == 'b') result.append('\b');
                        else if (next == 'f') result.append('\f');
                        else if (next == 'n') result.append('\n');
                        else if (next == 'r') result.append('\r');
                        else if (next == 't') result.append('\t');
                        else {
                            result.append('\\').append(next);
                        }
                        i += 2;
                    }
                } else {
                    result.append(c);
                    i++;
                }
            }
            return result.toString();
        }
    }
}
