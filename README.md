# What Is This

This is a microservice that can take a URL for a GitLab issue page, figures out the issue number, and starts your Toggl
timer using a project that has a name like: `12345 - Issue Title`. Where the gitlab issue number is `12345`, and the
title of the issue in gitlab is `Issue Title`.

If no such project exists in toggl that starts with `12345 -`, then a new toggle project will be created automatically. 
This can be paird with a bookmarklet to make it easy to start your toggl timer from a gitlab issue page.  This is
possibly not useful to anyone except myself.

# Requirements

Docker, Docker Compose.

If you want to compile this outside of docker you need Java 22.

# How to run it

1. Clone the repo
2. Build the docker image with `./scripts/build-docker-image`
3. Define the necessary environment variables for your toggl and gitlab accounts (see below)
4. Run `docker compose up -d`

The API will be available at http://localhost:8080 and the Swagger UI is available at
http://localhost:8080/swagger-ui.html

# Environment Variables

You must set the following environment variables for `docker compose up` to work:

- `GITLAB_ACCESS_TOKEN` - Your GitLab personal access token
- `TOGGL_API_KEY` - Your Toggl API key

# Dev Notes

This project uses AspectJ, mainly so we can use things like `@Cacheable` on
internal self-invocations.  In IntelliJ, you will likely get compile failures
unless you do do the following:

- Open File > Project Structure
- In the dialog that opens, go to Modules > AspectJ
- Under Compiler tick the box for "Post-compile weave mode".  This ensures
  lombok runs before the AspectJ compiler
