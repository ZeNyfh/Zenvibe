package Zenvibe.managers;


import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class JsonBrowserManager {

    private JsonBrowserManager() {}

    public static <T> List<T> asList(JsonBrowser node, Function<? super JsonBrowser, ? extends T> mapper) throws IOException {
        Objects.requireNonNull(mapper, "mapper");

        if (node == null || !node.isList()) {
            return Collections.emptyList();
        }

        List<?> raw = node.as(List.class);
        if (raw == null) {
            return Collections.emptyList();
        }

        if (raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof JsonBrowser) {
                result.add(mapper.apply((JsonBrowser) item));
            } else if (item == null) {
                result.add(null);
            } else {
                JsonBrowser wrapped = JsonBrowser.parse(item.toString());
                result.add(mapper.apply(wrapped));
            }
        }

        return result;
    }

    public static List<String> asStringList(JsonBrowser node) throws IOException {
        return asList(node, JsonBrowser::text);
    }

    @SuppressWarnings("unchecked") // keep this because I do not care for these warnings.
    public static <T> List<T> asList(JsonBrowser node, Class<T> clazz) throws IOException {
        if (node.isList() && clazz == String.class) {
            return (List<T>) asStringList(node);
        } else {
            throw new IllegalArgumentException("Unsupported class for asList(): " + clazz);
        }
    }
}
