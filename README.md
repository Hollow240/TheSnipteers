<div align="center">

# 🏛️ EPISTEME (ἐπιστήμη)
### AI-Powered Knowledge Architecture & Interactive Curriculum Engine

*Transform any subject into a structured 3-tier knowledge graph with AI-generated Feynman learning pathways.*

[**Live Demo**](http://15.206.28.127:8080) • [**Technical Report**](./PROJECT_REPORT.md) • [**GitHub Repo**](https://github.com/Hollow240/TheSnipteers.git)

![Java](https://img.shields.io/badge/Java-17-orange)
![Jakarta Servlets](https://img.shields.io/badge/Jakarta%20Servlets-6.0-red)
![Google Gemini](https://img.shields.io/badge/Google-Gemini%203.1%20Flash%20Lite-blue)
![AWS ECS Fargate](https://img.shields.io/badge/AWS-ECS%20Fargate-yellow)
![Amazon ECR](https://img.shields.io/badge/Amazon-ECR-blue)
![Docker](https://img.shields.io/badge/Docker-Containerized-blue)
![D3.js](https://img.shields.io/badge/D3.js-v7-green)

</div>

---

## 📖 Overview

**Episteme** *(from Ancient Greek ἐπιστήμη — meaning true, deep, structured knowledge)* is a cloud-native web application designed to eliminate unstructured information searching. 

Instead of wading through disjointed tutorials, typing any topic into Episteme generates a **3-tier Directed Acyclic Graph (DAG)** mapping out beginner, intermediate, and advanced prerequisites. Selecting any node launches an interactive AI tutor that delivers explanations using the **Feynman Technique** (*The Hook*, *The Core Concept*, *The Mental Model*, and *Active Recall*).

---

## ✨ Key Features

### 🧠 AI Curriculum Architecture & 3-Tier DAG
- **Progressive Skill Mapping**: Converts complex topics into 20–25 node Directed Acyclic Graphs (DAGs).
- **3 Learning Tiers**: Automatically partitions knowledge into **Beginner (Tier 1)**, **Intermediate (Tier 2)**, and **Advanced (Tier 3)**.
- **Dynamic Unlock Progression**: Sets Tier 1 nodes to `IN_PROGRESS` and dependent downstream concepts to `LOCKED` until prerequisites are met.

### 📚 Pedagogical Core — Feynman Learning Engine
- **The Hook**: Connects abstract sub-topics to everyday scenarios.
- **The Core Concept**: Explains foundational mechanics in plain, jargon-free English.
- **The Mental Model**: Provides clear visual and conceptual analogies.
- **Active Recall**: Delivers self-testing questions to solidify long-term memory retention.

### 🌐 100-Node Constellation Landing Web
- **Dual-Wing Interactive Graph**: Rendered using **D3.js v7** with physics force simulation.
- **Balanced Partitioning**: Features 50 Tech & Physical Science nodes (*Left Wing*) and 50 Humanities & Life Science nodes (*Right Wing*).
- **Center-Avoidance Physics**: Damped force attraction keeps the center search container clean and legible.
- **System "Bored?" Routine**: Integrated `⚠️ BORED?` system node providing curated cognitive breaks and educational distractions.

### ⚡ Hybrid Performance Architecture & Resilience
- **35ms Instant First-Search**: Client-side `sessionStorage` tracking (`firstSearch: true`) routes the user's initial query to a 35ms in-memory layout generator.
- **Exponential Backoff**: Automated retries (2s → 4s → 8s) to mitigate LLM API rate limits (HTTP 429).
- **Bulletproof Fallback**: Automatic local curriculum fallback guarantees 100% uptime and zero application freezes.

---

## 🏗 Architecture

```
                                 User Browser
                                      │
                         ┌────────────┴────────────┐
                         │   HTML5 / CSS3 / JS     │
                         │    (D3.js v7 Physics)   │
                         └────────────┬────────────┘
                                      │
                    HTTP POST (/api/generate-path, /api/get-lesson)
                                      │
                         ┌────────────▼────────────┐
                         │  Java 17 Jakarta Servlets│
                         │    (Embedded Tomcat)    │
                         └────────────┬────────────┘
                                      │
                     Google Generative Language REST API
                                      │
                         ┌────────────▼────────────┐
                         │ Gemini 3.1 Flash Lite   │
                         └─────────────────────────┘
```

---

## ⚙️ Tech Stack

| Layer | Technology | Description |
| :--- | :--- | :--- |
| **Frontend** | HTML5, Vanilla CSS, Vanilla JavaScript | Zero-framework client interface |
| **Visualization** | D3.js v7 | 100-node physics-based constellation graph |
| **Backend** | Java 17 & Jakarta Servlets | Lightweight JDK 17 REST API container |
| **Web Server** | Embedded Apache Tomcat | Listens on port `8080` |
| **Build Tool** | Apache Maven | Package dependency & assembly build |
| **AI Model** | Google Gemini 3.1 Flash Lite | `gemini-3.1-flash-lite` |
| **Container** | Docker (Alpine JRE 17) | Lightweight container base |
| **Cloud Hosting** | AWS ECS on Fargate & Amazon ECR | Serverless cloud container infrastructure |

---

## 🛠️ Running Locally

### 1. Clone Repository
```bash
git clone https://github.com/Hollow240/TheSnipteers.git
cd TheSnipteers
```

### 2. Set API Key
```bash
export GOOGLE_API_KEY=your_gemini_api_key
```
*(Windows PowerShell)*:
```powershell
$env:GOOGLE_API_KEY="your_gemini_api_key"
```

### 3. Build & Run
```bash
mvn clean package
java -jar backend/target/backend-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```
Access the application at `http://localhost:8080`.

---

## ☁️ Deploying to AWS ECS Fargate

The project includes an automated PowerShell deployment script (`deploy.ps1`):

```powershell
$env:GOOGLE_API_KEY="your_api_key"
$env:ECS_ROLE_ARN="arn:aws:iam::YOUR_ACCOUNT_ID:role/ecsTaskExecutionRole"
$env:SUBNETS="subnet-xxxxxx"
$env:SECURITY_GROUP="sg-xxxxxx"

.\deploy.ps1
```

---

## 📄 Documentation

For full architectural diagrams, prompt engineering strategies, and performance benchmarks, see the **[PROJECT_REPORT.md](./PROJECT_REPORT.md)** document.
