# DocFlow: intelligent invoice processing

| Folder | What | Tech |
|---|---|---|
| `docflow-api/` | REST API, database, business rules | Java 21, Spring Boot 3, JPA, Flyway, PostgreSQL/H2 |
| `docflow-ui/`  | Document AI console (dashboard, review, upload, evaluation) | React, TypeScript, Vite |

## Run both (two terminals)

```bash
# 1. Backend: http://localhost:8080 (Swagger UI at /swagger-ui.html)
cd docflow-api
mvn spring-boot:run

# 2. Front end: http://localhost:5173
cd docflow-ui
copy .env.example .env        # Windows (macOS/Linux: cp)
# edit .env and set: VITE_API_URL=/api/v1
npm run dev
```

Leave `VITE_API_URL` empty to run the UI on mock data without the backend.
Details in each folder's README.
