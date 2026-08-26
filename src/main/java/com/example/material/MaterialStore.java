package com.example.material;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 素材上传存储：并发去重（秒传）。
 * <p>
 * 核心并发保障：相同内容（MD5 相同）的文件并发上传时，只有第一个真正落盘，
 * 其余请求直接复用已有路径（秒传），避免重复存储浪费磁盘与带宽。
 */
public class MaterialStore {

    /** md5 -> 存储路径 */
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    /** 真正落盘次数（用于验证去重效果） */
    private final AtomicInteger actualStoreCount = new AtomicInteger(0);

    /**
     * 上传素材。
     *
     * @param md5     文件内容 MD5（去重键）
     * @param content 文件内容（真实场景是二进制流，这里用字符串示意）
     * @return 存储路径（相同 MD5 返回相同路径）
     */
    public String upload(String md5, String content) {
        String existing = store.get(md5);
        if (existing != null) {
            return existing; // 秒传：内容已存在
        }
        String path = "/material/" + md5 + ".bin";
        // putIfAbsent 原子：并发下只有一个线程能放入成功
        String prev = store.putIfAbsent(md5, path);
        if (prev == null) {
            actualStoreCount.incrementAndGet(); // 只有第一个线程真正落盘
            return path;
        }
        return prev; // 被别的线程抢先存储，走秒传
    }

    /** 真正落盘次数 */
    public int getActualStoreCount() {
        return actualStoreCount.get();
    }
}
