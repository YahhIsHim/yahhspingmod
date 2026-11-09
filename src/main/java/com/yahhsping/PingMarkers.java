package com.yahhsping;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/** Active ping markers (the ones drawn on screen). */
final class PingMarkers {
	/** How long a ping stays on screen. */
	static final long LIFETIME_MS = 30_000;

	record Marker(String user, double x, double y, double z, String dimension, long expires) {
	}

	private static final List<Marker> MARKERS = new ArrayList<>();

	private PingMarkers() {
	}

	/** A player only ever has one active ping, so their previous one is replaced. */
	static synchronized void add(String user, double x, double y, double z, String dimension) {
		MARKERS.removeIf(m -> m.user().equalsIgnoreCase(user));
		MARKERS.add(new Marker(user, x, y, z, dimension, System.currentTimeMillis() + LIFETIME_MS));
	}

	/** Markers that haven't expired and belong to the given dimension. */
	static synchronized List<Marker> active(String dimension) {
		long now = System.currentTimeMillis();
		MARKERS.removeIf(m -> m.expires() < now);

		List<Marker> result = new ArrayList<>();

		for (Marker m : MARKERS) {
			if (m.dimension().equals(dimension)) result.add(m);
		}

		return result;
	}

	/** Readable dimension name used in chat, e.g. "The Nether". Also decides whether two players share a dimension. */
	static String dimensionName(Level level) {
		Identifier id = level.dimension().identifier();

		if (id.getNamespace().equals("minecraft")) {
			return switch (id.getPath()) {
				case "overworld" -> "Overworld";
				case "the_nether" -> "The Nether";
				case "the_end" -> "The End";
				default -> id.getPath();
			};
		}

		return id.toString();
	}
}
