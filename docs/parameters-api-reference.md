# Parameters API Engine Analysis

Generated: 2026-04-14

## Scope

This document analyzes:
- Parameter controller endpoints
- Service behavior for create/compile/execute
- Formula parser, validation, and SQL compilation engine
- Allowed options for each request attribute in `POST /parameters`

## 1) Endpoint Contract Summary

Base path: `/parameters`

- `POST /parameters`: create parameter configuration
- `GET /parameters`: list all parameter configurations
- `GET /parameters/id/{id}`: fetch configuration by id
- `GET /parameters/code/{code}`: fetch configuration by code (explicit route)
- `PUT /parameters/{code}`: update configuration by code
- `DELETE /parameters/code/{code}`: delete one configuration by code
- `DELETE /parameters`: delete many configurations by code list
- `GET /parameters/{code}`: fetch configuration by code (legacy alias)
- `GET /parameters/{code}/sql`: compile formula to SQL
- `POST /parameters/{code}/execute`: execute compiled SQL
- `POST /parameters/{code}/execute/{date}`: execute compiled SQL on one reference balance date (`YYYY-MM-DD`)
- `GET /parameters/supported-fields`: list all supported filter/expression fields

`GET /parameters/supported-fields` response shape:

```json
{
  "fields": ["..."],
  "fieldsByTable": {
    "datamart.fact_balance": ["..."],
    "datamart.dim_client": ["..."],
    "datamart.dim_contrat": ["..."],
    "datamart.sub_dim_compte": ["..."]
  }
}
```

Important: execute endpoints are **POST**, not GET.

## 2) Request Body Contract (POST /parameters)

### Top-level attributes

| Attribute | Required | Type | Allowed values / rules | Default |
|---|---|---|---|---|
| `code` | Yes | string | Non-empty (`@NotBlank`), unique in DB, trimmed before save. Persisted to `mapping.parameters_config.code` (length 50). | None |
| `label` | Yes | string | Non-empty (`@NotBlank`), trimmed before save. Persisted with max length 255. | None |
| `isActive` | No | boolean | `true` or `false`. | `true` if omitted |
| `formula` | Conditional | JSON object | Required when `nativeSql` is not provided. Must parse into engine AST and pass validation. | None |
| `nativeSql` | Conditional | string | Optional native SQL input converted to internal formula JSON. Required when `formula` is not provided. | None |

Notes:
- Persistence defaults: `version=1`, `createdAt` and `updatedAt` auto-populated.
- Request contract is DTO-based (`code`, `label`, `formula`, `nativeSql`, `isActive`). Do not rely on sending DB fields like `id`, `version`, `createdAt`, `updatedAt`.
- If both `formula` and `nativeSql` are sent, `nativeSql` is converted and used as the source formula.

### 2.1 Native SQL Input (Optional)

`nativeSql` supports a restricted analytical SQL subset:

- Single `SELECT` with exactly one aggregation in `SUM`, `AVG`, `COUNT`, `MIN`, `MAX`
- `FROM fact_balance` (schema prefix accepted)
- Optional joins on `dim_client`, `dim_contrat`, and `sub_dim_*`
- Optional `WHERE` with `=`, `!=`, `<`, `>`, `<=`, `>=`, `IN`, `NOT IN`, `BETWEEN`, `LIKE`, `IS NULL`
- Optional `GROUP BY`, `ORDER BY`, `LIMIT` / `TOP`

Rejected SQL patterns include:

- `INSERT`, `UPDATE`, `DELETE`
- `UNION` / multi-select set operations
- Subqueries
- `HAVING`

On invalid SQL, API returns `400` with:

```json
{
  "error": "INVALID_SQL",
  "message": "Unsupported SQL structure",
  "details": ["..."]
}
```

### 2.2 Request Body Parameter Values (Postman Quick Matrix)

Use this matrix as a checklist when building your JSON body in Postman.

