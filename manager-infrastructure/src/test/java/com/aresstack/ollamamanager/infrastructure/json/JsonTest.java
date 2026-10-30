package com.aresstack.ollamamanager.infrastructure.json;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class JsonTest {

    @Test
    @SuppressWarnings("unchecked")
    public void parsesNestedValues() {
        Map<String, Object> value = (Map<String, Object>) Json.parse(
            "{\"a\": [1, 2.5, true, null], \"b\": \"x\\\"\\u00e4\\n\", \"c\": {}}");

        assertEquals(Arrays.asList(1L, 2.5, true, null), value.get("a"));
        assertEquals("x\"ä\n", value.get("b"));
        assertEquals(new LinkedHashMap<String, Object>(), value.get("c"));
    }

    @Test
    public void writtenJsonParsesBack() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("text", "line\tbreak\u0001");
        value.put("list", Arrays.<Object>asList(1L, "two", false));
        value.put("nothing", null);

        assertEquals(value, Json.parse(Json.write(value)));
    }

    @Test
    public void parsesNdjsonLine() {
        List<?> list = (List<?>) Json.parse("[]");
        assertEquals(0, list.size());
        assertNull(Json.parse("null"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTruncatedInput() {
        Json.parse("{\"a\": [1, 2");
    }

    @Test
    public void rejectsMalformedJsonThatLenientParsersAccept() {
        String[] malformed = {
            "{a: 1}",
            "{'a': 1}",
            "{\"a\": 1} // comment",
            "/* comment */ {\"a\": 1}",
            "{\"a\": \"line\nbreak\"}",
            "{\"a\": \"tab\tinside\"}",
            "{\"a\": 1,}",
            "[1, 2,]",
            "{\"a\": NaN}",
            "",
            "   ",
            "{",
            "{\"models\": [{\"name\": \"x\""
        };
        for (String text : malformed) {
            try {
                Json.parse(text);
                fail("accepted malformed JSON: " + text);
            } catch (IllegalArgumentException expected) {
                // Reject as required.
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTrailingContent() {
        Json.parse("{} x");
    }
}
