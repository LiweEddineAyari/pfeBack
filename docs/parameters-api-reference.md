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
- `PUT /parameters/{code}`: update configuration by code
- `GET /parameters/{code}`: fetch configuration
- `GET /parameters/{code}/sql`: compile formula to SQL
- `POST /parameters/{code}/execute`: execute compiled SQL
- `GET /parameters/supported-fields`: list all supported filter/expression fields

Important: execute endpoint is **POST**, not GET.

## 2) Request Body Contract (POST /parameters)

### Top-level attributes

| Attribute | Required | Type | Allowed values / rules | Default |
|---|---|---|---|---|
| `code` | Yes | string | Non-empty (`@NotBlank`), unique in DB, trimmed before save. Persisted to `mapping.parameters_config.code` (length 50). | None |
| `label` | Yes | string | Non-empty (`@NotBlank`), trimmed before save. Persisted with max length 255. | None |
| `isActive` | No | boolean | `true` or `false`. | `true` if omitted |
| `formula` | Yes | JSON object | Must parse into engine AST and pass validation. | None |

Notes:
- Persistence defaults: `version=1`, `createdAt` and `updatedAt` auto-populated.
- Request contract is DTO-based (`code`, `label`, `formula`, `isActive`). Do not rely on sending DB fields like `id`, `version`, `createdAt`, `updatedAt`.

## 3) Formula Object Shapes

The parser accepts 2 root shapes:

### Shape A (recommended wrapper)

```json
{
  "expression": { ...expression node... },
  "where": { ...filter group... },
  "groupBy": ["field1", "field2"]
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
- Execute endpoint returns list rows (`queryForList`) when groupBy is present
- Without groupBy, execute returns scalar (`queryForObject`)

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
- Joins are auto-inferred from referenced fields in expression, where/filter, aggregation filters, and groupBy.
- Runtime list endpoint: `GET /parameters/supported-fields` returns all currently supported keys from `FieldRegistry`.

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
- Arithmetic division uses `NULLIF(..., 0)` protection
- groupBy expressions are selected with generated aliases

## 9) Common API Errors and Causes

| HTTP | Message | Typical cause |
|---|---|---|
| 400 | `Request validation failed` | Missing `code`, `label`, or `formula` |
| 400 | `Formula validation failed` | Unknown field, bad operator token, type mismatch, empty filter group, too deep nesting |
| 400 | `Parameter config already exists for code` | Duplicate code on create |
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
