package pe.buildshield.core.iam.interfaces.rest;

/** Ejemplos de respuesta para la documentación OpenAPI del módulo iam. */
final class IamApiExamples {

    static final String SIGN_UP = """
            {
              "organizationId": "3f6c1d2e-8a4b-4c1f-9e2d-1a2b3c4d5e6f",
              "administratorId": "7b1e2c3d-4f5a-4b6c-8d9e-0f1a2b3c4d5e"
            }""";

    static final String TOKENS = """
            {
              "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3YjFlMmMzZCJ9.firma",
              "accessTokenExpiresAt": "2026-11-02T15:15:00Z",
              "refreshToken": "o1V8m3Qe5rT2yU7iP0aS4dF6gH9jK2lZ",
              "refreshTokenExpiresAt": "2026-11-09T15:00:00Z",
              "tokenType": "Bearer"
            }""";

    static final String USER = """
            {
              "id": "9c2d3e4f-5a6b-4c7d-8e9f-a0b1c2d3e4f5",
              "email": "rosa@andina.pe",
              "fullName": "Rosa Quispe",
              "role": "WAREHOUSE_MANAGER",
              "active": true
            }""";

    static final String USERS = """
            [
              {
                "id": "7b1e2c3d-4f5a-4b6c-8d9e-0f1a2b3c4d5e",
                "email": "ana@andina.pe",
                "fullName": "Ana Torres",
                "role": "ADMINISTRATOR",
                "active": true
              },
              {
                "id": "9c2d3e4f-5a6b-4c7d-8e9f-a0b1c2d3e4f5",
                "email": "rosa@andina.pe",
                "fullName": "Rosa Quispe",
                "role": "WAREHOUSE_MANAGER",
                "active": true
              }
            ]""";

    private IamApiExamples() {
    }
}
