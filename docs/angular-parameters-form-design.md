# Angular Parameters Form Design (Popup Card + Smart Formula + Native SQL)

Generated: 2026-04-18

## 1) Goal

Design a complete Angular frontend experience to:
- List parameters
- Create a parameter in a popup card
- View parameter details in a popup card
- Edit parameter from details popup
- Toggle form mode from formula builder to native SQL input from a top-right icon button
- In SQL view for an existing parameter, fetch generated SQL from API using parameter code

This document is implementation-ready for frontend teams.

## 2) Current Backend API Contract (Existing)

Base URL: http://localhost:8081

### Existing endpoints

| Purpose | Method | Endpoint | Request body | Response |
|---|---|---|---|---|
| Create parameter | POST | /parameters | FormulaRequestDTO | ParameterConfigResponseDTO (201) |
| List parameters | GET | /parameters | None | ParameterConfigResponseDTO[] |
| Get parameter by id | GET | /parameters/id/{id} | None | ParameterConfigResponseDTO |
| Get parameter by code (explicit) | GET | /parameters/code/{code} | None | ParameterConfigResponseDTO |
| Update parameter | PUT | /parameters/{code} | FormulaRequestDTO | ParameterConfigResponseDTO |
| Delete one parameter | DELETE | /parameters/code/{code} | None | 204 No Content |
| Delete many parameters | DELETE | /parameters | string[] (codes) | BulkDeleteResponseDTO |
| Get parameter by code (legacy alias) | GET | /parameters/{code} | None | ParameterConfigResponseDTO |
| Compile formula to SQL | GET | /parameters/{code}/sql | None | FormulaSqlResponseDTO |
| Execute parameter | POST | /parameters/{code}/execute | None | FormulaExecutionResponseDTO |
| Execute parameter at date | POST | /parameters/{code}/execute/{date} | None | FormulaExecutionResponseDTO |
| Supported fields | GET | /parameters/supported-fields | None | SupportedFieldsResponseDTO |

Important:
- Execute endpoint is POST, not GET.
- If UI sends GET /parameters/{code}/execute, backend returns method not supported.

### List API note

List API now exists as `GET /parameters` and returns `ParameterConfigResponseDTO[]`.

If your UI needs server-side pagination/filtering for large volumes, consider adding a paged variant later:
- GET /parameters?page=0&size=20&search=MTEP

## 3) DTOs to Use in Angular

## 3.1 Request DTO

```ts
export interface FormulaRequestDTO {
  code: string;
  label: string;
  formula?: FormulaJson;      // required if nativeSql is empty
  nativeSql?: string;         // required if formula is empty
  isActive?: boolean;
}
```

Backend validation rule:
- Either formula or nativeSql is required.

## 3.2 Response DTO (parameter)

```ts
export interface ParameterConfigResponseDTO {
  id: number;
  code: string;
  label: string;
  formula: FormulaJson;
  version: number;
  isActive: boolean;
  createdAt: string; // ISO datetime
  updatedAt: string; // ISO datetime
}
```

## 3.3 Response DTO (compiled SQL)

```ts
export interface FormulaSqlResponseDTO {
  code: string;
  version: number;
  sql: string;
  parameters: unknown[];
  referencedFields: string[];
  joins: string[];
  groupByFields: string[];
  orderBy: Array<{ field: string; direction: 'ASC' | 'DESC' }>;
  limit?: number | null;
  top?: number | null;
}
```

## 3.4 Response DTO (execute)

```ts
export interface FormulaExecutionResponseDTO {
  code: string;
  sql: string;
  parameters: unknown[];
  referenceDate?: string | null; // YYYY-MM-DD
  value: unknown;                // scalar or row array
}
```

## 3.5 Error DTO

```ts
export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;      // ex: INVALID_SQL, Bad Request, etc.
  message: string;
  details: string[];
  path: string;
}

export interface SupportedFieldsResponseDTO {
  fields: string[];
  fieldsByTable: Record<string, string[]>;
}

export interface BulkDeleteResponseDTO {
  requestedCount: number;
  deletedCount: number;
  deletedCodes: string[];
  missingCodes: string[];
}
```

## 4) Formula JSON Model (Frontend Types)

```ts
export type FormulaNodeType =
  | 'FIELD'
  | 'VALUE'
  | 'AGGREGATION'
  | 'ADD'
  | 'SUBTRACT'
  | 'MULTIPLY'
  | 'DIVIDE';

export type AggregationFunction = 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX';

export type FilterLogic = 'AND' | 'OR';

export type FilterOperator =
  | '=' | '!=' | '<>' | '>' | '>=' | '<' | '<='
  | 'LIKE' | 'IN' | 'NOT IN' | 'BETWEEN'
  | 'IS NULL' | 'IS NOT NULL'
  | 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'NOT_IN' | 'IS_NULL' | 'IS_NOT_NULL';

export interface FormulaNode {
  type: FormulaNodeType;
  field?: string;
  value?: unknown;
  function?: AggregationFunction;
  expression?: FormulaNode;
  left?: FormulaNode;
  right?: FormulaNode;
  distinct?: boolean;
  filters?: FilterGroup;
}

export interface FilterCondition {
  field: string;
  operator: FilterOperator;
  value?: unknown; // scalar, array, or omitted for IS NULL / IS NOT NULL
}

export interface FilterGroup {
  logic?: FilterLogic;
  conditions?: FilterCondition[];
  groups?: FilterGroup[];
}

export interface FormulaJson {
  expression: FormulaNode;
  where?: FilterGroup;
  filter?: FilterGroup;
  filters?: FilterGroup;
  groupBy?: string[];
  orderBy?: Array<string | { field: string; direction?: 'ASC' | 'DESC' }>;
  limit?: number;
  top?: number;
}
```

