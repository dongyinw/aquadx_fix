# Admin play analytics API

The administrator console requests play statistics from `POST /api/v2/admin/analytics`.
The request accepts optional ISO dates (`from`, `to`) in `YYYY-MM-DD` format. The
range includes both dates, defaults to the most recent seven calendar days, and
is limited to 366 days. The endpoint requires an administrator session token.

The response summarizes maimai DX play logs in that interval. `players` counts
accounts with a play, `guestPlayers` counts those whose played card is the
account virtual card, `totalPlays` counts playlog rows, and `achievementRate`
is the average achievement percentage (for example, `100` means 100%). `trend`
contains daily play, player, guest player, and average achievement values.
`ranking` contains the 20 most played music IDs with play counts and average
achievement percentages.

The published backend container is built by `.github/workflows/publish-image.yml`
on pushes to `main` and tagged with both `latest` and the source commit SHA.