| Parameter path | Required | Type | Possible values | Example value |
|---|---|---|---|---|
| `code` | Yes | string | Any non-empty unique code (recommended uppercase letters, numbers, `_`) | `"ENT10"` |
| `label` | Yes | string | Any non-empty display label | `"Top 10 exposition"` |
| `isActive` | No | boolean | `true`, `false` | `true` |
| `formula` | Conditional | object | Required when `nativeSql` is not provided. Shape A (wrapper with `expression`) or Shape B (direct expression root) | `{ ... }` |
| `nativeSql` | Conditional | string | Required when `formula` is not provided. Restricted native SQL subset converted to formula JSON. | `"SELECT SUM(soldeconvertie) FROM fact_balance f"` |
| `formula.expression` | Yes | object | Any valid expression node | `{ "type": "AGGREGATION", ... }` |
| `formula.expression.type` | Yes | string | `FIELD`, `VALUE`, `AGGREGATION`, `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE` | `"AGGREGATION"` |
| `formula.expression.field` | Conditional | string | Any field from `GET /parameters/supported-fields` | `"soldeconvertie"` |
| `formula.expression.value` | Conditional | scalar/array | Number, string, boolean, null, or array (depends on node/operator) | `100`, `"EUR"`, `true` |
| `formula.expression.function` | Conditional | string | `SUM`, `AVG`, `COUNT`, `MIN`, `MAX` | `"SUM"` |
| `formula.expression.expression` | Conditional | object | Nested expression (used by aggregation or arithmetic) | `{ "type": "FIELD", "field": "amount" }` |
| `formula.expression.left` | Conditional | object | Expression node (for arithmetic types) | `{ "type": "FIELD", "field": "cumulmvtdb" }` |
| `formula.expression.right` | Conditional | object | Expression node (for arithmetic types) | `{ "type": "FIELD", "field": "cumulmvtcr" }` |
| `formula.expression.distinct` | No | boolean | `true`, `false` | `false` |
| `formula.expression.filters` | No | object/array | Filter group syntax (same rules as `where/filter/filters`) | `{ "logic": "AND", ... }` |
| `formula.where` | No | object/array | Filter group syntax | `{ "logic": "AND", ... }` |
| `formula.filter` | No | object/array | Alias of `where` | `{ "conditions": [...] }` |
| `formula.filters` | No | object/array | Alias of `where` | `[ { "field": "dc.actif", ... } ]` |
| `formula.where.logic` | No | string | `AND`, `OR` | `"AND"` |
| `formula.where.conditions[].field` | Conditional | string | Any supported field | `"dc.actif"` |
| `formula.where.conditions[].operator` | Conditional | string | `=`, `!=`, `<>`, `>`, `>=`, `<`, `<=`, `LIKE`, `IN`, `NOT IN`, `BETWEEN`, `IS NULL`, `IS NOT NULL`, and enum aliases (`EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `NOT_IN`, `IS_NULL`, `IS_NOT_NULL`) | `"IN"` |
| `formula.where.conditions[].value` | Conditional | scalar/array | Depends on operator (`IN`/`NOT IN`: non-empty array, `BETWEEN`: 2-value array, others: scalar) | `[1,2]`, `["2026-01-01","2026-12-31"]`, `1` |
| `formula.groupBy` | No | array of string | Any supported fields | `["idClient"]` |
| `formula.orderBy` | No | array | Array of strings or objects | `[ { "field": "value", "direction": "DESC" } ]` |
| `formula.orderBy[].field` | Conditional | string | Any supported field or computed alias `value` | `"value"`, `"idClient"` |
| `formula.orderBy[].direction` | No | string | `ASC`, `DESC` | `"DESC"` |
| `formula.limit` | No | integer | Positive integer (`> 0`), requires `orderBy`, cannot be combined with `top` | `10` |
| `formula.top` | No | integer | Positive integer (`> 0`), requires `orderBy`, cannot be combined with `limit` | `10` |

### 2.3 Copy/Paste Request Template

```json
{
  "code": "YOUR_CODE",
  "label": "Your label",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "where": {
      "logic": "AND",
      "conditions": [
        {
          "field": "dc.actif",
          "operator": "=",
          "value": 1
        }
      ]
    },
    "groupBy": ["idClient"],
    "orderBy": [
      { "field": "value", "direction": "DESC" }
    ],
    "top": 10
  }
}
```

## 3) Formula Object Shapes

The parser accepts 2 root shapes:

### Shape A (recommended wrapper)

```json
{
  "expression": { ...expression node... },
  "where": { ...filter group... },
  "groupBy": ["field1", "field2"],
  "orderBy": [
    {"field": "field1", "direction": "ASC"},
    {"field": "value", "direction": "DESC"}
  ],
  "limit": 50,
  "top": 10
}
```

### Shape B (direct expression root)

```json
{
  "type": "AGGREGATION",
  "function": "SUM",
  "field": "soldeconvertie"
}
```

In direct shape, optional `where` or `filter` can still be added.
Optional `groupBy`, `orderBy`, `limit`, and `top` are also supported on direct shape.

## 4) expression Node Options

Every expression object requires `type`.

### `type` options
- `FIELD`
- `VALUE`
- `AGGREGATION`
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`

