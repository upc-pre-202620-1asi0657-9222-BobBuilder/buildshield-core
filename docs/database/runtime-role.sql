-- Aplicar como dueño/migrador. El rol de ejecución debe existir y no ser superusuario ni dueño.
-- psql ... -v app_role=buildshield_app -f docs/database/runtime-role.sql
\if :{?app_role}
BEGIN;
GRANT USAGE ON SCHEMA public, iam, organization, inventory, ordering, dispatch, reception, audit TO :"app_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA iam, organization, inventory, ordering, dispatch, reception TO :"app_role";
GRANT SELECT, INSERT, UPDATE ON public.idempotency_keys TO :"app_role";
GRANT SELECT, INSERT ON audit.events, inventory.stock_movements TO :"app_role";
REVOKE UPDATE, DELETE, TRUNCATE ON audit.events, inventory.stock_movements FROM :"app_role";
REVOKE CREATE ON SCHEMA public, iam, organization, inventory, ordering, dispatch, reception, audit FROM :"app_role";
COMMIT;
\else
\echo 'Se requiere -v app_role=nombre_del_rol_de_ejecucion'
\quit 1
\endif
