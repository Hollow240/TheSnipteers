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

        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?key=" + apiKey;

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
                + "\"responseMimeType\":\"application/json\""
                + "}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(geminiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(20))
                .build();

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Content-Type", "text/event-stream");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setCharacterEncoding("UTF-8");

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            if (response.statusCode() != 200) {
                resp.setStatus(response.statusCode());
                resp.setContentType("application/json");
                try (java.io.InputStream is = response.body()) {
                    String err = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    resp.getWriter().write("{\"error\": \"Gemini API returned error: " + escapeJson(err) + "\"}");
                }
                return;
            }

            // ponytail: custom stream parser to extract text chunks from Gemini stream on the fly and flush immediately
            try (java.io.InputStream is = response.body();
                 java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                
                StringBuilder buffer = new StringBuilder();
                char[] charBuffer = new char[1024];
                int numRead;
                while ((numRead = reader.read(charBuffer)) != -1) {
                    buffer.append(charBuffer, 0, numRead);
                    
                    while (true) {
                        int textIdx = buffer.indexOf("\"text\"");
                        if (textIdx == -1) break;
                        
                        int quoteStart = buffer.indexOf("\"", textIdx + 6);
                        if (quoteStart == -1) break;
                        
                        int start = quoteStart + 1;
                        int end = start;
                        boolean foundEnd = false;
                        
                        while (end < buffer.length()) {
                            if (buffer.charAt(end) == '"') {
                                int backslashes = 0;
                                for (int i = end - 1; i >= start; i--) {
                                    if (buffer.charAt(i) == '\\') backslashes++;
                                    else break;
                                }
                                if (backslashes % 2 == 0) {
                                    foundEnd = true;
                                    break;
                                }
                            }
                            end++;
                        }
                        
                        if (!foundEnd) {
                            break; // wait for more data
                        }
                        
                        String escapedText = buffer.substring(start, end);
                        String text = unescapeJson(escapedText);
                        
                        // Write chunk immediately to browser
                        resp.getWriter().write(text);
                        resp.getWriter().flush();
                        
                        // Remove processed portion from buffer
                        buffer.delete(0, end + 1);
                    }
                }
            }

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
}