### Per type details

#### A) FIELD

```json
{ "type": "FIELD", "field": "soldeconvertie" }
```

- `field`: required string
- Must exist in field registry

#### B) VALUE

```json
{ "type": "VALUE", "value": 123.45 }
```

- `value`: required
- Supports scalar values (number/string/boolean/null)
- Arrays are supported where semantically valid (for filter operators that require arrays)

#### C) AGGREGATION

```json
{
  "type": "AGGREGATION",
  "function": "SUM",
  "field": "soldeconvertie",
  "distinct": false,
  "filters": { ...filter group... }
}
```

- `function`: required, one of `SUM`, `AVG`, `COUNT`, `MIN`, `MAX`
- `field`: optional if `expression` is provided
- `expression`: optional if `field` is provided
- Constraint: cannot provide both `field` and `expression`
- Constraint: for non-COUNT, one of `field` or `expression` is required
- `distinct`: optional boolean
- `filters`: optional local filter group (applied as CASE WHEN in aggregation)
- Extra rule: `COUNT DISTINCT` is invalid when operand is implicit `*`
- Type rule: `SUM` and `AVG` require numeric input

#### D) Arithmetic nodes (`ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`)

```json
{
  "type": "DIVIDE",
  "left": { ...expression... },
  "right": { ...expression... }
}
```

- `left`: required expression
- `right`: required expression
- Both must resolve to numeric types
- Validation rejects literal division by zero
- SQL compiler also protects division with `NULLIF(right, 0)`

## 5) where / filter / filters Options

Top-level filter keys recognized by parser:
- `where`
- `filter`
- `filters`

All map to a recursive filter group.

### Filter group attributes

| Attribute | Required | Type | Allowed values / rules |
|---|---|---|---|
| `logic` | No | string | `AND` or `OR` (default `AND`) |
| `conditions` | No | array | Array of condition objects |
| `groups` | No | array | Array of nested filter groups |

Constraint: a group cannot be empty (`conditions` and `groups` cannot both be empty).

### Condition attributes

| Attribute | Required | Type | Allowed values / rules |
|---|---|---|---|
| `field` | Yes | string | Must exist in field registry |
| `operator` | Yes | string | See operator list below |
| `value` / `values` | Depends | scalar or array | Required except for NULL operators |

### Accepted operator tokens

Parser accepts SQL-style tokens:
- `=`
- `!=`
- `<>`
- `>`
- `>=`
- `<`
- `<=`
- `LIKE`
- `IN`
- `NOT IN`
- `BETWEEN`
- `IS NULL`
- `IS NOT NULL`

Parser also accepts enum-style tokens for compatibility:
- `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`
- `NOT_IN`, `IS_NULL`, `IS_NOT_NULL`

### Operator-specific value shape

| Operator | Value required | Expected shape |
|---|---|---|
| `IS NULL`, `IS NOT NULL` | No | None |
| `IN`, `NOT IN` | Yes | Non-empty array |
| `BETWEEN` | Yes | Array of exactly 2 values |
| Others (`=`, `>`, `LIKE`, etc.) | Yes | Scalar |

### Validation compatibility rules

- `LIKE` only on string fields
- `>`, `>=`, `<`, `<=`, `BETWEEN` only on numeric/date/datetime fields
- `IN` is not supported for boolean fields
- Numeric field values must be numeric
- Date/datetime values must be strings (ISO format expected by business usage)
- Max nesting depth for expressions and filter groups: 5

## 6) groupBy Options

