# SSE Multi-Turn Chat with Ollama (No RAG)

A minimal chat application built with **Spring Boot 3** and **LangChain4j**, streaming AI responses token-by-token to the browser via **Server-Sent Events (SSE)**. Responses are rendered with a typewriter effect in a simple web UI, and multi-turn conversation memory is kept per session.

## Features

- **Token streaming** — responses are streamed token-by-token over SSE instead of waiting for the full reply
- **Multi-turn memory** — each session keeps its own conversation history (last 8 messages) via `MessageWindowChatMemory`
- **Automatic session cleanup** — idle sessions (no request for 30 minutes) are evicted by a scheduled task to prevent memory leaks
- **Typewriter rendering** — the frontend buffers incoming tokens and renders them with an adaptive typing speed
- **Markdown support** — AI replies are rendered with [marked.js](https://marked.js.org/)
- **Session management** — each browser tab gets a unique session ID; conversations can be cleared individually

## Tech Stack

| Layer      | Technology                                       |
|------------|--------------------------------------------------|
| Backend    | Java 17, Spring Boot 3.2, LangChain4j 0.34.0     |
| LLM runtime| [Ollama](https://ollama.com/) (local)            |
| Protocol   | Server-Sent Events (SSE)                         |
| Frontend   | Plain HTML/CSS/JavaScript + marked.js            |
| Build      | Maven                                            |

## Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **Ollama** running locally ([download here](https://ollama.com/download))

## Getting Started

### 1. Pull a model

```bash
ollama pull llama3.2:1b
```

You can use any model available in Ollama — just update `model-name` in `application.yml` accordingly.

### 2. (Optional but recommended) Keep the model loaded in memory

```powershell
# Windows (PowerShell)
setx OLLAMA_KEEP_ALIVE -1
# then restart Ollama
```

Without this, Ollama unloads the model after a few idle minutes, which causes a long delay before the first token of the next request.

### 3. Build and run

```bash
mvn spring-boot:run
```

### 4. Open the app

Navigate to <http://localhost:8080> and start chatting.

## Configuration

`src/main/resources/application.yml`:

```yaml
langchain4j:
  ollama:
    chat-model:
      model-name: llama3.2:1b
      base-url: http://localhost:11434
    streaming-chat-model:
      model-name: llama3.2:1b
      base-url: http://localhost:11434
      temperature: 0.7
      top-k: 40
      timeout: 120s
```

| Property                | Description                                                        |
|-------------------------|--------------------------------------------------------------------|
| `model-name`            | Ollama model to use (must be pulled first)                         |
| `base-url`              | Ollama server address                                              |
| `temperature`           | Sampling temperature — higher means more creative output           |
| `top-k`                 | Limits sampling to the top K candidate tokens                      |
| `timeout`               | Max duration for a single streaming response                       |

> Note: the `langchain4j-ollama-spring-boot-starter` is required for these properties to take effect — it is what registers the `ChatLanguageModel` and `StreamingChatLanguageModel` beans.

## API Endpoints

| Method | Path                              | Description                                   |
|--------|-----------------------------------|-----------------------------------------------|
| POST   | `/api/stream-chat/ask`            | Send a message, receive SSE token stream      |
| POST   | `/api/stream-chat/clear/{sessionId}` | Clear the conversation history of a session |

Example request body for `/ask`:

```json
{
  "sessionId": "abc123",
  "message": "Tell me a story"
}
```

The response is an SSE stream where each event looks like:

```
data:Once
data: upon
data: a
data: time
...
```

## Project Structure

```
src/main/java/com/ai/ssedemo/
├── OllamaSseChatApplication.java   # Entry point, enables scheduling
├── config/
│   └── CorsConfig.java             # CORS configuration
└── controller/
    └── StreamChatController.java   # SSE streaming endpoint + session memory
                                    #   - memoryMap: sessionId -> chat memory
                                    #   - scheduled task evicts sessions idle > 30 min

src/main/resources/
├── application.yml                 # LangChain4j / Ollama configuration
└── static/
    └── index.html                  # Chat UI (SSE parsing, typewriter effect,
                                    #   typing indicator, error handling)
```

## How It Works

1. The browser assigns a random `sessionId` per tab and POSTs the user message to `/api/stream-chat/ask`.
2. The controller looks up (or creates) the `MessageWindowChatMemory` for that session, appends the user message, and calls the streaming model with the conversation history.
3. Each generated token is written to an `SseEmitter` as a `data:` event.
4. Once the model finishes, the full AI reply is appended to the session memory so the next turn has context.
5. The frontend parses the SSE events, strips the `data:` prefix, and feeds the tokens into a typewriter queue that renders them smoothly.
