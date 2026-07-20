# Project Concept Note & Technical Report

## Project Title
**Episteme: AI-Powered Knowledge Architecture & Research Assistant**

## Application Name
**Episteme** *(From Ancient Greek ἐπιστήμη — meaning true, deep, structured knowledge)*

## Live Application URL
[http://15.206.28.127:8080](http://15.206.28.127:8080)

---

## 1. Problem Statement / Objective

In today's digital landscape, learners and researchers spend significant time navigating fragmented websites, disjointed tutorials, and unstructured search results. The process of building a comprehensive mental model for a new subject is often overwhelming, non-linear, and time-consuming.

The objective of **Episteme** is to provide an intelligent web application that transforms any user topic query into a structured, progressive learning curriculum. Powered by Google's **Gemini 3.1 Flash Lite** Large Language Model, Episteme generates interactive Directed Acyclic Graphs (DAGs) and delivers deep, concept-first explanations through the **Feynman Learning Technique**, enabling users to master subjects efficiently.

---

## 2. Target Users and Use Cases

### Target Users
* **Students**: Seeking structured study pathways and simplified breakdowns of complex concepts.
* **Researchers**: Mapping out unfamiliar domains and adjacent academic fields.
* **Educators**: Designing progressive course curricula and topic dependencies.
* **Professionals**: Rapidly upskilling in technical frameworks, tools, and methodologies.
* **Lifelong Learners**: Exploring diverse subjects from Quantum Computing to Ancient History.

### Key Use Cases
* **Structured Curriculum Mapping**: Converting vague topics into progressive 3-tier learning graphs.
* **Feynman-Style Concept Explanation**: Breaking down sub-topics into analogies, plain English, and active recall.
* **Rapid Domain Exploration**: Fast-traveling across interconnected academic nodes.
* **Interactive Cognitive Breaks**: Utilizing the built-in system distraction routine (*⚠️ BORED?*) for curated educational exploration.

---

## 3. Pedagogical Core — The Feynman Technique Engine

Episteme goes beyond generic Q&A by enforcing the **Feynman Technique** for every generated lesson (`/api/get-lesson`). Each sub-topic explanation is dynamically structured into four distinct cognitive pillars:

1. **The Hook** *(1 Paragraph)*: Connects the abstract concept to an everyday real-world scenario.
2. **The Core Concept** *(2–3 Paragraphs)*: Explains the fundamental mechanisms in plain, jargon-free English.
3. **The Mental Model** *(1 Paragraph)*: Provides an intuitive mental framework or visual analogy.
4. **Active Recall** *(3 Bullet Points)*: Delivers self-testing questions to reinforce long-term memory retention.

---

## 4. Technology Stack

| Layer | Component | Technology / Tool | Description |
| :--- | :--- | :--- | :--- |
| **Frontend** | Markup & Logic | HTML5, Vanilla CSS, Vanilla JS | Lightweight, zero-framework monolithic client |
| **Visualization** | Graph Physics | D3.js v7 | 100-node dual-wing constellation layout with damped force simulation |
| **Backend** | Core Runtime | Java 17 | JDK 17 with Jakarta Servlets (`jakarta.servlet-api:6.0.0`) |
| **Server** | Web Server | Embedded Apache Tomcat | Lightweight, embedded servlet container listening on port `8080` |
| **Build System** | Dependency Manager | Apache Maven | Automated Java package build and shaded JAR assembly |
| **AI Model** | LLM Engine | Gemini 3.1 Flash Lite (`gemini-3.1-flash-lite`) | Google's high-speed generative language model |
| **AI Integration** | Protocol | Google Generative Language REST API | Server-side HTTP client with JSON schema enforcement (`responseMimeType: "application/json"`) |
| **Container** | Packaging | Docker / Podman (Alpine JRE) | Minimal Alpine Linux base image with OpenJDK 17 runtime |
| **Registry** | Image Storage | Amazon ECR | Private container repository (`816940507016.dkr.ecr.ap-south-1.amazonaws.com`) |
| **Cloud Hosting**| Compute | AWS ECS on Fargate | Serverless container execution with automated task definitions |

---

## 5. Prompting Strategy & LLM Integration

Episteme employs structured system prompting, explicit JSON Schema constraints, and low-temperature parameters to guarantee deterministic, parseable outputs from the Gemini model.

### Curriculum Graph Prompt (`/api/generate-path`)
```json
{
  "contents": [{
    "parts": [{
      "text": "You are an expert academic curriculum architect. The user wants to learn about: 'Quantum Computing'. Generate a massive, deeply structured Directed Acyclic Graph (DAG) for this subject in strict JSON format. CONSTRAINTS: 1. Scale graph to 20-25 sub-topics. 2. Group into 3 progressive tiers: Beginner (Tier 1), Intermediate (Tier 2), Advanced (Tier 3). 3. Nodes require fields: id, name, description, tier, status, resources, isSpecial. 4. Include one special 'Bored' node hooked into distraction routines. JSON Schema: { \"nodes\": [...], \"links\": [...] }"
    }]
  }],
  "generationConfig": {
    "responseMimeType": "application/json",
    "maxOutputTokens": 8192
  }
}
```

### Lesson Explanation Prompt (`/api/get-lesson`)
```text
You are a world-class tutor using the Feynman Technique. Explain the sub-topic 'Qubits & Superposition' within the overall course 'Quantum Computing'.

Structure your response in Markdown with exactly these sections:
1. The Hook (1 paragraph) - Connect it to everyday life.
2. The Core Concept (2-3 paragraphs) - Explain in plain English.
3. The Mental Model (1 paragraph) - Provide an analogy.
4. Active Recall (3 bullet points) - Questions for the user.
```

### Prompting Techniques Employed
* **Role-Based System Context**: Setting explicit domain expertise (Curriculum Architect / Feynman Tutor).
* **JSON Schema Enforcement**: Utilizing `responseMimeType: "application/json"` to eliminate markdown code-fence parsing errors.
* **Progressive Status Locking**: Setting Tier 1 nodes to `IN_PROGRESS` and dependent downstream nodes to `LOCKED` for step-by-step un-locking.

---

## 6. Performance Architecture & Resilience Engine

To solve LLM latency (~15 seconds for 25-node DAG generation) and rate-limiting constraints (HTTP 429 / `RESOURCE_EXHAUSTED`), Episteme implements a multi-layer hybrid performance architecture:

```
                  ┌───────────────────────────────┐
                  │    User Search Query Input    │
                  └──────────────┬────────────────┘
                                 │
                   Is it First Search of Session?
                    (sessionStorage Check)
                                 │
                 ┌───────────────┴───────────────┐
                YES                             NO
                 │                               │
       ┌─────────▼─────────┐           ┌─────────▼─────────┐
       │ Instant Local     │           │ Call Gemini 3.1   │
       │ Curriculum Generator│         │ Flash Lite REST API│
       │ (0.035s / 35ms)   │           └─────────┬─────────┘
       └───────────────────┘                     │
                                         Did Gemini Succeed?
                                         ┌───────┴───────┐
                                        YES             NO (429 / Error)
                                         │               │
                                   ┌─────▼─────┐   ┌─────▼───────────────┐
                                   │ Render AI │   │ Fallback Local      │
                                   │ Graph DAG │   │ Curriculum Generator│
                                   └───────────┘   └─────────────────────┘
```

1. **35ms Instant First-Search**: Using client-side `sessionStorage` tracking (`la_first_search_done`), the user's initial query bypasses API overhead and triggers an instant 35ms in-memory curriculum generator.
2. **Exponential Backoff Retry Loop**: Retries transient rate limits up to 4 times with exponential backoff (2s → 4s → 8s).
3. **Resilient Local Fallback Engine**: If the Gemini API fails or reaches quota, the Java servlet automatically catches the exception and falls back to a locally generated curriculum, ensuring **100% application availability and zero user freezes**.

---

## 7. Phase-by-Phase Development Summary

* **Phase 1: Requirement Analysis & Design**: Defined target personas, DAG curriculum schemas, and the 3-Tier learning progression.
* **Phase 2: Frontend & Constellation Web**: Built a monolithic client in HTML5/Vanilla CSS featuring a 100-node dual-wing D3.js force-directed graph with empty-center avoidance physics.
* **Phase 3: Java Servlet Backend**: Developed lightweight REST servlets (`GeneratePathServlet`, `GetLessonServlet`, `Launcher`) using JDK 17 and Jakarta Servlets.
* **Phase 4: AI & Prompt Engineering**: Integrated Google Generative Language REST API with Feynman Technique prompting and JSON output enforcement.
* **Phase 5: Containerization**: Packaged the application into a lightweight Docker image using Eclipse Temurin Alpine JRE 17.
* **Phase 6: AWS Cloud Deployment**: Deployed container images to Amazon ECR and orchestrated serverless deployment on AWS ECS Fargate.

---

## 8. Application Architecture & Deployment Flow

### System Architecture Flow
```
User Browser ──► HTML5/CSS/JS (D3.js) ──► Java 17 Servlets (Tomcat) ──► Gemini 3.1 REST API ──► AI Response
```

### AWS Cloud Deployment Pipeline
```
Local Workspace ──► Podman Build ──► Amazon ECR Repository ──► AWS ECS Task Definition ──► AWS Fargate Cluster (Live URL)
```

---

## 9. Key Learnings & Reflection

Through the development and deployment of Episteme, the following core engineering skills were demonstrated:
* **LLM Integration & Prompt Engineering**: Structuring JSON schemas and role-based prompts for consistent model outputs.
* **Java Web Architecture**: Building zero-dependency REST endpoints using Jakarta Servlets and embedded Tomcat.
* **Frontend Data Visualization**: Designing 100-node physics-based force graphs using D3.js.
* **High-Availability Cloud Architecture**: Configuring serverless container deployment using AWS ECR and ECS Fargate.
* **Resilience & Fallback Design**: Designing hybrid fallback mechanisms to withstand API rate limits and network degradation.

---

## 10. Conclusion

**Episteme** successfully demonstrates how modern Large Language Models and cloud-native serverless architectures can be combined to build a high-performance educational platform. By integrating **Gemini 3.1 Flash Lite**, **Java 17 Jakarta Servlets**, **D3.js**, **Docker**, and **AWS ECS Fargate**, Episteme delivers an engaging, intuitive, and resilient research assistant for students, educators, and lifelong learners worldwide.
