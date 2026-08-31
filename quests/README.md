# Authored Quest Definitions

Data-driven RVNKQuests definitions (one YAML per quest). These are **authored server content**,
not code and not jar resources — at runtime the plugin loads them from each server's
`plugins/RVNKQuests/quests/` directory. This folder is the version-controlled source of truth so
the authored content has history, review, and a Dev copy instead of living only on the live server.

## Source of truth & deploy flow

- **Edit here**, then deploy to a server by writing the file into `plugins/RVNKQuests/quests/` and
  running `/quest import <quest_id>` (reads the YAML, saves the definition to the DB, re-registers).
- On server boot the quest system **re-seeds from these YAML files**, so a file on disk is the
  durable definition — a `/quest import` alone (without updating the file) is lost on restart.
- Pulled from **RVNK Event** on 2026-07-30 as the initial import.

## Layout

Each file is `<quest_id>.yml` with `metadata.components` (triggers/objectives) and
`metadata.state_mapping` (which components are active per state). See any `tfah_*` file for the
canonical shape.

| File | Quest | Chain |
|------|-------|-------|
| `tfah_zeal_arrival.yml` | The Breach Point | Tales From A Hat — Zeal |
| `tfah_zeal_tower.yml` | Cross the Dead City | Tales From A Hat — Zeal |
| `tfah_zeal_sanctum.yml` | The Ring Closes | Tales From A Hat — Zeal |
| `tfah_ch1_journey.yml` | The Quiet World | Tales From A Hat — Chapter 1 |
| `tfah_ch1_archea.yml` | The Knights of the Darkness | Tales From A Hat — Chapter 1 |
| `tfah_sky_ascent.yml` | The Kingdom Looked Up | Tales From A Hat — Skylands (scenario 3) |
| `tfah_forge_outpost.yml` | The Shipment That Did Not Arrive | Tales From A Hat — Ravenforge (scenario 4) |
| `welcome_alphac.yml` | Welcome to Alpha Centauri | — |
| `survey_trail.yml` | The Surveyor's Trail | Surveyor (Zeal 2013 dome) |
| `survey_choice.yml` | Wanderer or Historian | Surveyor |
| `survey_sanctum.yml` | The Sanctum Remembers | Surveyor |

## Not yet captured here

Verified against `/quest validate` on RVNK Event, 2026-08-23: the server registers **14** quests, this
folder holds **11**. The three defined outside this folder (config/DB seed) and **not** version
controlled are:

- `twinkies_bridge` — "Where It Started"
- `koz_lost_sun` — "The Lost Sun"
- `twinkies_archea_mark` — "The Admin's Favourite"

Capturing those is the follow-up if the whole catalog should live here.

> This section previously listed `welcome_alphac` as uncaptured and the table above listed only
> eight files. Both were stale — `welcome_alphac`, `tfah_sky_ascent` and `tfah_forge_outpost` are all
> version controlled. Re-check against `/quest validate` rather than trusting this list.


## Known engine caveats (see board:rvnkquests)

- **Co-located triggers (#1853, fixed in 1.1.14):** if a quest's proximity trigger and its reach
  objective sit at nearly the same point, a player *arriving by teleport/portal* fires both in one
  tick. State changes are now serialized per player and may only move forward, so the outcome is
  deterministic — the higher state wins instead of last-write-wins. Co-locating them is still worth
  avoiding: both components run, and the intermediate state is skipped rather than observed.

  `tfah_zeal_tower` is no longer an example of the workaround. It was redesigned to a **single
  `tower_reach`** at the tower base (#1855) after the earlier point-radius trigger proved *missable*
  — a player who climbed rather than walked the path never crossed the sphere and the quest never
  armed. The lesson there is about missability, not the race: for a destination, use one `REACH` at
  the destination rather than a trigger plus a reach.
