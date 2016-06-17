package filerequestserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Created by Braynstorm on 14.6.2016 г..
 */
public class Utils {

	public static class NameFormatException extends Exception {
		public NameFormatException() {
		}

		public NameFormatException(String message) {
			super(message);
		}

		public NameFormatException(String message, Throwable cause) {
			super(message, cause);
		}

		public NameFormatException(Throwable cause) {
			super(cause);
		}

		public NameFormatException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
		}
	}

	public static int getIDFromFilename(final String name) throws NameFormatException {
		if (name == null || !name.contains("-")) {
			throw new NameFormatException("Name is not in the required format: ID-name.ext");
		}

		return Integer.parseUnsignedInt(name.substring(0, name.indexOf('-')), 10);
	}

	public static ByteBuffer hashFile(final Path path, final MessageDigest md) throws IOException {
		return hashBytes(Files.readAllBytes(path), md);
	}

	public static ByteBuffer hashBytes(final byte[] bytes, final MessageDigest md) throws IOException {
		final ByteBuffer temp = ByteBuffer.wrap(md.digest(bytes));
		md.reset();
		return temp;
	}

	public static Integer findLowestMissingNumber(final Set<Integer> idSet) {
		Integer[] idArray = idSet.stream().sorted(Integer::compareUnsigned).toArray(Integer[]::new);

		if (idArray[0] == 0) {
			int id;

			for (id = 1; id < idArray.length; id++)
				if (idArray[id] != id)
					break;

			return id;
		} else
			return 0;
	}

}
