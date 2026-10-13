# Running AI Support Hub with Docker

Docker turns the project into a self-contained runnable box called an **image**, and each time you
start that image you get a **container** — one live process made from it. The image is built once
and re-used forever; the container is thrown away and recreated every run. Nothing in this project
needs to survive that, because all state (conversations, knowledge documents, tenants, agents) lives
in the database — PostgreSQL by default, Oracle on the `oracle` profile — not inside the container.

Everything below is written for two shells: **IntelliJ's built-in Terminal** (PowerShell) and **Git
Bash**. The commands are the same in both except where noted.

## Files that make this work

| File | Job |
| --- | --- |
| `Dockerfile` | Two stages. Stage 1 is a throwaway Maven + JDK 27 container that compiles `src/` and builds the jar. Stage 2 is a JRE-only image that carries that jar and nothing else — no sources, no compiler, no Maven cache. |
| `.dockerignore` | Which files Docker is allowed to see while building. Secrets, `target/`, `.git/`, IDE folders and docs stay out. `pom.xml` and `src/` must stay in, because stage 1 builds from them. |
| `.env.docker` | **Your file, not in git.** A copy of `.env` whose database host is `host.docker.internal`. Created in the next section. |

The Dockerfile never mentions your database URL, API key or port. Those all arrive as environment
variables at run time, which is what makes one image safe to use locally and on a host.

## One-time setup: the env file for containers

Inside a container, `localhost` means the container itself, not Windows. A database on your machine —
Postgres published on 5433, or the Oracle listener on 1521 — runs on the host, so a container that
reads `jdbc:postgresql://localhost:5433/ash` or `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` looks
for it inside its own network namespace and fails with a refused connection or
`ORA-12541: ... No listener`. Docker Desktop's reserved name `host.docker.internal` points at the
host instead.

`application-pgvector.properties` reads the URL from the environment with no fallback (`${DB_URL}`),
so the variable is mandatory — a container started without it stops immediately with
`Could not resolve placeholder 'DB_URL'`. On the `oracle` profile the same is true of `${ORACLE_URL}`.

Keep `.env` as it is (so your IDE run keeps working) and derive the Docker copy from it. Git Bash:

```bash
sed -e "s#^DB_URL=.*#DB_URL=jdbc:postgresql://host.docker.internal:5433/ash#" .env > .env.docker
```

PowerShell:

```powershell
(Get-Content .env) -replace '^DB_URL=.*','DB_URL=jdbc:postgresql://host.docker.internal:5433/ash' | Set-Content .env.docker
```

For an Oracle container the same command targets `ORACLE_URL` instead, with
`jdbc:oracle:thin:@//host.docker.internal:1521/FREEPDB1`.

Only that one line changes; the keys stay in the file and are never printed. `.gitignore` covers
`.env.*` (with `.env.example` still tracked) and `.dockerignore` keeps both env files out of the
image, so the secrets can neither be committed nor baked into a layer. Re-run the command whenever
you edit `.env`.

## Build the image

```powershell
docker build -t ai-support-hub:0.0.1 .
```

The trailing dot is the build context — "package this directory". Expect 6–8 minutes the first time
(pulls `maven:3-eclipse-temurin-27` and `eclipse-temurin:27-jre`, downloads dependencies, compiles).
Later builds reuse those layers, so a code change costs roughly one recompile.

Build each time `src/` or `pom.xml` changes. The dashboard files under
`src/main/resources/static` count: they are packaged inside the jar, so a CSS edit needs a rebuild —
unlike `mvn spring-boot:run`, where copying into `target/classes` is enough.

Check what you produced:

```powershell
docker images ai-support-hub
```

About 690 MB on disk, roughly 219 MB compressed for a registry push. That is a Spring Boot fat jar
(104 MB) plus a JRE.

## Run it

Foreground — logs in this terminal, `Ctrl+C` stops and removes the container:

```powershell
docker run --rm -p 8001:8000 --env-file .env.docker ai-support-hub:0.0.1
```

Detached — the container runs on its own and the terminal comes back:

```powershell
docker run -d --rm --name ai-support-hub -p 8001:8000 --env-file .env.docker ai-support-hub:0.0.1
```

| Flag | Meaning |
| --- | --- |
| `-d` | Detached: start it and hand the terminal back. Without it, logs print here and `Ctrl+C` stops the app. |
| `--rm` | Delete the container when it exits, so finished runs do not pile up in `docker ps -a`. |
| `--name ai-support-hub` | A stable handle for `logs`/`stop`/`restart`. One at a time: a second start with the same name fails with "container name already in use". |
| `-p 8001:8000` | Publish the container's port 8000 on the host's 8001. Host port first. `8000` stays free for the instance you launch from IntelliJ. |
| `--env-file .env.docker` | Load every variable from the file. `-e KEY=value` overrides a single one. |
| last argument | Which image to start. |

