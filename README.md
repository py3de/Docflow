# DocFlow: intelligent invoice processing

Upload an invoice PDF; DocFlow reads it, extracts the fields with an LLM, checks them against business
rules and purchase orders, and either posts it automatically or sends it to a person for review.

| Folder | What | Tech |
|---|---|---|
| `docflow-api/` | REST API, extraction pipeline, database, business rules | Java 21, Spring Boot 3, JPA, Flyway, PDFBox, PostgreSQL/H2 |
| `docflow-ui/`  | Document AI console (dashboard, review with the real PDF page, upload, evaluation) | React, TypeScript, Vite |

## Run both (two terminals)

```bash
# 1. Backend: http://localhost:8080 (Swagger UI at /swagger-ui.html)
cd docflow-api
mvn spring-boot:run

# 2. Front end: http://localhost:5173
cd docflow-ui
# .env must contain: VITE_API_URL=/api/v1
npm run dev
```

Then upload the files from `docflow-api/samples/` on the Upload page. Without `VITE_API_URL`, the UI runs on mock data.
By default the pipeline uses a built-in offline extractor; see `docflow-api/README.md` to switch to Claude or an OpenAI-compatible model.
