-- Create staging schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS staging;

-- Create datamart schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS datamart;

-- Create mapping schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS mapping;

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

-- Dynamic financial ratios configuration table
CREATE TABLE IF NOT EXISTS mapping.ratios_config (
	id BIGSERIAL PRIMARY KEY,
	code VARCHAR(50) UNIQUE NOT NULL,
	label VARCHAR(255) NOT NULL,
	famille VARCHAR(255) NOT NULL,
	categorie VARCHAR(255) NOT NULL,
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

-- Backward-compatible migration from old famille_id model
ALTER TABLE mapping.ratios_config
	ADD COLUMN IF NOT EXISTS famille VARCHAR(255);

ALTER TABLE mapping.ratios_config
	ADD COLUMN IF NOT EXISTS categorie VARCHAR(255);

ALTER TABLE mapping.ratios_config
	DROP CONSTRAINT IF EXISTS fk_ratios_config_famille;

-- Spring SQL init splits statements by ';', so avoid PL/pgSQL DO blocks here.
-- This expression safely reads legacy famille_id when present and falls back otherwise.
UPDATE mapping.ratios_config r
SET famille = COALESCE(r.famille, to_jsonb(r) ->> 'famille_id', 'UNSPECIFIED')
WHERE r.famille IS NULL;

ALTER TABLE mapping.ratios_config
	DROP COLUMN IF EXISTS famille_id;

UPDATE mapping.ratios_config
SET categorie = COALESCE(categorie, 'GENERAL')
WHERE categorie IS NULL;

ALTER TABLE mapping.ratios_config
	ALTER COLUMN famille SET NOT NULL;

ALTER TABLE mapping.ratios_config
	ALTER COLUMN categorie SET NOT NULL;
