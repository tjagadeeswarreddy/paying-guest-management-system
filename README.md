# Paying Guest Management System

Corporate-style full-stack starter inside `/Users/jagadeeswarreddy/Desktop/ReactJS` with:
- `frontend/` -> React + Vite UI
- `backend/` -> Spring Boot REST APIs
- `backend/db/schema.sql` -> PostgreSQL schema

## 1) Project Structure

```
ReactJS/
  frontend/
  backend/
  README.md
```

## 2) Core Features Implemented

- Tenant management
  - Add tenant
  - Update tenant
  - Soft delete tenant
  - Tenant listing dashboard
- Rent management
  - Monthly due/paid record per tenant
  - Mark due as paid
  - Due rents dashboard (filtered by date range)
  - Collected rents dashboard (filtered by date range)
- Dashboard summary
  - Total collection
  - Total due amount
  - Pending collection
  - Active tenant count

## 3) Database Design (PostgreSQL)

### `tenants`
- `id` (PK)
- `full_name`
- `room_number`
- `rent`
- `deposit`
- `joining_date`
- `emergency_contact_number`
- `emergency_contact_relationship`
- `payment_status` (`ON_TIME|PARTIAL|DUE`)
- `company_name`
- `company_address`
- `rent_due_amount`
- `verification_status` (`DONE|NOT_DONE`)
- `active`
- `created_at`, `updated_at`

### `rent_records`
- `id` (PK)
- `tenant_id` (FK -> tenants.id)
- `billing_month` (1st day of month)
- `due_amount`
- `paid_amount`
- `status` (`DUE|PARTIAL|PAID`)
- `created_at`, `updated_at`
- unique constraint: `(tenant_id, billing_month)`

## 4) PostgreSQL Setup and Connection (Env Based)

Use any PostgreSQL provider (local, Neon, Supabase, etc).
Copy environment templates:

```bash
cp /Users/jagadeeswarreddy/Desktop/ReactJS/backend/.env.example /Users/jagadeeswarreddy/Desktop/ReactJS/backend/.env
cp /Users/jagadeeswarreddy/Desktop/ReactJS/frontend/.env.example /Users/jagadeeswarreddy/Desktop/ReactJS/frontend/.env
```

### Backend Runtime Variables

```bash
PORT=8080
DB_URL=jdbc:postgresql://<host>:5432/<db>?sslmode=require&channel_binding=require
DB_USERNAME=<username>
DB_PASSWORD=<password>
```

### Optional DB initialization
Run once on your target DB:

```sql
\i /Users/jagadeeswarreddy/Desktop/ReactJS/backend/db/schema.sql
```

## 5) Run Backend (Spring Boot)

Requirements:
- Java 17+
- Maven 3.9+

Commands:

```bash
cd /Users/jagadeeswarreddy/Desktop/ReactJS/backend
export DB_URL='jdbc:postgresql://<host>:5432/<db>?sslmode=require&channel_binding=require'
export DB_USERNAME='<username>'
export DB_PASSWORD='<password>'
mvn spring-boot:run
```

Backend runs at `http://localhost:8080`.

## 6) Run Frontend (React)

Requirements:
- Node.js 18+

Commands:

```bash
cd /Users/jagadeeswarreddy/Desktop/ReactJS/frontend
npm install
export VITE_API_BASE_URL='http://localhost:8080/api'
npm run dev
```

Frontend runs at `http://localhost:5173`.

## 7) API Endpoints

### Tenant
- `GET /api/tenants`
- `POST /api/tenants`
- `PUT /api/tenants/{tenantId}`
- `DELETE /api/tenants/{tenantId}`

### Rent
- `POST /api/rents` (upsert monthly due/paid)
- `PATCH /api/rents/{recordId}/pay` (mark as paid)
- `GET /api/rents/due?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/rents/collected?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/rents/dashboard?from=YYYY-MM-DD&to=YYYY-MM-DD`

## 8) Recommended Next Enhancements

- Authentication and role-based access (Admin, Manager)
- Automated monthly rent generation job
- Expense module and profit/loss dashboard
- Alerts (WhatsApp/SMS/email) for due rents
- Export monthly statements to PDF/Excel
- Audit logs for all updates
