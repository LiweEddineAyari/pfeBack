# Stress Test Simulation API Reference

Generated: 2026-04-24

## 1. Overview

The stress-test module runs what-if simulations fully in memory:

- No `UPDATE` / `DELETE` / `INSERT` is executed.
- Baseline values are computed from original rows.
- Simulated values are computed from copied/overridden rows.
- Response returns only entries that changed numerically.

Base path: `/stress-test`

- `POST /stress-test/simulate`
- `GET /stress-test/diagnostics`

---

## 2. Endpoints

### 2.1 `POST /stress-test/simulate`

Runs one simulation scenario using either:

- `BALANCE` method (virtual row-level balance overrides), or
- `PARAMETER` method (virtual parameter overrides).

### 2.2 `GET /stress-test/diagnostics`

Returns available dates and row counts to help choose a valid `referenceDate`.

Query parameter:

- `referenceDate` (optional, ISO date `YYYY-MM-DD`)

---

## 3. Request DTO (`StressTestRequestDTO`)

```json
{
  "method": "BALANCE | PARAMETER",
  "referenceDate": "YYYY-MM-DD",
  "balanceAdjustments": [],
  "parameterAdjustments": [],
  "parameterCodes": [],
  "ratioCodes": []
}
```

### 3.1 Attributes

| Attribute | Required | Type | Values / rules |
|---|---|---|---|
| `method` | Yes | enum | `BALANCE`, `PARAMETER` |
| `referenceDate` | Yes | date | ISO format (`YYYY-MM-DD`) |
| `balanceAdjustments` | Required if `method=BALANCE` | array | List of `BalanceAdjustmentDTO` |
| `parameterAdjustments` | Required if `method=PARAMETER` | array | List of `ParameterAdjustmentDTO` |
| `parameterCodes` | No | array of string | Optional scope restriction for parameters |
| `ratioCodes` | No | array of string | Optional scope restriction for ratios |

Validation:

- `method` controls which adjustment array must be non-empty.
- Provided codes must exist (`UNKNOWN_PARAMETER`, `UNKNOWN_RATIO` otherwise).

---

## 4. BALANCE Method

## 4.1 `BalanceAdjustmentDTO`

```json
{
  "operation": "SET | ADD | SUBTRACT",
  "field": "soldeconvertie",
  "value": 800,
  "filters": {
    "conditions": [
      { "field": "numcompte", "operator": "LIKE", "value": "10%" }
    ]
  }
}
```

| Attribute | Required | Type | Values / rules |
|---|---|---|---|
| `operation` | Yes | enum | `SET`, `ADD`, `SUBTRACT` |
| `field` | Yes | string | Must resolve to allow-listed canonical balance fields |
| `value` | Yes | number | BigDecimal-compatible |
| `filters` | No | JSON | Formula filter grammar (documented in section 6) |

Allow-listed canonical adjustable fields:

- `soldeorigine`
- `soldeconvertie`
- `cumulmvtdb`
- `cumulmvtcr`
- `soldeinitdebmois`
- `amount`

Aliases in `FieldRegistry` are accepted if they resolve to one of these.

## 4.2 Mandatory balance constraint

Across all touched rows:

`sum(positive deltas) == sum(negative deltas)` within epsilon `1e-6`.

If not, API returns `UNBALANCED_SIMULATION`.

---

## 5. PARAMETER Method

## 5.1 `ParameterAdjustmentDTO`

```json
{
  "operation": "MULTIPLY | ADD | REPLACE | MODIFY_FORMULA",
  "code": "ENCTENG",
  "value": 1.2,
  "formula": {}
}
```

| Attribute | Required | Type | Values / rules |
|---|---|---|---|
| `operation` | Yes | enum | `MULTIPLY`, `ADD`, `REPLACE`, `MODIFY_FORMULA` |
| `code` | Yes | string | Existing parameter code |
| `value` | Required for scalar ops | number | Used by `MULTIPLY`, `ADD`, `REPLACE` |
| `formula` | Required for `MODIFY_FORMULA` | JSON | Full formula JSON grammar (section 6) |

Semantics:

- `MULTIPLY`: `simulated = baseline * value`
- `ADD`: `simulated = baseline + value`
- `REPLACE`: `simulated = value`
- `MODIFY_FORMULA`: evaluate replacement formula on original rows

