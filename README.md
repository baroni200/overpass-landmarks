# Overpass Landmarks Service

A Spring Boot service that processes coordinate webhooks, queries the Overpass API for nearby landmarks, and provides cached results.

## Architecture Overview

This service follows **Domain-Driven Design (DDD)** principles with clear separation of concerns:

- **Domain Layer**: Core business logic (entities, value objects, domain services)
- **Application Layer**: Use cases, DTOs, application services
- **Infrastructure Layer**: External integrations (Overpass API, PostgreSQL, Cache)
- **Presentation Layer**: REST controllers, security, validation

### Technology Stack

- **Language**: Java 22
- **Framework**: Spring Boot 3.5.7
- **Database**: PostgreSQL 14+ (Spring Data JPA)
- **Cache**: Caffeine (in-JVM cache)
- **Migrations**: Flyway
- **Build**: Gradle (Kotlin DSL)
- **HTTP Client**: Spring WebClient (Reactive)

## Operational Choices

### Processing Mode: Synchronous

**Choice**: Synchronous webhook processing

**Rationale**:

- Simpler implementation for coding challenge
- Immediate feedback to webhook sender
- Overpass API typically responds within seconds
- Can be upgraded to async if needed for production (using Spring @Async or message queues)

The webhook endpoint processes the entire flow synchronously:

1. Transform coordinates
2. Query Overpass API
3. Persist to database
4. Populate cache
5. Return response

### Cache Backend: Caffeine

**Choice**: Caffeine over Redis

**Rationale**:

- Simpler setup (no external dependency)
- Good performance for single-instance deployments
- Zero configuration overhead
- Suitable for coding challenge/prototype

**Note**: For production multi-instance deployments, Redis would be preferred for shared cache across instances.

**Configuration**:

- Maximum size: 10,000 entries
- TTL: 600 seconds (configurable via `CACHE_TTL_SECONDS` env var)
- Cache key format: `{lat}:{lng}:{radius}`

### Coordinate Transformation: Rounding to 4 Decimals

**Choice**: Round latitude/longitude to 4 decimal places (~11m precision)

**Rationale**:

- Provides ~11 meter precision, which is suitable for landmark queries
- Deterministic and consistent for idempotency
- Simple to implement and understand
- Ensures nearby coordinates map to the same cache key

**Example**:

- Input: `48.8584123, 2.2944812`
- Transformed: `48.8584, 2.2945`

This transformation ensures:

- Idempotency: Same coordinates produce same results
- Cache efficiency: Nearby coordinates share cache entries
- Deduplication: Prevents duplicate work for similar coordinates

### Idempotency Strategy

**Approach**: Unique constraint on `(key_lat, key_lng, radius_m)` in the database

**Implementation**:

- Transformed coordinates create a stable key
- Database unique constraint prevents duplicate requests
- If a request with the same transformed key already exists, returns existing result without querying Overpass again

## Deployment

### Railway Deployment

This application is configured for deployment on Railway with Docker.

#### Prerequisites

- Railway account
- Railway CLI (optional)
- Docker (for local testing)

#### Step 1: Add PostgreSQL Database

1. Go to your Railway project dashboard
2. Click **"+ New"** → **"Database"** → **"Add PostgreSQL"**
3. Railway will automatically create a PostgreSQL service
4. The `DATABASE_URL` environment variable will be automatically shared with your application service

**Important:** Make sure your **application service** is connected to the PostgreSQL service:

