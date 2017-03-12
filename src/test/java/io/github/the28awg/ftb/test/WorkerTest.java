package io.github.the28awg.ftb.test;

import io.github.the28awg.ftb.Account;
import io.github.the28awg.ftb.App;
import io.github.the28awg.ftb.CookieStorage;
import io.github.the28awg.ftb.Worker;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public class WorkerTest {

    @BeforeClass
    public static void before() {
        CookieStorage.load();
    }

    @Test
    public void collection() throws IOException, SQLException {
//        Response response = App.get("Worker", "https://ficbook.net/home/collections?type=update");
        Document document = Jsoup.parse(new File("./index.html"),"UTF-8");
        document.select("div.container-fluid.js-unfollow-collection-container").stream().filter(element -> element.hasAttr("style"))
                .forEach(element -> {
                    Element description = element.select("article.block > div.description > h3 > a").first();
                    String url = description.attr("href");
                    String title = description.text();
                    System.out.println(url);
                    System.out.println(title);
                });
    }
}
