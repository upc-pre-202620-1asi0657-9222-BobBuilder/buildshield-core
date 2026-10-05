package pe.buildshield.core.ordering.interfaces.rest;

/** Ejemplos de respuesta y de parámetros para la documentación OpenAPI del módulo ordering. */
final class OrderApiExamples {

    static final String ORDER_ID = "6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c";

    private static final String LINES = """
              "lines": [
                {
                  "materialId": "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a",
                  "sku": "CEM-001",
                  "unit": "BAG",
                  "requested": 50,
                  "dispatched": 0,
                  "cancelled": 0,
                  "received": 0,
                  "pending": 50
                }
              ]
            }""";

    static final String ORDER_REGISTERED = """
            {
              "id": "6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c",
              "worksiteId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
              "warehouseId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
              "requestedBy": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
              "status": "REGISTERED",
              "statusLabel": "Registrado",
              "notes": "Vaciado de losa del piso 3",
              "rejectionReason": null,
              "placedAt": "2026-11-02T15:00:00Z",
              "decidedBy": null,
              "decidedAt": null,
            """ + LINES;

    static final String ORDERS = "[" + ORDER_REGISTERED + "]";

    static final String ORDER_APPROVED = """
            {
              "id": "6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c",
              "worksiteId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
              "warehouseId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
              "requestedBy": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
              "status": "IN_REVIEW",
              "statusLabel": "EnRevision",
              "notes": "Vaciado de losa del piso 3",
              "rejectionReason": null,
              "placedAt": "2026-11-02T15:00:00Z",
              "decidedBy": "9c2d3e4f-5a6b-4c7d-8e9f-a0b1c2d3e4f5",
              "decidedAt": "2026-11-02T16:20:00Z",
            """ + LINES;

    static final String ORDER_REJECTED = """
            {
              "id": "6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c",
              "worksiteId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
              "warehouseId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
              "requestedBy": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
              "status": "CANCELLED",
              "statusLabel": "Cancelado",
              "notes": "Vaciado de losa del piso 3",
              "rejectionReason": "No hay transporte disponible esta semana",
              "placedAt": "2026-11-02T15:00:00Z",
              "decidedBy": "9c2d3e4f-5a6b-4c7d-8e9f-a0b1c2d3e4f5",
              "decidedAt": "2026-11-02T16:20:00Z",
            """ + LINES;

    private OrderApiExamples() {
    }
}
