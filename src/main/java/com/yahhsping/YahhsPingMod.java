package com.yahhsping;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/**
 * Yahh's Ping Mod - client-side.
 *
 * Look at a block (or a player), press the keybind, and your trusted teammates see
 * "{user} marked {block} {distance} blocks away ({x} {y} {z})" in chat plus a marker on screen.
 */
public class YahhsPingMod implements ClientModInitializer {
	public static final String MOD_ID = "yahhsping";

	private static KeyMapping pingKey;

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		pingKey = Compat.registerKey("key.yahhsping.ping", category);

		Compat.registerHud(Identifier.fromNamespaceAndPath(MOD_ID, "ping_markers"), PingHud::render);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (pingKey.consumeClick()) {
				Pinger.ping(client);
			}
		});

		// Pings travel as one ordinary chat message. These hooks hide that raw message and show the nice line instead.
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || Pinger.allowIncoming(message.getString(), null));
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, type, time) ->
				Pinger.allowIncoming(message.getString(), sender == null ? null : sender.name()));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			dispatcher.register(commandTree("ypm"));
			dispatcher.register(commandTree("yahhsping"));
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> commandTree(String root) {
		return Compat.literal(root)
				.then(Compat.literal("add")
						.then(Compat.argument("player", StringArgumentType.word())
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "player");
									boolean added = Config.add(name);
									ctx.getSource().sendFeedback(text(added ? name + " is now trusted." : name + " was already trusted.", 0x55FF55));
									return 1;
								})))
				.then(Compat.literal("remove")
						.then(Compat.argument("player", StringArgumentType.word())
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "player");
									boolean removed = Config.remove(name);
									ctx.getSource().sendFeedback(text(removed ? name + " is no longer trusted." : name + " was not on the trusted list.", removed ? 0xFFFF55 : 0xFF5555));
									return 1;
								})))
				.then(Compat.literal("list")
						.executes(ctx -> {
							var names = Config.names();
							ctx.getSource().sendFeedback(text(names.isEmpty()
									? "Trusted list is empty. Use /" + root + " add <player>"
									: "Trusted (" + names.size() + "): " + String.join(", ", names), 0x55FFFF));
							return 1;
						}))
				.then(Compat.literal("key")
						.executes(ctx -> {
							boolean isDefault = Config.key().equals(Config.DEFAULT_KEY);
							ctx.getSource().sendFeedback(text(isDefault
									? "Using the default team key. Set your own with /" + root + " key <secret> (everyone must use the same one)."
									: "Using a custom team key.", 0x55FFFF));
							return 1;
						})
						.then(Compat.argument("secret", StringArgumentType.greedyString())
								.executes(ctx -> {
									Config.setKey(StringArgumentType.getString(ctx, "secret"));
									ctx.getSource().sendFeedback(text("Team key updated. Everyone on your team must use the same key.", 0x55FF55));
									return 1;
								})));
	}

	private static Component text(String message, int rgb) {
		return Component.literal(message).withStyle(style -> style.withColor(rgb));
	}
}
