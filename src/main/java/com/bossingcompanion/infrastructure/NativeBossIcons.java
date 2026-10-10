package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.application.BossIcons;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.client.game.SpriteManager;

/** EDT-owned bounded cache, following the Enhanced Messaging native sprite pattern. */
public final class NativeBossIcons implements BossIcons
{
	private final SpriteManager sprites;
	private final Map<Boss, BufferedImage> loaded = new EnumMap<>(Boss.class);
	private boolean closed;
	public NativeBossIcons(SpriteManager sprites) { this.sprites = sprites; }
	public void load(Boss boss, Consumer<BufferedImage> consumer)
	{
		if (closed) { return; }
		BufferedImage image = loaded.get(boss);
		if (image != null) { consumer.accept(image); return; }
		sprites.getSpriteAsync(BossCatalog.icon(boss).getSpriteId(), 0, sprite -> SwingUtilities.invokeLater(() ->
		{
			if (closed) { return; }
			if (sprite != null) { loaded.put(boss, sprite); }
			consumer.accept(sprite);
		}));
	}
	public void close() { closed = true; loaded.clear(); }
}
