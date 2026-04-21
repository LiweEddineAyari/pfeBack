# Ratios API Reference

Generated: 2026-04-17

## Scope

This document explains how to use the dynamic ratios module:
- Ratio CRUD APIs
- JSON formula structure (expression tree)
- Validation rules
- Simulation API
- Execute API at a specific reference date
- SQL expression generation behavior
- Postman-ready examples

## 1) Endpoints

Base path: `/ratios`

- `POST /ratios` create a ratio configuration
- `GET /ratios` list all ratios
- `GET /ratios/{code}` get one ratio by code
- `PUT /ratios/{code}` update one ratio by code
- `DELETE /ratios/{code}` delete one ratio by code
- `DELETE /ratios` delete many ratios by code list (request body: array of strings)
- `POST /ratios/simulate` evaluate formula without persisting
- `POST /ratios/{code}/execute/{date}` evaluate one stored ratio at reference balance date (`YYYY-MM-DD`)

Family lookup endpoints:
- `POST /ratios/families` create one family (`name`)
- `GET /ratios/families` list families
- `GET /ratios/families/{id}` get one family by id
- `PUT /ratios/families/{id}` update one family by id
- `DELETE /ratios/families/{id}` delete one family by id

Category lookup endpoints:
- `POST /ratios/categories` create one category (`name`)
- `GET /ratios/categories` list categories
- `GET /ratios/categories/{id}` get one category by id
- `PUT /ratios/categories/{id}` update one category by id
- `DELETE /ratios/categories/{id}` delete one category by id

Dashboard endpoints:
- `POST /dashboard` create one dashboard row
- `GET /dashboard` list dashboard rows (all dates)
- `GET /dashboard?date=YYYY-MM-DD` list dashboard rows for one date
- `GET /dashboard/date/{date}` list dashboard rows for one date

## 2) Request Body Contract (POST /ratios and PUT /ratios/{code})

### Top-level attributes

| Attribute | Required | Type | Rules |
|---|---|---|---|
| `code` | Yes | string | Non-empty, unique, recommended short business code (`RS`, `RL`, `ROE`) |
| `label` | Yes | string | Non-empty human-readable label |
| `familleId` | Yes | integer (`Long`) | Foreign key to `mapping.famille_ratios.id` (must exist) |
| `categorieId` | Yes | integer (`Long`) | Foreign key to `mapping.categorie_ratios.id` (must exist) |
| `formula` | Yes | object | Must be a valid expression tree |
| `seuilTolerance` | No | number | Optional threshold |
| `seuilAlerte` | No | number | Optional threshold |
| `seuilAppetence` | No | number | Optional threshold |
| `description` | No | string | Optional descriptive text |
| `isActive` | No | boolean | Defaults to `true` when omitted |

### Family and Category are Foreign Keys (Important)

`familleId` and `categorieId` are mandatory FK ids, not free-text labels.

Back-end checks before save/update:
- `familleId` exists in `mapping.famille_ratios`
- `categorieId` exists in `mapping.categorie_ratios`

Quick SQL to fetch valid ids:

```sql
SELECT id, name FROM mapping.famille_ratios ORDER BY id;
SELECT id, name FROM mapping.categorie_ratios ORDER BY id;
```

Reference tables are seeded in `schema.sql` and linked by DB constraints:
- `fk_ratios_config_famille` on `ratios_config.famille_id`
- `fk_ratios_config_categorie` on `ratios_config.categorie_id`

## 3) Formula JSON Structure (Important)

Formulas are always structured JSON (expression tree), never plain text.

The root of `formula` must be an expression node object (with `type` at root). Do not wrap it under `formula.expression` for ratio APIs.

Supported node types:
- `PARAM` references `ParameterConfig.code`
- `CONSTANT` numeric literal
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`

`type` must use these exact tokens (uppercase) so JSON polymorphic deserialization can map to the right node class.

### 3.1 PARAM node

```json
{ "type": "PARAM", "code": "p1" }
```

### 3.2 CONSTANT node

```json
{ "type": "CONSTANT", "value": 12.5 }
```

### 3.3 Binary node

```json
{
  "type": "ADD",
  "left": { "type": "PARAM", "code": "p1" },
  "right": { "type": "PARAM", "code": "p2" }
}
```

Supported binary `type` values:
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`

### 3.4 Formula Contract Details Often Missed

- Every binary node requires both `left` and `right`.
- `PARAM.code` must already exist in `/parameters` (`mapping.parameters_config.code`).
- `CONSTANT.value` must be finite (no `NaN`, no `Infinity`).
- Constant zero on divider side is rejected at validation time (including resolvable constant expressions).
- Runtime division by zero is still blocked if the divider becomes zero from parameter values.
- Parameter values are evaluated once per parameter code and cached during a single ratio evaluation.
- If a parameter execution returns `null` or blank string, it is treated as `0` during ratio evaluation.
- If a parameter execution returns a non-numeric string/object, ratio evaluation fails.

