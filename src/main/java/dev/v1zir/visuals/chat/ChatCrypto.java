package dev.v1zir.visuals.chat;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The cryptography of Messages, from the JDK alone: X25519 keys, HKDF-SHA256 and AES-256-GCM.
 * <p>
 * A message on the wire is sealed twice. The outer layer is sealed to the recipient's key with a fresh key of its own,
 * so the relay and anyone listening see only who it is for, never who sent it. Inside, the sender's name and key and
 * the body, sealed with the key the two of them share (their keys agreed with each other), which only those two can
 * make: a body that opens came from the holder of that sender key.
 */
public final class ChatCrypto {
	public static final int KEY = 32;
	public static final int NONCE = 12;
	private static final int VERSION = 1;
	/** DER prefix of an X.509 encoded X25519 public key; the raw 32 bytes follow it. */
	private static final byte[] X509_PREFIX = HexFormat.of().parseHex("302a300506032b656e032100");
	private static final byte[] SEAL_SALT = "v1zir-chat-1-seal".getBytes(StandardCharsets.UTF_8);
	private static final byte[] PAIR_SALT = "v1zir-chat-1-pair".getBytes(StandardCharsets.UTF_8);
	private static final SecureRandom RANDOM = new SecureRandom();

	private ChatCrypto() {
	}

	/** A key pair: the private key and the raw 32 bytes of the public one. */
	public record Keys(PrivateKey priv, byte[] pub) {
		public byte[] encodedPrivate() {
			return priv.getEncoded();
		}
	}

	/** A message opened: who sent it (as they name themselves), with which key, and the body. */
	public record Opened(String from, byte[] fromKey, byte[] body) {
	}

	public static Keys generate() {
		try {
			KeyPair pair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
			return new Keys(pair.getPrivate(), raw(pair.getPublic()));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("X25519 unavailable", e);
		}
	}

	/** Keys from a private key as stored (PKCS#8) and its raw public key. */
	public static Keys restore(byte[] pkcs8, byte[] pub) throws GeneralSecurityException {
		if (pub.length != KEY) throw new GeneralSecurityException("Bad public key");
		PrivateKey priv = KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		return new Keys(priv, pub.clone());
	}

	private static byte[] raw(PublicKey key) {
		byte[] encoded = key.getEncoded();
		return Arrays.copyOfRange(encoded, encoded.length - KEY, encoded.length);
	}

