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
| `survey_trail.yml` | The Surveyor's Trail | Surveyor (Zeal 2013 dome) |
| `survey_choice.yml` | Wanderer or Historian | Surveyor |
| `survey_sanctum.yml` | The Sanctum Remembers | Surveyor |

## Not yet captured here

`/quest validate` on Event lists quests beyond these eight (e.g. `koz_lost_sun`,
`twinkies_bridge`, `twinkies_archea_mark`, `welcome_alphac`, and disabled ones such as
`piglin_far_from_home`, `ancient_guardian`, `koz_road_to_origin`). Those are defined outside this
folder (config/DB seed) and are **not** version-controlled yet — a follow-up if the whole quest
catalog should live here.

## Known engine caveats (see board:rvnkquests)

- **Co-located triggers race (#1853):** if a quest's proximity trigger and its reach objective sit
  at nearly the same point, a player *arriving by teleport/portal* can fire both in one tick and the
  resulting state is non-deterministic. Author trigger and reach points far enough apart that a
  single arrival cannot satisfy both (see `tfah_zeal_tower` — its `tower_trigger` was moved off the
  breach for exactly this reason, #1855).
