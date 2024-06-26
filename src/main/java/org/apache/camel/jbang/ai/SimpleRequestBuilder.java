package org.apache.camel.jbang.ai;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class SimpleRequestBuilder {
    private StringBuilder stringBuilder = new StringBuilder();
    private boolean started;

    public SimpleRequestBuilder() {
        stringBuilder.append("{");
    }

    private SimpleRequestBuilder wrap(String key) {
        stringBuilder.append("\"").append(key).append("\"");

        return this;
    }

    private SimpleRequestBuilder separate() {
        stringBuilder.append(":");

        return this;
    }

    private SimpleRequestBuilder nextElement() {
        stringBuilder.append(",");

        return this;
    }

    private <T> SimpleRequestBuilder appendRaw(T value) {
        stringBuilder.append(value);

        return this;
    }

    public <T> SimpleRequestBuilder append(String key, T value) {
        if (started) {
            nextElement();
        }

        wrap(key).separate().appendRaw(value);
        started = true;

        return this;
    }

    public <T> SimpleRequestBuilder append(String key, String value) {
        if (started) {
            nextElement();
        }

        wrap(key).separate().wrap(value);
        started = true;

        return this;
    }


    public SimpleRequestBuilder append(String key, Instant value, DateTimeFormatter dateTimeFormatter) {
        return append(key, dateTimeFormatter.format(value));
    }

    public <T> SimpleRequestBuilder appendNull(String key) {
        if (started) {
            nextElement();
        }

        wrap(key).separate().appendRaw("null");
        started = true;

        return this;
    }

    public String build() {
        stringBuilder.append("}");
        String ret = stringBuilder.toString();
        stringBuilder.setLength(0);

        return ret;
    }

}
