# OmniStock Multi-Warehouse Inventory

OmniStock is a small microservices demo for managing products, warehouse stock,
and orders. The repository is organized into `backend/` (the Spring Boot
services) and `frontend/` (the React application).

## Architecture

```text
React UI
   |
   v
API Gateway (:8080) -- validates JWTs and applies write-role checks
   |            |
   |            +-- Auth Service (:8081)
   +-- Eureka service discovery (:8761)
   |
   +-- Product Service (:8082)
   +-- Inventory Service (:8083, :8093)
   +-- Order Service (:8084)
```

The Gateway routes domain requests using Eureka and Spring Cloud LoadBalancer.
The second Inventory instance demonstrates load balancing. When placing an
order, Order Service reads product details, asks Inventory Service to reserve
stock, and records the result. Cancelling a confirmed order releases the
reservation. Inventory exposes low-stock records for replenishment monitoring.

Each domain service owns a separate PostgreSQL database. The application uses
the four existing local databases by default: `omnistock_auth`,
`omnistock_product`, `omnistock_inventory`, and `omnistock_order`. Tests use
separate in-memory H2 databases. Application startup does not create demo users,
products, warehouses, stock, or orders.

## Run locally

Prerequisites: Java 17 or newer, Maven, and Node.js/npm.

From the repository root in PowerShell:

```powershell
npm --prefix frontend ci
.\scripts\start-all.ps1
```

The startup script builds and launches Eureka, Auth, the Gateway, Product,
Inventory (two instances), Order, and the Vite frontend. Open
<http://localhost:5173>. To stop the processes started by the script, run:

```powershell
.\scripts\stop-all.ps1
```

Use **Register** on the sign-in page to create a STAFF account. Registration
does not grant management permissions. An administrator can promote a user in
the `omnistock_auth` database with:

```sql
UPDATE users SET role = 'ADMIN' WHERE username = '<your-username>';
```

Use `MANAGER` instead of `ADMIN` to grant product and inventory management
without administrator privileges.

## Verify

Run the read-only smoke checks while the services are running. The script asks
for an existing ADMIN or MANAGER account and does not create test records:

```powershell
.\scripts\verify.ps1
```

The smoke test checks authentication and role restrictions, product and
inventory operations, order reservation/cancellation, low-stock alerts, and
requests reaching both Inventory instances.

The automated backend tests use isolated H2 fixtures and do not add records to
the PostgreSQL databases. Build the frontend independently with:

```powershell
mvn -f backend\pom.xml test
npm --prefix frontend run build
```

## Main API routes

All browser requests go through the Gateway at `http://localhost:8080`.

| Route | Purpose |
|---|---|
| `POST /auth/login` | Authenticate and receive a JWT |
| `/products` | Search and manage the product catalog |
| `/inventory` | Read and manage stock by product and warehouse |
| `/inventory/low-stock` | List stock at or below its reorder level |
| `/warehouses` | List and manage warehouses |
| `/orders` | Place, list, and inspect orders |
| `/orders/{id}/cancel` | Cancel a confirmed order and release its reservation |

Product and inventory write operations require the `ADMIN` or `MANAGER` role.
Inventory reservation and release endpoints are internal service operations
and are blocked at the public Gateway.

## Configuration

The frontend API base URL can be changed with `frontend/.env` using
`VITE_API_URL` (see `frontend/.env.example`). Auth Service and API Gateway must
use the same `JWT_SECRET`; they also support `EUREKA_URL` for the registry URL.
Set `DB_USER`, `DB_PASSWORD`, and `JWT_SECRET` in the environment before
starting the services. `DB_PASSWORD` must match the password configured for
your PostgreSQL user, and the same JWT secret is used by Auth Service and the
API Gateway. Keep secrets in local environment variables; never commit them.
