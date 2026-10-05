package pe.buildshield.core.support;

import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.core.env.Environment;

/** Opt-in explícito a bases temporales: nunca acepta una base de trabajo. */
public final class ExternalPostgres {
    private ExternalPostgres() { }
    public static JdbcConnectionDetails connection(Environment env, String group) {
        String url = env.getRequiredProperty("buildshield.test." + group + "-url");
        if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/buildshield_" + group + "_it"))
            throw new IllegalArgumentException("Se requiere una base temporal local buildshield_" + group + "_it");
        String user = env.getRequiredProperty("buildshield.test.database-user");
        String password = env.getProperty("buildshield.test.database-password", "");
        return new JdbcConnectionDetails() {
            @Override public String getJdbcUrl() { return url; }
            @Override public String getUsername() { return user; }
            @Override public String getPassword() { return password; }
            @Override public String getDriverClassName() { return "org.postgresql.Driver"; }
        };
    }
}
