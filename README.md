# What Is This

This is a microservice that can take a URL for a GitLab issue page, figures out the issue number, and starts your Toggl
timer using a project that has a name like: `12345 - Issue Title`. Where the gitlab issue number is `12345`, and the
title of the issue in gitlab is `Issue Title`.

If no such project exists in toggl that starts with `12345 -`, then a new toggle project will be created automatically. 
This can be paired with a bookmarklet to make it easy to start your toggl timer from a gitlab issue page.  This is
possibly not useful to anyone except myself.

# Requirements

- Docker, Docker Compose.
- A reachable PostgreSQL 16+ instance (used to store user accounts).
- An OIDC provider (e.g. PocketID, Authentik, Keycloak, Google) with a client registered for this app. The redirect URI to register is `http://<host>:8080/login/oauth2/code/oidc`.

If you want to compile this outside of Docker, you need Java 25.

# Local development

`./gradlew bootRun` uses Spring Boot's Docker Compose support to start a Postgres container from `compose-dev.yaml` and
wire the datasource automatically — you do not need to set `DB_*` env vars locally. The `OIDC_*` and `APP_ENCRYPTION_*`
variables are still required. The container is stopped when the app exits.

# How to run it

1. Clone the repo.
2. Build the docker image with `./scripts/build-docker-image`.
3. Provision a PostgreSQL database and an OIDC client (see below). The bundled `docker-compose.yml` only runs the app
   container; you'll need to add a `postgres` service (or run Postgres separately) and pass the `DB_*` and `OIDC_*`
   variables through the `environment:` block.
4. Define the required environment variables (see below).
5. Run `docker compose up -d`.

The web UI is at http://localhost:8080 — visiting any protected page will redirect you to `/login` where you can sign in
via OIDC. The Swagger UI is at http://localhost:8080/swagger-ui.html.

On first OIDC sign-in a user record is auto-provisioned from the provider's `sub`/`email`/`name` claims; there is no
public signup form. After signing in, a new user is redirected to `/settings` to enter their personal GitLab access
token and Toggl API key. The app cannot reach GitLab or Toggl until both are saved. Credentials are stored in the
`user_settings` table, encrypted at rest with AES.

Once signed in, a user can optionally set a local password at `/settings/sign-in` to enable email + password sign-in
alongside OIDC. Both methods then work against the same account. To run the app as OIDC-only and block password
sign-in entirely, set `APP_AUTH_PASSWORD_LOGIN_ENABLED=false` (see below).

# Environment Variables

The app reads all configuration from environment variables. None have safe defaults except where noted.

## Encryption

GitLab access tokens and Toggl API keys are encrypted at rest using a key derived from the values below. **If you
change either of these in production, existing per-user credentials will no longer decrypt** — users will have to
re-enter them at `/settings`.

| Variable | Required | Notes |
|---|---|---|
| `APP_ENCRYPTION_PASSWORD` | yes | Master password used to derive the AES key. |
| `APP_ENCRYPTION_SALT` | yes | Hex-encoded salt — generate with `openssl rand -hex 16`. |

## Database

| Variable | Required | Default | Notes |
|---|---|---|---|
| `DB_URL` | no | `jdbc:postgresql://localhost:5432/gitlab_toggl_timer` | JDBC URL. Override when Postgres lives elsewhere (e.g. another container, an external host). |
| `DB_USERNAME` | no | `gitlab_toggl_timer` | Database user. |
| `DB_PASSWORD` | yes | — | Database password. |

Flyway runs at startup and creates the `users`, `user_roles`, `user_auth_identities`, and `user_settings` tables.

## OIDC (sign-in)

The app uses standard OIDC discovery, so you only need to supply the issuer base URL plus client credentials. Spring fetches `<issuer>/.well-known/openid-configuration` automatically.

| Variable | Required | Default | Notes |
|---|---|---|---|
| `OIDC_ISSUER_URI` | yes | — | Issuer base URL — e.g. `https://id.example.com`. **Do not** include the `/.well-known/openid-configuration` suffix. |
| `OIDC_CLIENT_ID` | yes | — | Client ID from the OIDC provider's admin UI. |
| `OIDC_CLIENT_SECRET` | yes | — | Client secret from the OIDC provider's admin UI. |
| `OIDC_CLIENT_NAME` | no | `Single Sign-On` | Label shown on the login button (e.g. `PocketID`, `Google`). |

