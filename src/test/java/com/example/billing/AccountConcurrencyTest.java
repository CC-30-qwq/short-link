package com.example.billing;

import com.example.support.Concurrency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计费账户的高并发测试：幂等 + 不超扣 + 余额守恒。
 */
class AccountConcurrencyTest {

    @Test
    @DisplayName("并发重复扣费同一订单：只成功一次（幂等）")
    void deduct_isIdempotent_underConcurrency() throws Exception {
        Account account = new Account(1000);

        // 200 个线程用同一个订单号并发扣 1 分
        List<Boolean> results = Concurrency.run(200, i -> account.deduct(1, "same-order"));

        long success = results.stream().filter(Boolean::booleanValue).count();
        assertThat(success).isEqualTo(1);                  // 只有一次扣费成功
        assertThat(account.getBalance()).isEqualTo(999);   // 余额只减了 1 分
    }

    @Test
    @DisplayName("并发扣费：余额不会被扣成负数（不超扣）")
    void deduct_neverOverdraw_underConcurrency() throws Exception {
        Account account = new Account(50);

        // 200 个线程各扣 1 分（不同订单号），但余额只有 50 分
        List<Boolean> results = Concurrency.run(200, i -> account.deduct(1, "order-" + i));

        long success = results.stream().filter(Boolean::booleanValue).count();
        assertThat(success).isEqualTo(50);                // 只有 50 个成功
        assertThat(account.getBalance()).isEqualTo(0);    // 余额精确到 0
        assertThat(account.getBalance()).isNotNegative(); // 绝不超扣
    }

    @Test
    @DisplayName("并发扣费：余额守恒（余额 + 已扣 = 初始）")
    void balance_isConsistent_underConcurrency() throws Exception {
        long initial = 100_000;   // 1000 元 = 100000 分
        long amount = 3;          // 每次扣 3 分
        Account account = new Account(initial);

        List<Boolean> results = Concurrency.run(500, i -> account.deduct(amount, "order-" + i));

        long success = results.stream().filter(Boolean::booleanValue).count();
        // 守恒：当前余额 + 成功扣掉的 = 初始余额
        assertThat(account.getBalance() + success * amount).isEqualTo(initial);
    }
}
