package com.example.material;

import com.example.support.Concurrency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 素材上传的高并发测试：并发去重（秒传）。
 */
class MaterialStoreConcurrencyTest {

    @Test
    @DisplayName("并发上传同一文件：只真正落盘一次（秒传去重）")
    void upload_deduplicatesSameFile_underConcurrency() throws Exception {
        MaterialStore store = new MaterialStore();

        // 200 个线程并发上传同一个 MD5
        List<String> paths = Concurrency.run(200, i -> store.upload("same-md5", "content"));

        assertThat(store.getActualStoreCount()).isEqualTo(1);  // 只落盘一次
        assertThat(paths).allMatch(p -> p.equals("/material/same-md5.bin")); // 都拿到同一路径
    }

    @Test
    @DisplayName("并发上传不同文件：全部正确落盘")
    void upload_storesDistinctFiles_underConcurrency() throws Exception {
        MaterialStore store = new MaterialStore();

        // 200 个线程上传 200 个不同 MD5
        List<String> paths = Concurrency.run(200, i -> store.upload("md5-" + i, "content-" + i));

        assertThat(store.getActualStoreCount()).isEqualTo(200); // 每个都落盘
        assertThat(paths).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.range(0, 200)
                        .mapToObj(i -> "/material/md5-" + i + ".bin")
                        .toList());
    }
}
