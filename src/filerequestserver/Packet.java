package filerequestserver;

/**
 * Created by Braynstorm on 11.6.2016 г..
 */
public enum Packet {
	REQUEST_FILE((byte) 0),
	REQUEST_HASH_CHECK((byte) 1);

	byte val;

	Packet(byte val) {
		this.val = val;
	}

	public byte getVal() {
		return val;
	}

}