Top-level optional field:

```json
"groupBy": ["idAgence", "idDevise"]
```

Rules:
- Must be an array
- Each item must be a non-empty string
- Every groupBy field must exist in field registry

Behavior:
- SQL includes `GROUP BY` clause
- Execute endpoint returns list rows (`queryForList`) when `groupBy`, `limit`, or `top` is present
- Without groupBy, execute returns scalar (`queryForObject`)

### 6.1) orderBy options

Top-level optional field:

```json
"orderBy": [
  {"field": "idAgence", "direction": "ASC"},
  {"field": "value", "direction": "DESC"}
]
```

Rules:
- Must be an array
- Each item can be:
  - a string field name (defaults to `ASC`)
  - an object with `field` (required) and `direction` (`ASC` or `DESC`, default `ASC`)
- `field` can be any supported registry field or the computed alias `value`
- When `groupBy` is used, order fields coming from registry must also be present in `groupBy`

### 6.2) limit options

Top-level optional field:

```json
"limit": 100
```

Rules:
- Must be an integer
- Must be strictly positive
- Requires `orderBy` to be provided
- Cannot be combined with `top`

### 6.3) top options

Top-level optional field:

```json
"top": 20
```

Rules:
- Must be an integer
- Must be strictly positive
- Requires `orderBy` to be provided
- Cannot be combined with `limit`
- For PostgreSQL runtime, `top` is normalized to SQL `LIMIT` in compiled query

## 7) Supported Field Options (from FieldRegistry)

This section is exhaustive against the current `FieldRegistry` and now covers fact, dim, and sub_dim sources.

### 7.1 Canonical field names by source table

#### datamart.fact_balance
- `id`
- `idAgence`
- `idDevise`
- `idDevisebnq`
- `idCompte`
- `idChapitre`
- `idClient`
- `idContrat`
- `idDate`
- `soldeorigine`
- `soldeconvertie`
- `cumulmvtdb`
- `cumulmvtcr`
- `soldeinitdebmois`
- `amount`
- `actif`

#### datamart.dim_client
- `dimClient.idtiers`
- `dimClient.idResidence`
- `dimClient.idAgenteco`
- `dimClient.idDouteux`
- `dimClient.idGrpaffaire`
- `dimClient.idSectionactivite`
- `dimClient.nomprenom`
- `dimClient.raisonsoc`
- `dimClient.chiffreaffaires`

#### datamart.dim_contrat
- `dimContrat.id`
- `dimContrat.idClient`
- `dimContrat.idAgence`
- `dimContrat.idDevise`
- `dimContrat.idObjetfinance`
- `dimContrat.idTypcontrat`
- `dimContrat.idDateouverture`
- `dimContrat.idDateecheance`
- `dimContrat.ancienneteimpaye`
- `dimContrat.tauxcontrat`
- `dimContrat.actif`

#### datamart.sub_dim_agence
- `subDimAgence.numagence`
- `dimContrat.subDimAgence.numagence`

#### datamart.sub_dim_agenteco
- `subDimAgenteco.libelle`

#### datamart.sub_dim_chapitre
- `subDimChapitre.chapitre`

#### datamart.sub_dim_compte
- `subDimCompte.numcompte`
- `subDimCompte.libellecompte`

#### datamart.sub_dim_date
- `subDimDate.dateValue`
- `dimContrat.subDimDateOuverture.dateValue`
- `dimContrat.subDimDateEcheance.dateValue`

#### datamart.sub_dim_devise
- `subDimDevise.devise`
- `subDimDeviseBnq.devise`
- `dimContrat.subDimDevise.devise`

#### datamart.sub_dim_douteux
- `subDimDouteux.douteux`
- `subDimDouteux.datdouteux`

#### datamart.sub_dim_grpaffaire
- `subDimGrpaffaire.nomgrpaffaires`

#### datamart.sub_dim_objetfinance
- `dimContrat.subDimObjetfinance.libelle`

#### datamart.sub_dim_residence
- `subDimResidence.pays`
- `subDimResidence.residence`
- `subDimResidence.geo`

#### datamart.sub_dim_sectionactivite
- `subDimSectionactivite.libelle`

