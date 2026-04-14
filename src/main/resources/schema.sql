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
