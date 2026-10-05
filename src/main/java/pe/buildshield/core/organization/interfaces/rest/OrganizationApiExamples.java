package pe.buildshield.core.organization.interfaces.rest;

/** Ejemplos de respuesta y de parámetros para la documentación OpenAPI del módulo organization. */
final class OrganizationApiExamples {

    static final String WORKSITE_ID = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
    static final String WAREHOUSE_ID = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e";
    static final String MATERIAL_ID = "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a";
    static final String ASSIGNMENT_ID = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b";

    static final String WORKSITE = """
            {
              "id": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
              "name": "Torre Norte",
              "address": "Av. Javier Prado 123",
              "district": "San Isidro",
              "city": "Lima",
              "latitude": -12.0931,
              "longitude": -77.0465,
              "startDate": "2026-11-01",
              "endDate": "2027-06-30"
            }""";

    static final String WORKSITES = "[" + WORKSITE + "]";

    static final String WAREHOUSE = """
            {
              "id": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
              "name": "Almacén Central",
              "type": "WAREHOUSE",
              "address": "Av. Argentina 2500, Callao",
              "active": true
            }""";

    static final String WAREHOUSES = "[" + WAREHOUSE + "]";

    static final String MATERIAL = """
            {
              "id": "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a",
              "sku": "CEM-001",
              "name": "Cemento Portland tipo I",
              "unit": "BAG",
              "wasteTolerancePercent": 2.5,
              "active": true
            }""";

    static final String MATERIALS = "[" + MATERIAL + "]";

    static final String ASSIGNMENT = """
            {
              "id": "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b",
              "userId": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
              "siteType": "WORKSITE",
              "siteId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
              "assignedAt": "2026-11-02T15:00:00Z",
              "endedAt": null,
              "active": true
            }""";

    static final String ASSIGNMENTS = "[" + ASSIGNMENT + "]";

    static final String ASSIGNMENT_ENDED = """
            {
              "id": "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b",
              "userId": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
              "siteType": "WORKSITE",
              "siteId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
              "assignedAt": "2026-11-02T15:00:00Z",
              "endedAt": "2027-01-15T18:30:00Z",
              "active": false
            }""";

    private OrganizationApiExamples() {
    }
}