## 5) UI Design (Popup Card Workflow)

## 5.1 Main page: Parameters List

Layout:
- Header: title + New Parameter button
- Optional search input by code or label
- Data table columns:
  - code
  - label
  - version
  - isActive
  - updatedAt
  - actions (View)

Behavior:
- Click row or View button opens details popup
- New Parameter opens create popup

## 5.2 Details popup card

Sections:
- Header: code, label, status chip
- Read-only summary fields
- Tabs:
  - Formula JSON (pretty view)
  - Generated SQL preview
  - Last execute result preview (optional)

Actions:
- Edit button switches to editable form popup state
- Close button

When switching details tab to SQL:
- Call GET /parameters/{code}/sql
- Show sql, parameters, joins, referenced fields

## 5.3 Create/Edit popup card

Header area:
- Title: Create Parameter or Edit Parameter
- Top-right icon button to toggle mode:
  - formula mode icon: tune or account_tree
  - SQL mode icon: code

Body:
- Common fields: code, label, isActive
- Mode area:
  - Formula mode: smart expression/filter/group/order builder
  - SQL mode: nativeSql textarea + SQL hints

Footer:
- Cancel
- Validate (optional dry-run)
- Save

## 6) Intelligent Form Behavior (Required)

## 6.1 Mode toggle logic

- Create mode default: Formula mode
- If user toggles to SQL mode:
  - hide formula builder
  - show nativeSql textarea
- If user toggles back to Formula mode:
  - restore latest formula draft

Edit mode:
- Load parameter via GET /parameters/{code}
- Formula mode shows saved formula JSON
- On SQL toggle in edit/details:
  - call GET /parameters/{code}/sql
  - show returned SQL preview
  - keep formula draft unchanged

Note:
- Compiled SQL is generated from saved formula.
- It may not be identical to original nativeSql text typed at creation.

## 6.2 Smart validators

Top-level:
- code required
- label required
- either formula or nativeSql required

Formula rules:
- expression.type required
- FIELD node requires field
- VALUE node requires value
- AGGREGATION node requires function
- AGGREGATION requires field OR expression (not both)
- ADD/SUBTRACT/MULTIPLY/DIVIDE require left and right
- limit and top cannot both be set
- limit > 0, top > 0
- if limit or top is set, orderBy is required

Filter rules:
- group cannot be empty
- IN / NOT IN require non-empty array
- BETWEEN requires exactly 2 values
- IS NULL / IS NOT NULL must not require value input

Order/group rules:
- direction in ASC, DESC
- orderBy field may be value alias

## 6.3 Field suggestion intelligence

At popup open:
- call GET /parameters/supported-fields
- populate autocomplete for:
  - expression field
  - filter field
  - groupBy fields
  - orderBy fields

For operator-specific input:
- Operator IN => chips/multi-value editor
- Operator BETWEEN => two-value editor
- Operator IS NULL or IS NOT NULL => value editor hidden

## 7) Angular Component Architecture

Recommended components:

- ParametersPageComponent
  - owns table and open-dialog actions
- ParameterDetailsDialogComponent
  - read-only details and SQL tab
- ParameterFormDialogComponent
  - create/edit + mode toggle + save
- FormulaBuilderComponent
  - recursive node builder
- FilterGroupBuilderComponent
  - recursive filter group editor
- SqlEditorComponent
  - nativeSql textarea + syntax hint

Services:

- ParametersApiService
- ParameterFormMapperService
- ParameterFormValidationService

State model:

```ts
type FormMode = 'FORMULA' | 'SQL';

interface ParameterFormState {
  mode: FormMode;
  isEdit: boolean;
  originalCode?: string;
  formulaDraft?: FormulaJson;
  nativeSqlDraft?: string;
  compiledSqlPreview?: FormulaSqlResponseDTO;
}
```

## 8) API Service Methods (Angular)

