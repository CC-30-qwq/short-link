package com.example.billing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 计费账户：余额扣减。
 * <p>
 * 核心并发保障（计费系统两大命门）：
 * <ol>
 *   <li><b>幂等</b>：同一订单号（orderId）无论并发/重复请求多少次，只成功扣费一次</li>
 *   <li><b>不超扣</b>：余额不足时拒绝，余额绝不会被扣成负数</li>
 * </ol>
 * 金额单位：分（用 long 避免浮点误差，这是计费系统的铁律）。
 */
public class Account {

    /** 余额（分） */
    private final AtomicLong balance;

    /** 已处理订单集合，作为幂等键 */
    private final ConcurrentHashMap<String, Boolean> processedOrders = new ConcurrentHashMap<>();

    public Account(long initialBalance) {
        this.balance = new AtomicLong(initialBalance);
    }

    public long getBalance() {
        return balance.get();
    }

    /**
     * 扣费。
     *
     * @param amount  扣费金额（分，必须 > 0）
     * @param orderId 订单号（幂等键）
     * @return true=扣费成功；false=余额不足或订单已处理过
     */
    public boolean deduct(long amount, String orderId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("扣费金额必须 > 0");
        }
        // 1. 幂等：putIfAbsent 原子，同一订单只有第一个请求能占位成功
        if (processedOrders.putIfAbsent(orderId, Boolean.TRUE) != null) {
            return false;
        }
        // 2. CAS 不超扣：循环尝试，余额不足则拒绝
        while (true) {
            long current = balance.get();
            if (current < amount) {
                // 没扣成，释放幂等标记，允许之后余额充足时重试
                processedOrders.remove(orderId);
                return false;
            }
            if (balance.compareAndSet(current, current - amount)) {
                return true;
            }
        }
    }
}
