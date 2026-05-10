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

# How to run it

1. Clone the repo.
2. Build the docker image with `./scripts/build-docker-image`.
3. Provision a PostgreSQL database and an OIDC client (see below). The bundled `docker-compose.yml` only runs the app container; you'll need to add a `postgres` service (or run Postgres separately) and pass the `DB_*` and `OIDC_*` variables through the `environment:` block.
4. Define the required environment variables (see below).
5. Run `docker compose up -d`.

The web UI is at http://localhost:8080 — visiting any protected page will redirect you to `/login` where you can sign in via OIDC. The Swagger UI is at http://localhost:8080/swagger-ui.html.

On first OIDC sign-in a user record is auto-provisioned from the provider's `sub`/`email`/`name` claims; there is no public signup form.

# Environment Variables

The app reads all configuration from environment variables. None have safe defaults except where noted.

## GitLab / Toggl

| Variable | Required | Notes |
|---|---|---|
| `GITLAB_ACCESS_TOKEN` | yes | GitLab personal access token used to read issue metadata. |
| `TOGGL_API_KEY` | yes | Toggl API key used to create projects and start timers. |

## Database

| Variable | Required | Default | Notes |
|---|---|---|---|
| `DB_URL` | no | `jdbc:postgresql://localhost:5432/gitlab_toggl_timer` | JDBC URL. Override when Postgres lives elsewhere (e.g. another container, an external host). |
| `DB_USERNAME` | no | `gitlab_toggl_timer` | Database user. |
| `DB_PASSWORD` | yes | — | Database password. |

Flyway runs at startup and creates the `users`, `user_roles`, and `user_auth_identities` tables.

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
