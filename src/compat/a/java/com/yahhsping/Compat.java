package com.yahhsping;

import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/** Minecraft 1.21.11 version of the calls that changed in later versions. */
final class Compat {
	private Compat() {
	}

	/** Registers the keybind (default: V, rebindable in Controls). */
	static KeyMapping registerKey(String translationKey, KeyMapping.Category category) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(translationKey, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return ClientCommandManager.literal(name);
	}

	static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
		return ClientCommandManager.argument(name, type);
	}

	/** Prints a line in the player's own chat window (not sent to the server). */
	static void say(LocalPlayer player, Component message) {
		player.displayClientMessage(message, false);
	}

	static void registerHud(Identifier id, Consumer<Gfx> renderer) {
		HudElementRegistry.addLast(id, (graphics, tickCounter) -> renderer.accept(new GfxImpl(graphics)));
	}

	private static final class GfxImpl implements Gfx {
		private final GuiGraphics g;

		GfxImpl(GuiGraphics g) {
			this.g = g;
		}

		@Override
		public int width() {
			return g.guiWidth();
		}

		@Override
		public int height() {
			return g.guiHeight();
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			g.fill(x1, y1, x2, y2, argb);
		}

		@Override
		public void text(Font font, String text, int x, int y, int argb) {
			g.drawString(font, text, x, y, argb, true);
		}

		@Override
		public boolean head(String playerName, int x, int y, int size) {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			PlayerInfo info = connection == null ? null : connection.getPlayerInfo(playerName);
			if (info == null) return false;

			PlayerFaceRenderer.draw(g, info.getSkin(), x, y, size);
			return true;
		}
	}
}
