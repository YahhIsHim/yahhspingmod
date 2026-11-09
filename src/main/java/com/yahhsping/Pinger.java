package com.yahhsping;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class Pinger {
	/** Effectively unlimited. The client only knows blocks in chunks it has loaded, so in practice it's your render distance. */
	private static final double RANGE = 100_000.0;

	private static final int WHITE = 0xFFFFFF;
	private static final int GRAY = 0xAAAAAA;
	private static final int GREEN = 0x55FF55;
	private static final int RED = 0xFF5555;

	private static long lastSend = 0;
	private static final Set<String> NOTICED = ConcurrentHashMap.newKeySet();

	private Pinger() {
	}

	// ------------------------------------------------------------------ sending

	static void ping(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientPacketListener connection = mc.getConnection();

		if (player == null || mc.level == null || connection == null) return;

		// Anti-spam: at most one ping message per cooldown, however fast the key is pressed.
		long now = System.currentTimeMillis();
		if (now - lastSend < Config.cooldownMs()) return;

		double ox = player.getX();
		double oy = player.getEyeY();
		double oz = player.getZ();
		Vec3 eye = new Vec3(ox, oy, oz);

		double yaw = Math.toRadians(player.getYRot());
		double pitch = Math.toRadians(player.getXRot());
		double dx = -Math.sin(yaw) * Math.cos(pitch);
		double dy = -Math.sin(pitch);
		double dz = Math.cos(yaw) * Math.cos(pitch);
		Vec3 end = eye.add(dx * RANGE, dy * RANGE, dz * RANGE);

		// Nearest block along the ray.
		double blockDist = Double.MAX_VALUE;
		BlockPos blockPos = null;
		HitResult hit = player.pick(RANGE, 1.0F, false);

		if (hit.getType() == HitResult.Type.BLOCK) {
			blockDist = hit.getLocation().distanceTo(eye);
			blockPos = ((BlockHitResult) hit).getBlockPos();
		}

		// Nearest other player along the ray.
		double playerDist = Double.MAX_VALUE;
		Player targetPlayer = null;

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof Player other) || other == player || other.isSpectator()) continue;

			Optional<Vec3> point = other.getBoundingBox().inflate(0.15).clip(eye, end);

			if (point.isPresent()) {
				double d = point.get().distanceTo(eye);

				if (d < playerDist) {
					playerDist = d;
					targetPlayer = other;
				}
			}
		}

		int type;
		String targetName;
		double t;

		if (targetPlayer != null && playerDist < blockDist) {
			type = PingCodec.TYPE_PLAYER;
			targetName = targetPlayer.getGameProfile().name();
			t = playerDist;
		} else if (blockPos != null) {
			type = PingCodec.TYPE_BLOCK;
			targetName = mc.level.getBlockState(blockPos).getBlock().getName().getString();
			t = blockDist;
		} else {
			Compat.say(player, colored("Nothing to ping in sight.", RED, false));
			return;
		}

		PingCodec.Ping ping = new PingCodec.Ping(
				player.getGameProfile().name(), type, targetName, PingMarkers.dimensionName(mc.level),
				ox + dx * t, oy + dy * t, oz + dz * t);

		try {
			// One normal chat message for the whole team - NOT one /msg per person.
			connection.sendChat(PingCodec.encode(ping, Config.key()));
		} catch (Exception e) {
			Compat.say(player, colored("Could not send ping: " + e.getMessage(), RED, false));
			return;
		}

		lastSend = now;
		show(mc, ping);
	}

	// ---------------------------------------------------------------- receiving

	/**
	 * Called for every incoming chat/system message. Returns false to hide the message
	 * (our own ping messages are shown as a nicely formatted line instead of the raw text).
	 *
	 * @param senderName the verified chat sender, or null if the server doesn't tell us
	 */
	static boolean allowIncoming(String text, String senderName) {
		if (!PingCodec.looksLikePing(text)) return true;

		Minecraft mc = Minecraft.getInstance();
		PingCodec.Ping ping = PingCodec.decode(text, Config.key());

		if (ping == null) {
			notice(mc, "undecodable", "Hid a ping that could not be read. Is everyone using the same key? (/ypm key <key>)");
			return false;
		}

		String self = mc.player == null ? null : mc.player.getGameProfile().name();

		if (self != null && ping.user().equalsIgnoreCase(self)) return false; // our own message coming back
		if (senderName != null && !senderName.equalsIgnoreCase(ping.user())) return false; // somebody replaying another player's ping

		if (!Config.isTrusted(ping.user())) {
			notice(mc, "untrusted:" + ping.user().toLowerCase(Locale.ROOT),
					"Ignored a ping from " + ping.user() + " because they are not trusted. Use /ypm add " + ping.user());
			return false;
		}

		mc.execute(() -> show(mc, ping));
		return false;
	}

	/** Prints a short gray hint, but only once per reason so it can't spam the chat. */
	private static void notice(Minecraft mc, String reason, String message) {
		if (!NOTICED.add(reason)) return;

		mc.execute(() -> {
			if (mc.player != null) Compat.say(mc.player, colored(message, GRAY, false));
		});
	}

	// ------------------------------------------------------------------ showing

	/** Prints the "X marked Y N blocks away (x y z)" line and, if we're in the same dimension, adds the marker. */
	private static void show(Minecraft mc, PingCodec.Ping ping) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;

		boolean sameDimension = ping.dimension().equals(PingMarkers.dimensionName(mc.level));
		double distance = new Vec3(ping.x(), ping.y(), ping.z()).distanceTo(player.position());

		Compat.say(player, chatLine(ping, sameDimension, distance));

		// Pings from another dimension are chat-only: no marker, no sound.
		if (sameDimension) {
			PingMarkers.add(ping.user(), ping.x(), ping.y(), ping.z(), ping.dimension());
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5F, 1.0F));
		}
	}

	private static Component chatLine(PingCodec.Ping ping, boolean sameDimension, double distance) {
		boolean isPlayer = ping.type() == PingCodec.TYPE_PLAYER;
		String coords = " (" + num(ping.x()) + " " + num(ping.y()) + " " + num(ping.z()) + ")";

		MutableComponent line = Component.empty();
		line.append(colored(ping.user(), WHITE, true));
		line.append(colored(" marked ", GRAY, false));
		line.append(colored(ping.target(), isPlayer ? GREEN : WHITE, true));

		if (sameDimension) {
			line.append(colored(" " + num(distance) + " blocks away", GRAY, false));
			line.append(colored(coords, GRAY, false));
		} else {
			// Different dimension: a distance would be meaningless, so say where it is instead.
			line.append(colored(coords, GRAY, false));
			line.append(colored(" in " + ping.dimension(), GRAY, false));
		}

		return line;
	}

	private static String num(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static MutableComponent colored(String text, int rgb, boolean bold) {
		return Component.literal(text).withStyle(style -> style.withColor(rgb).withBold(bold));
	}
}
