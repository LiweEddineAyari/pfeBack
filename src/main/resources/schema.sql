-- Create staging schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS staging;

-- Create datamart schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS datamart;

-- Mapping configuration table for source/target column mapping rules
CREATE TABLE IF NOT EXISTS staging.mapping_config (
	id BIGSERIAL PRIMARY KEY,
	table_source VARCHAR(255) NOT NULL,
	table_target VARCHAR(255) NOT NULL,
	column_source VARCHAR(255) NOT NULL,
	column_target VARCHAR(255) NOT NULL,
	configgroupnumber INTEGER NOT NULL
);
