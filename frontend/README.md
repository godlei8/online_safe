# Online Safe frontend

Vue 3 + TypeScript + Vite + Vuetify implementation of the user authentication flow.

## Run locally

1. Start the backend on `http://localhost:8080` after configuring MySQL as described in [`../backend/README.md`](../backend/README.md).
2. In this directory, install dependencies with `npm install`.
3. Start Vite with `npm run dev`.
4. Open the local Vite URL, normally `http://localhost:5173`.

Vite proxies `/api` to Spring Boot. This preserves the session-cookie and CSRF behavior during development without enabling broad CORS.

## Routes

- `/login`: personal-user login; accepts username or phone.
- `/register`: personal-user registration with phone, username, password and confirmation.
- `/vault`: authenticated handoff page; credential-vault capabilities are not yet implemented.
- `/admin/login`: deliberately separate admin entry. The backend does not yet expose an administrator authentication flow.

The frontend calls `GET /api/csrf` before state-changing requests and sends the returned CSRF header with cookies included.
