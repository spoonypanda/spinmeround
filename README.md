# Spin Me Round

Copyright (C) 2026 Spoony Panda. Licensed under the GNU General Public License
version 3 (`GPL-3.0-only`); see [LICENSE.txt](LICENSE.txt).

A Forge mod for Minecraft 1.12.2 to play music while you use an AE2-UEL crank or an Extra Utilities 2 Manual Mill. Both integrations are optional.
Music on the Manual Mill fades with its spin-down when you stop turning it. 
You can right click with Records or custom made Compact Discs using resource packs with 
ogg files to add more. Right click with paper (or another item set in the config) to clear
the song.

Press **Ctrl+M** to mute or unmute crank music for yourself. Change the shortcut
under **Options > Controls > Spin Me Round**.

## Config Settings

Config is in `config/spinmeround.cfg` after running for the first time.

```text
playback {
    S:record=
    S:sound=
    S:clearItem=minecraft:paper
    I:volume=100
    B:loop=true
}
```

The `record` variable accepts a vanilla or modded music disc item registry ID. To use a
sound event from a resource pack instead, leave `record` empty and put its resource
ID in `sound`. Both settings default to empty, so unassigned cranks are silent. Existing configs
keep their values when upgrading; clear both settings to disable an old default track.

To make a tagged Compact Disc the default, append its item NBT to its registry ID:

```text
S:record=spinmeround:compact_disc{Sound:"mymusic:spinmeround_song",BPM:256.0}
S:sound=
```

The `Sound` and `BPM` tags use the same item NBT as a CraftTweaker disc recipe.
**BPM is optional.** Remove `,BPM:256.0` from the example to use the default note
timing. Optional display tags are accepted but do not affect playback. Plain record IDs such as
`minecraft:record_cat` still work. `record` takes priority over `sound` if both 
values are set in the config. Invalid item IDs or NBT leave playback silent and produce
a log warning. `graceTicks` defaults to 4 (0.2 seconds) to bridge short gaps between 
turns. `fadeTicks` defaults to 6 (0.3 seconds) for fade-in and fade-out. Set both to 0 
for immediate start/stop. `volume` ranges from 0 (silent) to 100 (full volume).
Records and Master volume sliders both apply.

`fadeBackgroundMusic` defaults to true. Minecraft background music fades out over
half a second while a nearby crank is playing, then fades back to the player's
Music volume afterward. 

Right-click the crank with the configured `clearItem` (default `minecraft:paper`)
to clear its song and stop its playback. The item used to clear is not consumed on usage.
Set `clearItem` to another item registry ID in the config to change it.

## Multiplayer

**Required on both client and server.**

Everyone listening to custom songs needs the matching resource pack enabled.

## Separate custom music resource pack

Example setup for a resource pack using ogg files. Most of you will know how to do this,
but here's an example for any newcomers:

```text
SpinMeRoundDisc.zip
  pack.mcmeta
  assets/
    mymusic/
      sounds.json
      sounds/
        spinmeround.ogg
        thiscouldbeanywhere.ogg
```

`pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 3,
    "description": "Spin Me Round Discs"
  }
}
```

`assets/mymusic/sounds.json`:

```json
{
  "spinmeround_song": {
    "sounds": [
      {
        "name": "mymusic:spinmeround",
        "stream": true
      }
    ]
  },
  "thiscouldbeanywhere_song": {
    "sounds": [
      {
        "name": "mymusic:thiscouldbeanywhere",
        "stream": true
      }
    ]
  }
}
```

Each song gets its own event with one audio file in its `sounds` list. Multiple
files in one list make Minecraft randomly choose between them. The event IDs
are `mymusic:spinmeround_song` and `mymusic:thiscouldbeanywhere_song`; the `name`
inside each list points to the audio filename without the `.ogg` extension.

**BPM is optional.** Songs work without it. To specify a song's BPM, add the
`BPM` tag to its disc's item NBT using the `/give` or CraftTweaker examples below.
This tag belongs on the disc, not in the resource pack's `sounds.json`.

Use Ogg Vorbis, preferably mono for positional playback. Ogg Opus will not work.
Zip the contents with `pack.mcmeta` and `assets` at the root, put
`SpinMeRoundDisc.zip` in the instance's `resourcepacks` folder, and enable it in
Minecraft. Press F3 + T after updating an enabled pack. The server does
not need the pack. Clients missing the sound remain silent and log a warning.
For an assignable item referencing this track, use the Compact Disc below.
A resource pack alone adds no items.

## Compact Disc item

The mod also provides `spinmeround:compact_disc` in the Miscellaneous creative tab.
Set its string NBT tag `Sound` to a resource-pack sound event ID. Use it to assign
songs to cranks; it cannot be played in a jukebox.

Example for Minecraft 1.12.2:

```text
/give @p spinmeround:compact_disc 1 0 {Sound:"mymusic:spinmeround_song",BPM:256.0,display:{Name:"You Spin Me Round"}}
/give @p spinmeround:compact_disc 1 0 {Sound:"mymusic:thiscouldbeanywhere_song",BPM:124.0,display:{Name:"This Could Be Anywhere"}}
```

The optional numeric `BPM` tag sets the timing of the music note effects without
changing the song's playback speed. These examples use `256.0` for You Spin Me
Round and `124.0` for This Could Be Anywhere. Values greater than 0 and up to
1000 are supported. Omit `BPM` to use the default timing of one note every half
second, equivalent to 120 BPM at normal game speed.

Right-click an AE2-UEL crank or Extra Utilities 2 Manual Mill with the disc, then use an empty hand to turn it.
The disc is never consumed, and your song is saved to the crank.
Blank or invalid tags leave the crank's current
selection unchanged and show a message. Different discs can use the same item ID
with different Sound tags and custom display names. No recipe is registered;
modpacks can add recipes and tagged outputs using their scripting mod.

## CraftingTweaks

The `BPM` entries are **optional**: remove either `BPM`
line to use default note timing. The disc will still play its song normally.

```zenscript
val songDisc1 = <spinmeround:compact_disc>.withTag({
    Sound: "mymusic:spinmeround_song",
    BPM: 256.0, // Optional: remove this line to use default note timing.
    display: {Name: "You Spin Me Round"}
});

recipes.addShapeless("spinmeround_song_disc", songDisc1, [
    <minecraft:iron_ingot>,
    <minecraft:redstone>,
    <minecraft:paper>
]);

val songDisc2 = <spinmeround:compact_disc>.withTag({
    Sound: "mymusic:thiscouldbeanywhere_song",
    BPM: 124.0, // Optional: remove this line to use default note timing.
    display: {Name: "This Could Be Anywhere"}
});

recipes.addShapeless("thiscouldbeanywhere_song_disc", songDisc2, [
    <minecraft:iron_ingot>,
    <minecraft:lapis_block>,
    <minecraft:paper>
]);

mods.jei.JEI.addItem(songDisc1);
mods.jei.JEI.addItem(songDisc2);
```

Restart Minecraft after changing the script. The `Sound` tags must match the
event IDs in `sounds.json`. The JEI entries make each named disc searchable.
Existing discs keep their old NBT; craft a fresh disc if you change its sound ID,
then right-click the crank again to replace its saved selection.