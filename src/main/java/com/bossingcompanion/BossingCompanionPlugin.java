package com.bossingcompanion;

import com.bossingcompanion.application.SessionTracker;
import com.bossingcompanion.application.ProgressTracker;
import com.bossingcompanion.application.DropLookup;
import com.bossingcompanion.application.FightTimerTracker;
import com.bossingcompanion.domain.FightTimerSnapshot;
import com.bossingcompanion.infrastructure.NativeFightEvents;
import com.bossingcompanion.infrastructure.FightSources;
import com.bossingcompanion.presentation.FightTimerOverlay;
import com.bossingcompanion.domain.BossProgress;
import com.bossingcompanion.domain.DropRates;
import com.bossingcompanion.infrastructure.NativeCollectionLog;
import com.bossingcompanion.infrastructure.BundledDropData;
import com.google.gson.Gson;
import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.SessionSnapshot;
import com.bossingcompanion.infrastructure.BossCatalog;
import com.bossingcompanion.infrastructure.CompletionMessages;
import com.bossingcompanion.infrastructure.NativeBossIcons;
import com.bossingcompanion.infrastructure.NativeLoot;
import com.bossingcompanion.presentation.BossingPanel;
import com.google.inject.Provides;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.LootManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Slf4j
@PluginDescriptor(name = "Bossing Companion", internalName = "bossing-companion",
	description = "Track bossing sessions, kills, awarded loot and completed kill times",
	tags = {"bossing", "pvm", "loot", "sessions"})
