# DocFlow UI — Document AI console

React + TypeScript front end for **DocFlow**, an intelligent invoice-processing service.
Documents are uploaded, text is extracted (PDFBox / Tesseract), fields are pulled out by an
LLM with structured output, validated against business rules, matched to purchase orders,
and anything uncertain goes to a human reviewer.

The backend is the Spring Boot 3 / Java 21 service in `../docflow-api`. This UI talks to it through a
single typed interface and also runs fully on mock data, so it can be demoed without the backend.

## Screens

| Screen | What it shows |
|---|---|
| **Overview** | KPIs (volume, straight-through rate, confidence, p95 latency, LLM cost/doc), daily volume by outcome, p95 latency per pipeline stage, latest documents |
| **Documents** | All jobs with status filter and search; tenant, model and token usage per document |
| **Review queue** | Low-confidence / failed-validation documents, lowest confidence first |
| **Document review** | Rendered page with bounding boxes linked to the extracted fields, confidence per field, validation rule failures with one-click fixes, PO match, per-stage pipeline timings and trace ID. Approve is blocked until every flagged field is resolved; corrections feed the evaluation set |
| **Upload** | Drag-and-drop batch upload with type/size validation and live pipeline progress |
| **Model evaluation** | F1 per CI run against the quality gate, per-field F1, and which commits the gate blocked |

Light and dark mode are both supported.

## Run it

```bash
npm install
cp .env.example .env    # empty VITE_API_URL = mock data; VITE_API_URL=/api/v1 = Java backend
npm run dev             # http://localhost:5173
npm run build           # production build -> dist/
npm run build:preview   # single self-contained HTML -> dist-preview/index.html
```

## Architecture

```
src/
  types.ts            # API contracts, mirrored from the backend's OpenAPI spec
  api/client.ts       # DocFlowApi interface + HTTP and mock implementations
  api/mock.ts         # deterministic (seeded) demo data
  components/         # layout, UI primitives, dependency-free SVG charts
  pages/              # Overview, Documents, DocumentDetail, Upload, Evaluation
```

**Production-minded details**

- One `DocFlowApi` interface: swap mock ↔ real backend with one env variable, no UI changes.
- `Idempotency-Key` header on review submissions; `X-Tenant-Id` on every request (multi-tenancy).
- Errors read from RFC 7807 `problem+json` responses.
- Charts are hand-built SVG (no chart library): hover tooltips, colours validated for
  colour-vision deficiency, status colours always paired with an icon and label.
- Loading skeletons, empty states, keyboard-accessible upload zone, responsive layout.

## Backend endpoints expected

| Method | Path | Purpose |
|---|---|---|
| GET | `/metrics/overview` | KPI and chart data |
| GET | `/documents` | List jobs |
| GET | `/documents/{id}` | Job with fields, boxes, timings, PO match |
| POST | `/documents` | Multipart upload (`file`, `docType`) |
| POST | `/documents/{id}/review` | `{ decision, fields[] }` |
| GET | `/evals` | CI evaluation runs |

## Next steps

- Server-Sent Events from `/documents/{id}/events` for live pipeline progress (currently simulated)
- Render real PDF pages with `pdf.js` under the bounding-box overlay
- Playwright end-to-end tests in the GitHub Actions pipeline
- OIDC login (Keycloak) with tenant taken from the token