---

## 6. JSON Formula Grammar (Complete Options)

This grammar is used for:

- `parameterAdjustments[].formula` (`MODIFY_FORMULA`)
- balance `filters` parsing (via the same parser/filter grammar)
- parameter formulas in configuration (`formula_json`)

## 6.1 Root shapes accepted

Shape A (wrapped):

```json
{
  "expression": { "...node..." },
  "where": { "...filter group..." },
  "filter": { "...filter group..." },
  "filters": { "...filter group..." },
  "groupBy": ["fieldA", "fieldB"],
  "orderBy": ["fieldA", { "field": "fieldB", "direction": "DESC" }],
  "limit": 100,
  "top": 10
}
```

Shape B (direct expression object):

```json
{
  "type": "AGGREGATION",
  "function": "SUM",
  "field": "soldeconvertie",
  "where": { "...filter group..." },
  "filter": { "...filter group..." },
  "groupBy": [],
  "orderBy": [],
  "limit": 10,
  "top": 10
}
```

Notes:

- For filtering at root level, parser accepts `where`, `filter`, and in wrapped shape also `filters`.
- `groupBy` / `orderBy` / `limit` / `top` are parsed/validated; stress-test scalar evaluator ignores them.

## 6.2 Expression node options (`type`)

Allowed `type` values:

- `FIELD`
- `VALUE`
- `AGGREGATION`
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`

### `FIELD`

```json
{ "type": "FIELD", "field": "soldeconvertie" }
```

### `VALUE`

```json
{ "type": "VALUE", "value": 123.45 }
```

`value` accepted JSON scalar types:

- string
- integer
- decimal
- boolean
- array (for filter values)

Objects are not allowed as scalar `value`.

### `AGGREGATION`

```json
{
  "type": "AGGREGATION",
  "function": "SUM",
  "field": "soldeconvertie",
  "expression": { "...row expression..." },
  "filters": { "...filter group..." },
  "distinct": false
}
```

Allowed `function` values:

- `SUM`
- `AVG`
- `COUNT`
- `MIN`
- `MAX`

`field` and `expression`:

- At least one should be provided for meaningful aggregations.
- `COUNT` supports `field`, `expression`, or count-all.
- `distinct` accepted boolean (`true` / `false`), default `false`.

### Binary arithmetic nodes

```json
{
  "type": "ADD | SUBTRACT | MULTIPLY | DIVIDE",
  "left": { "...node..." },
  "right": { "...node..." }
}
```

## 6.3 Filter group grammar

A filter can be any of:

1) condition object

```json
{ "field": "numcompte", "operator": "LIKE", "value": "10%" }
```

2) explicit group object

```json
{
  "logic": "AND | OR",
  "conditions": [
    { "field": "pays", "operator": "=", "value": "TN" }
  ],
  "groups": [
    {
      "logic": "OR",
      "conditions": [
        { "field": "grpaffaire", "operator": "=", "value": "PERSONNEL" },
        { "field": "grpaffaire", "operator": "=", "value": "CORPORATE" }
      ]
    }
  ]
}
```

3) array shorthand (treated as `AND`)

```json
[
  { "field": "pays", "operator": "=", "value": "TN" },
  { "field": "devise", "operator": "=", "value": "TND" }
]
```

## 6.4 Filter condition attributes

| Attribute | Required | Type | Rules |
|---|---|---|---|
| `field` | Yes | string | Must exist in `FieldRegistry` |
| `operator` | Yes | string | Supported operators listed below |
| `value` / `values` | Depends | any / array | Required for operators that require value |

Operator value requirements:

- Requires scalar value: `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `LIKE`, `STARTS_WITH`, `ENDS_WITH`, `CONTAINS`
- Requires array value: `IN`, `NOT_IN`
- Requires array of exactly 2 values: `BETWEEN`
- Requires no value: `IS_NULL`, `IS_NOT_NULL`

## 6.5 Filter operator full options (including aliases)

Canonical operators:

- `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`
- `LIKE`, `STARTS_WITH`, `ENDS_WITH`, `CONTAINS`
- `IN`, `NOT_IN`, `BETWEEN`
- `IS_NULL`, `IS_NOT_NULL`

Accepted string aliases:

