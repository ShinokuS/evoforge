package io.github.evoforge.simulation.result;

import java.util.regex.Pattern;

public record ResultCode(String value) {

    private static final Pattern FORMAT =
            Pattern.compile(
                    "[a-z][a-z0-9_.-]*:[a-z][a-z0-9_.-]*");

    public ResultCode {
        if (value == null) {
            throw new IllegalArgumentException(
                    "value must not be null");
        }

        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "invalid result code: " + value);
        }
    }

    public static ResultCode of(
            String domain,
            String code) {

        if (domain == null) {
            throw new IllegalArgumentException(
                    "domain must not be null");
        }

        if (code == null) {
            throw new IllegalArgumentException(
                    "code must not be null");
        }

        return new ResultCode(domain + ":" + code);
    }
}