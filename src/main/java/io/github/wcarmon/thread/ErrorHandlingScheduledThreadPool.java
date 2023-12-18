package io.github.wcarmon.thread;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import org.jetbrains.annotations.Nullable;

/** A ScheduledThreadPoolExecutor which sends unhandled errors to a sink */
public final class ErrorHandlingScheduledThreadPool extends ScheduledThreadPoolExecutor {

    private final ErrSink errSink;

    private ErrorHandlingScheduledThreadPool(
            int corePoolSize, ErrSink errSink, @Nullable ThreadFactory threadFactory) {

        super(
                corePoolSize,
                threadFactory == null ? Executors.defaultThreadFactory() : threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());

        checkArgument(corePoolSize > 0, "at least 1 thread required");
        Objects.requireNonNull(errSink, "errSink is required and null.");

        this.errSink = errSink;
    }

    /**
     * Builder pattern
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
        private @Nullable ThreadFactory threadFactory;

        Builder() {}

        public ErrorHandlingScheduledThreadPool build() {
            return new ErrorHandlingScheduledThreadPool(
                    this.corePoolSize, this.errSink, this.threadFactory);
        }

        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public Builder errSink(ErrSink errSink) {
            this.errSink = errSink;
            return this;
        }

        public Builder threadFactory(@Nullable ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }
    }
}
