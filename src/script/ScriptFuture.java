package script;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * A future created to allow asynchronous communication with the script engine.
 * Contains the default exception handlers.
 */
public class ScriptFuture<T> {
	private final CompletableFuture<T> future = new CompletableFuture<>();
	
	public void complete(T result) {
		future.complete(result);
		future.exceptionally(this::exceptionHandler);
	}
	
	public void completeExceptionally(Throwable throwable) {
		future.completeExceptionally(throwable);
	}

	public T get() throws InterruptedException, ExecutionException {
		return future.get();
	}

	public void thenAccept(Consumer<T> callback) {
		future
			.thenAccept(callback)
			.exceptionally(this::exceptionHandler);
	}

	public void thenRun(Runnable callback) {
		future
			.thenRun(callback)
			.exceptionally(this::exceptionHandler);
	}
	
	public void thenRunAlways(Runnable callback) {
		future
			.thenRun(callback)
			.exceptionally(e -> {
				exceptionHandler(e);
				callback.run();
				return null;
			})
			.exceptionally(this::exceptionHandler);
	}
	
	private <V> V exceptionHandler(Throwable e) {
		e.printStackTrace();
		return null;
	}
}
