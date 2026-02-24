# Deploy Backend To Render

## 1. Push code to GitHub
- Commit and push this project.

## 2. Create Render Web Service
- Render Dashboard -> `New` -> `Web Service`.
- Connect your GitHub repo.
- If this is a monorepo, set:
  - `Root Directory`: `backend`

## 3. Build and Start commands
- `Build Command`: `mvn -DskipTests package`
- `Start Command`: `java -jar target/pg-management-backend-1.0.0.jar`

## 4. Environment variables (Render)
Set these in Render service -> `Environment`:

- `PORT` = `10000` (or leave default, Render injects one)
- `DB_URL` = `jdbc:postgresql://<NEON_HOST>/<NEON_DB>?sslmode=require&channel_binding=require`
- `DB_USERNAME` = `<NEON_USER>`
- `DB_PASSWORD` = `<NEON_PASSWORD>`
- `DDL_AUTO` = `update`
- `HIBERNATE_FORMAT_SQL` = `false`
- `DB_POOL_MAX` = `10`
- `DB_POOL_MIN` = `2`
- `APP_CORS_ALLOWED_ORIGINS` = `https://<YOUR_FRONTEND_DOMAIN>`

Notes:
- You can also use `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` instead of `DB_*`.
- `APP_CORS_ALLOWED_ORIGINS` can contain multiple origins separated by commas.

## 5. Deploy and verify
- Click `Create Web Service`.
- Wait for status `Live`.
- Test:
  - `GET https://<render-backend-domain>/api/tenants/active`
  - `GET https://<render-backend-domain>/api/accounts`

## 6. If deployment fails
- Check `Logs` in Render.
- Most common issue: wrong JDBC URL format.
  - Must start with `jdbc:postgresql://...`
  - Not `postgresql://...`