public class BossingCompanionPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar toolbar;
	@Inject private ItemManager items;
	@Inject private SpriteManager sprites;
	@Inject private BossingCompanionConfig config;
	@Inject private ConfigManager configManager;
	@Inject private Provider<LootManager> coreLoot;
	@Inject private Gson gson;
	@Inject private OverlayManager overlays;
	@Inject private TooltipManager tooltips;
	private volatile RuntimeState runtime;

	@Provides BossingCompanionConfig provideConfig(ConfigManager manager) { return manager.getConfig(BossingCompanionConfig.class); }

	@Override protected void startUp()
	{
		coreLoot.get(); // Initialize the core observation service without the Loot Tracker plugin.
		RuntimeState state = new RuntimeState();
		state.fightEvents = new NativeFightEvents(client, state.fights);
		state.fightOverlay = new FightTimerOverlay(this, new NativeBossIcons(sprites), System::nanoTime, tooltips);
		overlays.add(state.fightOverlay);
		state.collection = new NativeCollectionLog(client, items, state.progress);
		state.data = new BundledDropData(gson);
		state.drops = new DropLookup(state.data,
			command -> clientThread.invoke(() -> { if (runtime == state) { command.run(); } }), () -> publish(state));
		state.profileKey = configManager.getRSProfileKey();
		runtime = state;
		SwingUtilities.invokeLater(() ->
		{
			if (runtime != state) { return; }
			state.panel = new BossingPanel(items, new NativeBossIcons(sprites), BossCatalog.supported(), state.clock,
				boss -> clientThread.invoke(() ->
				{
					if (runtime == state && client.getGameState() == GameState.LOGGED_IN)
					{
						state.tracker.start(boss);
						publish(state);
					}
				}), () -> clientThread.invoke(() ->
				{
					if (runtime == state) { state.tracker.end(); publish(state); }
				}));
			state.button = NavigationButton.builder().tooltip("Bossing Companion").icon(navigationIcon())
				.priority(7).panel(state.panel).build();
			toolbar.addNavigation(state.button);
			clientThread.invoke(() -> { if (runtime == state) { publish(state); } });
		});
	}

	@Override protected void shutDown()
	{
		RuntimeState state = runtime;
		runtime = null;
		if (state == null) { return; }
		state.fightOverlay.close(); overlays.remove(state.fightOverlay);
		state.data.close();
		clientThread.invoke(() -> { state.drops.close(); state.tracker.clear(); state.progress.clearCharacter(); state.fightEvents.setEnabled(false); state.fights.clearCharacter(); state.fightEvents.reset(); state.seen.clear(); });
		SwingUtilities.invokeLater(() ->
		{
			if (state.panel != null) { state.panel.close(); }
			if (state.button != null) { toolbar.removeNavigation(state.button); }
		});
	}

	@Subscribe public void onChatMessage(ChatMessage event)
	{
		RuntimeState state = runtime;
		if (!canObserve(state) || event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) { return; }
		CompletionMessages.KillCount kc = CompletionMessages.killCount(event.getMessage());
		Duration time = kc == null ? CompletionMessages.completedTime(event.getMessage()) : null;
		if (kc == null && time == null) { return; }
		// Chat nodes are recycled by the client; event identity is not a persistent node identity.
		if (!state.first(event)) { return; }
		state.tracker.setAutomatic(config.automaticRecording());
		if (kc != null)
		{
			state.fights.creditedKill(kc.getBoss(), kc.getCount(), client.getTickCount());
			if (kc.getBoss() == null) { state.tracker.excludedCompletion(client.getTickCount()); }
			else
			{
				state.progress.totalKills(kc.getBoss(), kc.getCount());
				boolean recorded = state.tracker.creditedKill(kc.getBoss(), kc.getCount(), client.getTickCount());
				log.debug("Boss completion {} KC {} recorded={}", kc.getBoss(), kc.getCount(), recorded);
			}
		}
		else { state.tracker.completedTime(time, client.getTickCount()); state.fights.completedTime(time, client.getTickCount()); }
		publish(state);
	}

	@Subscribe public void onServerNpcLoot(ServerNpcLoot event)
	{
		RuntimeState state = runtime;
		if (!canObserve(state)) { return; }
		Boss boss = BossCatalog.fromNpc(event.getComposition().getId());
		if (boss == null || !state.first(event)) { return; }
		state.tracker.serverLoot(boss, new NativeLoot(items).describe(event.getItems()), client.getTickCount());
		publish(state);
	}

	@Subscribe public void onGameTick(GameTick event)
	{
		RuntimeState state = runtime;
		if (state != null)
		{
			state.tracker.expire(client.getTickCount());
			if (canObserve(state)) { state.fightEvents.tick(); state.collection.initialize(); state.collection.tick(); publish(state); }
		}
	}
	@Subscribe public void onInteractingChanged(InteractingChanged event)
	{
		RuntimeState state = runtime;
		if (canObserve(state) && isStandardWorld()) { state.fightEvents.interacting(event.getSource(), event.getTarget()); }
	}
	@Subscribe public void onHitsplatApplied(HitsplatApplied event)
	{
		RuntimeState state = runtime;
		if (canObserve(state) && isStandardWorld() && state.fightEvents.hitsplat(event.getActor(), event.getHitsplat())) { publish(state); }
	}
	@Subscribe public void onActorDeath(ActorDeath event)
	{
		RuntimeState state = runtime;
		if (canObserve(state) && state.fightEvents.died(event.getActor())) { publish(state); }
	}
	@Subscribe public void onNpcSpawned(NpcSpawned event)
	{
		RuntimeState state = runtime;
		if (canObserve(state)) { state.fightEvents.spawned(event.getNpc()); }
	}
	@Subscribe public void onNpcChanged(NpcChanged event)
	{
		RuntimeState state = runtime;
		if (canObserve(state) && event.getOld() != null && state.fightEvents.changed(event.getNpc(), event.getOld().getId())) { publish(state); }
	}
	@Subscribe public void onNpcDespawned(NpcDespawned event)
	{
		RuntimeState state = runtime;
		if (state != null && state.fightEvents.despawned(event.getNpc())) { publish(state); }
	}
	@Subscribe public void onScriptPreFired(ScriptPreFired event)
	{
		RuntimeState state = runtime;
		if (!canObserve(state)) { return; }
		if (event.getScriptId() == NativeCollectionLog.ITEM_TRANSMIT_SCRIPT && event.getScriptEvent() != null)
		{
			state.collection.itemTransmitted(event.getScriptEvent().getArguments());
		}
	}
	@Subscribe public void onScriptPostFired(ScriptPostFired event)
	{
		RuntimeState state = runtime;
		if (state != null && (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST || event.getScriptId() == NativeCollectionLog.CATEGORY_SEND_SCRIPT)) { state.collection.changed(); }
	}
	@Subscribe public void onWidgetLoaded(WidgetLoaded event)
	{
		RuntimeState state = runtime;
		if (state != null) { state.collection.changed(); }
	}
	@Subscribe public void onVarbitChanged(VarbitChanged event)
	{
		RuntimeState state = runtime;
		if (state != null && event.getVarbitId() == net.runelite.api.gameval.VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) { state.collection.changed(); }
	}
	@Subscribe public void onGameStateChanged(GameStateChanged event)
	{
		RuntimeState state = runtime;
		if (state == null) { return; }
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			state.tracker.clear();
			state.progress.clearCharacter(); state.collection.reset(); state.drops.resetSelection();
			state.fights.clearCharacter(); state.fightEvents.reset();
			state.seen.clear();
		}
		else if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.CONNECTION_LOST)
		{
			state.tracker.clearPending();
			state.fightEvents.interrupt(); state.fightEvents.reset();
			state.seen.clear();
		}
		else if (event.getGameState() == GameState.LOADING) { state.fightEvents.interrupt(); }
		publish(state);
	}
	@Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		clientThread.invoke(() ->
		{
			RuntimeState state = runtime;
			if (state != null)
			{
				String key = configManager.getRSProfileKey();
				if (!java.util.Objects.equals(key, state.profileKey))
				{
					state.tracker.clear(); state.progress.clearCharacter(); state.collection.reset(); state.drops.resetSelection();
					state.fights.clearCharacter(); state.fightEvents.reset();
					state.seen.clear(); state.profileKey = key;
				}
				publish(state);
			}
		});
	}
	@Subscribe public void onConfigChanged(ConfigChanged event)
	{
		if (!BossingCompanionConfig.GROUP.equals(event.getGroup())) { return; }
		clientThread.invoke(() ->
		{
			RuntimeState state = runtime;
			if (state != null) { state.tracker.setAutomatic(config.automaticRecording()); publish(state); }
		});
	}

	private boolean canObserve(RuntimeState state)
	{
		return state != null && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
	}
	private void publish(RuntimeState state)
	{
		SessionSnapshot snapshot = state.tracker.snapshot();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		boolean automatic = config.automaticRecording();
		boolean eligible = isStandardWorld();
		if (loggedIn && state.eligible != null && state.eligible != eligible)
		{
			state.progress.clearCharacter(); state.tracker.clear(); state.collection.reset(); state.drops.resetSelection(); snapshot = null;
			state.fights.clearCharacter(); state.fightEvents.reset();
		}
		if (loggedIn) { state.eligible = eligible; }
		state.drops.configure(eligible);
		state.drops.select(snapshot, snapshot == null ? java.util.List.of() : state.progress.items(snapshot.getBoss()));
		BossProgress progress = snapshot == null ? null : state.progress.snapshot(snapshot.getBoss());
		DropRates rates = snapshot == null ? null : state.drops.snapshot();
		state.fightEvents.setEnabled(config.fightTimerEnabled() && loggedIn && eligible);
		FightTimerSnapshot fight = state.fights.snapshot();
		if (fight.getBoss() == null && snapshot != null && snapshot.isActive()
			&& FightSources.supports(snapshot.getBoss()))
		{
			fight = new FightTimerSnapshot(snapshot.getBoss(), false, 0, Duration.ZERO, false);
		}
		state.fightOverlay.showSnapshot(fight, state.fights.isEnabled());
		SessionSnapshot displayed = snapshot;
		SwingUtilities.invokeLater(() ->
		{
			if (runtime == state && state.panel != null) { state.panel.showSnapshot(displayed, loggedIn, automatic); state.panel.showProgress(progress, rates); }
		});
	}
	private boolean isStandardWorld()
	{
		return client.getWorldType().stream().noneMatch(t -> t == WorldType.SEASONAL || t == WorldType.DEADMAN
			|| t == WorldType.BETA_WORLD || t == WorldType.NOSAVE_MODE || t == WorldType.TOURNAMENT_WORLD
			|| t == WorldType.QUEST_SPEEDRUNNING || t == WorldType.PVP_ARENA || t == WorldType.LAST_MAN_STANDING);
	}
	private static BufferedImage navigationIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(ColorScheme.BRAND_ORANGE);
			graphics.setFont(FontManager.getRunescapeBoldFont());
			graphics.drawString("B", 3, 13);
		}
		finally { graphics.dispose(); }
		return image;
	}
	private static final class RuntimeState
	{
		final Clock clock = Clock.systemUTC();
		final SessionTracker tracker = new SessionTracker(clock);
		final ProgressTracker progress = new ProgressTracker();
		final FightTimerTracker fights = new FightTimerTracker(System::nanoTime);
		NativeFightEvents fightEvents;
		FightTimerOverlay fightOverlay;
		NativeCollectionLog collection;
		DropLookup drops;
		BundledDropData data;
		Boolean eligible;
		final Deque<Object> seen = new ArrayDeque<>();
		volatile BossingPanel panel;
		NavigationButton button;
		String profileKey;
		boolean first(Object event)
		{
			for (Object old : seen) { if (old == event) { return false; } }
			if (seen.size() == 64) { seen.removeFirst(); }
			seen.addLast(event);
			return true;
		}
	}
}