- `=` -> `EQ`
- `!=`, `<>` -> `NE`
- `>`, `<`, `>=`, `<=`
- `LIKE`
- `STARTS WITH`, `STARTS_WITH`, `STARTSWITH`, `BEGINS WITH`, `BEGINS_WITH`, `BEGINSWITH` -> `STARTS_WITH`
- `ENDS WITH`, `ENDS_WITH`, `ENDSWITH` -> `ENDS_WITH`
- `CONTAINS`
- `IN`
- `NOT IN`, `NOT_IN` -> `NOT_IN`
- `BETWEEN`
- `IS NULL`, `IS_NULL` -> `IS_NULL`
- `IS NOT NULL`, `IS_NOT_NULL` -> `IS_NOT_NULL`

## 6.6 Other root attributes

| Attribute | Type | Allowed values |
|---|---|---|
| `groupBy` | array of string | field names |
| `orderBy` | array | each entry: string field OR object `{field, direction}` |
| `orderBy[].direction` | string | `ASC`, `DESC` |
| `limit` | integer | 32-bit int |
| `top` | integer | 32-bit int |

---

## 7. Request JSON Examples

## 7.1 BALANCE request example

```json
{
  "method": "BALANCE",
  "referenceDate": "2026-04-13",
  "balanceAdjustments": [
    {
      "operation": "ADD",
      "field": "soldeconvertie",
      "value": 800,
      "filters": {
        "conditions": [
          { "field": "numcompte", "operator": "LIKE", "value": "10%" }
        ]
      }
    },
    {
      "operation": "SUBTRACT",
      "field": "soldeconvertie",
      "value": 1.3985,
      "filters": {
        "conditions": [
          { "field": "numcompte", "operator": "LIKE", "value": "11%" }
        ]
      }
    }
  ],
  "parameterCodes": ["ENCTENG", "FPBT1", "ENTENG"],
  "ratioCodes": ["RCET1", "RNPL"]
}
```

## 7.2 PARAMETER scalar request example

```json
{
  "method": "PARAMETER",
  "referenceDate": "2026-04-13",
  "parameterAdjustments": [
    { "operation": "MULTIPLY", "code": "ENCTENG", "value": 1.05 },
    { "operation": "ADD", "code": "FPBT1", "value": 1000000 },
    { "operation": "REPLACE", "code": "RCR", "value": 50000000 }
  ]
}
```

## 7.3 PARAMETER formula override request example

```json
{
  "method": "PARAMETER",
  "referenceDate": "2026-04-13",
  "parameterAdjustments": [
    {
      "operation": "MODIFY_FORMULA",
      "code": "ENCTENG",
      "formula": {
        "expression": {
          "type": "AGGREGATION",
          "function": "SUM",
          "field": "soldeconvertie"
        },
        "where": {
          "logic": "AND",
          "conditions": [
            { "field": "numcompte", "operator": "LIKE", "value": "10%" }
          ]
        }
      }
    }
  ]
}
```

---

## 8. Response DTOs

## 8.1 Simulate success (`StressTestResponseDTO`)

Important current behavior:

- `parameters` contains only changed items (`changed=true`).
- `ratios` contains only changed items (`changed=true`).
- `affectedParameters` / `affectedRatios` list only changed codes.
- `impacted` remains dependency-based.

```json
{
  "method": "BALANCE",
  "referenceDate": "2026-04-13",
  "factRowsLoaded": 174225,
  "factRowsImpacted": 2,
  "affectedFields": ["soldeconvertie"],
  "affectedParameters": ["ENCTENG", "FPBT1", "TOEXP", "ENTENG", "ENTRES"],
  "affectedRatios": ["RNPL", "TECH", "TCR", "TCS", "TCPS", "TCGAR", "RCET1", "LGPARTCOM"],
  "parameters": [
    {
      "code": "ENCTENG",
      "label": "Encours total engagements",
      "original": -136762838752.0,
      "simulated": -136763838752.0,
      "delta": -1000000.0,
      "impactPercent": 0.0007311927780421099,
      "impacted": true,
      "changed": true
    }
  ],
  "ratios": [
    {
      "code": "RCET1",
      "label": "Ratio CET1",
      "original": 0.2966215423360454,
      "simulated": 0.2966002971822813,
      "dashboardValue": 12.0,
      "delta": -0.00002124515376406011,
      "impactPercent": -0.007162377215337675,
      "impacted": true,
      "changed": true
    }
  ]
}
```

