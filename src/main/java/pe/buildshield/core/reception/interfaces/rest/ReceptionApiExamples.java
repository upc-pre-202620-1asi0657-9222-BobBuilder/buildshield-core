package pe.buildshield.core.reception.interfaces.rest;

/** Ejemplos de respuesta y de parámetros para la documentación OpenAPI del módulo reception. */
final class ReceptionApiExamples {

    static final String RECEPTION_ID = "8b9c0d1e-2f3a-4b4c-8d5e-6f7a8b9c0d1e";
    static final String LINE_ID = "9c0d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f";

    private static final String HEAD = """
            {
              "id": "8b9c0d1e-2f3a-4b4c-8d5e-6f7a8b9c0d1e",
              "dispatchId": "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f",
              "orderId": "6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c",
              "warehouseId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
              "worksiteId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
            """;

    static final String RECEPTION_STARTED = HEAD + """
              "status": "IN_PROGRESS",
              "statusLabel": "EnCurso",
              "confirmedBy": null,
              "confirmedAt": null,
              "lines": [
                {
                  "id": "9c0d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f",
                  "dispatchLineId": "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b",
                  "orderLineId": "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d",
                  "materialId": "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a",
                  "dispatchedQty": 30.000,
                  "receivedQty": null,
                  "shrinkagePercent": null
                }
              ]
            }""";

    static final String RECEPTION_RECORDED = HEAD + """
              "status": "IN_PROGRESS",
              "statusLabel": "EnCurso",
              "confirmedBy": null,
              "confirmedAt": null,
              "lines": [
                {
                  "id": "9c0d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f",
                  "dispatchLineId": "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b",
                  "orderLineId": "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d",
                  "materialId": "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a",
                  "dispatchedQty": 30.000,
                  "receivedQty": 29.500,
                  "shrinkagePercent": 1.67
                }
              ]
            }""";

    static final String RECEPTION_CONFIRMED = HEAD + """
              "status": "CONFIRMED",
              "statusLabel": "Confirmada",
              "confirmedBy": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
              "confirmedAt": "2026-11-03T16:30:00Z",
              "lines": [
                {
                  "id": "9c0d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f",
                  "dispatchLineId": "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b",
                  "orderLineId": "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d",
                  "materialId": "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a",
                  "dispatchedQty": 30.000,
                  "receivedQty": 29.500,
                  "shrinkagePercent": 1.67
                }
              ]
            }""";

    static final String COMPARISON = """
            {
              "receptionId": "8b9c0d1e-2f3a-4b4c-8d5e-6f7a8b9c0d1e",
              "dispatchId": "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f",
              "orderId": "6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b9c",
              "status": "IN_PROGRESS",
              "statusLabel": "EnCurso",
              "complete": true,
              "withinTolerance": true,
              "lines": [
                {
                  "lineId": "9c0d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f",
                  "materialId": "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a",
                  "sku": "CEM-001",
                  "materialName": "Cemento Portland tipo I",
                  "unit": "BAG",
                  "requested": 50.000,
                  "dispatched": 30.000,
                  "received": 29.500,
                  "difference": 0.500,
                  "shrinkagePercent": 1.67,
                  "tolerancePercent": 2.50,
                  "withinTolerance": true
                }
              ]
            }""";

    private ReceptionApiExamples() {
    }
}
