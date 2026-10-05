package pe.buildshield.core.acceptance;

import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;

/** Petición HTTP de un paso: método, ruta y parámetros de consulta. */
public final class ApiRequest {

    private final HttpMethod method;
    private final UriComponentsBuilder uri;

    private ApiRequest(HttpMethod method, String path) {
        this.method = method;
        this.uri = UriComponentsBuilder.fromPath(path);
    }

    public static ApiRequest get(String path) {
        return new ApiRequest(HttpMethod.GET, path);
    }

    public static ApiRequest post(String path) {
        return new ApiRequest(HttpMethod.POST, path);
    }

    public static ApiRequest patch(String path) {
        return new ApiRequest(HttpMethod.PATCH, path);
    }

    public ApiRequest param(String name, String value) {
        uri.queryParam(name, value);
        return this;
    }

    HttpMethod method() {
        return method;
    }

    String pathAndQuery() {
        return uri.encode().build().toUriString();
    }
}
