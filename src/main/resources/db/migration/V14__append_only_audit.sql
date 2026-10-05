CREATE TABLE audit.events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(64),
    operation_id UUID,
    details JSONB NOT NULL
);
CREATE INDEX idx_audit_events_history ON audit.events (organization_id, resource_type, resource_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_events_organization ON audit.events (organization_id, occurred_at DESC, id DESC);

CREATE FUNCTION public.reject_history_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El historial solo admite anexar registros' USING ERRCODE = '42501';
END;
$$;
CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit.events
    FOR EACH ROW EXECUTE FUNCTION public.reject_history_mutation();
CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE ON audit.events
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_history_mutation();
CREATE TRIGGER stock_movements_immutable BEFORE UPDATE OR DELETE ON inventory.stock_movements
    FOR EACH ROW EXECUTE FUNCTION public.reject_history_mutation();
CREATE TRIGGER stock_movements_no_truncate BEFORE TRUNCATE ON inventory.stock_movements
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_history_mutation();
REVOKE UPDATE, DELETE, TRUNCATE ON audit.events, inventory.stock_movements FROM PUBLIC;