	private static PublicKey publicKey(byte[] raw) throws GeneralSecurityException {
		if (raw.length != KEY) throw new GeneralSecurityException("Bad public key");
		return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(concat(X509_PREFIX, raw)));
	}

	/** X25519 between a private key and a raw public key; throws for a key that gives no secret. */
	private static byte[] agree(PrivateKey priv, byte[] pub) throws GeneralSecurityException {
		KeyAgreement agreement = KeyAgreement.getInstance("X25519");
		agreement.init(priv);
		agreement.doPhase(publicKey(pub), true);
		byte[] secret = agreement.generateSecret();
		int any = 0;
		for (byte b : secret) any |= b;
		if (any == 0) throw new GeneralSecurityException("Degenerate key");
		return secret;
	}

	/** HKDF-SHA256 (RFC 5869), up to 32 bytes. */
	static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(salt, "HmacSHA256"));
		byte[] prk = mac.doFinal(ikm);
		mac.init(new SecretKeySpec(prk, "HmacSHA256"));
		mac.update(info);
		mac.update((byte) 1);
		return Arrays.copyOf(mac.doFinal(), length);
	}

	private static byte[] seal(byte[] key, byte[] nonce, byte[] plain, byte[] aad) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
		cipher.updateAAD(aad);
		return cipher.doFinal(plain);
	}

	private static byte[] open(byte[] key, byte[] nonce, byte[] sealed, int offset, int length, byte[] aad) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
		cipher.updateAAD(aad);
		return cipher.doFinal(sealed, offset, length);
	}

	/** The key two people share: the same from either side, whoever sends. */
	private static byte[] pairKey(Keys me, byte[] theirs) throws GeneralSecurityException {
		boolean mineFirst = Arrays.compareUnsigned(me.pub, theirs) < 0;
		byte[] info = mineFirst ? concat(me.pub, theirs) : concat(theirs, me.pub);
		return hkdf(agree(me.priv, theirs), PAIR_SALT, info, KEY);
	}

	/** Seals a body from me (named myName) to the holder of their key. */
	public static byte[] wrap(Keys me, String myName, byte[] theirs, byte[] body) throws GeneralSecurityException {
		Keys once = generate();
		byte[] outerKey = hkdf(agree(once.priv, theirs), SEAL_SALT, concat(once.pub, theirs), KEY);
		byte[] innerNonce = random(NONCE);
		byte[] inner = seal(pairKey(me, theirs), innerNonce, body, once.pub);
		byte[] name = myName.getBytes(StandardCharsets.UTF_8);
		if (name.length > 64) throw new GeneralSecurityException("Name too long");
		ByteArrayOutputStream plain = new ByteArrayOutputStream(1 + name.length + KEY + NONCE + inner.length);
		plain.write(name.length);
		plain.writeBytes(name);
		plain.writeBytes(me.pub);
		plain.writeBytes(innerNonce);
		plain.writeBytes(inner);
		byte[] outerNonce = random(NONCE);
		byte[] outer = seal(outerKey, outerNonce, plain.toByteArray(), new byte[]{VERSION});
		ByteArrayOutputStream wire = new ByteArrayOutputStream(1 + KEY + NONCE + outer.length);
		wire.write(VERSION);
		wire.writeBytes(once.pub);
		wire.writeBytes(outerNonce);
		wire.writeBytes(outer);
		return wire.toByteArray();
	}

	/** Opens a message sealed to me; throws when it is not for this key, or was changed on the way. */
	public static Opened unwrap(Keys me, byte[] wire) throws GeneralSecurityException {
		if (wire.length < 1 + KEY + NONCE + 16 || wire[0] != VERSION) throw new GeneralSecurityException("Not a message");
		byte[] once = Arrays.copyOfRange(wire, 1, 1 + KEY);
		byte[] outerNonce = Arrays.copyOfRange(wire, 1 + KEY, 1 + KEY + NONCE);
		byte[] outerKey = hkdf(agree(me.priv, once), SEAL_SALT, concat(once, me.pub), KEY);
		int start = 1 + KEY + NONCE;
		byte[] plain = open(outerKey, outerNonce, wire, start, wire.length - start, new byte[]{VERSION});
		int nameLength = plain[0] & 0xFF;
		int at = 1 + nameLength;
		if (nameLength == 0 || plain.length < at + KEY + NONCE + 16) throw new GeneralSecurityException("Bad message");
		String from = new String(plain, 1, nameLength, StandardCharsets.UTF_8);
		byte[] fromKey = Arrays.copyOfRange(plain, at, at + KEY);
		byte[] innerNonce = Arrays.copyOfRange(plain, at + KEY, at + KEY + NONCE);
		int innerStart = at + KEY + NONCE;
		byte[] body = open(pairKey(me, fromKey), innerNonce, plain, innerStart, plain.length - innerStart, once);
		return new Opened(from, fromKey, body);
	}

	public static byte[] random(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	/** The topic part for a name: a hash of it, the same for everyone, that does not spell the name out. */
	public static String id(String name) {
		return hex(sha256(("v1zir-chat:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)), 12);
	}

	public static byte[] sha256(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	public static byte[] sha1(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-1").digest(data);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The first bytes of data as lowercase hex. */
	public static String hex(byte[] data, int bytes) {
		return HexFormat.of().formatHex(data, 0, Math.min(bytes, data.length));
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] out = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}
}