## 4) Full Formula Example

Target expression:

`p1 * p2 / (p4 + p5) - p6 * p7`

JSON tree:

```json
{
  "type": "SUBTRACT",
  "left": {
    "type": "DIVIDE",
    "left": {
      "type": "MULTIPLY",
      "left": { "type": "PARAM", "code": "p1" },
      "right": { "type": "PARAM", "code": "p2" }
    },
    "right": {
      "type": "ADD",
      "left": { "type": "PARAM", "code": "p4" },
      "right": { "type": "PARAM", "code": "p5" }
    }
  },
  "right": {
    "type": "MULTIPLY",
    "left": { "type": "PARAM", "code": "p6" },
    "right": { "type": "PARAM", "code": "p7" }
  }
}
```

## 5) Validation Rules

Server-side validator ensures:
- All `PARAM.code` values exist in `ParameterConfig`
- Node type is valid (`PARAM`, `CONSTANT`, arithmetic binary types)
- Binary nodes always include `left` and `right`
- `CONSTANT.value` is present and finite
- Division-by-zero risk is rejected when right side is a constant-zero expression
- Formula JSON must deserialize to ratio node model (invalid `type` payload is rejected)

## 6) Evaluation Rules

The simulation/evaluation engine:
- Resolves each `PARAM` by executing the corresponding `ParameterConfig` formula
- Caches parameter values during evaluation
- Applies recursive arithmetic operations
- Rejects runtime division by zero

For `POST /ratios/{code}/execute/{date}`:
- Every referenced `PARAM` is executed with the same `{date}` value
- Parameter execution is date-scoped on `sub_dim_date.date_value = {date}`
- Ratio value is computed from those date-scoped parameter results

## 7) SQL Expression Generation Rules

SQL builder output is recursive and parenthesized.

Behavior:
- `PARAM` becomes parameter SQL subquery: `( <compiled parameter SQL> )`
- `CONSTANT` becomes numeric literal
- Binary operations become `(left OP right)`

Example pattern:

`((p1_sql * p2_sql) / (p4_sql + p5_sql)) - (p6_sql * p7_sql)`

## 8) Postman Examples

Base URL: `http://localhost:8081`
Header: `Content-Type: application/json`

### 8.1 Create Ratio

`POST /ratios`

```json
{
  "code": "RS",
  "label": "Ratio de Solvabilite",
  "familleId": 5,
  "categorieId": 1,
  "formula": {
    "type": "DIVIDE",
    "left": { "type": "PARAM", "code": "FONDS_PROPRES" },
    "right": { "type": "PARAM", "code": "RWA" }
  },
  "seuilTolerance": 0.10,
  "seuilAlerte": 0.09,
  "seuilAppetence": 0.12,
  "description": "Fonds propres / actifs ponderes par les risques",
  "isActive": true
}
```

### 8.2 Update Ratio

`PUT /ratios/RS`

```json
{
  "code": "RS",
  "label": "Ratio de Solvabilite (maj)",
  "familleId": 5,
  "categorieId": 1,
  "formula": {
    "type": "DIVIDE",
    "left": {
      "type": "MULTIPLY",
      "left": { "type": "PARAM", "code": "FONDS_PROPRES" },
      "right": { "type": "CONSTANT", "value": 100 }
    },
    "right": { "type": "PARAM", "code": "RWA" }
  },
  "seuilTolerance": 10.0,
  "seuilAlerte": 9.0,
  "seuilAppetence": 12.0,
  "description": "Exprime en pourcentage",
  "isActive": true
}
```

### 8.3 List Ratios

`GET /ratios`

No body.

### 8.4 Get One Ratio

`GET /ratios/RS`

No body.

### 8.5 Delete Ratio

`DELETE /ratios/RS`

No body.

### 8.6 Simulate Formula Without Saving

`POST /ratios/simulate`

```json
{
  "formula": {
    "type": "SUBTRACT",
    "left": {
      "type": "DIVIDE",
      "left": {
        "type": "MULTIPLY",
        "left": { "type": "PARAM", "code": "p1" },
        "right": { "type": "PARAM", "code": "p2" }
      },
      "right": {
        "type": "ADD",
        "left": { "type": "PARAM", "code": "p4" },
        "right": { "type": "PARAM", "code": "p5" }
      }
    },
    "right": {
      "type": "MULTIPLY",
      "left": { "type": "PARAM", "code": "p6" },
      "right": { "type": "PARAM", "code": "p7" }
    }
  }
}
```

