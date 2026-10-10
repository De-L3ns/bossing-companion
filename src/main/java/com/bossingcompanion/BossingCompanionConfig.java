package com.bossingcompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BossingCompanionConfig.GROUP)
public interface BossingCompanionConfig extends Config
{
	String GROUP = "bossing-companion";
	@ConfigItem(keyName = "fightTimerEnabled", name = "Enable fight timer",
		description = "Show a movable observed fight timer for Obor, Bryophyta and Vorkath. Enable before engaging; completed values remain frozen.")
	default boolean fightTimerEnabled() { return false; }
	@ConfigItem(keyName = "automaticRecording", name = "Automatic recording",
		description = "Start a session on the first credited boss kill when no session is active")
	default boolean automaticRecording()
	{
		return true;
	}
}