#### datamart.sub_dim_typcontrat
- `dimContrat.subDimTypcontrat.typcontrat`

### 7.2 Compatibility aliases (also accepted)

#### Short aliases used in business formulas
- `numcompte` -> `subDimCompte.numcompte`
- `libellecompte` -> `subDimCompte.libellecompte`
- `chapitre` -> `subDimChapitre.chapitre`
- `numagence` -> `subDimAgence.numagence`
- `idDouteux` -> `dimClient.idDouteux`
- `idGrpaffaire` -> `dimClient.idGrpaffaire`
- `idAgenteco` -> `dimClient.idAgenteco`
- `idSectionactivite` -> `dimClient.idSectionactivite`
- `idtiers` -> `dimClient.idtiers`
- `idResidence` -> `dimClient.idResidence`
- `nomprenom` -> `dimClient.nomprenom`
- `raisonsoc` -> `dimClient.raisonsoc`
- `chiffreaffaires` -> `dimClient.chiffreaffaires`
- `contratActif` -> `dimContrat.actif`
- `ancienneteimpaye` -> `dimContrat.ancienneteimpaye`
- `idTypcontrat` -> `dimContrat.idTypcontrat`
- `idObjetfinance` -> `dimContrat.idObjetfinance`
- `idDateouverture` -> `dimContrat.idDateouverture`
- `idDateecheance` -> `dimContrat.idDateecheance`
- `tauxcontrat` -> `dimContrat.tauxcontrat`
- `typcontrat` -> `dimContrat.subDimTypcontrat.typcontrat`
- `douteux` -> `subDimDouteux.douteux`
- `datdouteux` -> `subDimDouteux.datdouteux`
- `nomgrpaffaires` -> `subDimGrpaffaire.nomgrpaffaires`
- `pays` -> `subDimResidence.pays`
- `residence` -> `subDimResidence.residence`
- `geo` -> `subDimResidence.geo`
- `libelleagenteco` -> `subDimAgenteco.libelle`
- `sectionactivite` -> `subDimSectionactivite.libelle`
- `grpaffaire` -> `subDimGrpaffaire.nomgrpaffaires`
- `devise` -> `subDimDevise.devise`
- `datevalue` -> `subDimDate.dateValue`

#### Lowercase/legacy aliases
- `idclient` -> `idClient`
- `idcontrat` -> `idContrat`
- `idagence` -> `idAgence`
- `iddevise` -> `idDevise`
- `iddevisebnq` -> `idDevisebnq`
- `iddate` -> `idDate`
- `iddouteux` -> `dimClient.idDouteux`
- `anciennete_impaye` -> `dimContrat.ancienneteimpaye`

#### Dot-path aliases used in examples
- `c.numcompte` -> `subDimCompte.numcompte`
- `cl.iddouteux` -> `dimClient.idDouteux`
- `dc.actif` -> `dimContrat.actif`
- `dc.ancienneteimpaye` -> `dimContrat.ancienneteimpaye`
- `dc.anciennete_impaye` -> `dimContrat.ancienneteimpaye`

Notes:
- Field matching is case-insensitive (internal normalization to lowercase).
- Joins are auto-inferred from referenced fields in expression, where/filter, aggregation filters, groupBy, and orderBy.
- Runtime list endpoint: `GET /parameters/supported-fields` returns all currently supported keys and grouped keys by table from `FieldRegistry`.

## 8) SQL Generation Behavior

- Base FROM is always `datamart.fact_balance f`
- Joins are auto-added only when fields require them
- Join groups:
  - `COMPTE` -> `sub_dim_compte`
  - `CHAPITRE` -> `sub_dim_chapitre`
  - `FACT_AGENCE` -> fact-side `sub_dim_agence`
  - `FACT_DEVISE` -> fact-side `sub_dim_devise`
  - `FACT_DEVISE_BNQ` -> fact-side `sub_dim_devise` via `id_devisebnq`
  - `FACT_DATE` -> fact-side `sub_dim_date`
  - `CLIENT` -> `dim_client` plus client-related sub dimensions (`residence`, `agenteco`, `douteux`, `grpaffaire`, `sectionactivite`)
  - `CONTRAT` -> `dim_contrat` plus contract-related sub dimensions (`agence`, `devise`, `objetfinance`, `typcontrat`, `dateouverture`, `dateecheance`)
