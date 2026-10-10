package com.bossingcompanion.presentation;

import com.bossingcompanion.domain.FightTimerSnapshot;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Native compact widget. No state caption; raw geometry is shared by the actual overlay and render tests. */
public final class FightTimerPainter
{
	private static final Font CLOCK_FONT = FontManager.getRunescapeBoldFont().deriveFont(18f);
	private static final int ICON_SIZE = 16;
	private static final int PAD_X = 5;
	private static final int PAD_Y = 3;
	private FightTimerPainter() { }
	public static Dimension paint(Graphics2D target, FightTimerSnapshot snapshot, long nowNanos, BufferedImage icon)
	{
		Graphics2D graphics = (Graphics2D) target.create();
		try
		{
			String clock = (snapshot.isApproximate() ? "~" : "") + FightTimerSnapshot.format(snapshot.elapsedAt(nowNanos));
			FontMetrics clockMetrics = graphics.getFontMetrics(CLOCK_FONT);
			boolean hasBoss = snapshot.getBoss() != null;
			int lineHeight = Math.max(clockMetrics.getHeight(), hasBoss ? ICON_SIZE : 0);
			int clockX = PAD_X + (hasBoss ? ICON_SIZE + 4 : 0);
			int width = clockX + clockMetrics.stringWidth(clock) + PAD_X + 1;
			int height = PAD_Y * 2 + lineHeight + 1;
			graphics.setColor(ColorScheme.DARKER_GRAY_COLOR); graphics.fillRect(0, 0, width, height);
			graphics.setColor(ColorScheme.MEDIUM_GRAY_COLOR); graphics.drawRect(0, 0, width - 1, height - 1);
			if (hasBoss)
			{
				int iconY = PAD_Y + (lineHeight - ICON_SIZE) / 2;
				if (icon != null)
				{
					int largest = Math.max(icon.getWidth(), icon.getHeight());
					int iconWidth = Math.max(1, icon.getWidth() * ICON_SIZE / largest);
					int iconHeight = Math.max(1, icon.getHeight() * ICON_SIZE / largest);
					graphics.drawImage(icon, PAD_X + (ICON_SIZE - iconWidth) / 2, iconY + (ICON_SIZE - iconHeight) / 2, iconWidth, iconHeight, null);
				}
				else { graphics.setColor(ColorScheme.MEDIUM_GRAY_COLOR); graphics.drawRect(PAD_X, iconY, ICON_SIZE - 1, ICON_SIZE - 1); }
			}
			graphics.setFont(CLOCK_FONT);
			text(graphics, clock, clockX, PAD_Y + (lineHeight - clockMetrics.getHeight()) / 2 + clockMetrics.getAscent(), Color.YELLOW);
			return new Dimension(width, height);
		}
		finally { graphics.dispose(); }
	}
	private static void text(Graphics2D graphics, String text, int x, int y, Color color)
	{
		graphics.setColor(Color.BLACK); graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(color); graphics.drawString(text, x, y);
	}
	public static String provenance(FightTimerSnapshot snapshot)
	{
		String boss = snapshot.getBoss() == null ? "Fight timer" : snapshot.getBoss().getDisplayName();
		if (snapshot.isOfficial()) { return boss + ": game-reported fight time."; }
		if (!snapshot.isObserved()) { return boss + ": no fight timed yet. Starts on your first detected boss hit."; }
		return snapshot.isRunning() ? boss + ": elapsed since your first detected hit (~ = estimate)."
			: boss + ": frozen estimated time (~). May differ from the game's fight time.";
	}
}
