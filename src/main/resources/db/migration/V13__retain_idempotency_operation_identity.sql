-- Las respuestas vencen; la identidad confirmada no se elimina ni se reutiliza.
ALTER TABLE idempotency_keys
    ADD COLUMN user_id UUID,
    ADD COLUMN actor_role VARCHAR(50),
    ADD COLUMN request_fingerprint VARCHAR(64),
    ADD COLUMN response_location VARCHAR(500),
    ADD COLUMN response_expired BOOLEAN NOT NULL DEFAULT FALSE;
-- Las columnas históricas quedan nulas: no se inventa el actor ni el contenido anterior.
