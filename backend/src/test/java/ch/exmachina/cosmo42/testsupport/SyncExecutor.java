package ch.exmachina.cosmo42.testsupport;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class SyncExecutor extends AbstractExecutorService {

    private volatile boolean shutdown = false;

    public static ExecutorService newInstance() {
        return new SyncExecutor();
    }

    @Override
    public void execute(Runnable command) {
        if (shutdown) {
            throw new IllegalStateException("SyncExecutor is shut down");
        }
        command.run();
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
