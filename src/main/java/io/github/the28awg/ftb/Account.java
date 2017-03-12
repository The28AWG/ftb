package io.github.the28awg.ftb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.api.objects.Chat;
import org.telegram.telegrambots.api.objects.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Account {

    private static Logger logger = LoggerFactory.getLogger(Account.class.getName());
    private static final String DEFAULT_ANONYMOUS_USERNAME = "Anonymous";
    private static final String DEFAULT_ANONYMOUS_USERNAME_KEY = "account.anonymous";
    public static final int NEW = 0;
    public static final int ACTIVE = 1;
    public static final int LOCKED = 2;
    private static final Map<Integer, Account> CACHE = new ConcurrentHashMap<>();
    private static final String SELECT_BY_USER_ID = "SELECT user_name, first_name, last_name, state, created_date, modified_date FROM account WHERE user_id = ?;";
    private static final String SELECT_BY_STATE = "SELECT user_id, user_name, first_name, last_name, state, created_date, modified_date FROM account WHERE state = ? LIMIT ?, ?;";
    private static final String INSERT = "INSERT INTO account (user_id, user_name, first_name, last_name, state, created_date, modified_date) VALUES (?, ?, ?, ?, ?, ?, ?);";
    private static final String UPDATE = "UPDATE account SET user_name = ?, first_name = ?, last_name = ?, state = ?, modified_date = ? WHERE user_id = ?;";
    private static final String COUNT = "SELECT count(*) FROM account;";
    private static final String COUNT_BY_STATE = "SELECT count(*) FROM account WHERE state = ?;";

    private Integer user_id;
    private String user_name;
    private String first_name;
    private String last_name;
    private LocalDateTime created_date;
    private LocalDateTime modified_date;
    private Integer state;

    public Account() {
        this.state = NEW;
        this.created_date = LocalDateTime.now();
        this.modified_date = LocalDateTime.now();
    }

    public Account(Integer user_id) {
        this();
        this.user_id = user_id;
    }

    private Account(Integer user_id, String user_name, String first_name, String last_name, Integer state, LocalDateTime created_date, LocalDateTime modified_date) {
        this.user_id = user_id;
        this.user_name = user_name;
        this.first_name = first_name;
        this.last_name = last_name;
        this.state = state;
        this.created_date = created_date;
        this.modified_date = modified_date;
    }

    public static Account get(User user) {
        Integer user_id = user.getId();
        Account account = get(user_id);
        if (account.state == NEW) {
            create(account.user_name(user.getUserName()).first_name(user.getFirstName()).last_name(user.getLastName()).state(ACTIVE));
        }
        return account;
    }

    public static boolean admin(Integer user_id) {
        return user_id.equals(317925369);
    }

    private static void create(Account account) {
        logger.debug("create: " + account.toString());
        try(Connection c = C.me(); PreparedStatement s = c.prepareStatement(INSERT)) {
            s.setInt(1, account.user_id());
            s.setString(2, account.user_name());
            s.setString(3, account.first_name());
            s.setString(4, account.last_name());
            s.setInt(5, account.state());
            s.setString(6, account.created_date().toString());
            s.setString(7, account.modified_date().toString());
            if (s.executeUpdate() > 0) {
                CACHE.put(account.user_id(), account);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean contains(Integer user_id) {
        return get(user_id).state != NEW;
    }

    public static Account get(Integer user_id) {
        if (CACHE.containsKey(user_id)) {
            return CACHE.get(user_id);
        }
        try(Connection c = C.me(); PreparedStatement s = c.prepareStatement(SELECT_BY_USER_ID)) {
            s.setInt(1, user_id);
            try (ResultSet r = s.executeQuery()) {
                Account account;
                if (r.next()) {
                    account = new Account(user_id, r.getString(1), r.getString(2), r.getString(3), r.getInt(4), LocalDateTime.parse(r.getString(5)), LocalDateTime.parse(r.getString(6)));
                } else {
                    account = new Account(user_id);
                }
                CACHE.put(user_id, account);
                return account;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Account> findByState(Integer state) {
        return findByState(state, 0L, -1L);
    }

    public static List<Account> findByState(Integer state, Long offset, Long limit) {
        List<Account> accounts = new ArrayList<>();
        try(Connection c = C.me(); PreparedStatement s = c.prepareStatement(SELECT_BY_STATE)) {
            s.setInt(1, state);
            s.setLong(2, offset);
            s.setLong(3, limit);

            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    Account account = new Account(r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5), LocalDateTime.parse(r.getString(6)), LocalDateTime.parse(r.getString(7)));
                    accounts.add(account);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return accounts;
    }

    private static void update(Account account) {
        logger.debug("update: " + account.toString());
        try(Connection c = C.me(); PreparedStatement s = c.prepareStatement(UPDATE)) {
            s.setString(1, account.user_name());
            s.setString(2, account.first_name());
            s.setString(3, account.last_name());
            s.setInt(4, account.state());
            s.setString(5, account.modified_date().toString());
            s.setInt(6, account.user_id());
            s.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static long count() {
        logger.debug("count");
        try(Connection c = C.me(); PreparedStatement s = c.prepareStatement(COUNT); ResultSet r = s.executeQuery()) {
            if (r.next()) {
                return r.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    public static long count(Integer state) {
        logger.debug("count");
        try(Connection c = C.me(); PreparedStatement s = c.prepareStatement(COUNT_BY_STATE)) {
            s.setInt(1, state);
            try (ResultSet r = s.executeQuery()) {
                if (r.next()) {
                    return r.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    public Integer user_id() {
        return user_id;
    }

    public Account user_id(Integer user_id) {
        this.user_id = user_id;
        return this;
    }

    public String user_name() {
        return user_name;
    }

    public Account user_name(String user_name) {
        this.user_name = user_name;
        return this;
    }

    public String first_name() {
        return first_name;
    }

    public Account first_name(String first_name) {
        this.first_name = first_name;
        return this;
    }

    public String last_name() {
        return last_name;
    }

    public Account last_name(String last_name) {
        this.last_name = last_name;
        return this;
    }

    public LocalDateTime created_date() {
        return created_date;
    }

    public LocalDateTime modified_date() {
        return modified_date;
    }

    public Integer state() {
        return state;
    }

    public Account state(Integer state) {
        this.state = state;
        return this;
    }

    public void commit() {
        this.modified_date = LocalDateTime.now();
        if (state != NEW) {
            update(this);
        } else {
            create(this);
        }
    }

    public String name() {
        if (user_name != null && !user_name.isEmpty()) {
            return user_name;
        }
        if (first_name != null && !first_name.isEmpty() && last_name != null && !last_name.isEmpty()) {
            return first_name + " " + last_name;
        }
        if (first_name != null && !first_name.isEmpty()) {
            return first_name;
        }
        if (last_name != null && !last_name.isEmpty()) {
            return last_name;
        }
        return S.get(DEFAULT_ANONYMOUS_USERNAME_KEY, DEFAULT_ANONYMOUS_USERNAME);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Account account = (Account) o;

        if (user_id != null ? !user_id.equals(account.user_id) : account.user_id != null) return false;
        if (user_name != null ? !user_name.equals(account.user_name) : account.user_name != null) return false;
        if (first_name != null ? !first_name.equals(account.first_name) : account.first_name != null) return false;
        if (last_name != null ? !last_name.equals(account.last_name) : account.last_name != null) return false;
        if (created_date != null ? !created_date.equals(account.created_date) : account.created_date != null)
            return false;
        if (modified_date != null ? !modified_date.equals(account.modified_date) : account.modified_date != null)
            return false;
        return state != null ? state.equals(account.state) : account.state == null;

    }

    @Override
    public int hashCode() {
        int result = user_id != null ? user_id.hashCode() : 0;
        result = 31 * result + (user_name != null ? user_name.hashCode() : 0);
        result = 31 * result + (first_name != null ? first_name.hashCode() : 0);
        result = 31 * result + (last_name != null ? last_name.hashCode() : 0);
        result = 31 * result + (created_date != null ? created_date.hashCode() : 0);
        result = 31 * result + (modified_date != null ? modified_date.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Account{" +
                "user_id=" + user_id +
                ", user_name='" + user_name + '\'' +
                ", first_name='" + first_name + '\'' +
                ", last_name='" + last_name + '\'' +
                ", created_date=" + created_date +
                ", modified_date=" + modified_date +
                ", state=" + state +
                '}';
    }
}
