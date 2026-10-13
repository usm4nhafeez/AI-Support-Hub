-- Bootstrap for the pgvector profile. Idempotent: it runs on every start and is
-- safe against a database that is already set up.
-- Spring Boot executes this before Hibernate's ddl-auto=update and before the
-- vector store creates its table, so the types below exist when they are used.
--
-- Statements end in ";;" here, matching spring.sql.init.separator in
-- application-pgvector.properties. PostgreSQL has no "CREATE DOMAIN IF NOT
-- EXISTS", so the domain needs a guarded block, and a guarded block contains
-- ordinary semicolons that must not be mistaken for statement ends.

-- Types and functions the vector store needs.
CREATE EXTENSION IF NOT EXISTS vector;;
CREATE EXTENSION IF NOT EXISTS hstore;;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";;

-- The entities declare columnDefinition = "CLOB" for the long-text columns
-- (message bodies, audit payloads, tool request and response). That literal is
-- dialect-independent raw DDL, so Hibernate writes "CLOB" against PostgreSQL too
-- and there is no such type there. Declaring a domain named clob over text makes
-- the name resolve, keeps the Oracle mapping untouched, and stores real text with
-- no length limit.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_type t
        JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        WHERE t.typname = 'clob'
          AND n.nspname = current_schema()
    ) THEN
        CREATE DOMAIN clob AS text;
    END IF;
END
$$;;
