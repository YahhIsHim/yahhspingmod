package com.yahhsping;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Settings saved in config/yahhsping.json: trusted players, the shared team key and the ping cooldown. */
final class Config {
	static final String DEFAULT_KEY = "yahhs-ping-mod";
	private static final long DEFAULT_COOLDOWN_MS = 1250;
	private static final long MIN_COOLDOWN_MS = 500;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir();
	private static final Path FILE = DIR.resolve("yahhsping.json");
	// The trusted list from the old "Ping Trusted" version of this mod is picked up automatically.
	private static final Path LEGACY_FILE = DIR.resolve("pingtrusted.json");

	private static final class Data {
		List<String> trusted = new ArrayList<>();
		String key = DEFAULT_KEY;
		long cooldownMs = DEFAULT_COOLDOWN_MS;
	}

	private static Data data = new Data();

	private Config() {
	}

	static synchronized void load() {
		Path source = Files.exists(FILE) ? FILE : (Files.exists(LEGACY_FILE) ? LEGACY_FILE : null);

		if (source == null) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(source)) {
			Data loaded = GSON.fromJson(reader, Data.class);

			if (loaded != null) {
				if (loaded.trusted == null) loaded.trusted = new ArrayList<>();
				if (loaded.key == null || loaded.key.isEmpty()) loaded.key = DEFAULT_KEY;
				if (loaded.cooldownMs < MIN_COOLDOWN_MS) loaded.cooldownMs = DEFAULT_COOLDOWN_MS;
				data = loaded;
			}
		} catch (Exception e) {
			System.err.println("[yahhsping] Could not read " + source + ": " + e);
		}

		if (source != FILE) save();
	}

	private static void save() {
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			GSON.toJson(data, writer);
		} catch (IOException e) {
			System.err.println("[yahhsping] Could not write " + FILE + ": " + e);
		}
	}

	static synchronized boolean add(String name) {
		if (isTrusted(name)) return false;
		data.trusted.add(name);
		save();
		return true;
	}

	static synchronized boolean remove(String name) {
		boolean removed = data.trusted.removeIf(n -> n.equalsIgnoreCase(name));
		if (removed) save();
		return removed;
	}

	static synchronized boolean isTrusted(String name) {
		return data.trusted.stream().anyMatch(n -> n.equalsIgnoreCase(name));
	}

	static synchronized List<String> names() {
		return new ArrayList<>(data.trusted);
	}

	static synchronized String key() {
		return data.key;
	}

	static synchronized void setKey(String key) {
		data.key = key;
		save();
	}

	/** Minimum time between two pings that actually get sent (keeps you under the server's chat spam limit). */
	static synchronized long cooldownMs() {
		return data.cooldownMs;
	}
}
