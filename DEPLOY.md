# Deploying DocFlow to SAP BTP (Cloud Foundry)

One app serves the API and the dashboard; PostgreSQL is a managed BTP service bound to it.

```
Browser ──HTTPS──► docflow-9093cc73 (Cloud Foundry app)
                    ├─ Spring Boot API  /api/v1/...
                    ├─ React dashboard (built into the jar)
                    └─ bound service: docflow-db (PostgreSQL, hyperscaler option, plan "trial")
```

Your trial: region **US East (VA) - AWS**, org **`9093cc73trial`**, space **`dev`**,
API endpoint **`https://api.cf.us10-001.hana.ondemand.com`**.

## 0. Prerequisites (once)

- JDK 21, Maven, Node.js: already working (`java -version`, `mvn -v`, `node -v`).
- **Cloud Foundry CLI v8**: Windows installer from https://github.com/cloudfoundry/cli/releases.
  Open a new terminal and check `cf version`.

## 1. Log in

```powershell
cf login -a https://api.cf.us10-001.hana.ondemand.com
```
Enter the e-mail and password of your SAP account. If it asks for an org and space, choose
`9093cc73trial` and `dev`.

If the password login is refused (accounts with single sign-on or two-factor authentication), use:
```powershell
cf login -a https://api.cf.us10-001.hana.ondemand.com --sso
```
It prints a link: open it in the browser, sign in, copy the one-time passcode back into the terminal.

## 2. Create the database (once)

```powershell
cf create-service postgresql-db trial docflow-db
cf service docflow-db
```
Creating the database takes a while, often 10 to 20 minutes. Repeat `cf service docflow-db` until the
status says **create succeeded**. Don't push before that: the app needs the database to start.

## 3. Build

```powershell
cd F:\docflow\docflow-ui
npm ci
npm run build:cloud          # builds the dashboard into docflow-api\src\main\resources\static

cd ..\docflow-api
mvn clean verify             # tests + the jar in target\
```

## 4. Deploy

```powershell
cd F:\docflow\docflow-api
cf push
```
The first push uploads the jar, installs Java 21 on the platform, starts the app and fills the
database with demo data (about a minute). At the end it shows **routes:**, the app's address.

Open it in the browser. Also check `https://<that address>/actuator/health`: it should say `{"status":"UP"}`.

## 5. Check it works

- The sidebar shows a green dot, **Connected to API**.
- On **Upload**, click **Clean invoice**: it should end as **Matched to PO** within a few seconds.
- **Wrong VAT** and **Wrong PO number** should end in **Needs review**.

## Useful commands

| Command | What it does |
|---|---|
| `cf app docflow-9093cc73` | Status, memory, address |
| `cf logs docflow-9093cc73 --recent` | The last log lines (start here when something fails) |
| `cf logs docflow-9093cc73` | Live logs |
| `cf restart docflow-9093cc73` | Restart |
| `cf env docflow-9093cc73` | Environment, including the bound database (contains secrets: don't share the output) |
| `cf stop docflow-9093cc73` | Stop the app |

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Staging fails with a buildpack error | Use the community Java buildpack instead: in `manifest.yml` replace the `buildpacks` entry with `- https://github.com/cloudfoundry/java-buildpack.git`, replace the three `JBP_CONFIG_*`/`TARGET_RUNTIME` lines with `JBP_CONFIG_OPEN_JDK_JRE: '{ jre: { version: 21.+ } }'`, and push again |
| `insufficient resources` / memory quota | Lower `memory:` in `manifest.yml` to `768M` and push again |
| App crashes: "No PostgreSQL service found" | The database isn't bound or not ready: check `cf service docflow-db`, then `cf restart docflow-9093cc73` |
| App crashes during start, logs mention a timeout | The first start (demo data) took too long: run `cf restart docflow-9093cc73`; the data is only created once |
| Dashboard shows "Mock data" | The jar was built without `npm run build:cloud`: do step 3 again, then `cf push` |
| Upload says "limit reached" | The public demo allows 20 uploads per hour per visitor and 300 per day |

## 6. Automatic deployment from GitHub (after the first manual deploy works)

`.github/workflows/ci.yml` builds and tests every push. Its deploy job runs on `main` once you switch it on:

1. GitHub repo → **Settings → Secrets and variables → Actions**.
2. **Secrets** tab → **New repository secret**: `CF_USERNAME` (your SAP account e-mail) and `CF_PASSWORD`.
3. **Variables** tab → **New repository variable**: `CF_DEPLOY` = `true`.
4. Push to `main`. The **Actions** tab shows *Build and test*, then *Deploy to SAP BTP*.

The deploy job logs in with a password. If your SAP account requires single sign-on or two-factor
authentication, that login fails: keep deploying manually with `cf push`, or ask how to set up a technical user.

## Things to know about the trial

- It runs for 90 days, and you need to log in at least once every 30 days, or it gets deleted.
- After it ends: create a new trial, update the org name in `manifest.yml` and `ci.yml`, then repeat
  steps 1 to 4. Everything is in Git, so redeploying takes minutes.
- The public demo uses the offline extractor (`DOCFLOW_LLM_PROVIDER: offline` in `manifest.yml`): no API key,
  no cost. Never put an API key into `manifest.yml`; use `cf set-env` and a login before enabling a real LLM.
