# Bossing Companion

Track bossing sessions in the RuneLite sidebar: session kills, elapsed time,
completed kill times, and awarded loot in a compact grid with native item icons.

Enable **Bossing Companion**, open its sidebar tab, and choose **New session** to
select a boss and pre-start recording. The session begins immediately with zero
kills. **End** freezes the session duration and retains its latest summary.

**Automatic recording** is enabled by default. Change it in RuneLite's normal
plugin configuration (the wrench panel). When no session is active, the first
recognized, player-credited boss kill-count message starts a session for that
boss and includes the triggering kill. This is the first kill observed by the
plugin, not a requirement to have zero lifetime kills. A different boss never
silently replaces an active session. A later kill can start another session after
End if automatic recording remains enabled.

The initial catalogue contains 41 individual combat bosses from the collection
log's Bosses tab. Skilling bosses, grouped entries, raids, wave/activity
encounters, Doom of Mokhaiotl, Demonic Brutus, and Phosani's Nightmare are deferred.
Hespori is included because its encounter uses combat. Group participation does
not exclude bosses such as Nex or Yama.

## What is recorded

Kills require recognized live game-generated kill-count messages. Lifetime KC is
used to identify duplicate notifications; the plugin never imports historical
kills from that number. Enable the game's boss kill-count notifications if they
are disabled. Loot alone, another player's kill, NPC death animations, and phase
changes cannot create kills.

Awarded loot comes from RuneLite's core server NPC loot event and is correlated
with credited completions. This works independently of the optional Loot Tracker
plugin. There is no ground-item or inventory-change fallback: attribution that
cannot be established stays unavailable. **Loot observed** shows how many kills
received an attributable batch. Items left on the ground still count when the
server reported them. Select a loot tile for its full name, exact quantity, and
estimated GE value; noted and unnoted stacks share a display tile.

Game-reported **Fight duration** messages provide last, best, and average completed
kill times. Personal best is not substituted for the current duration. Missing
times stay unavailable and are excluded from the average; **Kills timed** shows
coverage. The plugin does not estimate fight time or display a combat countdown.
Source coverage for each boss needs in-game verification; the catalogue alone
does not guarantee the game supplies loot or duration messages for every boss.

GE totals use RuneLite's cached prices at receipt and are estimates, not sold
revenue or profit after supplies. Unknown tradeable prices make the value partial.
Late matching evidence can settle into the ended session within a bounded
correlation window; ambiguous or expired evidence is left unassigned.

## Session lifetime

Sessions are held in memory only. World hops and temporary connection loss retain
the summary and wall-clock session duration while clearing unmatched observations.
Logout, a different character profile, plugin disable, and client restart clear
the session. No history files or external services are used by this feature.
Pending completion correlation is bounded; the plugin does not retain unlimited
per-kill history. Boss/item icons load through RuneLite's native sprite services.

## Collection-log progress and drop rates

Drop rates are bundled as public JSON for all 41 supported bosses. The plugin
loads the resource once on a worker and makes no background Wiki requests.
The local maintenance updater is `scripts/update-boss-data.ps1`; `/scripts/`
is Git-ignored. It supports initial population and later refreshes; ordinary
builds and client startup never run it. Reviewed data ships with plugin updates.

After a session starts manually or automatically, its boss icon and
**Drop Information** appear in the same sidebar. Expand the section to see **Total kills**, native
unique-item icons, obtained quantities and drop details. The section starts
collapsed and keeps its expansion state as session data refreshes. Information
is tied to that session's boss; the pre-start picker does not browse drops.
The ended summary retains its boss information until a new session or reset.
After End, **New session** sits just below the boss header. Its picker defaults
to that boss; choose **Start new session** to replace the summary, or Cancel
to keep it.
Items remain unknown until the
game supplies evidence. Open your own collection log and visit a boss page to
confirm its missing items and any displayed kill count. Invoking the game's
**Search** can supply positive item quantities across pages; unreceived items
stay unknown. Another player's adventure log is excluded. Shared items are
account unlocks and do not imply a drop from this particular boss.

Drop information works offline and needs no network opt-in. The former
**Wiki drop rates** option has been retired; any saved `wikiDropRates` boolean
is inert, and the config key/group has not been renamed or reused. Other saved
settings are preserved. Missing/corrupt bundled data stays unavailable, without
an HTTP fallback or network Retry. Character progress remains local and in memory.

Select a unique for rarity, the exact source fraction, roll count, conditions,
special mechanics and a Wiki link. Conditional or hidden mechanics are marked;
related rewards retain their source units: per chest opening, Unsired or dossier.
Shared Nightmare entries label Phosani's source explicitly. Clicking the Wiki
link opens the source page only at your request.
Unknown rates never become zero odds. Standard rates are unavailable on modified
worlds. This iteration displays rates only; it does not calculate luck or infer
historical drop timing. Collection data resets on logout, character changes,
plugin disable and restart. Moving between standard and modified worlds also
clears character progress and sessions. There is no progress persistence.

Abyssal Sire's uniques include rewards from offering an **Unsired**. Those rates
are labeled **per Unsired**, with alternative/context notes, separately from the
boss's Unsired drop rate. Missing source matches stay unavailable.

## Live fight timer

Enable **Enable fight timer** in RuneLite configuration before targeting a boss.
It defaults off. This first iteration supports **Obor, Bryophyta and post-quest
Vorkath** on standard worlds; other bosses keep their existing session/progress
features but do not start the timer.

The movable native overlay is a single compact row with a small boss icon and
clock (about 98×26 pixels for a typical observed time). It starts at your first
qualifying owned hit, including a blocked hit. Target selection, NPC spawn and
manual session pre-start do not start it. `~` marks observed timing, which may
start later than the game's official encounter clock. Normal movement or losing
your target does not pause it. Use RuneLite's configured overlay drag hotkey to
reposition it. Hover identifies the boss and distinguishes an initial zero,
running estimate, frozen estimate and game-reported time.

Final death/end evidence freezes provisionally; a matching credited KC confirms
the result and a uniquely matched **Fight duration** can supply the official
final time. **Enable the game's boss kill-count messages** for confirmation.
An uncredited end is discarded after a bounded window, restoring the last
confirmed time or zero. Confirmed results stay frozen indefinitely between
fights, without waiting/status labels; the next fight resets to zero.

Enabling partway through an existing engagement does not resume/backdate it.
Opt-out, player death, scene loading/hops/reconnects and encounter exit interrupt
active/pending timing. Logout/character change clears private results. The live
clock is independent of session recording and never supplies estimated values
to completed last/best/average statistics. No timer history files or external
requests are used.

## Development and verification

Build with `javm exec --jdk temurin@11 ./gradlew.bat build`.
Launch the development client with
`javm exec --jdk temurin@11 ./gradlew.bat run`.
For Jagex accounts, follow RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions to sign in to the development client.

In-game acceptance must be confirmed by the user. Test manual and automatic
recording, a missing-time kill, leaving awarded loot on the ground, End and late
loot, world hops, a different boss during an active session, and logout/character
separation. A successful build or JVM startup is not in-game acceptance.

Also verify collection-log page sync, total kills versus session kills, positive
Search sync without marking unread items missing, native images and compact
details, Wiki opt-in/offline/retry, and logout/character isolation. Vorkath and
Zulrah are useful first cases for the head guarantee and multi-roll rate labels.

Timer acceptance: enable before engaging an eligible pilot boss; test first-hit
start, movement/target loss, death/credit/official-time matching, indefinite
frozen result and next-fight reset. Check opt-out, mid-fight opt-in, interrupted
attempts, Vorkath reawakening, logout/another character and overlay positioning.
