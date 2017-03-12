package io.github.the28awg.ftb;

import com.google.gson.stream.JsonReader;
import okhttp3.Cookie;
import okhttp3.FormBody;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by the28awg on 08.03.17.
 */
public class Worker {

    private static final String USERNAME_KEY = "ficbook.username";
    private static final String PASSWORD_KEY = "ficbook.password";
    private static final String COLLECTION_KEY = "ficbook.collection";
    private static final String ID = "Worker";
    private static final String SELECT_BY_BOOK_ID_LIMIT_1 = "SELECT * FROM fanfic WHERE book_id = ? LIMIT 1;";
    private static final String SELECT = "SELECT user_id FROM follow_book WHERE book_id = ?;";
    private static final String SELECT_BY_USER_ID_AND_BOOK_ID_LIMIT_1 = "SELECT * FROM follow_book WHERE user_id = ? AND book_id = ? LIMIT 1;";
    private static final String INSERT_FOLLOW_BOOK = "INSERT INTO follow_book (user_id, book_id) VALUES (?, ?);";
    private static final String INSERT_FANFIC = "INSERT INTO fanfic (book_id) VALUES (?);";
    private static final String DELETE_FANFIC = "DELETE FROM follow_book WHERE user_id = ? AND book_id = ?;";
    private static Logger logger = LoggerFactory.getLogger(App.class.getName());
    private static Worker worker;
    private Timer timer;
    private TimerTask task;

    private Worker() {
        if (task == null) {
            task = new TimerTask() {
                @Override
                public void run() {
                    tick();
                }
            };
        }
        if (timer == null) {
            timer = new Timer(ID);
        }
    }

    public static Worker get() {
        if (worker == null) {
            worker = new Worker();
        }
        return worker;
    }

    public static boolean contains(String book_id) {
        try (Connection c = C.me(); PreparedStatement s = c.prepareStatement(SELECT_BY_BOOK_ID_LIMIT_1)) {
            s.setString(1, book_id);
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean contains(Integer user_id, String book_id) {
        try (Connection c = C.me(); PreparedStatement s = c.prepareStatement(SELECT_BY_USER_ID_AND_BOOK_ID_LIMIT_1)) {
            s.setInt(1, user_id);
            s.setString(2, book_id);
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        timer.schedule(task, 0, TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES));
    }

    public void stop() {
        task.cancel();
    }

    public void tick() {
        if (login_check()) {
            logger.debug("start check...");
            try {
                Map<String, String> update = update();
                Map<Integer, List<String>> messages = new HashMap<>();
                try (Connection c = C.me()) {
                    for (Map.Entry<String, String> entry: update.entrySet()) {
                        try (PreparedStatement s = c.prepareStatement(SELECT)) {
                            s.setString(1, entry.getKey());
                            try (ResultSet r = s.executeQuery()) {
                                while (r.next()) {
                                    Integer user_id = r.getInt(1);
                                    List<String> tmp = messages.computeIfAbsent(user_id, k -> new ArrayList<>());
                                    tmp.add("Обновился рассказ \"" + entry.getValue() + "\".");
                                }
                            }
                        }
                    }
                }
                for (Map.Entry<Integer, List<String>> entry : messages.entrySet()) {
                    StringBuilder message = new StringBuilder();
                    Iterator<String> iterator = entry.getValue().iterator();
                    while (iterator.hasNext()) {
                        message.append(iterator.next());
                        if (iterator.hasNext()) {
                            message.append("\n");
                        }
                    }
                    App.send(App.app(), entry.getKey(), message.toString());
                }
                logger.debug("done.");
            } catch (IOException | SQLException e) {
                logger.debug("failed.");
                logger.error("exception: ", e);
            }
        }
    }

    public static boolean login_check() {
        Cookie cookie = CookieStorage.get(ID).cookie("remme");
        if (cookie == null || CookieStorage.isCookieExpired(cookie)) {
            FormBody.Builder builder = new FormBody.Builder().add("login", S.get(USERNAME_KEY)).add("password", S.get(PASSWORD_KEY)).add("remember", "on");
            Boolean result = false;
            String error = null;
            try {
                Response response = App.post(ID, "https://ficbook.net/login_check", builder.build());
                JsonReader reader = new JsonReader(response.body().charStream());
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case "result":
                            result = reader.nextBoolean();
                            break;
                        case "error":
                            error = reader.nextString();
                            break;
                        default:
                            System.out.println(name);
                            reader.skipValue();
                    }
                }
                reader.endObject();
            } catch (IOException e) {
                logger.debug("failed.");
                logger.error("exception: ", e);
            }
            if (error != null) {
                logger.debug("error: " + error);
            }
            logger.debug("result: " + result);
            return result;
        } else {
            return true;
        }
    }

    public static boolean follow(Account account, String book_id) throws IOException, SQLException {
        boolean result = false;
        if (!contains(book_id)) {
            FormBody.Builder builder = new FormBody.Builder().add("collection_id", S.get(COLLECTION_KEY)).add("fanfic_id", book_id).add("action", "add");
            Response response = App.post(ID, "https://ficbook.net/ajax/collection", builder.build());
            if (response.isSuccessful()) {
                try (Connection c = C.me(); PreparedStatement s = c.prepareStatement(INSERT_FANFIC)) {
                    s.setString(1, book_id);
                    result = s.executeUpdate() > 0;
                }
            }
        }
        if (!contains(account.user_id(), book_id) && result) {
            try (Connection c = C.me(); PreparedStatement s = c.prepareStatement(INSERT_FOLLOW_BOOK)) {
                s.setInt(1, account.user_id());
                s.setString(2, book_id);
                result = s.executeUpdate() > 0;
            }
        }
        return result;
    }

    public static boolean unfollow(Account account, String book_id) throws SQLException {
        try (Connection c = C.me(); PreparedStatement s = c.prepareStatement(DELETE_FANFIC)) {
            s.setInt(1, account.user_id());
            s.setString(2, book_id);
            return s.executeUpdate() > 0;
        }
    }

    public static Map<String, String> update() throws IOException {
        Map<String, String> result = new HashMap<>();
        Response response = App.get("Worker", "https://ficbook.net/home/collections?type=update");
        Document document = Jsoup.parse(response.body().string());
        document.select("div.container-fluid.js-unfollow-collection-container").stream().filter(element -> element.hasAttr("style"))
                .forEach(element -> {
                    Element description = element.select("article.block > div.description > h3 > a").first();
                    String url = description.attr("href");
                    String title = description.text();
                    result.put(url.replace("/readfic/", ""), title);
                });
        return result;
    }
    private static void create_collection(String title) throws IOException {
        FormBody.Builder builder = new FormBody.Builder().add("title", title).add("is_public", "0").add("action", "new");
        Response response = App.post(ID, "https://ficbook.net/ajax/collection", builder.build());
    }
}
