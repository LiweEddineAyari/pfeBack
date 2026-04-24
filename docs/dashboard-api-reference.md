# Dashboard API Reference

Generated: 2026-04-21

## Scope

This document describes dashboard APIs and DTO contracts:
- Create dashboard row
- List dashboard rows
- Filter dashboard rows by date
- Request DTO and response DTO fields
- Common validation and business errors

## 1) Endpoints

Base path: `/dashboard`

- `POST /dashboard` create one dashboard row
- `GET /dashboard` list dashboard rows (all dates)
- `GET /dashboard?date=YYYY-MM-DD` list dashboard rows filtered by date
- `GET /dashboard/date/{date}` list dashboard rows filtered by date (`YYYY-MM-DD`)

## 2) Request DTO

DTO: `DashboardCreateRequestDTO`

### Fields

| Field | Required | Type | Rules |
|---|---|---|---|
| `idRatios` | Yes | number (`Long`) | Must reference an existing ratio id in `mapping.ratios_config` |
| `value` | Yes | number (`Double`) | Dashboard value to persist |
| `date` | Yes | date (`LocalDate`) | Must be in `YYYY-MM-DD` format |

### Create Request Example

```json
{
  "idRatios": 3,
  "value": 15.37,
  "date": "2026-04-13"
}
```

## 3) Response DTO

DTO: `DashboardRowResponseDTO`

### Fields

| Field | Type | Description |
|---|---|---|
| `id` | number (`Long`) | Dashboard row id |
| `idRatios` | number (`Long`) | Ratio id (`mapping.ratios_config.id`) |
| `code` | string | Ratio code |
| `label` | string | Ratio label |
| `description` | string | Ratio description |
| `familleId` | number (`Long`) | Family id |
| `categorieId` | number (`Long`) | Category id |
| `familleCode` | string | Family display value (currently `famille_ratios.name`) |
| `categorieCode` | string | Category display value (currently `categorie_ratios.name`) |
| `seuilTolerance` | number (`Double`) | Ratio threshold tolerance |
| `seuilAlerte` | number (`Double`) | Ratio alert threshold |
| `seuilAppetence` | number (`Double`) | Ratio appetite threshold |
| `value` | number (`Double`) | Dashboard value |
| `date` | date (`LocalDate`) | Dashboard reference date |

### Response Example

```json
{
  "id": 10,
  "idRatios": 3,
  "code": "RS",
  "label": "Ratio de Solvabilite",
  "description": "Fonds propres / actifs ponderes",
  "familleId": 5,
  "categorieId": 1,
  "familleCode": "Indicateurs de solidite financiere: Normes de solvabilite",
  "categorieCode": "Ratios Prudentiels",
  "seuilTolerance": 10.0,
  "seuilAlerte": 9.0,
  "seuilAppetence": 12.0,
  "value": 15.37,
  "date": "2026-04-13"
}
```

## 4) API Behavior Notes

- `POST /dashboard` returns `201 Created`.
- `GET /dashboard` uses optional query param `date` to filter.
- `GET /dashboard/date/{date}` is an alternative date-filter route.
- Without date filter, rows are sorted by `date`, then `idRatios`, then `id`.

## 5) Common Errors

- `400 Request validation failed`
  - Missing required fields: `idRatios`, `value`, or `date`

- `400 Ratios config does not exist for id: ...`
  - `idRatios` does not exist in ratios config table

- `400 Dashboard row already exists for ratio id ... and date ...`
  - Duplicate row for same (`idRatios`, `date`)

- `400 Failed to convert value ... to LocalDate`
  - Invalid date format in query/path (`YYYY-MM-DD` required)
