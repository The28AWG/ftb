package io.github.the28awg.ftb;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.HashMap;

/**
 * Created by the28awg on 13.01.17.
 */
public class S extends HashMap<String, Object> {

    private static final String FILE = "config.json";
    private static S SETTINGS;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(new TypeToken<S>() {
    }.getType(), new JsonDeserializer() {
        @Override
        public S deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            S root = new S();
            if (json.isJsonObject()) {
                root = get(json.getAsJsonObject());
            }
            return root;
        }

        private S get(JsonObject object) {
            S root = new S();
            for (Entry<String, JsonElement> s : object.entrySet()) {
                if (s.getValue().isJsonObject()) {
                    root.put(s.getKey(), get(s.getValue().getAsJsonObject()));
                } else if (s.getValue().isJsonPrimitive()) {
                    root.put(s.getKey(), get(s.getValue().getAsJsonPrimitive()));
                }
            }
            return root;
        }

        private Object get(JsonPrimitive primitive) {
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else {
                if (primitive.isNumber()) {
                    String tmp = primitive.getAsString();
                    try {
                        return Integer.parseInt(tmp);
                    } catch (NumberFormatException ignore) {
                        try {
                            return Long.parseLong(tmp);
                        } catch (NumberFormatException ignore2) {
                            try {
                                return Float.parseFloat(tmp);
                            } catch (NumberFormatException ignore3) {
                                try {
                                    return Double.parseDouble(tmp);
                                } catch (NumberFormatException ignore4) {
                                    return new BigDecimal(tmp);
                                }
                            }
                        }
                    }
                } else {
                    return primitive.getAsString();
                }
            }
        }
    }).create();

    static {
        load();
    }

    public static void load() {
        try {
            SETTINGS = GSON.fromJson(Files.toString(new File(FILE), Charsets.UTF_8), S.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void commit() {
        try {
            Files.write(GSON.toJson(SETTINGS), new File(FILE), Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void set(String key, Object value) {
        String[] levels = key.split("\\.");
        if (SETTINGS == null) {
            SETTINGS = new S();
        }
        S tmp = SETTINGS;
        String current_level = levels[0];
        for (int i = 1; i < levels.length; i++) {
            if (i < levels.length + 1) {
                if (tmp.containsKey("_" + current_level)) {
                    S s = new S();
                    tmp = (S) tmp.getOrDefault("_" + current_level, s);
                } else {
                    S s = new S();
                    tmp.put("_" + current_level, s);
                    tmp = s;
                }
            }
            current_level = levels[i];
        }
        tmp.put(current_level, value);
    }

    public static <T> T get(String key, T defaultValue) {
        T tmp = get(key);
        if (tmp == null) {
            return defaultValue;
        }
        return tmp;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        String[] levels = key.split("\\.");
        if (SETTINGS == null) {
            SETTINGS = new S();
            load();
        }
        S tmp = SETTINGS;
        String current_level = levels[0];
        for (int i = 1; i < levels.length; i++) {
            if (tmp.containsKey("_" + current_level) && i < levels.length + 1) {
                tmp = (S) tmp.getOrDefault("_" + current_level, new S());
            }
            current_level = levels[i];
        }
        return (T) tmp.getOrDefault(current_level, null);
    }
}
