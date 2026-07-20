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
import java.util.concurrent.ConcurrentHashMap;

@WebServlet(name = "GeneratePathServlet", urlPatterns = {"/api/generate-path"})
public class GeneratePathServlet extends HttpServlet {

    private static final ConcurrentHashMap<String, String> curriculumCache = new ConcurrentHashMap<>();

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

        boolean firstSearch = "true".equalsIgnoreCase(req.getParameter("firstSearch"));
        handleTopicRequest(topic, firstSearch, resp);
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

        boolean firstSearch = body.contains("\"firstSearch\":true") || body.contains("\"firstSearch\": true");
        handleTopicRequest(topic, firstSearch, resp);
    }

    private void handleTopicRequest(String topic, boolean firstSearch, HttpServletResponse resp) throws IOException {
        String normalizedTopic = topic.trim().toLowerCase();

        if (firstSearch) {
            System.out.println("⚡ Instant First Search routing for topic: " + topic);
            String curriculumJson = generateLocalCurriculum(topic);
            curriculumCache.put(normalizedTopic, curriculumJson);
            resp.getWriter().write(curriculumJson);
            resp.getWriter().flush();
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

        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

        // ponytail: demand a packed, high-density academic network with 35-50 nodes and a special system Bored node
        String prompt = "You are an expert academic curriculum architect. The user wants to learn about: '" + escapeJson(topic) + "'.\n\n"
                  + "Generate a massive, deeply structured, and extremely dense Directed Acyclic Graph (DAG) for this subject in strict JSON format.\n"
                  + "CRITICAL CONSTRAINTS:\n"
                  + "1. FULL-WINDOW DENSITY: Scale the graph to include between 20 to 25 highly specific sub-topics, historical methodologies, related frameworks, and adjacent academic fields.\n"
                  + "2. Group the nodes logically into 3 progressive learning tiers: 'Beginner' (Tier 1), 'Intermediate' (Tier 2), and 'Advanced' (Tier 3).\n"
                  + "3. Create explicit dependency links between parent and child nodes.\n"
                  + "4. For EACH individual node, include exactly these fields:\n"
                  + "   - 'id': unique string slug (e.g., 'intro-to-variables')\n"
                  + "   - 'name': crisp, clean display title (e.g., 'Variables & Control Flow')\n"
                  + "   - 'description': one-sentence description explaining what the node is about\n"
                  + "   - 'tier': 1, 2, or 3\n"
                  + "   - 'status': Set the first 2-3 Tier 1 nodes to 'IN_PROGRESS' or 'COMPLETED', and all subsequent dependent nodes strictly to 'LOCKED'.\n"
                  + "   - 'resources': A single string containing 2 high-quality educational markdown links separated by a semicolon and space (e.g., '[MDN Docs](https://developer.mozilla.org); [GitHub Source](https://github.com/Hollow240/TheSnipteers)').\n"
                  + "   - 'isSpecial': false\n"
                  + "5. THE SPECIAL 'BORED' NODE: You MUST include exactly one special system node explicitly configured to hook into the client's randomized distraction routine. You must hardcode this node inside the 'nodes' array exactly as follows:\n"
                  + "   { \"id\": \"Bored\", \"name\": \"⚠️ BORED?\", \"description\": \"Take a brief cognitive break with a curated educational distraction.\", \"tier\": 1, \"status\": \"IN_PROGRESS\", \"resources\": \"[Curated Distraction](https://github.com/Hollow240/TheSnipteers); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\", \"isSpecial\": true }\n"
                  + "   You MUST also create exactly 3 to 4 link objects connecting this 'Bored' node to highly disparate, unexpected branches (different node IDs) of the generated curriculum.\n"
                  + "6. NEVER use raw double quotes (\") inside any text values (like name or description). If you need quotes, use single quotes (').\n"
                  + "7. Strictly avoid trailing commas at the end of objects or arrays (e.g., [a, b,] is invalid).\n\n"
                  + "Ensure your output contains ONLY the raw JSON string. Do not wrap it in markdown code blocks like ```json ... ``` as it will break the Java parsing buffer.\n\n"
                  + "JSON Schema:\n"
                  + "{ \"nodes\": [{ \"id\": \"string\", \"name\": \"string\", \"description\": \"string\", \"tier\": 1, \"status\": \"string\", \"resources\": \"string\", \"isSpecial\": boolean }], \"links\": [{ \"source\": \"string\", \"target\": \"string\" }] }";

        String jsonPayload = "{"
                + "\"contents\":[{"
                + "\"parts\":[{"
                + "\"text\":\"" + escapeJson(prompt) + "\""
                + "}]"
                + "}],"
                + "\"generationConfig\":{"
                + "\"responseMimeType\":\"application/json\","
                + "\"maxOutputTokens\":8192"
                + "}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(geminiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(60)) // ponytail: 60s for expanded 12-15 node DAG generation
                .build();

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (curriculumCache.containsKey(normalizedTopic)) {
            System.out.println("🚀 Cache Hit for topic: " + normalizedTopic);
            resp.getWriter().write(curriculumCache.get(normalizedTopic));
            resp.getWriter().flush();
            return;
        }

        try {
            HttpResponse<String> response = null;
            int maxRetries = 4;
            long waitTimeMs = 2000; // Start with a 2-second pause

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    // Detect if rate limit was hit
                    if (response.statusCode() == 429 || (response.body() != null && response.body().contains("RESOURCE_EXHAUSTED"))) {
                        throw new IOException("Gemini API Rate Limit (429 / RESOURCE_EXHAUSTED)");
                    }
                    
                    // Success or non-429 error code: stop retrying
                    break;
                } catch (IOException | InterruptedException e) {
                    String errorMsg = e.getMessage();
                    boolean isRateLimit = (errorMsg != null && (errorMsg.contains("429") || errorMsg.contains("RESOURCE_EXHAUSTED") || errorMsg.contains("Rate Limit")));
                    
                    if (isRateLimit && attempt < maxRetries) {
                        System.out.println("⚠️ Gemini Rate Limit hit (429). Attempt " + attempt + " of " + maxRetries);
                        try {
                            System.out.println("⏳ Waiting " + (waitTimeMs / 1000) + " seconds before retry...");
                            Thread.sleep(waitTimeMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Retry wait interrupted", ie);
                        }
                        waitTimeMs *= 2; // Exponential backoff: 2s -> 4s -> 8s
                    } else {
                        throw e; // Non-rate-limit error or max retries reached: propagate it
                    }
                }
            }
            
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

            // Cache the successful result
            curriculumCache.put(normalizedTopic, curriculumJson);

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

        // Strip leading markdown fence (```json or ```)
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            trimmed = trimmed.trim();
        }

        // Strip ANY trailing backticks (Gemini sometimes outputs `` or ``` after closing brace)
        while (trimmed.endsWith("`")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        trimmed = trimmed.trim();

        // Final safety net: extract from first { or [ to last } or ]
        int firstBrace = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '{' || c == '[') { firstBrace = i; break; }
        }
        int lastBrace = -1;
        for (int i = trimmed.length() - 1; i >= 0; i--) {
            char c = trimmed.charAt(i);
            if (c == '}' || c == ']') { lastBrace = i; break; }
        }
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
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
    // generateLocalCurriculum: generates an instant, valid 8-node curriculum locally
    private String generateLocalCurriculum(String topic) {
        String escapedTopic = escapeJson(topic);
        return "{\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"id\": \"intro\",\n" +
                "      \"name\": \"Introduction to " + escapedTopic + "\",\n" +
                "      \"description\": \"Learn the foundational concepts and basic principles of " + escapedTopic + ".\",\n" +
                "      \"tier\": 1,\n" +
                "      \"status\": \"IN_PROGRESS\",\n" +
                "      \"resources\": \"[Wikipedia](https://en.wikipedia.org/wiki/" + escapedTopic.replace(" ", "_") + "); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\",\n" +
                "      \"isSpecial\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"history\",\n" +
                "      \"name\": \"History & Origins\",\n" +
                "      \"description\": \"Explore the historical context and pioneers who shaped " + escapedTopic + ".\",\n" +
                "      \"tier\": 1,\n" +
                "      \"status\": \"LOCKED\",\n" +
                "      \"resources\": \"[Britannica](https://www.britannica.com); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\",\n" +
                "      \"isSpecial\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"core-principles\",\n" +
                "      \"name\": \"Core Principles\",\n" +
                "      \"description\": \"Understand the fundamental rules, theories, and laws of " + escapedTopic + ".\",\n" +
                "      \"tier\": 1,\n" +
                "      \"status\": \"LOCKED\",\n" +
                "      \"resources\": \"[Feynman Lectures](https://www.feynmanlectures.caltech.edu); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\",\n" +
                "      \"isSpecial\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"methodology\",\n" +
                "      \"name\": \"Advanced Methodology\",\n" +
                "      \"description\": \"Deep dive into the tools, processes, and techniques used in " + escapedTopic + ".\",\n" +
                "      \"tier\": 2,\n" +
                "      \"status\": \"LOCKED\",\n" +
                "      \"resources\": \"[MIT Courseware](https://ocw.mit.edu); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\",\n" +
                "      \"isSpecial\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"applications\",\n" +
                "      \"name\": \"Practical Applications\",\n" +
                "      \"description\": \"See how " + escapedTopic + " is applied to solve real-world problems.\",\n" +
                "      \"tier\": 2,\n" +
                "      \"status\": \"LOCKED\",\n" +
                "      \"resources\": \"[Coursera](https://www.coursera.org); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\",\n" +
                "      \"isSpecial\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"challenges\",\n" +
                "      \"name\": \"Modern Challenges\",\n" +
                "      \"description\": \"Analyze the current limitations, controversies, and open questions in " + escapedTopic + ".\",\n" +
                "      \"tier\": 3,\n" +
                "      \"status\": \"LOCKED\",\n" +
                "      \"resources\": \"[Google Scholar](https://scholar.google.com); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\",\n" +
                "      \"isSpecial\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"future\",\n" +
                "      \"name\": \"Future Horizons\",\n" +
                "      \"description\": \"Examine emerging trends and the future landscape of " + escapedTopic + ".\",\n" +
                "      \"tier\": 3,\n" +
                "      \"status\": \"LOCKED\",\n" +
                "      \"resources\": \"[TechRadar](https://www.techradar.com); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\",\n" +
                "      \"isSpecial\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"Bored\",\n" +
                "      \"name\": \"⚠️ BORED?\",\n" +
                "      \"description\": \"Take a brief cognitive break with a curated educational distraction.\",\n" +
                "      \"tier\": 1,\n" +
                "      \"status\": \"IN_PROGRESS\",\n" +
                "      \"resources\": \"[Curated Distraction](https://github.com/Hollow240/TheSnipteers); [Project Wiki](https://github.com/Hollow240/TheSnipteers/wiki)\",\n" +
                "      \"isSpecial\": true\n" +
                "    }\n" +
                "  ],\n" +
                "  \"links\": [\n" +
                "    { \"source\": \"intro\", \"target\": \"core-principles\" },\n" +
                "    { \"source\": \"core-principles\", \"target\": \"methodology\" },\n" +
                "    { \"source\": \"history\", \"target\": \"core-principles\" },\n" +
                "    { \"source\": \"methodology\", \"target\": \"applications\" },\n" +
                "    { \"source\": \"applications\", \"target\": \"challenges\" },\n" +
                "    { \"source\": \"challenges\", \"target\": \"future\" },\n" +
                "    { \"source\": \"Bored\", \"target\": \"core-principles\" },\n" +
                "    { \"source\": \"Bored\", \"target\": \"applications\" },\n" +
                "    { \"source\": \"Bored\", \"target\": \"future\" }\n" +
                "  ]\n" +
                "}";
    }

}