Whichever provider you use, register `http://<host>:8080/login/oauth2/code/oidc` as the allowed redirect URI and ensure the client releases the `email` scope (without it the provisioning step will fail with a clear error).

To swap providers later, just change `OIDC_*` — no code changes needed.

## GitLab Token Permissions

You need the following permissions on the GitLab Personal Access Token:

### Group Permissions
- Global Search: Use
- Group: Read
- Project: Read
- Work Item: Read

### User Permissions
- User: Read

## Authentication

| Variable | Required | Default | Notes |
|---|---|---|---|
| `APP_AUTH_PASSWORD_LOGIN_ENABLED` | no | `true` | When `false`, the email + password form is removed from `/login`, the `/settings/sign-in` page returns 404, and Spring Security's form-login filter is not registered — leaving OIDC as the only way to sign in. Existing password hashes are kept in the database and become usable again if the flag is turned back on. |
| `APP_AUTH_RP_ID` | yes (for passkeys, unless serving from `localhost`) | `localhost` | WebAuthn Relying Party ID — the bare domain you serve the app from, e.g. `timer.example.com`. Must be a registrable suffix of the browser's origin host, otherwise passkey registration fails with `'rp.id' cannot be used with the current origin`. Do not include a scheme or port. |
| `APP_AUTH_ORIGINS` | yes (for passkeys, unless serving from `http://localhost:8080`) | `http://localhost:8080` | Comma-separated list of full origins (scheme + host + port) the browser will use when registering or asserting passkeys, e.g. `https://timer.example.com`. Must match what the browser sees, including `https` when behind a TLS-terminating proxy. |

## Toggl time-entry synchronization

The application periodically imports time entries for each enabled user who has saved a Toggl API key. The first run
imports the preceding seven days; later runs request entries modified since the last successful sync. An open timer
page refreshes its recent-entry list every minute.

| Variable | Required | Default | Notes |
|---|---|---|---|
| `APP_TOGGL_SYNC_ENABLED` | no | `true` | Set to `false` to disable background synchronization. |
| `APP_TOGGL_SYNC_INTERVAL` | no | `PT15M` | Fixed delay between completed sync runs, expressed as an ISO-8601 duration. |
| `APP_TOGGL_SYNC_INITIAL_DELAY` | no | `PT30S` | Delay after application startup before the first sync. |
| `APP_TOGGL_SYNC_INITIAL_LOOKBACK` | no | `P7D` | History window used when a user has no successful sync cursor yet. |

## Reverse proxy / SSL termination

If you run the app behind an SSL-terminating reverse proxy (e.g. Traefik, Nginx, Caddy), set:

| Variable | Required | Notes |
|---|---|---|
| `SERVER_FORWARD_HEADERS_STRATEGY` | yes (behind a proxy) | Set to `native` so Spring Boot honours the `X-Forwarded-*` headers from the proxy. Without this, OIDC redirect URIs are generated with the internal `http://` scheme and port, causing the OIDC handshake to fail. |

# Bookmarklets

Bookmarklets run in your browser and reuse your existing login session, so they will work after you've signed in to http://localhost:8080 in the same browser.

In all bookmarks, you must use your own Toggl `workspaceId` and `clientId`
values.

The following bookmarklet will create a toggle project for the current gitlab
issue page or return the existing project if it already exists:

```
javascript:(function(){
  window.open("http://localhost:8080/timer/create-project?issueUrl="
    + encodeURIComponent(window.location.href)
    + "&workspaceId=12345&clientId=12345",
    "_blank");
})();
```

The following bookmarklet will start a timer for the current gitlab issue page, creating the issue on toggl if
necessary:

```
javascript:(function(){
  window.open("http://localhost:8080/timer/start?issueUrl="
    + encodeURIComponent(window.location.href)
    + "&workspaceId=12345&clientId=12345",
    "_blank");
})();
```
