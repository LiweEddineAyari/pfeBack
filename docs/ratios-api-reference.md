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
- `POST /ratios/simulate` evaluate formula without persisting
- `POST /ratios/{code}/execute/{date}` evaluate one stored ratio at reference balance date (`YYYY-MM-DD`)

## 2) Request Body Contract (POST /ratios and PUT /ratios/{code})

### Top-level attributes

| Attribute | Required | Type | Rules |
|---|---|---|---|
| `code` | Yes | string | Non-empty, unique, recommended short business code (`RS`, `RL`, `ROE`) |
| `label` | Yes | string | Non-empty human-readable label |
| `famille` | Yes | string | Non-empty family name (text, not numeric id) |
| `categorie` | Yes | string | Non-empty category name |
| `formula` | Yes | object | Must be a valid expression tree |
| `seuilTolerance` | No | number | Optional threshold |
| `seuilAlerte` | No | number | Optional threshold |
| `seuilAppetence` | No | number | Optional threshold |
| `description` | No | string | Optional descriptive text |
| `isActive` | No | boolean | Defaults to `true` when omitted |

## 3) Formula JSON Structure (Important)

Formulas are always structured JSON (expression tree), never plain text.

Supported node types:
- `PARAM` references `ParameterConfig.code`
- `CONSTANT` numeric literal
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`

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
  "famille": "Prudentiel",
  "categorie": "Capital",
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
  "famille": "Prudentiel",
  "categorie": "Capital",
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

- `404 Ratios config not found for code`
  - Code does not exist for get/update/delete

- `400 Failed to convert value ... to LocalDate`
  - Invalid date path value for `/ratios/{code}/execute/{date}` (must be `YYYY-MM-DD`)

- `400 Request validation failed`
  - Missing `code`, `label`, `famille`, `categorie`, or `formula`

## 10) Practical Testing Sequence

1. Ensure referenced parameter codes already exist in `/parameters`
2. Create ratio with `POST /ratios`
3. Check persisted object with `GET /ratios/{code}`
4. Execute stored ratio at date with `POST /ratios/{code}/execute/{date}`
5. Simulate ad-hoc formula with `POST /ratios/simulate`
6. Update thresholds/formula with `PUT /ratios/{code}`
7. Remove obsolete ratio with `DELETE /ratios/{code}`