With `--env-file`, one line feeds the seven variables the default build needs: `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `GEMINI_API_KEY`, `JWT_SECRET`, `DEMO_AGENT_USERNAME`,
`DEMO_AGENT_PASSWORD`. On the `oracle` profile, the first three are the `ORACLE_*` names.

## Watch it start

Healthy boot looks like this:

```text
Starting AiSupportHubApplication ... using Java 27 with PID 1 (/app/app.jar started by spring)
Tomcat initialized with port 8000 (http)
HikariPool-1 - Starting...
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
Started AiSupportHubApplication in 2x.xxx seconds
```

On the `oracle` profile the connection line reads `oracle.jdbc.driver.T4CConnection` instead.

`PID 1` and `started by spring` confirm the container's non-root user is running the JVM. Then:

```powershell
curl http://localhost:8001/actuator/health     # {"status":"UP"}
start http://localhost:8001/                   # the dashboard, in your browser
```

For a detached container, follow the log with `docker logs -f ai-support-hub`. `Ctrl+C` there only
leaves the log view — the app keeps running, which is the point of `-d`.

## Running it again after a stop

`--rm` deletes the container, so there is nothing left to start. Running it again means repeating the
`docker run` command above; the image is still there, so no rebuild is needed. Press Up twice in the
terminal and you are back in a few seconds.

To keep stopped containers around instead, drop `--rm` and start them again by name:

```powershell
docker stop ai-support-hub          # graceful SIGTERM, then SIGKILL after 10s
docker start ai-support-hub         # same container, same name, starts again
docker start -a ai-support-hub      # ...and show its logs in this terminal
docker restart ai-support-hub       # stop + start in one go
docker ps                           # what is running now (add -a for stopped ones)
```

`docker run` always creates a new container; `docker start` re-uses an existing one. That is the
distinction, and it is the only thing `--rm` changes.

## Everyday commands

```powershell
docker logs --tail 100 ai-support-hub                  # recent log lines
docker exec -it ai-support-hub sh                      # a shell inside the container (exit leaves it running)
docker stats ai-support-hub --no-stream                # live CPU / memory of the container
docker inspect ai-support-hub --format '{{json .NetworkSettings.Ports}}'   # actual port mapping
docker rm -f ai-support-hub                            # kill it now, no graceful wait
docker system df                                       # how much disk images and build cache are using
docker builder prune                                   # free the build cache (images stay)
```

## Choosing the database for the container

The same image serves either database with no rebuild. PostgreSQL + pgvector is the
default, so every `docker run` on this page is already the Postgres path — no profile
flag is needed. To use Oracle instead, add `-e SPRING_PROFILES_ACTIVE=oracle` and give
the container `ORACLE_URL` / `ORACLE_USERNAME` / `ORACLE_PASSWORD`; that profile swaps
the driver, the dialect and the vector store, and never reads `DB_URL`. See the README
section *Choosing your database: PostgreSQL + pgvector or Oracle* for the
property-by-property detail.

Start a database container first (data survives app restarts because it has a named volume):

```powershell
docker run -d --name ash-pgvector -e POSTGRES_PASSWORD=change-me -e POSTGRES_DB=ash -v ash-pg-data:/var/lib/postgresql/data -p 5433:5432 pgvector/pgvector:pg17
```

Then run the app against it. `host.docker.internal` is what a container uses to
reach a port published by Docker on your Windows machine — here, Postgres on 5433:

```powershell
docker run --rm -d --name ai-support-hub -p 8001:8000 --env-file .env.docker -e DB_URL="jdbc:postgresql://host.docker.internal:5433/ash" -e DB_USERNAME=postgres -e DB_PASSWORD=change-me ai-support-hub
```

`-e` wins over `--env-file` for the same key, so those flags are enough; you do not need a separate
env file for the Postgres variables. If the URL says `localhost` you get `Connection to localhost:5432
refused` — the container is reaching its own loopback, not your host.

The database does not have to be a container. Pointed at a hosted database, the same image needs no
`host.docker.internal` at all, and the container no longer depends on anything running on your
machine:

```powershell
docker run --rm -d --name ai-support-hub -p 8001:8000 --env-file .env -e DB_URL="jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require" -e DB_USERNAME=postgres -e DB_PASSWORD=your-database-password ai-support-hub
```

Pass the real `.env` there rather than `.env.docker`: that second file exists only to rewrite the
database host, and a hosted URL needs no rewriting. Quotes matter — the URL contains `?`
and `&`, and PowerShell would otherwise split it into extra arguments.

Oracle in a container is the same command with the profile switched and Oracle's variables:

```powershell
docker run --rm -d --name ai-support-hub -p 8001:8000 --env-file .env.docker -e SPRING_PROFILES_ACTIVE=oracle ai-support-hub
```

That assumes `.env.docker` carries the rewritten `ORACLE_URL`. A container can only reach an Oracle
database the host can: nothing here installs or hosts Oracle for you.

## If it does not come up

| Message | What happened |
| --- | --- |
| `unknown shorthand flag: 'p' in -p` | Two commands were pasted as one line. `docker build` and `docker run` are separate commands; run one, press Enter, then the other. |
| `Could not resolve placeholder 'DB_URL'` | The env file was not loaded, or the file is missing that key: `docker run --rm --env-file .env.docker` must point at a real `.env.docker`. On the `oracle` profile the same message names `ORACLE_URL`. |
| `Connection to localhost:5432 refused` | The container is still using `localhost`. Rewrite `DB_URL` to `host.docker.internal` (command above), and pass the file with `--env-file`. |
| `ORA-12541: Cannot connect. No listener at host localhost port 1521` | Only on the `oracle` profile: the container is still using `localhost`. Recreate `.env.docker` with the rewritten `ORACLE_URL`. |
| `ORA-12541 ... host.docker.internal` with Oracle running on Windows | Docker Desktop is stopped, or Windows Firewall is blocking the Docker network from port 1521. Check the listener binds widely: `netstat -ano \| findstr 1521` should show `0.0.0.0:1521`. |
| `Bind for 0.0.0.0:8001 failed: port is already allocated` | Something already holds 8001 — usually an earlier container that was started without `--rm`. `docker ps`, then `docker rm -f <name>`. |
| `Conflict. The container name "/ai-support-hub" is already in use` | A previous container of that name still exists. `docker rm -f ai-support-hub`. |
| `java.lang.OutOfMemoryError` or the container is killed right after start | The Docker VM is short on RAM. The JVM sizes itself to 75% of the container limit; raise Docker Desktop's memory (Settings → Resources) or pass `-e JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=192m"`. |
| Build fails in the Maven stage after a `pom.xml` edit | Dependency resolution inside the container failed; the log above it says which artifact. Nothing about the running app changed. |

