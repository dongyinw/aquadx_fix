# AquaDX server backup

This backup contains the source changes and game data used by the running
server. The `main` branch publishes the application image to
`ghcr.io/dongyinw/aquadx_fix:latest`. Runtime configuration, database files,
passwords, SMTP credentials, JWT secrets, and user data are intentionally
excluded.

## Restore

1. Copy `deploy/.env.example` to `deploy/.env` and fill in the database values.
2. Put the server `config/` and `data/` directories in the repository root.
3. Log in to GHCR if the package is private:

   ```sh
   docker login ghcr.io
   ```

4. Pull and start the published image:

   ```sh
   docker compose -f deploy/docker-compose.server.yml --env-file deploy/.env pull
   docker compose -f deploy/docker-compose.server.yml --env-file deploy/.env up -d --force-recreate
   ```

For a local image build instead, run `docker build -t ghcr.io/dongyinw/aquadx_fix:local .`
from the repository root and change the compose tag before starting.
