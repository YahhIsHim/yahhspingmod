package com.yahhsping;

import net.minecraft.client.gui.Font;

/**
 * The few GUI drawing operations the HUD needs. Minecraft renamed its GUI classes in 26.1, so each version
 * has its own implementation (see the Compat classes) and the rest of the mod only talks to this interface.
 * Colours are ARGB (alpha must be FF to be visible).
 */
interface Gfx {
	int width();

	int height();

	void fill(int x1, int y1, int x2, int y2, int argb);

	/** Draws text with a shadow. */
	void text(Font font, String text, int x, int y, int argb);

	/** Draws the face of the named online player; returns false if that player isn't in the player list. */
	boolean head(String playerName, int x, int y, int size);
}
