package filerequestserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Created by Braynstorm on 11.6.2016 г..
 */
public class Client {

	public static void main(String[] args) {
		try {
			Socket socket = new Socket(InetAddress.getLocalHost(), 13336);

			OutputStream ostream = socket.getOutputStream();
			InputStream istream = socket.getInputStream();

			ostream.write(new byte[] {
					1, 0xA, 0xB, 0xC, 0xD
			});

			while (socket.isConnected()) {
				if(istream.available() > 0){
					byte[] bytes = new byte[istream.available()];
					istream.read(bytes);
					for (byte aByte : bytes) {
						System.out.print(Integer.toHexString(aByte) + ' ');
					}
					System.out.println();
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
