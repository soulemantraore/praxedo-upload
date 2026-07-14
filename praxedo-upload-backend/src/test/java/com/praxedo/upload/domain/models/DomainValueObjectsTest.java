package com.praxedo.upload.domain.models;

import com.praxedo.upload.domain.models.ApiClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainValueObjectsTest {

    @Test
    void page_result_computes_total_pages() {
        PageResult<String> p = PageResult.of(List.of("a", "b"), 0, 10, 25);
        assertThat(p.totalPages()).isEqualTo(3);
        assertThat(p.items()).containsExactly("a", "b");
    }

    @Test
    void file_query_defaults_are_sane() {
        FileQuery q = FileQuery.of(UUID.randomUUID(), null, null, 0, 20);
        assertThat(q.size()).isEqualTo(20);
    }

    @Test
    void file_query_clamps_page_size() {
        FileQuery q = FileQuery.of(UUID.randomUUID(), null, null, -3, 500);
        assertThat(q.page()).isEqualTo(0);
        assertThat(q.size()).isEqualTo(100);
    }

    @Test
    void status_counts_holds_all_buckets() {
        StatusCounts c = new StatusCounts(8, 4, 2, 1, 1);
        assertThat(c.total()).isEqualTo(8);
        assertThat(c.blocked()).isEqualTo(1);
    }

    @Test
    void api_client_is_active_by_construction() {
        ApiClient c = new ApiClient(UUID.randomUUID(), "Batir SA", "hash", true, Instant.parse("2026-07-10T10:00:00Z"));
        assertThat(c.active()).isTrue();
    }
}
