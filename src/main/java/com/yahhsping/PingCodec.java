package com.yahhsping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Turns a ping into one short chat message and back.
 *
 * The data is encrypted with the team key, so people without the mod (or with a different key) only see
 * a harmless "YPM1:..." string, never the coordinates.
 */
final class PingCodec {
	static final String PREFIX = "YPM1:";
	static final int TYPE_BLOCK = 0;
	static final int TYPE_PLAYER = 1;

	private static final Pattern WIRE = Pattern.compile(Pattern.quote(PREFIX) + "([A-Za-z0-9_-]+)");
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Coordinates are exact to one decimal place. */
	record Ping(String user, int type, String target, String dimension, double x, double y, double z) {
	}

	private PingCodec() {
	}

	static String encode(Ping ping, String passphrase) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);

		out.writeByte(ping.type());
		out.writeUTF(limit(ping.user(), 32));
		out.writeUTF(limit(ping.target(), 40));
		out.writeUTF(limit(ping.dimension(), 40));
		out.writeInt((int) Math.round(ping.x() * 10));
		out.writeInt((int) Math.round(ping.y() * 10));
		out.writeInt((int) Math.round(ping.z() * 10));

		byte[] iv = new byte[12];
		RANDOM.nextBytes(iv);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, key(passphrase), new GCMParameterSpec(128, iv));
		byte[] encrypted = cipher.doFinal(bytes.toByteArray());

		byte[] all = new byte[iv.length + encrypted.length];
		System.arraycopy(iv, 0, all, 0, iv.length);
		System.arraycopy(encrypted, 0, all, iv.length, encrypted.length);

		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(all);
	}

	/** True if the text contains a ping-shaped payload (whether or not we can read it). */
	static boolean looksLikePing(String text) {
		return text.contains(PREFIX) && WIRE.matcher(text).find();
	}

	/** Returns the ping, or null if the text has none or it was made with a different key. */
	static Ping decode(String text, String passphrase) {
		try {
			Matcher m = WIRE.matcher(text);
			if (!m.find()) return null;

			byte[] all = Base64.getUrlDecoder().decode(m.group(1));
			if (all.length < 13) return null;

			byte[] iv = Arrays.copyOfRange(all, 0, 12);
			byte[] encrypted = Arrays.copyOfRange(all, 12, all.length);

			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key(passphrase), new GCMParameterSpec(128, iv));
			byte[] plain = cipher.doFinal(encrypted);

			DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain));
			int type = in.readByte();
			String user = in.readUTF();
			String target = in.readUTF();
			String dimension = in.readUTF();
			double x = in.readInt() / 10.0;
			double y = in.readInt() / 10.0;
			double z = in.readInt() / 10.0;

			return new Ping(user, type, target, dimension, x, y, z);
		} catch (Exception e) {
			return null;
		}
	}

	private static SecretKeySpec key(String passphrase) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
		return new SecretKeySpec(hash, "AES");
	}

	private static String limit(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}
}
