package com.aresstack.ollamamanager.domain.server;

import com.aresstack.ollamamanager.domain.common.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Typed descriptor of one server-wide setting: UI metadata, validation and environment mapping.
 * <p>
 * A {@code null} value means "unset": the variable is not passed and Ollama applies its own default.
 * Required settings always carry a value.
 *
 * @param <T> value type
 */
public final class ServerSetting<T> {

    private final String id;
    private final String label;
    private final String tooltip;
    private final OptionType optionType;
    private final Class<T> valueType;
    private final T defaultValue;
    private final String environmentVariable;
    private final boolean restartRequired;
    private final Integer minimum;
    private final Integer maximum;
    private final List<T> choices;
    private final Function<T, String> validator;
    private final Function<T, String> encoder;
    private final Function<String, T> decoder;

    private ServerSetting(Builder<T> builder) {
        this.id = Preconditions.requireNonBlank(builder.id, "id");
        this.label = Preconditions.requireNonBlank(builder.label, "label");
        this.tooltip = Preconditions.requireNonBlank(builder.tooltip, "tooltip");
        this.optionType = Preconditions.requireNonNull(builder.optionType, "optionType");
        this.valueType = Preconditions.requireNonNull(builder.valueType, "valueType");
        this.environmentVariable = Preconditions.requireNonBlank(builder.environmentVariable, "environmentVariable");
        this.restartRequired = builder.restartRequired;
        this.minimum = builder.minimum;
        this.maximum = builder.maximum;
        this.choices = Collections.unmodifiableList(new ArrayList<T>(builder.choices));
        this.validator = builder.validator;
        this.encoder = Preconditions.requireNonNull(builder.encoder, "encoder");
        this.decoder = Preconditions.requireNonNull(builder.decoder, "decoder");
        this.defaultValue = builder.defaultValue;
        if (defaultValue != null && validate(defaultValue) != null) {
            throw new IllegalArgumentException("invalid default for " + id + ": " + validate(defaultValue));
        }
    }

    public static <T> Builder<T> builder(String id, Class<T> valueType) {
        return new Builder<T>(id, valueType);
    }

    /** Stable identifier used in persistence. */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String tooltip() {
        return tooltip;
    }

    public OptionType optionType() {
        return optionType;
    }

    public Class<T> valueType() {
        return valueType;
    }

    /**
     * Return the value used when the user did not choose one, or {@code null} if the variable stays unset.
     */
    public T defaultValue() {
        return defaultValue;
    }

    /** Return whether this setting can never be unset. */
    public boolean isRequired() {
        return defaultValue != null;
    }

    public String environmentVariable() {
        return environmentVariable;
    }

    /** Return whether a running server must be restarted for a change to take effect. */
    public boolean isRestartRequired() {
        return restartRequired;
    }

    /** Return the inclusive lower bound for integer settings, or {@code null}. */
    public Integer minimum() {
        return minimum;
    }

    /** Return the inclusive upper bound for integer settings, or {@code null}. */
    public Integer maximum() {
        return maximum;
    }

    public List<T> choices() {
        return choices;
    }

    /**
     * Validate a value and return a user-readable error, or {@code null} if it is acceptable.
     */
    public String validate(T value) {
        if (value == null) {
            return isRequired() ? label + " is required" : null;
        }
        if (!valueType.isInstance(value)) {
            return label + " expects " + valueType.getSimpleName();
        }
        if (value instanceof Integer) {
            int number = (Integer) value;
            if (minimum != null && number < minimum || maximum != null && number > maximum) {
                return label + " must be between " + minimum + " and " + maximum;
            }
        }
        if (!choices.isEmpty() && !choices.contains(value)) {
            return label + " must be one of " + choices;
        }
        String error = validator == null ? null : validator.apply(value);
        return error == null ? null : label + ": " + error;
    }

    /** Convert a value to its environment variable representation. */
    public String encode(T value) {
        return encoder.apply(value);
    }

    /** Parse the environment variable representation; throws {@link IllegalArgumentException} if malformed. */
    public T decode(String text) {
        T value = decoder.apply(text);
        String error = validate(value);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }

    @Override
    public String toString() {
        return id;
    }

    /**
     * Assembles a descriptor.
     *
     * @param <T> value type
     */
    public static final class Builder<T> {
        private final String id;
        private final Class<T> valueType;
        private String label;
        private String tooltip;
        private OptionType optionType;
        private T defaultValue;
        private String environmentVariable;
        private boolean restartRequired = true;
        private Integer minimum;
        private Integer maximum;
        private List<T> choices = Collections.emptyList();
        private Function<T, String> validator;
        private Function<T, String> encoder = String::valueOf;
        private Function<String, T> decoder;

        private Builder(String id, Class<T> valueType) {
            this.id = id;
            this.valueType = valueType;
        }

        public Builder<T> label(String value) {
            this.label = value;
            return this;
        }

        public Builder<T> tooltip(String value) {
            this.tooltip = value;
            return this;
        }

        public Builder<T> optionType(OptionType value) {
            this.optionType = value;
            return this;
        }

        public Builder<T> defaultValue(T value) {
            this.defaultValue = value;
            return this;
        }

        public Builder<T> environmentVariable(String value) {
            this.environmentVariable = value;
            return this;
        }

        public Builder<T> restartRequired(boolean value) {
            this.restartRequired = value;
            return this;
        }

        public Builder<T> range(int min, int max) {
            this.minimum = min;
            this.maximum = max;
            return this;
        }

        public Builder<T> choices(List<T> values) {
            this.choices = values;
            return this;
        }

        public Builder<T> validator(Function<T, String> value) {
            this.validator = value;
            return this;
        }

        public Builder<T> encoder(Function<T, String> value) {
            this.encoder = value;
            return this;
        }

        public Builder<T> decoder(Function<String, T> value) {
            this.decoder = value;
            return this;
        }

        public ServerSetting<T> build() {
            return new ServerSetting<T>(this);
        }
    }
}
