package filerequestserver;

import directorywatcher.DirectoryWatcher;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Created by Braynstorm on 11.6.2016 г..
 */
public class Server {
	private static final MessageDigest sha1;
	private static final byte REQUEST_SIZE = 1 + Long.BYTES; // packetType; ID.

	static {

		// A save from IntelliJ.
		MessageDigest sha;
		try {
			sha = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			sha = null;
		}
		sha1 = sha;
	}

	private long lastDate = 0;

	private final Path directory;

	private final Map<Integer, ByteBuffer> bufferedFiles;
	private final Map<Integer, ByteBuffer> bufferedHashes;
	private final Map<Integer, Path> idToPath;
	private final Set<String> filenames;

	private Selector selector;
	private ServerSocketChannel serverSocketChannel;

	private Map<Integer, Statistic> downloadsSince; //TODO Interesting implementation following. To be continued...
	private Map<SocketChannel, Connection> connections;


	private DirectoryWatcher watcher;

	private class Connection {
		private final SocketChannel channel;
		private final ByteBuffer currentPacket;

		private Queue<ByteBuffer> packets;
		private long acceptedOn;
		private long lastAction;

		public Connection(SocketChannel channel) {
			lastAction = System.currentTimeMillis();
			acceptedOn = lastAction;

			this.channel = channel;

			currentPacket = ByteBuffer.allocate(REQUEST_SIZE);
			packets = new ArrayDeque<>(5);
		}

		void sendPacket(ByteBuffer packet) throws IOException {
			packets.add(packet.asReadOnlyBuffer());
		}

		void sendNextPacket(SelectionKey key) throws IOException {
			if (!packets.isEmpty()) {
				ByteBuffer packet = packets.peek();
				channel.write(packet);

				if (!packet.hasRemaining()) {
					packets.remove();
					key.interestOps(SelectionKey.OP_READ);
				} else
					key.interestOps(SelectionKey.OP_WRITE);
			}
		}


		void readPacket(SelectionKey key) throws IOException {
			channel.read(currentPacket);

			if (currentPacket.hasRemaining()) {
				currentPacket.flip();
				processRequest();
				key.interestOps(SelectionKey.OP_WRITE);
				currentPacket.clear();
			} else {
				key.interestOps(SelectionKey.OP_READ);
			}
		}

		private void processRequest() throws IOException {
			final byte op = currentPacket.get();
			final Packet[] packets = Packet.values();
			if (op > packets.length) {
				addPenalty();
				return;
			}

			final long fileID = currentPacket.getLong();

			System.out.println(toString() + " request=" + op + "; fieldID=" + Long.toHexString(fileID));

			switch (packets[op]) {
				case REQUEST_FILE:
					if (bufferedFiles.containsKey(fileID)) {
						sendPacket(bufferedFiles.get(fileID));
					} else {
						//TODO Read the file and send it.

					}
					break;
				case REQUEST_HASH_CHECK:
					if (bufferedHashes.containsKey(fileID)) {
						sendPacket(bufferedHashes.get(fileID));
					} else {
						//TODO Read the file hash it and send it.
					}
					break;
			}
		}

		private void addPenalty() {
			//TODO addPenalty.
			System.out.println(toString() + " Add penalty.");
		}

		@Override
		public String toString() {
			try {
				final SocketAddress remoteAddress = channel.getRemoteAddress();
				if (remoteAddress != null)
					return '[' + remoteAddress.toString() + ']';
				else
					return "[DISCONNECTED_CHANNEL]";
			} catch (IOException e) {
				return "[INVALID_OR_CLOSED_CHANNEL]";
			}
		}
	}

	private class Statistic {
		long downloaded = 0;
		long avgTimeBetweenRequests = Long.MAX_VALUE;
	}

	public Server(File directory) throws IOException {
		this(directory.toPath());
	}

