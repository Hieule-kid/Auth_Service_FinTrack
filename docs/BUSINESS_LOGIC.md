# Auth Service — Business Logic & System Flow

This document captures the *why* behind the Auth Service: business rules, cross-service flow, and security decisions that used to live as inline comments in the source. Source comments that only described "how the code works" were removed; anything that explained "why this rule/flow exists" was moved here so this doc is the single source of truth going forward. References point to the class/method the knowledge came from, without line numbers (which drift).

## Authentication Flow

- Login accepts either a username or an email address in the same `emailOrUsername` field — the service looks up by username first, then falls back to an email lookup (see `AuthController.java`, `login`; `AuthService.java`, `login`; `AuthServiceImpl.java`, `login`).
- The security filter chain is fully stateless: no HTTP session is ever created, and every protected request must carry a valid JWT. CSRF protection is disabled because there is no session/cookie-based state for CSRF to protect in a stateless REST API (see `SecurityConfig.java`, `securityFilterChain`).
- The JWT filter (`JwtAuthFilter`) is registered *before* Spring's `UsernamePasswordAuthenticationFilter` in the filter chain, so a valid `Authorization` header is honored ahead of Spring Security's default filters. This ordering is required for header-based JWT auth to work correctly (see `SecurityConfig.java`, `securityFilterChain`).
- Endpoints that do **not** require a JWT: `POST /api/v1/auth/login`, `/register`, `/refresh`, `GET /ping`, `/actuator/health`, `/actuator/info`, and the Swagger UI / OpenAPI routes (kept public so API docs and the warmup ping are reachable before authentication). Every other endpoint requires a valid JWT (see `SecurityConfig.java`, `securityFilterChain`).
- Method-level security (`@PreAuthorize`) is enabled service-wide via `@EnableMethodSecurity` (see `SecurityConfig.java`).
- Logging out revokes **all** of a user's refresh tokens/sessions at once — not just the session that called logout. Already-issued access tokens are not tracked server-side, so they remain valid until they naturally expire (see `AuthController.java`, `logout`; `AuthServiceImpl.java`, `logout`).
- A soft-deleted user (`deleted = true`) is treated as a disabled account by Spring Security — `User.isEnabled()` returns `false` for a soft-deleted user, so they cannot log in even with otherwise-correct credentials (see `User.java`, `isEnabled`).

## Token / JWT Rules

- Access tokens are short-lived (default 15 minutes); refresh tokens are long-lived (default 7 days) and are the only way to obtain a new access token without re-authenticating (see `AuthResponse.java`).
- Recommended client handling of the login/refresh response: keep the access token in memory only (never `localStorage`), store the refresh token in an HttpOnly cookie, attach the access token as `Authorization: Bearer <token>` on every request, and call `POST /api/v1/auth/refresh` once the access token expires (see `AuthResponse.java`).
- The service refuses to start if `fintrack.jwt.secret` is missing, shorter than 32 characters, or still contains the placeholder text `change-me`. A predictable or default secret would let anyone with the source code forge valid access tokens, so this is enforced at startup rather than left to configuration discipline (see `JwtService.java`, `validateSecret`).
- Refresh tokens are stored in PostgreSQL, which — unlike the project's earlier MongoDB-based storage — has no TTL index to auto-expire rows. A scheduled job (`TokenCleanupScheduler`, runs daily at 02:00) deletes tokens whose `expiry_date` has passed. Expired tokens are also rejected lazily by the service layer whenever `/refresh` is called, even before the cleanup job runs (see `TokenCleanupScheduler.java`; `RefreshToken.java`; `RefreshTokenRepository.java`).
- A refresh token is invalidated (deleted) on: explicit logout, expiry, or the daily cleanup job. Presenting an invalidated token to `/refresh` results in an error (see `RefreshToken.java`).

## Password & Credential Handling

- Passwords are always BCrypt-hashed before being persisted; the service never stores or returns a plain-text password (see `User.java`, `password` field; `RegisterRequest.java`, `password` field).
- Login verifies the submitted plain-text password against the stored BCrypt hash rather than storing or comparing plain text directly (see `LoginRequest.java`, `password` field).

## User Profile & Currency

- `PUT /api/v1/users/profile` is a full replace of the profile: full name, email, and currency are all required and all get overwritten. It is not a partial update — use `PATCH /api/v1/users/currency` for a partial, currency-only update (see `UpdateUserProfileRequest.java`).
- Because email also doubles as an alternative login identifier, a profile update that changes the email is checked for uniqueness against other users before being saved, to avoid two accounts sharing a login identifier (see `AuthServiceImpl.java`, `updateProfile`).

## Authorization / Roles

- `ROLE_ADMIN` grants full administrative access and is intended to be assigned only to designated administrators.
- `ROLE_USER` is the standard role, intended to be assigned automatically at registration (see `Role.java`).
- **Discrepancy worth confirming:** as currently implemented, `AuthServiceImpl.register` assigns every newly-registered account `ROLE_ADMIN` rather than `ROLE_USER`. This contradicts the intended role semantics above and may be a bug (see `AuthServiceImpl.java`, `register`).

## OAuth2 Linked Accounts

- Users can link third-party social provider accounts (Google, GitHub, etc.) for OAuth2 login. Each linked account belongs to exactly one `User`, enforced by a physical foreign key, and the combination of `user_id` + `provider` + `provider_account_id` must be unique (see `Account.java`).
- Provider names (e.g. `google`, `github`, `facebook`) and provider account IDs are treated as case-sensitive; provider names are normalized to lowercase before storage/comparison (see `Account.java`, `provider` field).
- The stored OAuth2 access token is used to call the provider's API on behalf of the user (e.g., fetch profile data, send emails) and should be encrypted at rest if used for sensitive operations. Token refresh logic and secure storage are not yet implemented — a known gap (see `Account.java`, `accessToken` field).
- When a user account is deleted, its linked OAuth2 accounts are cascade-deleted with it (`orphanRemoval`), so no orphaned `accounts` rows are left behind (see `User.java`, `accounts` field).

## Cross-Service Interactions

- This service registers with Eureka (`@EnableDiscoveryClient`) so other FinTrack services (Core Service, Planning Service, and the frontend/gateway) can discover it at runtime (see `AuthServiceApplication.java`).
- `FINTRACK_JWT_SECRET` must be identical across every service that needs to validate the same JWTs (e.g. Planning Service) — a mismatch means tokens issued by the Auth Service silently fail validation elsewhere.
- `GET /ping` is a public, dependency-free liveness endpoint used by the frontend to pre-warm this service. On Render's free tier the container spins down after roughly 15 minutes idle and takes several minutes to cold-start, so the frontend calls `/ping` as soon as a user lands on the login page, starting the JVM/Spring context warm-up while they are still typing their credentials. The endpoint deliberately touches no database, repository, or service-layer bean, so a response only confirms the process itself is up (see `WarmupController.java`).
