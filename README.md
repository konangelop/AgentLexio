# Lexio

AI-powered German vocabulary learning assistant with an interactive chat interface.

## Project Structure

```
Lexio/
├── AgentLexio/     # Spring Boot backend
└── frontend/       # React frontend
```

## Tech Stack

### Backend (AgentLexio)
- Java 21
- Spring Boot 3.4.1
- LangChain4j 1.9.1 with Anthropic integration
- H2 Database (development)
- Maven

### Frontend
- React 18
- TypeScript
- Vite

## Prerequisites

- Java 21+
- Node.js 18+
- Maven 3.9+
- Anthropic API key

## Getting Started

### Backend

1. Navigate to the backend directory:
   ```bash
   cd AgentLexio
   ```

2. Set your Anthropic API key:
   ```bash
   export ANTHROPIC_API_KEY=your-api-key
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

The backend will start on `http://localhost:8080`.

### Frontend

1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start the development server:
   ```bash
   npm run dev
   ```

The frontend will start on `http://localhost:5173`.

## Features

- Interactive chat interface for German vocabulary learning
- AI-powered vocabulary exercises
- Translation assistance
- Exercise summaries and progress tracking
