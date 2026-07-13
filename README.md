<div align="center">

# 🚀 Learn Anything
### AI-Powered Personalized Curriculum Engine

Generate an interactive learning roadmap for **any subject**, powered by **Google Gemini**.  
Explore concepts through a dynamic knowledge graph and learn every topic with AI-generated Feynman-style explanations.

![Java](https://img.shields.io/badge/Java-21-orange)
![Jakarta EE](https://img.shields.io/badge/Jakarta%20EE-10-red)
![Gemini](https://img.shields.io/badge/Google-Gemini%203.5-blue)
![AWS](https://img.shields.io/badge/AWS-App%20Runner-yellow)
![Docker](https://img.shields.io/badge/Docker-Containerized-blue)

</div>

---

# 📖 Overview

**Learn Anything** is an AI-driven curriculum engine that transforms any learning goal into a structured, interactive roadmap.

Instead of searching through dozens of tutorials and videos, simply enter a topic and the application generates a complete prerequisite graph using **Gemini 3.5 Flash**.

Each concept becomes an interactive node that can be explored individually. Selecting a node opens an AI tutor capable of explaining the topic using the **Feynman Technique**, making difficult concepts easier to understand.

---

# ✨ Features

### 🧠 AI Curriculum Generation
- Generates structured learning roadmaps for any topic
- Creates prerequisite relationships as a Directed Acyclic Graph (DAG)
- Prevents cyclic dependencies for logical learning progression

### 🌐 Interactive Knowledge Graph
- Dynamic D3.js force-directed visualization
- Drag, zoom and explore relationships
- Visual prerequisite mapping

### 📚 AI Tutor
- Context-aware explanations
- Feynman-style teaching
- Beginner-friendly lessons
- Builds on previously learned concepts

### ⚡ Real-Time Streaming
- Server-Sent Events (SSE)
- Progressive content generation
- No waiting for entire responses

### ☁ Cloud Ready
- Docker containerization
- AWS App Runner deployment
- Environment-based API key management

---

# 🏗 Architecture

```
                    User
                      │
                      ▼
          HTML + JavaScript + D3.js
                      │
             Server Sent Events
                      │
                      ▼
          Jakarta EE Java Servlets
                      │
          Google Gen AI SDK
                      │
                      ▼
             Gemini 3.5 Flash
                      │
                      ▼
      Learning DAG + AI Explanations
```

---

# ⚙ Tech Stack

| Layer | Technology |
|--------|------------|
| Frontend | HTML5, CSS3, JavaScript |
| Visualization | D3.js |
| Backend | Java Servlets (Jakarta EE) |
| AI | Google Gemini 3.5 Flash |
| Streaming | Server-Sent Events (SSE) |
| Containerization | Docker |
| Deployment | AWS App Runner |

---

# 🚀 How It Works

1. Enter any topic.
2. The backend sends a prompt to Gemini.
3. Gemini generates a structured prerequisite graph.
4. The graph is rendered using D3.js.
5. Clicking any node requests a detailed AI explanation.
6. Responses stream back live through SSE.

---

# 📂 Project Structure

```
learn-anything/
│
├── frontend/
│   ├── index.html
│   ├── app.js
│   └── styles.css
│
├── backend/
│   ├── servlets/
│   ├── services/
│   └── models/
│
├── Dockerfile
├── pom.xml
└── README.md
```

---

# 🔐 Security

Sensitive credentials are **never committed** to the repository.

The application uses runtime environment variables:

```bash
GOOGLE_API_KEY=your_api_key
```

This configuration works seamlessly with Docker and AWS App Runner.

---

# 🛠 Running Locally

## Clone

```bash
git clone https://github.com/yourusername/learn-anything.git
cd learn-anything
```

## Set Environment Variable

```bash
export GOOGLE_API_KEY=YOUR_API_KEY
```

or on Windows

```powershell
set GOOGLE_API_KEY=YOUR_API_KEY
```

## Build

```bash
mvn clean package
```

## Run

Deploy the generated WAR to your Jakarta EE server (Tomcat/Jetty compatible) or run using Docker.

---

# 📸 Screenshots

> Add screenshots or GIFs here.

```
Landing Page
```

```
Knowledge Graph
```

```
AI Tutor
```

---

# 🎯 Future Improvements

- User accounts
- Progress tracking
- Learning analytics
- Quiz generation
- Flashcards
- Spaced repetition
- PDF export
- Multi-language support
- Collaborative learning paths

---

# 🤝 Contributing

Contributions, feature requests, and pull requests are welcome.

---

# 📜 License

This project is licensed under the MIT License.

---

<div align="center">

**Build knowledge visually. Learn intelligently.**

</div>
