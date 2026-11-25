# DocFlow API: Java backend

Spring Boot 3 / Java 21 REST API for **DocFlow**, an intelligent invoice-processing service.
It serves the React console in `../docflow-ui` and stores everything in a real database.

It covers documents, upload, human review, metrics and evaluation runs, backed by PostgreSQL
(or H2 locally), and the **extraction pipeline** that turns an uploaded PDF into checked, structured data.

## The extraction pipeline

```
upload ─► text + word positions ─► redact ─► LLM extraction ─► locate on page ─► validate ─► match PO ─► route
          (PDFBox)                 (IBAN,    (strict JSON       (boxes for the    (business   (fuzzy)     MATCHED or
                                   e-mail,    schema)            review screen)    rules)                  NEEDS_REVIEW
                                   phone)
```

| Stage | What happens | Code |
|---|---|---|
| Text | PDFBox reads the text layer and every word's position on page 1 | `PdfTextExtractor` |
| Redact | Bank details, e-mail addresses and phone numbers are removed before any external call | `Redactor` |
| Extract | The LLM returns the fields as JSON that must match a fixed schema, with a confidence per field | `extract/*` |
| Locate | Each value is found again on the page; a value that isn't printed there gets its confidence capped | `FieldLocator` |
| Validate | Plain-Java rules: totals add up, VAT rate, VAT ID format, dates, PO format, line items sum | `InvoiceValidator` |
| Match | Fuzzy match against open purchase orders (50% PO number, 30% vendor, 20% amount) | `po/PoMatcher` |
| Route | All fields ≥ 85% confident, no rule failures, exact PO match → **MATCHED**; otherwise → **NEEDS_REVIEW** | `DocumentPipeline` |

It runs in the background after the upload commits, every stage is timed (the dashboard's latency chart),
the LLM is retried on rate limits and timeouts, and any failure ends in **FAILED** with a readable reason
(retry with `POST /documents/{id}/reprocess`). Real token counts and cost are stored per document.

### Choosing the extractor

| `DOCFLOW_LLM_PROVIDER` | What it uses | Needs |
|---|---|---|
| `offline` (default) | Built-in rules that read printed labels. Free, deterministic; used by the tests. Handles clean layouts like the samples | nothing |
| `anthropic` | Claude, via the Messages API with a forced tool call | `DOCFLOW_LLM_API_KEY` (model defaults to `claude-haiku-5-5`) |
| `openai` | Any OpenAI-compatible Chat Completions API (OpenAI, Mistral, Groq, local Ollama ...) with a strict JSON schema | `DOCFLOW_LLM_MODEL`, and `DOCFLOW_LLM_API_KEY` unless `DOCFLOW_LLM_BASE_URL` points to a local server |

```powershell
# Windows PowerShell, for the current terminal
$env:DOCFLOW_LLM_PROVIDER = "anthropic"
$env:DOCFLOW_LLM_API_KEY  = "<your key>"
# Optional, to track cost: your provider's prices in EUR per million tokens
$env:DOCFLOW_LLM_INPUT_PRICE  = "..."
$env:DOCFLOW_LLM_OUTPUT_PRICE = "..."
mvn spring-boot:run
```

Never commit an API key. The startup log says which extractor is active.

### Try it with the sample invoices

`samples/` holds three fictional invoices. Upload them in the dashboard (or Swagger); the demo data includes
matching purchase orders for tenant `acme-de`:

| File | Expected outcome |
|---|---|
| `sample-invoice-clean.pdf` | **Matched** automatically: every field found, PO-450099001 matches exactly |
| `sample-invoice-vat-error.pdf` | **Needs review**: VAT printed 10.00 too high, so net + VAT ≠ gross |
| `sample-invoice-wrong-po.pdf` | **Needs review**: prints PO-450099999; fuzzy matching finds PO-450099003 and flags the difference |

Scanned PDFs and photos fail with a clear message for now: OCR (Tesseract) is a planned addition.

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
| POST | `/api/v1/documents/{id}/reprocess` | Run the pipeline again for a FAILED document (202) |
| GET | `/api/v1/documents/{id}/pages/{n}` | Page n of the original PDF as PNG (review screen) |
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
- **LLM output is never trusted on its own:** a strict JSON schema shapes the answer, every value must be
  found on the page, and business rules check the numbers. The prompt treats document text as untrusted data.
- **Provider-neutral:** one `InvoiceExtractor` interface, three implementations, chosen by configuration.
  The HTTP calls are written directly with Spring's `RestClient`, so token usage and errors are fully visible.
- **Async after commit:** `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` on a bounded thread pool,
  so the pipeline never sees an upload that was rolled back.

## Project layout

```
src/main/java/com/docflow/
  document/   entities, repository, service, controller, API records
  metrics/    overview metrics + percentile helper
  eval/       CI evaluation runs
  pipeline/   text extraction, redaction, field locating, validation rules, orchestration
  pipeline/extract/  offline, Anthropic and OpenAI-compatible extractors, shared prompt + schema
  pipeline/po/       purchase orders + fuzzy matcher
  storage/    FileStorage interface + local-disk implementation (S3/MinIO later)
  common/     errors (problem+json), tenant check, IDs
  config/     typed settings, CORS, clock
  demo/       demo data seeder
src/main/resources/db/migration/   Flyway SQL
src/test/java/com/docflow/         MockMvc API + pipeline end-to-end tests, rule/matcher/LLM-client unit tests
samples/                           three fictional invoices for demos and tests
.github/workflows/ci.yml           build + test on every push
```

## Next steps

1. ~~Extraction pipeline~~ ✅ done (OCR for scans still to come)
2. **Cloud deployment:** Docker image, SAP BTP (Cloud Foundry), managed PostgreSQL, deploy from CI
3. **Async processing:** RabbitMQ queue, transactional outbox, retries with Resilience4j
4. **Testcontainers** integration tests against real PostgreSQL; LLM evaluation gate in CI
5. **Observability:** OpenTelemetry traces, Prometheus metrics
6. Upgrade to Spring Boot 4.x (3.5.x has reached the end of open-source support)