### Field semantics

| Field | Meaning |
|---|---|
| `original` (parameter) | Baseline in-memory parameter value (original rows) |
| `simulated` (parameter/ratio) | Value after simulation |
| `original` (ratio) | Baseline in-memory ratio value from baseline parameter map |
| `dashboardValue` | Stored dashboard ratio value for reference date (informational only) |
| `impacted` | Dependency graph says this item is reachable from changed field/code |
| `changed` | `abs(delta) > 1e-9` |

## 8.2 Diagnostics success (`StressTestDiagnosticsResponseDTO`)

```json
{
  "referenceDate": "2026-04-30",
  "rowCountForReferenceDate": 0,
  "availableReferenceDates": ["2026-04-13"],
  "totalFactRows": 174225
}
```

---

## 9. Error DTO Response

All API errors follow `ApiErrorResponse`:

```json
{
  "timestamp": "2026-04-24T14:11:59.000",
  "status": 400,
  "error": "UNBALANCED_SIMULATION",
  "message": "Total positive adjustments must equal total negative adjustments",
  "details": ["positive=191200.000000", "negative=109300800.000000", "difference=109109600.000000"],
  "path": "/stress-test/simulate"
}
```

## 9.1 Stress-test error codes

| `error` | HTTP | Raised when |
|---|---|---|
| `INVALID_REQUEST` | 400 | Missing/invalid method/date or wrong adjustment array |
| `INVALID_OPERATION` | 400 | Missing operation/code/value/formula, unsupported op, invalid filter block |
| `UNBALANCED_SIMULATION` | 400 | Positive and negative deltas are not balanced |
| `UNKNOWN_FIELD` | 400 | Unknown balance field or filter field |
| `UNKNOWN_PARAMETER` | 400 | Unknown parameter code in scope or adjustments |
| `UNKNOWN_RATIO` | 400 | Unknown ratio code in scope |
| `NO_DATA_FOR_DATE` | 400 | No fact rows found for requested `referenceDate` |

## 9.2 Other framework/global errors applicable

| `error` | HTTP | Source |
|---|---|---|
| `Bad Request` | 400 | Formula validation (`FormulaValidationException`) |
| `Bad Request` | 400 | Bean validation (`MethodArgumentNotValidException`) |
| `Bad Request` | 400 | Illegal enum/string conversion (`IllegalArgumentException`) |
| `Internal Server Error` | 500 | Unexpected runtime exception |

## 9.3 Error examples

### Unbalanced simulation

```json
{
  "timestamp": "2026-04-24T14:13:08.756",
  "status": 400,
  "error": "UNBALANCED_SIMULATION",
  "message": "Total positive adjustments must equal total negative adjustments",
  "details": ["positive=191200.000000", "negative=109300800.000000", "difference=109109600.000000"],
  "path": "/stress-test/simulate"
}
```

### No rows for date

```json
{
  "timestamp": "2026-04-24T14:20:10.120",
  "status": 400,
  "error": "NO_DATA_FOR_DATE",
  "message": "No fact_balance rows found for referenceDate=2026-04-30",
  "details": ["Available dates: [2026-04-13]"],
  "path": "/stress-test/simulate"
}
```

### Formula validation failed

```json
{
  "timestamp": "2026-04-24T14:22:40.090",
  "status": 400,
  "error": "Bad Request",
  "message": "Formula validation failed",
  "details": ["root.expression: aggregation SUM requires numeric field/expression"],
  "path": "/stress-test/simulate"
}
```

---

## 10. Execution Notes

- Simulation is read-only and in-memory.
- Dependency graph logic is separate from numerical change logic.
- Response filtering uses changed-only semantics by default.
- `dashboardValue` does not drive filtering; only `original` vs `simulated` does.

---

## 11. Quick Checklist

Before calling `POST /stress-test/simulate`:

1. Call `GET /stress-test/diagnostics?referenceDate=...` if date availability is uncertain.
2. For `BALANCE`, ensure add/subtract totals are balanced.
3. For `MODIFY_FORMULA`, validate formula JSON shape against section 6.
4. Expect response to include only changed parameters/ratios.
