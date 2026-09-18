# AquaDX server backup

This backup contains the source changes and game data used by the running
server. Runtime configuration, database files, passwords, SMTP credentials,
JWT secrets, and user data are intentionally excluded.

## Restore

1. Copy `deploy/.env.example` to `deploy/.env` and fill in the database values.
2. Put the server `config/` and `data/` directories in the repository root.
3. Build the application from the repository root:

   ```sh
   ./gradlew --no-daemon --console=plain bootJar
   ```

4. Start the server:

   ```sh
   docker compose -f deploy/docker-compose.server.yml --env-file deploy/.env up -d --force-recreate
   ```

The compose file mounts the built JAR over the base AquaDX image so the
checked-in source and game data are the version that runs.
