-- Create staging schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS staging;

-- Create datamart schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS datamart;

-- Create mapping schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS mapping;

-- Create dashboard schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS dashboard;

-- Mapping configuration table for source/target column mapping rules
CREATE TABLE IF NOT EXISTS mapping.mapping_config (
	id BIGSERIAL PRIMARY KEY,
	table_source VARCHAR(255) NOT NULL,
	table_target VARCHAR(255) NOT NULL,
	column_source VARCHAR(255) NOT NULL,
	column_target VARCHAR(255) NOT NULL,
	configgroupnumber INTEGER NOT NULL
);

-- Parameterized formula configuration table (AST JSON driven SQL engine)
CREATE TABLE IF NOT EXISTS mapping.parameters_config (
	id BIGSERIAL PRIMARY KEY,
	code VARCHAR(50) UNIQUE NOT NULL,
	label VARCHAR(255) NOT NULL,
	formula_json JSONB NULL,
	version INT DEFAULT 1,
	is_active BOOLEAN DEFAULT TRUE,
	created_at TIMESTAMP DEFAULT NOW(),
	updated_at TIMESTAMP DEFAULT NOW()
);

-- Ratios category lookup table
CREATE TABLE IF NOT EXISTS mapping.categorie_ratios (
	id BIGSERIAL PRIMARY KEY,
	name VARCHAR(255) UNIQUE NOT NULL
);



-- Ratios family lookup table
CREATE TABLE IF NOT EXISTS mapping.famille_ratios (
	id BIGSERIAL PRIMARY KEY,
	name VARCHAR(255) UNIQUE NOT NULL
);



-- Dynamic financial ratios configuration table
CREATE TABLE IF NOT EXISTS mapping.ratios_config (
	id BIGSERIAL PRIMARY KEY,
	code VARCHAR(50) UNIQUE NOT NULL,
	label VARCHAR(255) NOT NULL,
	famille_id BIGINT NOT NULL,
	categorie_id BIGINT NOT NULL,
	formula_json JSONB NOT NULL,
	seuil_tolerance DOUBLE PRECISION NULL,
	seuil_alerte DOUBLE PRECISION NULL,
	seuil_appetence DOUBLE PRECISION NULL,
	description VARCHAR(2000) NULL,
	version INT DEFAULT 1,
	is_active BOOLEAN DEFAULT TRUE,
	created_at TIMESTAMP DEFAULT NOW(),
	updated_at TIMESTAMP DEFAULT NOW()
);

-- Ratios values persisted by reference date for dashboarding
CREATE TABLE IF NOT EXISTS dashboard.dashboard (
	id BIGSERIAL PRIMARY KEY,
	id_ratios BIGINT NOT NULL,
	ratios_value DOUBLE PRECISION NOT NULL,
	reference_date DATE NOT NULL,
	created_at TIMESTAMP DEFAULT NOW(),
	CONSTRAINT fk_dashboard_id_ratios
		FOREIGN KEY (id_ratios) REFERENCES mapping.ratios_config(id) ON DELETE CASCADE,
	CONSTRAINT uq_dashboard_ratio_date UNIQUE (id_ratios, reference_date)
);

CREATE INDEX IF NOT EXISTS idx_dashboard_reference_date
	ON dashboard.dashboard(reference_date);

CREATE INDEX IF NOT EXISTS idx_dashboard_id_ratios
	ON dashboard.dashboard(id_ratios);

-- Backward-compatible migration from legacy text columns to foreign-key ids
ALTER TABLE mapping.ratios_config
	ADD COLUMN IF NOT EXISTS famille_id BIGINT;

ALTER TABLE mapping.ratios_config
	ADD COLUMN IF NOT EXISTS categorie_id BIGINT;

ALTER TABLE mapping.ratios_config
	ALTER COLUMN famille_id TYPE BIGINT USING (
		CASE
			WHEN BTRIM(CAST(famille_id AS TEXT)) ~ '^[0-9]+$'
				THEN CAST(BTRIM(CAST(famille_id AS TEXT)) AS BIGINT)
			ELSE NULL
		END
	);

ALTER TABLE mapping.ratios_config
	ALTER COLUMN categorie_id TYPE BIGINT USING (
		CASE
			WHEN BTRIM(CAST(categorie_id AS TEXT)) ~ '^[0-9]+$'
				THEN CAST(BTRIM(CAST(categorie_id AS TEXT)) AS BIGINT)
			ELSE NULL
		END
	);

