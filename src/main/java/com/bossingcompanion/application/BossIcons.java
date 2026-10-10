package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** Presentation asset boundary; completion callbacks run on Swing's EDT. */
public interface BossIcons
{
	void load(Boss boss, Consumer<BufferedImage> consumer);
	void close();
}
