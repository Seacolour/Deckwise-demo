package com.xiaoyuan.paperpuppyagent.core.deadline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicDeadlineManager 单元测试。
 */
class DynamicDeadlineManagerTest {

    @Test
    void testNotExpiredInitially() {
        DynamicDeadlineManager mgr = new DynamicDeadlineManager(
                20000, 15000, 120000, "test:1");
        assertFalse(mgr.isExpired(), "刚创建时不应超时");
        assertTrue(mgr.getRemainingBudgetMs() > 0);
    }

    @Test
    void testExpiredWhenBudgetExhausted() throws InterruptedException {
        // 基础预算 100ms，无工具调用
        DynamicDeadlineManager mgr = new DynamicDeadlineManager(
                100, 50, 1000, "test:2");
        assertFalse(mgr.isExpired());
        // 等待超过 100ms
        Thread.sleep(150);
        assertTrue(mgr.isExpired(), "超过基础预算后应超时");
    }

    @Test
    void testHeartbeatExtendsBudget() throws InterruptedException {
        // 基础预算 100ms，每次工具 +100ms
        DynamicDeadlineManager mgr = new DynamicDeadlineManager(
                100, 100, 1000, "test:3");
        // 等待 80ms，接近超时
        Thread.sleep(80);
        assertFalse(mgr.isExpired());
        // 工具心跳
        mgr.onToolExecuted();
        // 应该被延长
        assertTrue(mgr.getRemainingBudgetMs() > 50);
        // 再等 80ms（总共约 160ms），但因为心跳应该还没超时
        Thread.sleep(80);
        assertFalse(mgr.isExpired(), "心跳后应延长超时");
    }

    @Test
    void testHardCapEnforced() throws InterruptedException {
        // 基础预算 200ms，每次工具 +200ms，硬上限 300ms
        DynamicDeadlineManager mgr = new DynamicDeadlineManager(
                200, 200, 300, "test:4");
        // 持续调用工具
        for (int i = 0; i < 5; i++) {
            mgr.onToolExecuted();
            Thread.sleep(50);
        }
        // 总共已超过 300ms
        Thread.sleep(100);
        assertTrue(mgr.isExpired(), "超过硬上限后应强制超时");
    }

    @Test
    void testElapsedTimeTracking() throws InterruptedException {
        DynamicDeadlineManager mgr = new DynamicDeadlineManager(
                10000, 5000, 60000, "test:5");
        assertTrue(mgr.getElapsedMs() < 100);
        Thread.sleep(100);
        assertTrue(mgr.getElapsedMs() >= 100);
    }
}