	public Server(Path directory) throws IOException {
		this.directory = directory;

		File directoryFile = directory.toFile();
		directoryFile.mkdirs();

		if (!directoryFile.isDirectory())
			throw new IOException("The file supplied isn't an existing and accessible directory.");

		bufferedFiles = new ConcurrentHashMap<>();
		bufferedHashes = new ConcurrentHashMap<>();
		idToPath = new ConcurrentHashMap<>();

		filenames = Collections.newSetFromMap(new ConcurrentHashMap<>());

		Files.list(directory).filter(path -> !path.toFile().isDirectory()).parallel().forEach(path -> {
			try (final DataInputStream inputStream = new DataInputStream(Files.newInputStream(path))) {
				final byte[] bytes = new byte[inputStream.available()];
				inputStream.readFully(bytes);

				final int id = Utils.getIDFromFilename(path.getFileName().toString());

				bufferedHashes.put(id, ByteBuffer.wrap(sha1.digest(bytes)));
				idToPath.put(id, path);
			} catch (IOException | Utils.NameFormatException e) {
				e.printStackTrace();
			}
		});


	}

	public void open(int port) throws IOException {
		if (serverSocketChannel == null || !serverSocketChannel.isOpen()) {
			selector = Selector.open();

			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.bind(new InetSocketAddress(port));
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

			connections = new ConcurrentHashMap<>();

			processNetwork();
		}
	}

	private void processNetwork() throws IOException {
		while (selector.isOpen()) {
			int selectedKeysCount = selector.select(200);

			if (selectedKeysCount == 0)
				continue;

			Set<SelectionKey> keys = selector.selectedKeys();

			keys.parallelStream().filter(SelectionKey::isValid).forEach(key -> {
				System.out.println("Parallel Thread " + Thread.currentThread().getName());
				try {
					if (key.isAcceptable()) {
						SocketChannel channel = serverSocketChannel.accept();

						if (channel != null) {
							channel.configureBlocking(false);
							channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
							connections.put(channel, new Connection(channel));
						}

						return;
					}

					SocketChannel channel = (SocketChannel) key.channel();
					if (channel == null)
						return; // FIXME suspicious

					Connection connection = connections.get(channel);
					if (connection == null) {
						System.err.println("No connection for channel, closing.");
						channel.close();
						return; // FIXME suspicious
					}

					if (key.isReadable()) {
						connection.readPacket(key);
					}

					if (key.isWritable()) {
						connection.sendNextPacket(key);
					}

				} catch (IOException e) {
					e.printStackTrace();
				}
			});

			keys.clear();
		}
	}

	private void updateFileCache() throws IOException {
		final long newDate = System.currentTimeMillis();
		final long ONE_MINUTE = 60 * 1000;

		if (newDate >= lastDate + ONE_MINUTE) {
			Files.list(directory).forEach(path -> {
				try {
					final byte[] bytes;
					int id;
					String filename;
					boolean wasEdited = false;

					try {
						final FileTime time = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
						wasEdited = time.to(TimeUnit.MILLISECONDS) > lastDate;

						if (!wasEdited)
							return;

						id = Utils.getIDFromFilename(path.getFileName().toString());
						filename = path.getFileName().toString();
					} catch (Utils.NameFormatException e) {
						id = Utils.findLowestMissingNumber(idToPath.keySet());

						// Rename the file to <path><separator><id>-<old_filename>
						File newFile = new File(path.getParent() + FileSystems.getDefault().getSeparator() + Integer.toUnsignedString(id) + '-' + path.getFileName());

						final Path oldPath = path;
						path = newFile.toPath();

						// Do the actual rename (a.k.a move)
						Files.move(oldPath, path);
						filename = path.getFileName().toString();
					}

					bytes = Files.readAllBytes(path);

					if (bufferedFiles.containsKey(id)) {
						bufferedFiles.put(id, ByteBuffer.wrap(bytes));
					}

					bufferedHashes.put(id, Utils.hashBytes(bytes, sha1));
					idToPath.put(id, path);
					if (!filenames.contains(filename))
						filenames.add(filename);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

			lastDate = newDate;
		}
	}

	private void close() throws IOException {
		selector.close();
		serverSocketChannel.close();
		watcher.stop();

		connections.keySet().parallelStream().forEach((socketChannel) -> {
			try {
				socketChannel.close();
			} catch (IOException ignored) {
			}
		});
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		close();
	}

	public static void main(String[] args) {
		try {
			Server server = new Server(new File(args[0]));
			server.open(20); // Like FTP
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
