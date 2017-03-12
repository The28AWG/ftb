package io.github.the28awg.ftb.test;

import io.github.the28awg.ftb.Account;
import io.github.the28awg.ftb.C;
import io.github.the28awg.ftb.S;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;

public class AccountTest {

    private static final int CONTAINS_USER = 317925369;
    private static final int BAD_USER = 317925360;
    private static int generated_user;

    @BeforeClass
    public static void before() {
        generated_user = new Random().nextInt(10000);
        System.out.println(generated_user);
        S.set(C.URL_KEY, "jdbc:sqlite:ftb_test.db");
    }
    @Test
    public void create() {
        Account.get(generated_user).commit();
    }

    @Test
    public void contains() {
        Assert.assertTrue(Account.contains(CONTAINS_USER));
        Assert.assertFalse(Account.contains(generated_user));
        Assert.assertFalse(Account.contains(BAD_USER));
    }

    @Test
    public void get() {
        Assert.assertTrue(Account.get(CONTAINS_USER).state() == Account.ACTIVE);
        Assert.assertTrue(Account.get(generated_user).state() == Account.NEW);
        Assert.assertTrue(Account.get(BAD_USER).state() == Account.NEW);
    }

    @Test
    public void findByState() {
        System.out.println(Account.findByState(Account.LOCKED).size());
        System.out.println(Account.findByState(Account.ACTIVE).size());
        System.out.println(Account.findByState(Account.NEW).size());
    }

    @Test
    public void count() {
        long page = Account.count() / 10;
        long mod = Account.count() % 10;

        System.out.println("count: " + Account.count());
        System.out.println("page: " + (mod > 0 ? page + 1 : page));
    }
}