- Go to your application service → **Variables** tab
- You should see `DATABASE_URL` listed (it's automatically shared from the PostgreSQL service)
- If you don't see it, click **"Add Variable"** → **"Add Reference"** → Select the PostgreSQL service's `DATABASE_URL`

#### Step 2: Configure Environment Variables

**Railway automatically provides `DATABASE_URL`** when you add a PostgreSQL service. The application will automatically parse this and configure the database connection.

**Required environment variables to set manually:**

1. Go to your **application service** → **Variables** tab
2. Add the following variable:
   ```
   WEBHOOK_SECRET=<your-secret-key>
   ```
   (Replace `<your-secret-key>` with a secure random string)

#### Step 3: Get Your Railway URL

After deployment, Railway provides a public URL for your service:

1. Go to your **application service** → **Settings** → **Domains**
2. Your public URL will be listed (e.g., `your-app-name.up.railway.app`)
3. Or check the **Overview** tab - the URL is displayed at the top

#### Step 4: Test the API

**Health Check** (no authentication required):

```bash
curl https://your-app-name.up.railway.app/actuator/health
```

**GET /landmarks** (no authentication required):

```bash
curl "https://your-app-name.up.railway.app/landmarks?lat=48.8584&lng=2.2945"
```

**POST /webhook** (requires Bearer token):

```bash
curl -X POST https://your-app-name.up.railway.app/webhook \
  -H "Authorization: Bearer YOUR_WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

Replace `YOUR_WEBHOOK_SECRET` with the value you set in Railway's `WEBHOOK_SECRET` environment variable.

**Automatic environment variables (no action needed):**

- `DATABASE_URL` - Automatically provided by Railway PostgreSQL service
- `PORT` - Automatically set by Railway

**Optional environment variables:**

```bash
PORT=8080  # Railway sets this automatically
QUERY_RADIUS_METERS=500
CACHE_TTL_SECONDS=600
LOG_LEVEL=INFO
```

**Note:** If you prefer to use Spring Boot's standard environment variables instead of `DATABASE_URL`, you can set:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

#### Deploy via Railway CLI

```bash
# Install Railway CLI
npm i -g @railway/cli

# Login
railway login

# Link to project
railway link

# Deploy
railway up
```

#### Deploy via GitHub Actions

The repository includes a GitHub Actions workflow that:

1. Runs tests on every push/PR
2. Builds Docker image
3. Deploys to Railway automatically on push to `main` branch

**Setup:**

1. Add Railway secrets to GitHub:

   - `RAILWAY_TOKEN`: Your Railway API token
   - `RAILWAY_SERVICE_NAME`: Your Railway service name
   - `RAILWAY_SERVICE_URL`: Your Railway service URL (optional)

2. Push to `main` branch to trigger deployment

#### Local Docker Build

```bash
# Build Docker image
docker build -t overpass-landmarks:latest .

# Run locally
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/overpass \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e WEBHOOK_SECRET=supersecret \
  overpass-landmarks:latest
```

### CI/CD Pipeline

The GitHub Actions workflow (`.github/workflows/ci-cd.yml`) performs:

1. **Test Job**: Runs all tests with PostgreSQL testcontainer
2. **Lint Job**: Checks code quality and compiles with warnings
3. **Docker Build Job**: Builds and tests Docker image
4. **Deploy Job**: Deploys to Railway on push to `main` branch

**Workflow Status**: Check the "Actions" tab in GitHub repository.

## Setup Instructions

### Prerequisites

- Java 22
- Docker and Docker Compose
- Gradle 8.x+

### 1. Start Infrastructure

Start PostgreSQL using Docker Compose:

```bash
docker-compose up -d
```

This will start PostgreSQL on port 5432 with:

- Database: `overpass`
- Username: `postgres`
- Password: `postgres`

### 2. Configure Environment Variables

Create `.env` file or set environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/overpass
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
WEBHOOK_SECRET=supersecret
QUERY_RADIUS_METERS=500
CACHE_TTL_SECONDS=600
```

### 3. Build and Run

```bash
./gradlew build
./gradlew bootRun
```

The application will:

- Run Flyway migrations automatically on startup (including seed data migration)
- Start on port 8080 (configurable via `PORT` env var)
- Expose health check at `/actuator/health`

### 4. Seed Data

The project includes two types of seed data:

#### Flyway Seed Data (V3\_\_seed_data.sql)

- **Automatically loaded** on startup via Flyway migration
- Contains reference data: Eiffel Tower coordinates with sample landmarks
- Idempotent: Uses `ON CONFLICT DO NOTHING` to prevent duplicates
- Available in all environments

#### Java-Based Seed Data (DataSeeder)

- **Enabled by default** when using `local` profile: `./gradlew bootRun --args='--spring.profiles.active=local'`
- Adds additional test data: Notre-Dame coordinates with sample landmark
- Controlled by `app.seeding.enabled=true` property (enabled in `application-local.yml`)
- Only runs in local development environments

**Seed Data Contents:**

- **Flyway Seed:**
  - Coordinate Request: Eiffel Tower (48.8584, 2.2945) with 2 landmarks
- **Java Seeder:**
  - Coordinate Request: Notre-Dame de Paris (48.8530, 2.3499) with 1 landmark

To disable Java seeding, set `app.seeding.enabled=false` in your configuration.

## API Endpoints

### POST /webhook

Protected endpoint that receives coordinates, queries Overpass API, and stores results.

**Authentication**: Bearer token via `Authorization` header

**Request**:

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

**Response** (200 OK):

```json
{
  "key": {
    "lat": 48.8584,
    "lng": 2.2945
  },
  "count": 5,
  "radiusMeters": 500
}
```

**Error Responses**:

- `400 Bad Request`: Invalid coordinates (outside valid ranges)
- `401 Unauthorized`: Missing or invalid Authorization token
- `502 Bad Gateway`: Overpass API error
- `500 Internal Server Error`: Processing error

### GET /landmarks

Query landmarks by coordinates. Uses cache-first strategy with database fallback.

**Request**:

```bash
curl "http://localhost:8080/landmarks?lat=48.8584&lng=2.2945"
```

**Response** (200 OK):

```json
{
  "key": {
    "lat": 48.8584,
    "lng": 2.2945,
    "radiusMeters": 500
  },
  "source": "cache",
  "landmarks": [
    {
      "id": "uuid",
      "name": "Eiffel Tower",
      "osmType": "way",
      "osmId": 123456,
      "lat": 48.8584,
      "lng": 2.2945,
      "tags": {
        "tourism": "attraction",
        "wikidata": "Q243"
      }
    }
  ]
}
```

**Source Field**:

- `cache`: Retrieved from cache
- `db`: Retrieved from database (cache miss)
- `none`: No data found

## Testing

### Manual Testing

1. **Test Webhook** (first call should query Overpass):

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

2. **Test Landmarks Retrieval** (should return from cache):

```bash
curl "http://localhost:8080/landmarks?lat=48.8584&lng=2.2945"
```

3. **Test Idempotency** (second webhook call should return immediately without querying Overpass):

```bash
curl -X POST http://localhost:8080/webhook \
  -H "Authorization: Bearer supersecret" \
  -H "Content-Type: application/json" \
  -d '{"lat":48.8584,"lng":2.2945}'
```

4. **Test Invalid Cases**:
   - Missing token: `curl -X POST http://localhost:8080/webhook -d '{"lat":48.8584,"lng":2.2945}'`
   - Invalid coordinates: `curl "http://localhost:8080/landmarks?lat=123&lng=200"`
   - Missing parameters: `curl "http://localhost:8080/landmarks?lat=48.8584"`

### Integration Tests

Run integration tests:

```bash
./gradlew test
```

Tests cover:

- Webhook endpoint (happy path, validation, auth)
- Landmarks retrieval (cache hit, cache miss, empty results)
- Idempotency verification
- Error scenarios

## Database Schema

### coordinate_request

Stores coordinate requests with transformed keys for idempotency.

- `id` (UUID, primary key)
- `key_lat` (NUMERIC(9,6)) - Transformed latitude
- `key_lng` (NUMERIC(9,6)) - Transformed longitude
- `radius_m` (INTEGER) - Query radius in meters
- `status` (TEXT) - FOUND, EMPTY, or ERROR
- `error_message` (TEXT, nullable)
- `requested_at` (TIMESTAMPTZ)
- `updated_at` (TIMESTAMPTZ)

**Unique constraint**: `(key_lat, key_lng, radius_m)`

### landmark

Stores landmarks retrieved from Overpass API.

- `id` (UUID, primary key)
- `coord_request_id` (UUID, foreign key)
- `osm_type` (TEXT) - way, relation, or node
- `osm_id` (BIGINT) - OSM element ID
- `name` (TEXT, nullable)
- `lat` (NUMERIC(9,6))
- `lng` (NUMERIC(9,6))
- `tags` (JSONB) - Raw OSM tags
- `created_at` (TIMESTAMPTZ)

**Unique constraint**: `(osm_type, osm_id)`

## Cache Strategy

### Write-Through Cache

- **On POST /webhook**: Results are persisted to database AND cached immediately
- **On GET /landmarks**: Cache-first lookup → DB fallback → populate cache

### Cache Key Format

```
{transformed_lat}:{transformed_lng}:{radius_meters}
```

Example: `48.8584:2.2945:500`

## Error Handling

All errors return consistent JSON format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message"
}
```

**Error Codes**:

- `VALIDATION_ERROR`: Request validation failed
- `INVALID_PARAMETER`: Invalid parameter type
- `UNAUTHORIZED`: Missing or invalid authentication
- `OVERPASS_ERROR`: Overpass API error
- `WEBHOOK_PROCESSING_ERROR`: Webhook processing failed
- `INTERNAL_ERROR`: Unexpected server error

## Monitoring

Health check endpoint:

```bash
curl http://localhost:8080/actuator/health
```

## Production Considerations

For production deployment:

1. **Use Redis** instead of Caffeine for shared cache across instances
2. **Implement async processing** for webhook endpoint (Spring @Async or message queue)
3. **Add rate limiting** (e.g., using Spring Cloud Gateway or rate limiting library)
4. **Enable HTTPS** for all endpoints
5. **Add monitoring** (Prometheus metrics, distributed tracing)
6. **Configure connection pooling** for database and HTTP client
7. **Set up log aggregation** (ELK stack, CloudWatch, etc.)
8. **Implement retry logic** with exponential backoff for Overpass API
9. **Add circuit breaker** for Overpass API resilience
10. **Use secrets management** (AWS Secrets Manager, HashiCorp Vault) for credentials

## License

MIT
