package com.reservationhub.api.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/** Configuration precedence: JVM property, environment variable, properties file. */
public final class ApiConfig {
    private static final Properties DEFAULTS = new Properties();

    static {
        try (InputStream stream = ApiConfig.class.getResourceAsStream("/config.properties")) {
            if (stream == null) throw new IllegalStateException("Missing config.properties");
            DEFAULTS.load(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load API configuration", e);
        }
    }

    private ApiConfig() { }

    public static String get(String key) {
        String environmentKey = key.replace('.', '_').toUpperCase(Locale.ROOT);
        String value = System.getProperty(key,
                System.getenv().getOrDefault(environmentKey, DEFAULTS.getProperty(key)));
        if (value == null) throw new IllegalArgumentException("Missing configuration: " + key);
        return value.trim();
    }

    public static int number(String key) {
        int value = Integer.parseInt(get(key));
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }
}