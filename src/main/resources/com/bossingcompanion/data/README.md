# Bundled public boss drop data

`boss-drops.json` contains public structured source data from the Old School RuneScape Wiki, operated by Weird Gloop. Attribution: Old School RuneScape Wiki contributors. Wiki-derived content is identified separately from the plugin's BSD-2 code license; see the Wiki's licensing terms at https://meta.weirdgloop.org/w/Licensing and https://creativecommons.org/licenses/by-nc-sa/3.0/ . Source page links are retained in the JSON. No Wiki images, prices, account identifiers or character progress are included.

Schema 1 covers 41 supported boss identities. The dataset was captured/reviewed on 2026-10-10 and regenerated from those cached public responses; `generatedAt` denotes serialization time, and `mode: cache` does not claim another live retrieval. Maintenance filters economic fields and sorts/deduplicates rows while retaining rates, rolls, conditions, approximation, alternatives, variants and public identity candidates. Original responses remain in the local ignored maintenance cache.

Only collection-log items are bundled. The public native collection manifest filters ordinary loot out while preserving all relevant source/rate components, including guaranteed entries on the log. The readable JSON is approximately 149 KB; full ordinary Wiki tables and unrelated variants are not shipped. The maintenance manifest is kept with the ignored updater and can be regenerated from a copied public game cache.

The reviewed related sources are Unsired for Sire, the lair chests for Obor/Bryophyta, Phosani's Nightmare for the shared Nightmare log, and Dossier for Yama. Components remain explicitly per Unsired/chest opening/dossier/kill; there is no automatic conversion to personal per-kill odds. Phosani sources remain labelled and their counters are not inferred from normal Nightmare KC. Worded or missing probabilities remain explicit. No luck evaluator is included.

The initial native public-cache audit matched 244/251 collection entries. Reviewing those gaps and querying Wiki identity aliases/source reward pages brought the reviewed dataset to 251/251 for the 41 cached boss pages. This static audit does not establish that live game widgets, private progress or every conditional mechanic work correctly; user in-game verification is required.

Local maintainer commands from the project root:

```powershell
.\scripts\update-boss-data.ps1
.\scripts\update-boss-data.ps1 -FromCache
```

Review `scripts/generated/boss-drops.json`, preserve source semantics, audit/test, then copy the candidate over this resource. `/scripts/` is intentionally Git-ignored and transferred separately. No runtime or ordinary build invokes it. HTTP failures, including rate limiting, retain the prior candidate; requests are paced and must be rerun explicitly after the service recovers. The plugin uses this packaged resource with no background HTTP or live fallback. The old `bossing-companion/wikiDropRates` value is inert and its key/group has not been renamed or reused.
