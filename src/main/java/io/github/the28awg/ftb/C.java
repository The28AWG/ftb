package io.github.the28awg.ftb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Created by the28awg on 13.01.17.
 */
public class C {

    public static final String URL_KEY = "connection.url";
    public static Connection connection(String url) {
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Connection me() {
        return connection(S.get(URL_KEY));
    }

}
