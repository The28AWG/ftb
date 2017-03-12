package io.github.the28awg.ftb;

import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.the28awg.ftb.App;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CookieStorage {
    private static Logger logger = LoggerFactory.getLogger(CookieStorage.class.getName());
    private static String cookies_storage = "cookies.json";
    private static ArrayList<CookieStorage> storage = new ArrayList<>();
    private static Map<String, CookieJar> cache = new HashMap<>();
    private static AtomicInteger count = new AtomicInteger();
    private String email;
    private List<Cookie> cookies;

    public CookieStorage() {
        this.cookies = new ArrayList<>();
    }

    public CookieStorage(String email) {
        this.email = email;
        this.cookies = new ArrayList<>();
    }

    private static String createCookieKey(Cookie cookie) {
        return (cookie.secure() ? "https" : "http") + "://" + cookie.domain() + cookie.path() + "|" + cookie.name();
    }

    public static CookieJar jar(String email) {
        return cache.computeIfAbsent(email, k -> new CookieJar(email));
    }

    public static void load() {
        try {
            logger.debug("load cookie storage.");
            Type type = new TypeToken<ArrayList<CookieStorage>>() {
            }.getType();
            storage = App.gson().fromJson(new FileReader(cookies_storage), type);
            logger.debug("done.");
        } catch (FileNotFoundException e) {
            logger.debug("failed.");
            logger.error("exception: ", e);
        }
    }

    public static void save() {
        try {
            logger.debug("save cookie storage.");
            Type type = new TypeToken<ArrayList<CookieStorage>>() {
            }.getType();
            String json = App.gson().toJson(storage, type);
            Writer writer = new FileWriter(cookies_storage);
            writer.write(json);
            writer.flush();
            writer.close();
            logger.debug("done.");
        } catch (IOException e) {
            logger.debug("failed.");
            logger.error("exception: ", e);
        }
    }

    public static CookieStorage create(String email) {
        CookieStorage tmp = get(email);
        if (tmp != null) {
            return tmp;
        }
        tmp = new CookieStorage(email);
        storage.add(tmp);
        return tmp;

    }

    public static CookieStorage get(String email) {
        return storage.stream().filter(storage -> storage.email().equals(email)).findFirst().orElse(null);
    }

    public static boolean isCookieExpired(Cookie cookie) {
        return cookie.expiresAt() < System.currentTimeMillis();
    }

    public CookieStorage email(String email) {
        this.email = email;
        return this;
    }

    public String email() {
        return email;
    }

    public List<Cookie> cookies() {
        return cookies;
    }

    public CookieStorage cookies(List<Cookie> cookies) {
        this.cookies.addAll(cookies);
        return this;
    }

    public Cookie cookie(String name) {
        return this.cookies.stream().filter(cookie -> cookie.name().equals(name)).findFirst().orElse(null);
    }

    public static void clear(String email) {
        CookieStorage cookie = get(email);
        if (cookie != null) {
            storage.remove(cookie);
            CookieStorage.save();
        }
    }

    public static class SerializableCookie implements Serializable {

        private static final long serialVersionUID = -8594045714036645534L;
        private static long NON_VALID_EXPIRES_AT = -1L;
        private transient Cookie cookie;

        /**
         * Using some super basic byte array &lt;-&gt; hex conversions so we don't
         * have to rely on any large Base64 libraries. Can be overridden if you
         * like!
         *
         * @param bytes byte array to be converted
         * @return string containing hex values
         */
        private static String byteArrayToHexString(byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte element : bytes) {
                int v = element & 0xff;
                if (v < 16) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        }

        /**
         * Converts hex values from strings to byte array
         *
         * @param hexString string of hex-encoded values
         * @return decoded byte array
         */
        private static byte[] hexStringToByteArray(String hexString) {
            int len = hexString.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character
                        .digit(hexString.charAt(i + 1), 16));
            }
            return data;
        }

        public String encode(Cookie cookie) {
            this.cookie = cookie;

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = null;

            try {
                objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                objectOutputStream.writeObject(this);
            } catch (IOException e) {
                logger.debug("IOException in encodeCookie", e);
                return null;
            } finally {
                if (objectOutputStream != null) {
                    try {
                        // Closing a ByteArrayOutputStream has no effect, it can be used later (and is used in the return statement)
                        objectOutputStream.close();
                    } catch (IOException e) {
                        logger.debug("Stream not closed in encodeCookie", e);
                    }
                }
            }

            return byteArrayToHexString(byteArrayOutputStream.toByteArray());
        }

        public Cookie decode(String encodedCookie) {

            byte[] bytes = hexStringToByteArray(encodedCookie);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                    bytes);

            Cookie cookie = null;
            ObjectInputStream objectInputStream = null;
            try {
                objectInputStream = new ObjectInputStream(byteArrayInputStream);
                cookie = ((SerializableCookie) objectInputStream.readObject()).cookie;
            } catch (IOException e) {
                logger.debug("IOException in decodeCookie", e);
            } catch (ClassNotFoundException e) {
                logger.debug("ClassNotFoundException in decodeCookie", e);
            } finally {
                if (objectInputStream != null) {
                    try {
                        objectInputStream.close();
                    } catch (IOException e) {
                        logger.debug("Stream not closed in decodeCookie", e);
                    }
                }
            }
            return cookie;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(cookie.name());
            out.writeObject(cookie.value());
            out.writeLong(cookie.persistent() ? cookie.expiresAt() : NON_VALID_EXPIRES_AT);
            out.writeObject(cookie.domain());
            out.writeObject(cookie.path());
            out.writeBoolean(cookie.secure());
            out.writeBoolean(cookie.httpOnly());
            out.writeBoolean(cookie.hostOnly());
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            Cookie.Builder builder = new Cookie.Builder();

            builder.name((String) in.readObject());

            builder.value((String) in.readObject());

            long expiresAt = in.readLong();
            if (expiresAt != NON_VALID_EXPIRES_AT) {
                builder.expiresAt(expiresAt);
            }

            final String domain = (String) in.readObject();
            builder.domain(domain);

            builder.path((String) in.readObject());

            if (in.readBoolean())
                builder.secure();

            if (in.readBoolean())
                builder.httpOnly();

            if (in.readBoolean())
                builder.hostOnlyDomain(domain);

            cookie = builder.build();
        }

    }

    public static class CookieAdapter extends TypeAdapter<CookieStorage> {
        @Override
        public void write(JsonWriter out, CookieStorage value) throws IOException {
            out.beginObject();
            out.name("email").value(value.email());
            JsonWriter cookies_out = out.name("cookies").beginArray();
            value.cookies().forEach(cookie -> {
                try {
                    cookies_out.value(new SerializableCookie().encode(cookie));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            cookies_out.endArray();
            out.endObject();
        }

        @Override
        public CookieStorage read(JsonReader in) throws IOException {
            CookieStorage storage = new CookieStorage();
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "email":
                        storage.email(in.nextString());
                        break;
                    case "cookies":
                        List<Cookie> cookies = new ArrayList<>();
                        in.beginArray();
                        while (in.hasNext()) {
                            cookies.add(new SerializableCookie().decode(in.nextString()));
                        }
                        in.endArray();
                        storage.cookies(cookies);
                        break;
                }
            }
            in.endObject();
            return storage;
        }
    }

    public static class CookieJar implements okhttp3.CookieJar {

        private String email;
        private int index;

        public CookieJar(String email) {
            this.email = email;
            index = count.incrementAndGet();
        }

        private static List<Cookie> filter(List<Cookie> cookies) {
            List<Cookie> persistentCookies = new ArrayList<>();

            for (Cookie cookie : cookies) {
                if (cookie.persistent()) {
                    persistentCookies.add(cookie);
                }
            }
            return persistentCookies;
        }

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            CookieStorage.create(email).cookies(filter(cookies));
            CookieStorage.save();

        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookiesToRemove = new ArrayList<>();
            List<Cookie> validCookies = new ArrayList<>();
            List<Cookie> cookies = new ArrayList<>(CookieStorage.create(email).cookies());
            for (Iterator<Cookie> it = cookies.iterator(); it.hasNext(); ) {
                Cookie currentCookie = it.next();

                if (isCookieExpired(currentCookie)) {
                    cookiesToRemove.add(currentCookie);
                    it.remove();

                } else if (currentCookie.matches(url)) {
                    validCookies.add(currentCookie);
                }
            }

            if (cookiesToRemove.size() > 0) {
                logger.debug("remove " + cookiesToRemove.size() + " cooke.");
                logger.debug("before: " + CookieStorage.create(email).cookies().size());
                CookieStorage.create(email).cookies().removeAll(cookiesToRemove);
                logger.debug("after: " + CookieStorage.create(email).cookies().size());
                CookieStorage.save();

            }
            return validCookies;
        }

        @Override
        public String toString() {
            return "index: " + index;
        }
    }
}
