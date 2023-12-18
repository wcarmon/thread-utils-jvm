package io.github.wcarmon.thread;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/** A ThreadPoolExecutor which sends unhandled errors to a sink */
public final class ErrorHandlingThreadPool extends ThreadPoolExecutor {

    private final ErrSink errSink;

    private ErrorHandlingThreadPool(
            int corePoolSize,
            int maximumPoolSize,
            Duration keepAlive,
            ErrSink errSink,
            @Nullable ThreadFactory threadFactory) {

        super(
                corePoolSize,
                maximumPoolSize,
                Objects.requireNonNull(keepAlive, "keepAlive is required and null.").getSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory == null ? Executors.defaultThreadFactory() : threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());

        checkArgument(corePoolSize > 1, "There's no point if you only need 1 thread");
        checkArgument(
                keepAlive.compareTo(Duration.ofSeconds(1)) > 0,
                "keepAlive too short: " + keepAlive);
        checkArgument(maximumPoolSize >= corePoolSize, "maximumPoolSize too small");
        Objects.requireNonNull(errSink, "errSink is required and null.");

        this.errSink = errSink;
    }

    /**
     * builder pattern
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        if (t != null) {
            errSink.report(t);
            return;
        }

        // GOTCHA: unfortunately stupid java treats RuntimeExceptions
        //   different from Checked here, t == null for runtime exceptions

        // -- Invariant: t == null
        if (!(r instanceof Future)) {
            return;
        }

        try {
            ((Future<?>) r).get();

        } catch (ExecutionException ee) {
            errSink.report(ee.getCause());

        } catch (Exception ex) {
            errSink.report(ex);
        }
    }

    public static class Builder {

        private int corePoolSize;
        private ErrSink errSink;
        private Duration keepAlive;
        private int maximumPoolSize;
        private @Nullable ThreadFactory threadFactory;

        Builder() {}

        public ErrorHandlingThreadPool build() {
            return new ErrorHandlingThreadPool(
                    this.corePoolSize,
                    this.maximumPoolSize,
                    this.keepAlive,
                    this.errSink,
                    this.threadFactory);
        }

        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public Builder errSink(ErrSink errSink) {
            this.errSink = errSink;
            return this;
        }

        public Builder keepAlive(Duration keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public Builder threadFactory(@Nullable ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }
    }
}
