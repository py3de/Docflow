# DocFlow API: Java backend

Spring Boot 3 / Java 21 REST API for **DocFlow**, an intelligent invoice-processing service.
It serves the React console in `../docflow-ui` and stores everything in a real database.

This is **step 1** of the backend: documents, upload, human review, metrics and evaluation runs,
all backed by PostgreSQL (or H2 locally). Step 2 adds the real extraction pipeline
(PDFBox / Tesseract OCR → LLM structured output → validation rules → PO matching).

## Run it

**You need:** JDK 21 and Maven 3.9+.

```powershell
# Windows: install JDK 21 (skip if `java -version` already shows 21)
winget install EclipseAdoptium.Temurin.21.JDK
# Maven: download the binary zip from https://maven.apache.org/download.cgi,
# extract it (e.g. C:\tools\maven) and add C:\tools\maven\bin to PATH.
# Or open this folder in IntelliJ IDEA, which ships with Maven.
```

```bash
mvn spring-boot:run          # starts on http://localhost:8080 with an in-memory H2 database
mvn verify                   # compiles and runs all tests
```

On first start it seeds about 14 days of realistic, fictional invoices (two tenants) so the
dashboard has data straight away. Turn that off with `docflow.demo-data.enabled=false`.

| URL | What |
|---|---|
| http://localhost:8080/swagger-ui.html | Interactive API docs (OpenAPI) |
| http://localhost:8080/v3/api-docs | OpenAPI spec (JSON) |
| http://localhost:8080/actuator/health | Health check |

**With PostgreSQL instead of H2** (needs Docker):

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

**Connect the UI:** in `docflow-ui/.env` set `VITE_API_URL=/api/v1` and run `npm run dev`.
The Vite dev server forwards `/api` to `localhost:8080`.

## Endpoints

All document and metrics endpoints require the header `X-Tenant-Id` (e.g. `acme-de`).

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/documents?size=100&sort=receivedAt,desc` | List (JSON array; total in `X-Total-Count`) |
| GET | `/api/v1/documents/{id}` | Full document: fields, boxes, issues, timings, PO match |
| POST | `/api/v1/documents` | Multipart upload (`file`, `docType`). 201 new, 200 duplicate |
| POST | `/api/v1/documents/{id}/review` | `{"decision":"APPROVE"|"REJECT","fields":[{"key","value"}]}` |
| GET | `/api/v1/metrics/overview` | Dashboard numbers, computed from the database |
| GET | `/api/v1/evals?limit=20` | CI evaluation runs, newest first |

Errors are RFC 7807 `application/problem+json`:

```json
{ "type": "https://docflow.dev/problems/conflict", "title": "Conflict", "status": 409,
  "detail": "Document doc_3f2a… is APPROVED and cannot be reviewed." }
```

## Design decisions (good interview material)

- **Layered architecture:** controller → service → repository. Controllers only translate HTTP;
  business rules live in `DocumentService` and the `DocumentEntity` aggregate.
- **API records separate from JPA entities** (`DocumentDtos`): the database can change without
  breaking the UI contract. Each record matches a type in the UI's `src/types.ts`.
- **Multi-tenancy:** every query is scoped by tenant. Another tenant's document returns **404, not 403**,
  so the API never reveals that an ID exists.
- **Idempotency:** `POST /review` honours an `Idempotency-Key` header; a retried request is not applied twice.
- **Optimistic locking** (`@Version`): two reviewers saving at once get a 409 instead of a lost update.
- **Safe uploads:** the file type is detected from its first bytes (not the client's Content-Type),
  stored under its SHA-256 hash (no path traversal), and deduplicated per tenant.
- **Feedback loop:** every human correction is saved in `field_correction`, ready to become new
  labelled examples for the evaluation set.
- **Schema owned by Flyway** (`db/migration`), written to run on both PostgreSQL and H2.
- **Metrics computed, not hard-coded:** straight-through rate, p95 latency (nearest-rank), cost per document.
- **No open-session-in-view:** entities are mapped to records inside transactions, so there are no
  hidden lazy-loading queries from controllers.

## Project layout

```
src/main/java/com/docflow/
  document/   entities, repository, service, controller, API records
  metrics/    overview metrics + percentile helper
  eval/       CI evaluation runs
  storage/    FileStorage interface + local-disk implementation (S3/MinIO later)
  common/     errors (problem+json), tenant check, IDs
  config/     typed settings, CORS, clock
  demo/       demo data seeder
src/main/resources/db/migration/   Flyway SQL
src/test/java/com/docflow/         MockMvc API tests + unit tests
.github/workflows/ci.yml           build + test on every push
```

## Next steps

1. **Extraction pipeline:** PDFBox text → LLM structured output (Spring AI) → validation rules → PO matching
2. **Async processing:** RabbitMQ queue, transactional outbox, retries with Resilience4j
3. **Testcontainers** integration tests against real PostgreSQL; LLM evaluation gate in CI
4. **Observability:** OpenTelemetry traces, Prometheus metrics, token cost per document
5. Upgrade to Spring Boot 4.x (3.5.x has reached the end of open-source support)