- WHERE is parameterized (`?`) to avoid raw value interpolation
- For `POST /parameters/{code}/execute/{date}`, compiler forces `LEFT JOIN datamart.sub_dim_date sdatef ON f.id_date = sdatef.id` and appends `sdatef.date_value = ?`
- Arithmetic division uses `NULLIF(..., 0)` protection
- groupBy expressions are selected with generated aliases
- ORDER BY supports registry fields and computed alias `value`
- `limit` compiles to SQL `LIMIT n`
- `top` is accepted in payload and compiled to SQL `LIMIT n` for PostgreSQL compatibility

## 9) Common API Errors and Causes

| HTTP | Message | Typical cause |
|---|---|---|
| 400 | `Request validation failed` | Missing `code`, `label`, or `formula` |
| 400 | `Formula validation failed` | Unknown field, bad operator token, type mismatch, empty filter group, too deep nesting |
| 400 | `Parameter config already exists for code` | Duplicate code on create |
| 400 | `Failed to convert value ... to LocalDate` | Invalid date path value for `/parameters/{code}/execute/{date}` (must be `YYYY-MM-DD`) |
| 404 | `Parameter config not found for code` | Missing or inactive code for compile/execute |
| 500 | `Request method 'GET' is not supported` | Calling `GET /parameters/{code}/execute` instead of POST |

## 10) Example Payload (ENCTENG)

```json
{
  "code": "ENCTENG",
  "label": "Encours total engagements",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "where": {
      "logic": "AND",
      "conditions": [
        {
          "field": "dc.actif",
          "operator": "=",
          "value": 1
        }
      ]
    }
  }
}
```

This compiles to a query equivalent to:
- SUM on `f.soldeconvertie`
- auto join `datamart.dim_contrat dc`
- filter `dc.actif = ?` with parameter `1`

### 10.1 Ranked grouped example (TOP)

```json
{
  "code": "TOP_AGENCE_ENC",
  "label": "Top agencies by total encours",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "groupBy": ["idAgence"],
    "orderBy": [
      {"field": "value", "direction": "DESC"}
    ],
    "top": 10
  }
}
```

Compiled SQL tail:
- `GROUP BY f.id_agence ORDER BY value DESC LIMIT 10`

## 11) Supported Scenario Matrix

### 11.1 Fact-only aggregations
- Example: `SUM(soldeconvertie)`
- Requires no additional join

### 11.2 Fact + dimensional filtering
- Example: `SUM(soldeconvertie)` with `dc.actif = 1`
- Automatically resolves required `CONTRAT` join tree

### 11.3 Fact + client risk segmentation
- Example A: `SUM(soldeconvertie)` with `subDimDouteux.douteux IN [1,2]`
- Example B: `SUM(soldeconvertie)` with alias `douteux = 1`
- Automatically resolves nested join chain `fact_balance -> dim_client -> sub_dim_douteux` using `cl.id_douteux = dout.id`

### 11.4 Sub-dimension lookups (compte/chapitre/devise/date/agence)
- Example: `subDimCompte.numcompte LIKE '13%'`
- Example: `subDimDeviseBnq.devise = 'EUR'`
- Example: `subDimDate.dateValue BETWEEN ['2026-01-01','2026-12-31']`

### 11.5 Aggregation with local filters
- Use `expression.type = AGGREGATION` and `filters` inside aggregation node
- Compiles as `CASE WHEN (...) THEN operand END` inside aggregate

### 11.6 Arithmetic expressions
- `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE` with numeric operands
- `DIVIDE` is guarded by SQL `NULLIF(right, 0)` and validation for literal zero

### 11.7 Nested global filter groups
- Recursive `groups` supported with `AND`/`OR`
- Max nesting depth for expressions and filters: 5

### 11.8 Grouped outputs
- Add top-level `groupBy`
- Execute endpoint returns row list for grouped queries, scalar value for non-grouped queries

### 11.9 Ranked outputs (ORDER BY + LIMIT/TOP)
- Add top-level `orderBy` with one or more sort expressions
- Add either `limit` or `top` to restrict row count
- Use `value` alias to sort by computed aggregate result
- Typical pattern: grouped aggregate + `ORDER BY value DESC` + `LIMIT/TOP N`

