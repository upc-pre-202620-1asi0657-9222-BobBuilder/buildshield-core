package pe.buildshield.core.inventory.interfaces.rest;

/** Ejemplos de respuesta y de parámetros para la documentación OpenAPI del módulo inventory. */
final class StockApiExamples {

    static final String WAREHOUSE_ID = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e";

    static final String STOCK_LEVEL = """
            {
              "warehouseId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
              "materialId": "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a",
              "availableQty": 500.000,
              "reservedQty": 0.000
            }""";

    static final String STOCK = """
            [
              {
                "warehouseId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
                "warehouseName": "Almacén Central",
                "materialId": "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a",
                "sku": "CEM-001",
                "materialName": "Cemento Portland tipo I",
                "unit": "BAG",
                "availableQty": 500.000,
                "reservedQty": 0.000
              }
            ]""";

    private StockApiExamples() {
    }
}