Expected response shape:

```json
{
  "value": 1.2345,
  "sqlExpression": "(((<p1_sql>) * (<p2_sql>)) / ((<p4_sql>) + (<p5_sql>))) - ((<p6_sql>) * (<p7_sql>))",
  "referencedParameters": ["p1", "p2", "p4", "p5", "p6", "p7"]
}
```

### 8.7 Execute Stored Ratio At Specific Date

`POST /ratios/RS/execute/2026-04-13`

No body.

Expected response shape:

```json
{
  "code": "RS",
  "referenceDate": "2026-04-13",
  "value": 15.37,
  "sqlExpression": "((<FPE_sql_on_date>) / ((<RCR_sql_on_date> + <RM_sql_on_date>) + <RO_sql_on_date>))",
  "referencedParameters": ["FPE", "RCR", "RM", "RO"],
  "resolvedParameters": {
    "FPE": 2300.0,
    "RCR": 90.0,
    "RM": 35.0,
    "RO": 24.6
  }
}
```

## 9) Common Errors

- `400 Formula validation failed`
  - Unknown `PARAM` code
  - Missing node fields (`left`, `right`, `code`, `value`)
  - Unsupported node type
  - Division by zero risk

- `400 familleId does not exist: ...`
  - FK id not found in `mapping.famille_ratios`

- `400 categorieId does not exist: ...`
  - FK id not found in `mapping.categorie_ratios`

- `400 Invalid ratio formula JSON: ...`
  - Invalid polymorphic node payload (`type` token/structure mismatch)

- `404 Ratios config not found for code`
  - Code does not exist for get/update/delete

- `400 Failed to convert value ... to LocalDate`
  - Invalid date path value for `/ratios/{code}/execute/{date}` (must be `YYYY-MM-DD`)

- `400 Request validation failed`
  - Missing `code`, `label`, `familleId`, `categorieId`, or `formula`

- `400 Request code does not match path code`
  - On `PUT /ratios/{code}`, request body `code` (if provided) differs from path

- `404 Famille ratios not found for id: ...`
  - Family id does not exist

- `404 Categorie ratios not found for id: ...`
  - Category id does not exist

- `400 Cannot delete famille ratios id ... because it is referenced by ratios config`
  - Family is still used by one or more ratios

- `400 Cannot delete categorie ratios id ... because it is referenced by ratios config`
  - Category is still used by one or more ratios

## 10) Practical Testing Sequence

1. Ensure referenced parameter codes already exist in `/parameters`
2. Resolve valid `familleId` and `categorieId` from lookup tables
3. Create ratio with `POST /ratios`
4. Check persisted object with `GET /ratios/{code}`
5. Execute stored ratio at date with `POST /ratios/{code}/execute/{date}`
6. Simulate ad-hoc formula with `POST /ratios/simulate`
7. Update thresholds/formula with `PUT /ratios/{code}`
8. Remove obsolete ratio with `DELETE /ratios/{code}` or bulk `DELETE /ratios`

## 11) Family and Category CRUD Examples

Base URL: `http://localhost:8081`
Header: `Content-Type: application/json`

Request body for create/update (families and categories):

```json
{
  "name": "Ratios Prudentiels"
}
```

Response shape (families and categories):

```json
{
  "id": 1,
  "name": "Ratios Prudentiels"
}
```

Families examples:
- `POST /ratios/families`
- `GET /ratios/families`
- `GET /ratios/families/1`
- `PUT /ratios/families/1`
- `DELETE /ratios/families/1`

Categories examples:
- `POST /ratios/categories`
- `GET /ratios/categories`
- `GET /ratios/categories/1`
- `PUT /ratios/categories/1`
- `DELETE /ratios/categories/1`

## 12) Dashboard Table and API

When a new ratio is created (`POST /ratios`), the backend now automatically inserts dashboard rows for each distinct date present in `datamart.fact_balance` (joined to `datamart.sub_dim_date`).

DB table:
- Schema: `dashboard`
- Table: `dashboard`
- Columns: `id`, `id_ratios`, `ratios_value`, `reference_date`, `created_at`

Example behavior:
- If `fact_balance` contains 3 distinct dates, creating one new ratio inserts 3 rows in `dashboard.dashboard` for that ratio id.

Dashboard API response shape:

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
  "value": 15.37,
  "date": "2026-04-13"
}
```

Create dashboard row request body:

```json
{
  "idRatios": 3,
  "value": 15.37,
  "date": "2026-04-13"
}
```

Common create errors:
- `400 Ratios config does not exist for id: ...`
- `400 Dashboard row already exists for ratio id ... and date ...`
