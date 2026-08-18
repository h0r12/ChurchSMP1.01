# Church SMP — Plugin Skeleton

A working starting implementation of the Church SMP design doc: alignment
scoring, tier-based passive effects, and all 7 special weapons activated via
the offhand-swap key (plain = Ability 1, sneak + swap = Ability 2).

## Build

### Option A — GitHub Actions (no local install needed)

This project includes `.github/workflows/build.yml`, which builds the jar
in the cloud on every push. Steps:

1. Create a new **public or private** repo on GitHub.
2. Upload this entire folder's contents to it (drag-and-drop works fine
   via GitHub's web UI, or `git push` if you're comfortable with git).
3. Go to the repo's **Actions** tab. A "Build ChurchSMP Plugin" run should
   start automatically (or click **Run workflow** if it doesn't).
4. Once it finishes (green checkmark, ~1-2 minutes), click into the run,
   scroll to **Artifacts**, and download `ChurchSMP-jar` — that's a zip
   containing `ChurchSMP.jar`, ready to upload to Minehut's `plugins/`
   folder.

### Option B — Build locally

Requires **JDK 21+** and Maven, with internet access to pull the Paper API from
`repo.papermc.io` (declared in `pom.xml`). This project targets **Minecraft
1.21.11** ("Mounts of Mayhem") — the pom pulls `paper-api:1.21.11-R0.1-SNAPSHOT`.

```
mvn clean package
```

The built jar lands in `target/ChurchSMP.jar` — drop it in your server's
`plugins/` folder (Paper or a Paper-based fork). If your server runs a
different Minecraft version, bump the `paper-api` version in `pom.xml` to
match (and the `maven.compiler.source/target` back to 17 if you go below
1.21.4 — see the note below).

> I could not compile this in my sandbox (no network access to fetch the
> Paper API jar), so treat it as reviewed-but-unverified: skim it, especially
> `Particle` / `PotionEffectType` / `Attribute` constant names, since Paper
> renames a few of these between Minecraft versions. One rename I already
> caught and fixed: `Attribute.GENERIC_MAX_HEALTH` / `GENERIC_ATTACK_DAMAGE`
> became `Attribute.MAX_HEALTH` / `Attribute.ATTACK_DAMAGE` as of API 1.21.4+.
> If you target an older version, you'll need to revert that.
>
> 1.21.11 also requires **Java 21** on the server itself (not just for
> building) — it was the last version before Mojang required a newer JDK, so
> make sure whatever's running your server jar is on JDK 21+.

## What's implemented

- **Alignment system** — score, decay, 5 tiers, YAML persistence (`alignment/`).
- **Deeds** — a starting set of listeners for murder, villager kills,
  zombie villager curing, breeding near holy ground, griefing/desecrating
  church regions (`listeners/DeedListener.java`). The full deed table from
  the design doc has more entries (tithing GUI, altar, confession booth,
  sermons) that need their own custom blocks/GUIs — see "Next steps" below.
- **Passive effects** — periodic tier-based buffs/debuffs, holy-ground burn
  for Fallen players, fall-damage negation for Good tiers
  (`listeners/EffectListener.java`).
- **All 7 weapons** — item creation with PDC tagging, offhand-swap /
  shift+offhand activation, cooldowns, and concrete (if simple) effects for
  all 14 abilities (`weapons/`).
- **Church regions** — a self-contained cuboid region store for "holy
  ground" (`util/ChurchRegionManager.java`). Swap in WorldGuard region
  checks here if your server already runs it.
- **Commands** — `/alignment`, `/donate`, `/sermon start|cancel`,
  `/churchadmin give|set|region|shrine`.
- **Shrines** (`shrine/`) — any existing block can be designated as an
  Altar, Offering box, or Confession booth via
  `/churchadmin shrine add <type>` while looking at it:
  - **Altar**: right-click for a small daily-capped prayer bonus.
  - **Offering**: right-click opens a 9-slot GUI; whatever's left inside
    when the player closes it is tithed (points via
    `good-deeds.tithe-per-item`, capped by `good-deeds.tithe-daily-cap`).
  - **Confession**: only usable by Wicked/Fallen players — hand over a
    stack of items for redemption points (`confession.points-per-item`,
    capped by `confession.daily-cap`). This is the primary "comeback"
    path referenced in the original design doc.
- **Sermons** (`sermon/`) — `/sermon start [durationSeconds] [radius]`
  starts a timed gathering; whoever stays within radius for the whole
  duration gets the `good-deeds.sermon-attendance` bonus when it ends.
- **Region wand** (`util/RegionWandListener.java`) — `/churchadmin region
  wand` gives a Blaze Rod wand: left-click sets corner 1, right-click sets
  corner 2, then `/churchadmin region create` saves the cuboid as
  consecrated ground.
- **Weapon crafting** (`weapons/WeaponRecipeManager.java`,
  `CraftingGuardListener.java`) — all 7 weapons have shaped recipes using
  thematic ingredients. Anyone can gather the ingredients, but
  `CraftingGuardListener` nulls out the crafting result unless the
  crafter's alignment tier matches the weapon's category — a Fallen
  player can assemble the ingredients for the Blade of the Archangel but
  the crafting table will refuse to yield it.

## Next steps (not yet built)

- Persistent weapon lockout when a player's tier no longer matches
  (currently just blocks activation — the item stays in inventory either way).
- Region wand doesn't yet warn if corners are in different worlds.
- No admin command to remove/list regions or shrines yet — only add.
- Confession/offering GUIs are plain chest inventories; a custom
  crafted-item-value system (rather than flat per-item points) would make
  tithing/confession harder to game with junk items.