UPDATE mapping.ratios_config r
SET famille_id = fr.id
FROM mapping.famille_ratios fr
WHERE r.famille_id IS NULL
	AND LOWER(BTRIM(COALESCE(TO_JSONB(r) ->> 'famille', ''))) = LOWER(BTRIM(fr.name));

UPDATE mapping.ratios_config r
SET categorie_id = cr.id
FROM mapping.categorie_ratios cr
WHERE r.categorie_id IS NULL
	AND LOWER(BTRIM(COALESCE(TO_JSONB(r) ->> 'categorie', ''))) = LOWER(BTRIM(cr.name));

UPDATE mapping.ratios_config r
SET famille_id = fr.id
FROM mapping.famille_ratios fr
WHERE r.famille_id IS NULL
	AND fr.name = 'Indicateurs de solidité financière';

UPDATE mapping.ratios_config r
SET categorie_id = cr.id
FROM mapping.categorie_ratios cr
WHERE r.categorie_id IS NULL
	AND cr.name = 'Ratios Prudentiels';

ALTER TABLE mapping.ratios_config
	DROP CONSTRAINT IF EXISTS fk_ratios_config_famille;

ALTER TABLE mapping.ratios_config
	DROP CONSTRAINT IF EXISTS fk_ratios_config_categorie;

ALTER TABLE mapping.ratios_config
	ADD CONSTRAINT fk_ratios_config_famille
		FOREIGN KEY (famille_id) REFERENCES mapping.famille_ratios(id);

ALTER TABLE mapping.ratios_config
	ADD CONSTRAINT fk_ratios_config_categorie
		FOREIGN KEY (categorie_id) REFERENCES mapping.categorie_ratios(id);

ALTER TABLE mapping.ratios_config
	ALTER COLUMN famille_id SET NOT NULL;

ALTER TABLE mapping.ratios_config
	ALTER COLUMN categorie_id SET NOT NULL;

ALTER TABLE mapping.ratios_config
	DROP COLUMN IF EXISTS famille;

ALTER TABLE mapping.ratios_config
	DROP COLUMN IF EXISTS categorie;

-- =============================================================================
-- AI Module: chat-memory tables (ai schema).
-- NOTE: pgvector extension + rag.documents table are created programmatically
-- by AiSchemaInitializer so that a missing pgvector installation degrades
-- gracefully (FTS-only RAG) instead of crashing the whole application.
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS ai;
CREATE SCHEMA IF NOT EXISTS rag;

-- One session = one chat conversation with a title.
CREATE TABLE IF NOT EXISTS ai.chat_sessions (
	id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	user_id         VARCHAR(100) NOT NULL,
	title           TEXT,
	status          VARCHAR(20) DEFAULT 'ACTIVE',
	created_at      TIMESTAMP DEFAULT now(),
	updated_at      TIMESTAMP DEFAULT now(),
	last_message_at TIMESTAMP
);

-- All messages across all sessions. Holds USER, AI and TOOL_EXECUTION rows.
CREATE TABLE IF NOT EXISTS ai.chat_messages (
	id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	session_id  UUID NOT NULL REFERENCES ai.chat_sessions(id) ON DELETE CASCADE,
	role        VARCHAR(20) NOT NULL,
	content     TEXT NOT NULL,
	tool_name   VARCHAR(100),
	tool_input  JSONB,
	tool_output JSONB,
	tokens_used INTEGER,
	sequence_no BIGINT NOT NULL DEFAULT 0,
	created_at  TIMESTAMP DEFAULT now()
);

-- Periodic summaries to compress long sessions.
CREATE TABLE IF NOT EXISTS ai.chat_summaries (
	id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	session_id  UUID NOT NULL REFERENCES ai.chat_sessions(id) ON DELETE CASCADE,
	summary     TEXT NOT NULL,
	turn_count  INTEGER,
	created_at  TIMESTAMP DEFAULT now()
);

-- Financial entities extracted during a conversation (analytics / replay).
CREATE TABLE IF NOT EXISTS ai.extracted_entities (
	id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	session_id    UUID REFERENCES ai.chat_sessions(id) ON DELETE CASCADE,
	entity_type   VARCHAR(50),
	entity_value  VARCHAR(200),
	created_at    TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sessions_user
	ON ai.chat_sessions (user_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_session
	ON ai.chat_messages (session_id, sequence_no);
