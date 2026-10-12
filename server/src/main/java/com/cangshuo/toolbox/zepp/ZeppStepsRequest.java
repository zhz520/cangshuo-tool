package com.cangshuo.toolbox.zepp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import java.io.IOException;

public record ZeppStepsRequest(
        @NotBlank @Size(max = 254) String account,
        @NotBlank @Size(max = 128) String password,
        @NotNull @Min(1) @Max(50000) @JsonDeserialize(using = StrictInteger.class) Integer steps,
        @NotNull @AssertTrue @JsonDeserialize(using = StrictBoolean.class) Boolean consent) {
    @Override public String toString() { return "ZeppStepsRequest[credentials=redacted]"; }

    /** Jackson normally coerces fractional numbers and numeric strings into integers. */
    public static final class StrictInteger extends JsonDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }

    public static final class StrictBoolean extends JsonDeserializer<Boolean> {
        @Override public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_TRUE) return true;
            if (parser.currentToken() == JsonToken.VALUE_FALSE) return false;
            return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
        }
    }
}
