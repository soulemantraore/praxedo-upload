package com.praxedo.upload.domain.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileStatusTest {

    @Test
    void pending_can_go_to_scanning_or_expired() {
        assertThat(FileStatus.PENDING.canTransitionTo(FileStatus.SCANNING)).isTrue();
        assertThat(FileStatus.PENDING.canTransitionTo(FileStatus.EXPIRED)).isTrue();
        assertThat(FileStatus.PENDING.canTransitionTo(FileStatus.CLEAN)).isFalse();
    }

    @Test
    void scanning_can_reach_verdicts() {
        assertThat(FileStatus.SCANNING.canTransitionTo(FileStatus.CLEAN)).isTrue();
        assertThat(FileStatus.SCANNING.canTransitionTo(FileStatus.INFECTED)).isTrue();
        assertThat(FileStatus.SCANNING.canTransitionTo(FileStatus.SCAN_FAILED)).isTrue();
    }

    @Test
    void scan_failed_can_be_retried() {
        assertThat(FileStatus.SCAN_FAILED.canTransitionTo(FileStatus.SCANNING)).isTrue();
    }

    @Test
    void terminal_states_are_final() {
        assertThat(FileStatus.CLEAN.canTransitionTo(FileStatus.SCANNING)).isFalse();
        assertThat(FileStatus.INFECTED.canTransitionTo(FileStatus.CLEAN)).isFalse();
    }

    @Test
    void only_clean_is_downloadable() {
        assertThat(FileStatus.CLEAN.isDownloadable()).isTrue();
        assertThat(FileStatus.PENDING.isDownloadable()).isFalse();
        assertThat(FileStatus.INFECTED.isDownloadable()).isFalse();
    }
}
