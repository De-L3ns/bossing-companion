package com.bossingcompanion;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BossingCompanionLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BossingCompanionPlugin.class);
		RuneLite.main(args);
	}
}
