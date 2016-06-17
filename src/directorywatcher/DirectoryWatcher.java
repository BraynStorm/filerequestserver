package directorywatcher;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Created by Braynstorm on 15.6.2016 г..
 */
public class DirectoryWatcher {
	private final Path directory;
	private final WatchEvent.Kind[] events;

	private Thread workerThread;
	private Worker worker;
	private boolean watching = false;

	private WatchService watchService;
	private BiConsumer<Path, WatchEvent.Kind> consumer;

	private class Worker implements Runnable {
		@Override
		public void run() {
			while (watching) {
				try {
//					System.out.println("Listening...");
					final WatchKey key = watchService.take();
					Thread.sleep(100);
					final List<WatchEvent<?>> watchEvents = key.pollEvents();
					watchEvents.forEach(watchEvent -> consumer.accept((Path) watchEvent.context(), watchEvent.kind()));
					key.reset();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			try {
				watchService.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@SafeVarargs
	public DirectoryWatcher(Path directory, WatchEvent.Kind<Path>... events) {
		this.directory = directory;
		this.events = events;

		consumer = ((path, kind) -> {
		}); // Empty.
	}

	public void start() throws IOException {
		if (workerThread == null || !workerThread.isAlive()) {
			watchService = FileSystems.getDefault().newWatchService();
			this.directory.register(watchService, events);

			watching = true;
			worker = new Worker();

			workerThread = new Thread(worker, "DirectoryWatcherWorker");
			workerThread.start();
		}
	}

	public void setEventListener(BiConsumer<Path, WatchEvent.Kind> consumer) {
		this.consumer = consumer;
	}

	public void stop() {
		watching = false;
	}
}
