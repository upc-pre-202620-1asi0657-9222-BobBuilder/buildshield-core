-- Monolito modular: ya no hay broker de mensajes entre unidades desplegables, así que el
-- Transactional Outbox y el registro de mensajes procesados dejan de existir.
-- idempotency_keys se mantiene (cabecera Idempotency-Key del navegador).
DROP TABLE IF EXISTS outbox;
DROP TABLE IF EXISTS processed_messages;