## 12) Postman Testing Cookbook

### 12.1 Postman setup

- Base URL: `http://localhost:8081`
- Create: `POST /parameters`
- Update: `PUT /parameters/{code}`
- Compile SQL: `GET /parameters/{code}/sql`
- Execute: `POST /parameters/{code}/execute`
- Execute at date: `POST /parameters/{code}/execute/{date}`
- Headers: `Content-Type: application/json`

Testing flow:
- Send `POST /parameters` with a payload below.
- If code already exists, send same payload to `PUT /parameters/{code}`.
- Verify SQL with `GET /parameters/{code}/sql`.
- Run query with `POST /parameters/{code}/execute`.
- Run query for a specific balance date with `POST /parameters/{code}/execute/2026-04-13`.

### 12.2 Top N (DESC)

```json
{
  "code": "ENT10_TOP_DESC",
  "label": "Top 10 exposition DESC",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "groupBy": ["idClient"],
    "orderBy": [
      { "field": "value", "direction": "DESC" }
    ],
    "top": 10
  }
}
```

Expected SQL tail:
- `GROUP BY f.id_client ORDER BY value DESC LIMIT 10`

### 12.3 Top N (ASC)

```json
{
  "code": "ENT10_TOP_ASC",
  "label": "Top 10 exposition ASC",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "groupBy": ["idClient"],
    "orderBy": [
      { "field": "value", "direction": "ASC" }
    ],
    "top": 10
  }
}
```

Expected SQL tail:
- `GROUP BY f.id_client ORDER BY value ASC LIMIT 10`

### 12.4 Limit N (DESC)

```json
{
  "code": "ENT10_LIMIT_DESC",
  "label": "Limit 10 exposition DESC",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "groupBy": ["idClient"],
    "orderBy": [
      { "field": "value", "direction": "DESC" }
    ],
    "limit": 10
  }
}
```

Expected SQL tail:
- `GROUP BY f.id_client ORDER BY value DESC LIMIT 10`

### 12.5 Limit N (ASC)

```json
{
  "code": "ENT10_LIMIT_ASC",
  "label": "Limit 10 exposition ASC",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "groupBy": ["idClient"],
    "orderBy": [
      { "field": "value", "direction": "ASC" }
    ],
    "limit": 10
  }
}
```

Expected SQL tail:
- `GROUP BY f.id_client ORDER BY value ASC LIMIT 10`

### 12.6 Filter operators quick tests

```json
{
  "code": "FILTER_IN_TEST",
  "label": "Filter IN test",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "where": {
      "logic": "AND",
      "conditions": [
        {
          "field": "douteux",
          "operator": "IN",
          "value": [1, 2]
        }
      ]
    }
  }
}
```

Other operator value examples:
- `"operator": "BETWEEN", "value": ["2026-01-01", "2026-12-31"]`
- `"operator": "LIKE", "value": "13%"`
- `"operator": "IS NULL"`
- `"operator": "GT", "value": 100`

### 12.7 Arithmetic expression test

```json
{
  "code": "ARITH_DIV_TEST",
  "label": "Arithmetic divide test",
  "isActive": true,
  "formula": {
    "expression": {
      "type": "DIVIDE",
      "left": {
        "type": "AGGREGATION",
        "function": "SUM",
        "field": "cumulmvtdb"
      },
      "right": {
        "type": "AGGREGATION",
        "function": "SUM",
        "field": "cumulmvtcr"
      }
    }
  }
}
```

### 12.8 Validation fail examples (expected HTTP 400)

`limit` and `top` together:

```json
{
  "code": "BAD_LIMIT_TOP",
  "label": "Invalid limit top",
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "orderBy": [{ "field": "value", "direction": "DESC" }],
    "limit": 10,
    "top": 5
  }
}
```

`top` without `orderBy`:

```json
{
  "code": "BAD_TOP_NO_ORDER",
  "label": "Invalid top without order",
  "formula": {
    "expression": {
      "type": "AGGREGATION",
      "function": "SUM",
      "field": "soldeconvertie"
    },
    "top": 10
  }
}
```