```ts
list(params?: { page?: number; size?: number; search?: string }) {
  return this.http.get<ParameterConfigResponseDTO[]>('/parameters');
}

getById(id: number) {
  return this.http.get<ParameterConfigResponseDTO>(`/parameters/id/${id}`);
}

getByCode(code: string) {
  return this.http.get<ParameterConfigResponseDTO>(`/parameters/code/${code}`);
}

create(payload: FormulaRequestDTO) {
  return this.http.post<ParameterConfigResponseDTO>('/parameters', payload);
}

update(code: string, payload: FormulaRequestDTO) {
  return this.http.put<ParameterConfigResponseDTO>(`/parameters/${code}`, payload);
}

compileSql(code: string) {
  return this.http.get<FormulaSqlResponseDTO>(`/parameters/${code}/sql`);
}

execute(code: string) {
  return this.http.post<FormulaExecutionResponseDTO>(`/parameters/${code}/execute`, {});
}

executeAtDate(code: string, date: string) {
  return this.http.post<FormulaExecutionResponseDTO>(`/parameters/${code}/execute/${date}`, {});
}

supportedFields() {
  return this.http.get<SupportedFieldsResponseDTO>(`/parameters/supported-fields`);
}

deleteByCode(code: string) {
  return this.http.delete<void>(`/parameters/code/${code}`);
}

deleteMany(codes: string[]) {
  return this.http.request<BulkDeleteResponseDTO>('DELETE', '/parameters', { body: codes });
}
```

## 9) Form Field Documentation (for UI and QA)

| UI field | Type | Required | Visible in mode | Rule |
|---|---|---|---|---|
| code | text | yes | formula + sql | unique, not blank |
| label | text | yes | formula + sql | not blank |
| isActive | toggle | no | formula + sql | default true |
| nativeSql | textarea | conditional | sql only | required if formula empty |
| expression.type | select | yes | formula only | FIELD VALUE AGGREGATION ADD SUBTRACT MULTIPLY DIVIDE |
| expression.field | autocomplete | conditional | formula only | required for FIELD and some AGGREGATION |
| expression.value | dynamic input | conditional | formula only | required for VALUE |
| aggregation.function | select | conditional | formula only | SUM AVG COUNT MIN MAX |
| where.logic | select | no | formula only | AND OR |
| filter condition field | autocomplete | conditional | formula only | from supported fields |
| filter condition operator | select | conditional | formula only | depends on condition |
| filter condition value | dynamic input | conditional | formula only | shape by operator |
| groupBy | chip list | no | formula only | fields list |
| orderBy | array rows | no | formula only | field + direction |
| limit | number | no | formula only | positive, exclusive with top |
| top | number | no | formula only | positive, exclusive with limit |

## 10) Request Payload Examples

## 10.1 Formula mode create

```json
{
  "code": "MTEP_FORM_01",
  "label": "Total engagement by formula",
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
          "field": "grpaffaire",
          "operator": "=",
          "value": "PERSONNEL"
        }
      ]
    },
    "orderBy": [
      { "field": "value", "direction": "DESC" }
    ],
    "top": 10
  }
}
```

## 10.2 SQL mode create

```json
{
  "code": "MTEP_SQL_01",
  "label": "Total engagement by SQL",
  "isActive": true,
  "nativeSql": "SELECT SUM(f.soldeconvertie) FROM datamart.fact_balance f JOIN datamart.dim_client dc ON f.id_client = dc.idtiers WHERE dc.grpaffaire = 'PERSONNEL'"
}
```

## 11) Details Popup Action Flows

## 11.1 View details
- Open popup from row click
- Call GET /parameters/{code}
- Show formula and metadata

## 11.2 Toggle SQL in details/edit
- Call GET /parameters/{code}/sql
- Show:
  - sql text
  - parameters
  - joins
  - referencedFields

## 11.3 Execute from details
- Run POST /parameters/{code}/execute
- Show current result preview

## 11.4 Execute at date
- Date picker + POST /parameters/{code}/execute/{date}
- Show date-scoped result

## 12) Error Handling Rules

If API returns ApiErrorResponse:
- Show message as banner
- Show details as bullet list
- Keep user draft values in form

Examples:
- INVALID_SQL: show SQL section with highlighted hint
- Formula validation failed: show field-level mapping if possible

## 13) Recommended UX Microcopy

- Toggle button tooltip in formula mode: Switch to Native SQL
- Toggle button tooltip in SQL mode: Switch to Formula Builder
- SQL helper text: Only analytical SELECT subset is accepted
- Save success: Parameter saved successfully
- Save error: Save failed, check validation details

## 14) Documentation Pack Needed for This Formulaire

Keep these files in frontend repository:

1. Functional spec
   - This document
2. API contract summary
   - Endpoint table + DTO interfaces
3. Validation matrix
   - Field-by-field rules + conditional logic
4. UX states
   - Empty, loading, success, error, no access
5. QA test cases
   - Formula mode, SQL mode, toggle behavior, error mapping, edit flow
6. Postman collection
   - Create, update, get, compile SQL, execute, execute at date

## 15) Optional Backend Enhancements (for full UX)

To fully support list page and richer details:

- Optionally add a paged list variant: GET /parameters?page=0&size=20&search=MTEP
- Optionally add endpoint to return both formula and original nativeSql (if you decide to persist original SQL text)

---

If you want, next step is I can generate an Angular starter implementation document with:
- exact Reactive Form builder code
- Angular Material dialog templates
- full service layer and mapping functions
- UI state management with signals or NgRx
