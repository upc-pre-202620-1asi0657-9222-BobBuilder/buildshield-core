package pe.buildshield.core.dispatch.domain.model;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Código único del manifiesto del despacho (US24), por ejemplo {@code MAN-20261106-7KQ2M9XA}: fecha de
 * preparación y 8 caracteres aleatorios sin letras ambiguas. Es lo que codifica el QR del manifiesto.
 */
public record ManifestCode(String value) {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Pattern FORMAT = Pattern.compile("MAN-\\d{8}-[" + ALPHABET + "]{8}");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    public ManifestCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Código de manifiesto inválido: " + value);
        }
    }

    public static ManifestCode generate(Instant preparedAt, RandomGenerator random) {
        StringBuilder code = new StringBuilder("MAN-")
                .append(LocalDate.ofInstant(preparedAt, ZoneOffset.UTC).format(DAY)).append('-');
        for (int i = 0; i < 8; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new ManifestCode(code.toString());
    }
}