## Running it from IntelliJ IDEA

The container gets its own environment: nothing injected into your `ai hub` run configuration is
passed on, which is exactly why `--env-file` / `env_file` is required. Three options, least to most
setup:

1. **Built-in Terminal** (View → Tool Windows → Terminal): the commands on this page. Works in every
   edition and is the most honest view of what Docker is doing.
2. **Services tool window** (View → Tool Windows → Docker, from the Docker plugin): click to build an
   image from the `Dockerfile`, start containers, tail logs, exec in. Environment variables are typed
   into a dialog there, so they land in `.idea/workspace.xml` rather than your env file.
3. **`docker-compose.yml`** with `env_file: .env.docker` and `ports: ["8001:8000"]`: IntelliJ shows it
   as a service with a green Run arrow, the same file works unchanged on any host, and the secrets
   stay in the gitignored env file. Ask for it and it gets added.

To hit breakpoints in the container, start with
`-e JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" -p 5005:5005`
and attach an IntelliJ **Remote JVM Debug** configuration to port 5005.

## Deploying the image somewhere

Two facts decide the destination:

- The container needs a **reachable database** the moment it starts. By default that is PostgreSQL
  with pgvector, and a free hosted plan — Supabase, Neon, a school-tier Postgres — satisfies it, so
  the app can move to a free app tier while the database stays managed. On the `oracle` profile
  instead, whatever hosts the app must also reach Oracle; nobody hosts Oracle for free, so a free
  app tier alone will crash-loop.
- Hosts assign their own port through the `PORT` variable, which this image already honours
  (`${PORT:+--server.port=$PORT}` in the entrypoint), and the app runs as a non-root user, as several
  platforms require.

Push it to a registry first — Docker Hub or GitHub Packages both work with a free account:

```powershell
docker tag ai-support-hub:0.0.1 <registry-user>/ai-support-hub:0.0.1
docker push <registry-user>/ai-support-hub:0.0.1
```

Beyond that, keep in mind that a public URL puts your Gemini key behind whoever calls the API: the
dashboard is an operator console, `/api/v1/clients/**` needs an agent login, and rate limiting is
in-memory. A demo you can hand to a reviewer is better served by a throwaway tunnel to the local
container than by an always-on public host.
