package com.skillloop.ui;

import com.skillloop.exception.SkillLoopException;

import javax.swing.JComponent;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs database work off the Event Dispatch Thread.
 *
 * <p>Every panel uses {@link #run(Component, Callable, Consumer)}: the task executes in a
 * {@link SwingWorker} background thread, the UI stays responsive and shows a wait cursor, and
 * failures are reported with a dialog instead of being swallowed.</p>
 */
public final class AsyncRunner {

    private AsyncRunner() {
        // utility class
    }

    /**
     * Executes {@code task} in the background and hands the result to {@code onSuccess} on the
     * EDT. Errors are shown as a dialog on the EDT as well.
     */
    public static <T> void run(Component parent, Callable<T> task, Consumer<T> onSuccess) {
        run(parent, null, task, onSuccess, null);
    }

    /** Variant with an explicit failure callback (for example to reset a button). */
    public static <T> void run(Component parent, Callable<T> task, Consumer<T> onSuccess, Runnable onFailure) {
        run(parent, null, task, onSuccess, onFailure);
    }

    /**
     * @param target    component that shows the wait cursor (may be null to use {@code parent})
     * @param onFailure optional callback executed on the EDT when the task failed
     */
    public static <T> void run(Component parent, JComponent target, Callable<T> task,
                               Consumer<T> onSuccess, Runnable onFailure) {
        Component cursorTarget = target != null ? target : parent;
        if (cursorTarget != null) {
            cursorTarget.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                if (cursorTarget != null) {
                    cursorTarget.setCursor(Cursor.getDefaultCursor());
                }
                try {
                    T result = get();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (onFailure != null) {
                        onFailure.run();
                    }
                    report(parent, cause);
                }
            }
        };
        worker.execute();
    }

    /** Convenience overload for tasks without a return value. */
    public static void runVoid(Component parent, Runnable task, Runnable onSuccess) {
        run(parent, () -> {
            task.run();
            return Boolean.TRUE;
        }, result -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
    }

    /** Turns an exception into a friendly message and shows it on the EDT. */
    public static void report(Component parent, Throwable error) {
        String message;
        if (error instanceof SkillLoopException) {
            message = error.getMessage();
        } else if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
            message = error.getMessage();
        } else {
            message = "Unexpected error: " + error;
        }
        Dialogs.error(parent, message);
    }

    /** Loads several independent lists in one background task and refreshes the UI once. */
    public static void refreshAll(Component parent, Runnable reload) {
        run(parent, (Callable<Void>) () -> {
            reload.run();
            return null;
        }, result -> {
        });
    }

    /** Joins validation problems into a bullet list for the user. */
    public static String bulletList(List<String> problems) {
        StringBuilder text = new StringBuilder("<html><b>Please fix the following:</b><ul>");
        for (String problem : problems) {
            text.append("<li>").append(problem).append("</li>");
        }
        return text.append("</ul></html>").toString();
    }

    /** Small helper for "lazy" suppliers used by the panels. */
    public static <T> Supplier<T> lazy(Callable<T> callable) {
        return () -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
