package io.github.the28awg.ftb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendLocation;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Chat;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.AbsSender;
import org.telegram.telegrambots.bots.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.bots.commands.BotCommand;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.logging.BotLogger;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.LogManager;

/**
 * Created by the28awg on 13.01.17.
 */
public class App extends TelegramLongPollingCommandBot {

    private static final String TAG = "FTB";
    private static Logger logger = LoggerFactory.getLogger(App.class.getName());
    private static HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE);
    private static Gson gson;
    private static UserAgentInterceptor userAgentInterceptor = new UserAgentInterceptor("Mozilla/5.0 (Windows NT 6.1; rv:40.0)");
    private static App app;
    private Map<Integer, AtomicLong> ignore_counter = new ConcurrentHashMap<>();

    public App() {
        register(new BotCommand("start", "") {
            @Override
            public void execute(AbsSender sender, User user, Chat chat, String[] arguments) {
                Account account = Account.get(user);
                logger.debug("start: user_id = " + account.user_id());
                send(sender, account.user_id(), "Привет, " + account.name() + "!");
            }
        });
        register(new BotCommand("new_users", "") {
            @Override
            public void execute(AbsSender sender, User user, Chat chat, String[] arguments) {
                Account account = Account.get(user);
                logger.debug("new_users: user_id = " + account.user_id());
                if (Account.admin(account.user_id())) {
                    if (arguments == null || arguments.length == 0) {
                        long count = Account.count(Account.NEW);
                        long page = count / 10;
                        long mod = count % 10;
                        StringBuilder builder = new StringBuilder("<b>Список пользователей</b>: ");
                        AtomicInteger total = new AtomicInteger();
                        Account.findByState(Account.NEW, 0L, 10L).forEach(a -> {
                            builder.append("\n    ").append(a.user_id()).append(" - ").append(a.name());
                            total.incrementAndGet();
                        });
                        builder.append("\nВсего: <b>").append(count).append("</b>.");
                        builder.append("\nСтраница: <b>").append(1).append("</b> из <b>").append((mod > 0 ? page + 1 : page == 0 ? 1 : page)).append("</b>.");
                        send(sender, account.user_id(), builder.toString());
                    } else if (arguments.length >= 1) {
                        if (arguments[0].equals("page")) {
                            Long page = 0L;
                            try {
                                page = Long.parseLong(arguments[1]);
                            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignore) {

                            }
                            if (page != 0L) {
                                page -= 1L;
                            }
                            long count = Account.count(Account.NEW);
                            long all_page = count / 10;
                            long mod = count % 10;
                            if (page > all_page) {
                                page = all_page;
                            }
                            StringBuilder builder = new StringBuilder("<b>Список пользователей</b>: ");
                            AtomicInteger total = new AtomicInteger();
                            Account.findByState(Account.NEW, page * 10L, 10L).forEach(a -> {
                                builder.append("\n    ").append(a.user_id()).append(" - ").append(a.name());
                                total.incrementAndGet();
                            });
                            builder.append("\nВсего: <b>").append(count).append("</b>.");
                            builder.append("\nСтраница: <b>").append(page + 1).append("</b> из <b>").append((mod > 0 ? all_page + 1 : all_page == 0 ? 1 : all_page)).append("</b>.");
                            send(sender, account.user_id(), builder.toString());
                        } else {
                            send(sender, account.user_id(), "Что?.");
                        }
                    }
                }
            }
        });
        register(new BotCommand("lock", "") {
            @Override
            public void execute(AbsSender sender, User user, Chat chat, String[] arguments) {
                Account account = Account.get(user);
                logger.debug("lock: user_id = " + account.user_id());
                if (Account.admin(account.user_id())) {
                    if (arguments == null || arguments.length == 0) {
                        long count = Account.count(Account.ACTIVE);
                        long page = count / 10;
                        long mod = count % 10;
                        StringBuilder builder = new StringBuilder("<b>Список пользователей</b>: ");
                        AtomicInteger total = new AtomicInteger();
                        Account.findByState(Account.ACTIVE, 0L, 10L).forEach(a -> {
                            builder.append("\n    ").append(a.user_id()).append(" - ").append(a.name());
                            total.incrementAndGet();
                        });
                        builder.append("\nВсего: <b>").append(count).append("</b>.");
                        builder.append("\nСтраница: <b>").append(1).append("</b> из <b>").append((mod > 0 ? page + 1 : page == 0 ? 1 : page)).append("</b>.");
                        send(sender, account.user_id(), builder.toString());
                    } else if (arguments.length >= 1) {
                        Integer block_user_id = 0;
                        try {
                            block_user_id = Integer.parseInt(arguments[0]);
                            if (Objects.equals(block_user_id, account.user_id())) {
                                send(sender, account.user_id(), "Нельзя блокировать самого себя!");
                            } else {
                                Account block_account = Account.get(block_user_id);
                                if (block_account.state() == Account.NEW) {
                                    send(sender, account.user_id(), "\"" + block_user_id + "\" не является идентификатором пользователя.");
                                } else if (block_account.state() == Account.LOCKED) {
                                    send(sender, account.user_id(), "Пользователь \"" + account.name() + "\" уже заблокирован.");
                                } else {
                                    block_account.state(Account.LOCKED).commit();
                                    send(sender, account.user_id(), "Пользователь \"" + account.name() + "\" заблокирован.");
                                }
                            }
                            return;
                        } catch (NumberFormatException ignore) {
                        }
                        if (arguments[0].equals("page")) {
                            Long page = 0L;
                            try {
                                page = Long.parseLong(arguments[1]);
                            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignore) {
                            }
                            if (page != 0L) {
                                page -= 1L;
                            }
                            long count = Account.count(Account.ACTIVE);
                            long all_page = count / 10;
                            long mod = count % 10;
                            if (page > all_page) {
                                page = all_page;
                            }
                            StringBuilder builder = new StringBuilder("<b>Список пользователей</b>: ");
                            AtomicInteger total = new AtomicInteger();
                            Account.findByState(Account.ACTIVE, page * 10L, 10L).forEach(a -> {
                                builder.append("\n    ").append(a.user_id()).append(" - ").append(a.name());
                                total.incrementAndGet();
                            });
                            builder.append("\nВсего: <b>").append(count).append("</b>.");
                            builder.append("\nСтраница: <b>").append(page + 1).append("</b> из <b>").append((mod > 0 ? all_page + 1 : all_page == 0 ? 1 : all_page)).append("</b>.");
                            send(sender, account.user_id(), builder.toString());
                        } else {
                            send(sender, account.user_id(), "\"" + block_user_id + "\" не является идентификатором пользователя.");
                        }
                    }
                }
            }
        });
        register(new BotCommand("unlock", "") {
            @Override
            public void execute(AbsSender sender, User user, Chat chat, String[] arguments) {
                Account account = Account.get(user);
                logger.debug("unlock: user_id = " + account.user_id());
                if (Account.admin(account.user_id())) {
                    if (arguments == null || arguments.length == 0) {
                        long count = Account.count(Account.LOCKED);
                        long page = count / 10;
                        long mod = count % 10;
                        StringBuilder builder = new StringBuilder("<b>Список заблокированых пользователей:</b>");
                        AtomicInteger total = new AtomicInteger();
                        Account.findByState(Account.LOCKED, 0L, 10L).forEach(a -> {
                            builder.append("\n    ").append(a.user_id()).append(" - ").append(a.name());
                            total.incrementAndGet();
                        });
                        builder.append("\nВсего: <b>").append(total.get()).append("</b>.");
                        builder.append("\nСтраница: <b>").append(1).append("</b> из <b>").append((mod > 0 ? page + 1 : page == 0 ? 1 : page)).append("</b>.");
                        send(sender, account.user_id(), builder.toString());
                    } else if (arguments.length >= 1) {
                        Integer block_user_id = 0;
                        try {
                            block_user_id = Integer.parseInt(arguments[0]);
                            Account block_account = Account.get(block_user_id);
                            if (block_account.state() == Account.NEW) {
                                send(sender, account.user_id(), "\"" + block_user_id + "\" не является идентификатором пользователя.");
                            } else if (block_account.state() == Account.ACTIVE) {
                                send(sender, account.user_id(), "Пользователь \"" + account.name() + "\" не заблокирован.");
                            } else {
                                block_account.state(Account.ACTIVE).commit();
                                if (ignore_counter.containsKey(block_user_id)) {
                                    ignore_counter.remove(block_user_id);
                                }
                                send(sender, account.user_id(), "Пользователь \"" + account.name() + "\" разблокирован.");
                            }
                            return;
                        } catch (NumberFormatException ignore) {
                        }
                        if (arguments[0].equals("page")) {
                            Long page = 0L;
                            try {
                                page = Long.parseLong(arguments[1]);
                            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignore) {

                            }
                            if (page != 0L) {
                                page -= 1L;
                            }
                            long count = Account.count(Account.LOCKED);
                            long all_page = count / 10;
                            long mod = count % 10;
                            if (page > all_page) {
                                page = all_page;
                            }
                            StringBuilder builder = new StringBuilder("<b>Список заблокированых пользователей:</b>");
                            AtomicInteger total = new AtomicInteger();
                            Account.findByState(Account.LOCKED, page * 10L, 10L).forEach(a -> {
                                builder.append("\n    ").append(a.user_id()).append(" - ").append(a.name());
                                total.incrementAndGet();
                            });
                            builder.append("\nВсего: <b>").append(count).append("</b>.");
                            builder.append("\nСтраница: <b>").append(page + 1).append("</b> из <b>").append((mod > 0 ? all_page + 1 : all_page == 0 ? 1 : all_page)).append("</b>.");
                            send(sender, account.user_id(), builder.toString());
                        } else {
                            send(sender, account.user_id(), "\"" + block_user_id + "\" не является идентификатором пользователя.");
                        }
                    }
                }
            }
        });
        register(new BotCommand("follow", "") {
            @Override
            public void execute(AbsSender sender, User user, Chat chat, String[] arguments) {
                Account account = Account.get(user);
                logger.debug("follow: user_id = " + account.user_id());
                if (arguments != null && arguments.length > 0) {
                    int max = Math.min(arguments.length, 10);
                    for (int i = 0; i < max; i++) {
                        if (Worker.contains(account.user_id(), arguments[i])) {
                            send(sender, account.user_id(), "Вы уже следите за " + arguments[i] + "!");
                        } else {
                            try {
                                if (Worker.follow(account, arguments[i])) {
                                    send(sender, account.user_id(), "Подписка на " + arguments[i] + " успешна!");
                                } else {
                                    send(sender, account.user_id(), "Не удалось подписаться на " + arguments[i] + "! Повторите попытку позднее.");
                                }
                            } catch (IOException | SQLException e) {
                                logger.debug("failed.");
                                logger.error("exception: ", e);
                                send(sender, account.user_id(), "Не удалось подписаться на " + arguments[i] + "! Повторите попытку позднее.");
                            }
                        }
                    }
                } else {
                    send(sender, account.user_id(), "Для того чтобы начать следить за обновлением фанфика наберите /follow 123456, где 123456 номер фанфика взятый из адреса.\nНапример адрес фанфика https://ficbook.net/readfic/5285211, чтобы начасть следить за ним необходимо набрать команду /follow 5285211.");
                }
            }
        });
        register(new BotCommand("unfollow", "") {
            @Override
            public void execute(AbsSender sender, User user, Chat chat, String[] arguments) {
                Account account = Account.get(user);
                logger.debug("unfollow: user_id = " + account.user_id());
                if (arguments != null && arguments.length > 0) {
                    int max = Math.min(arguments.length, 10);
                    for (int i = 0; i < max; i++) {
                        if (!Worker.contains(account.user_id(), arguments[i])) {
                            send(sender, account.user_id(), "Вы не следите за " + arguments[i] + "!");
                        } else {
                            try {
                                if (Worker.unfollow(account, arguments[i])) {
                                    send(sender, account.user_id(), "Подписка на " + arguments[i] + " успешно отменена!");
                                } else {
                                    send(sender, account.user_id(), "Не удалось отдписаться на " + arguments[i] + "! Повторите попытку позднее.");
                                }
                            } catch (SQLException e) {
                                logger.debug("failed.");
                                logger.error("exception: ", e);
                                send(sender, account.user_id(), "Не удалось отдписаться на " + arguments[i] + "! Повторите попытку позднее.");
                            }
                        }
                    }
                } else {
                    send(sender, account.user_id(), "Для того чтобы перестать следить за обновлением фанфика наберите /follow 123456, где 123456 номер фанфика взятый из адреса.\nНапример адрес фанфика https://ficbook.net/readfic/5285211, чтобы перестать следить за ним необходимо набрать команду /follow 5285211.");
                }
            }
        });
    }

    public static App app() {
        return app;
    }

    public static void main(String[] args) {
        try {
            LogManager.getLogManager().readConfiguration(App.class.getResourceAsStream("/logging.properties"));
        } catch (IOException e) {
            System.err.println("Could not setup logger configuration: " + e.toString());
        }
        File log_dir = new File("./logs");
        if (!log_dir.exists()) {
            log_dir.mkdirs();
        }
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("ftb", options);
            System.exit(1);
            return;
        }
        start();
    }

    public static void start() {
        CookieStorage.load();
        ApiContextInitializer.init();
        TelegramBotsApi api = new TelegramBotsApi();
        try {
            app = new App();
            api.registerBot(app);
            Worker.get().start();
        } catch (TelegramApiRequestException e) {
            throw new RuntimeException(e);
        }
    }

    public static void send(AbsSender sender, Integer id, String text) {
        SendMessage answer = new SendMessage();
        answer.setChatId(id.toString());
        answer.setText(text);
        answer.enableHtml(true);
        try {
            sender.sendMessage(answer);
        } catch (TelegramApiException e) {
            BotLogger.error(TAG, e);
        }
    }

    public static Gson gson() {
        if (gson == null) {
            logger.debug("create gson.");
            gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().registerTypeAdapter(CookieStorage.class, new CookieStorage.CookieAdapter()).create();
        }
        return gson;
    }

    public static Response get(String id, String url) throws IOException {
        logger.debug("[{}] send GET request.", id);
        Request request = new Request.Builder().url(url).build();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(httpLoggingInterceptor).cookieJar(CookieStorage.jar(id))
                .addInterceptor(userAgentInterceptor)
                .build();
        return client.newCall(request).execute();
    }

    public static Response get(String url) throws IOException {
        logger.debug("send GET request.");
        Request request = new Request.Builder().url(url).build();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(httpLoggingInterceptor)
                .addInterceptor(userAgentInterceptor)
                .build();
        return client.newCall(request).execute();
    }

    public static Response post(String id, String url, RequestBody request_body) throws IOException {
        logger.debug("[{}] send POST request.", id);
        Request request = new Request.Builder().url(url).post(request_body).build();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(httpLoggingInterceptor).cookieJar(CookieStorage.jar(id))
                .addInterceptor(userAgentInterceptor)
                .build();
        return client.newCall(request).execute();
    }

    public static Response post(String url, RequestBody request_body) throws IOException {
        logger.debug("send POST request.");
        Request request = new Request.Builder().url(url).post(request_body).build();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(httpLoggingInterceptor)
                .addInterceptor(userAgentInterceptor)
                .build();
        return client.newCall(request).execute();
    }

    @Override
    public void processNonCommandUpdate(Update update) {
        System.out.println(update.toString());
    }

    @Override
    protected boolean filter(Message message) {
        Integer user_id = message.getFrom().getId();
        Account account;
        if (!Account.contains(user_id)) {
            account = Account.get(message.getFrom());
        } else {
            account = Account.get(user_id);
        }
        if (account.state() == Account.LOCKED && !Account.admin(user_id)) {
            AtomicLong counter;
            if (ignore_counter.containsKey(user_id)) {
                counter = ignore_counter.get(user_id);
            } else {
                counter = new AtomicLong();
                ignore_counter.put(user_id, counter);
            }
            if (counter.getAndIncrement() < 3) {
                App.send(this, user_id, "Вы не можете пользоваться функционалом бота.");
            }
            return true;
        }
        return false;
    }

    @Override
    public String getBotUsername() {
        return S.get("bot.username");
    }

    @Override
    public String getBotToken() {
        return S.get("bot.token");
    }

    private static class UserAgentInterceptor implements Interceptor {

        private final String userAgent;

        public UserAgentInterceptor(String userAgent) {
            this.userAgent = userAgent;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", userAgent)
                    .build();
            return chain.proceed(requestWithUserAgent);
        }
    }
}
