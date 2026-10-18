# Spin Me Round

Copyright (C) 2026 Spoony Panda. Licensed under the GNU General Public License
version 3 (`GPL-3.0-only`); see [LICENSE.txt](LICENSE.txt).

Play music while turning an AE2 Wooden Crank, Create Hand Crank, or Hand Crank Generator.
Forge 1.20.1 development build.

All three crank mods are optional; install whichever you want to use.
Create 0.5.1 and 6.x share the integration, so the
same Spin Me Round jar targets both families. Headless server tests pass with Create
0.5.1.i and 6.0.8. Local LAN playback and note effects have been checked with Create
6.0.8; the remaining client integration checks are in progress.

Right-click a supported crank with a vanilla/modded music disc or a Compact Disc to
assign its song. The disc is not consumed. Turn the crank with an empty hand to listen.
Right-click with paper to clear its song. Each crank remembers its own selection.
Music fades when you stop turning and resumes from the saved position on the next turn.
Hand Crank Generator advances music when its handle actually changes position.
Create valve handles and other rotating machines are not included.

Ctrl+M mutes crank music locally without stopping its shared timeline;
change the shortcut under Options > Controls > Spin Me Round. Records and Master
volume settings apply. Background Music fades while a nearby crank is audible.

## Config Settings

World settings are in `serverconfig/spinmeround-server.toml` inside the world folder
(single player: `saves/<world>/serverconfig/`). Forge synchronizes these settings to
joining clients. Put a preset in `defaultconfigs/spinmeround-server.toml` to use it
for new worlds. Under `[playback]`:

- `record`: default disc item ID, optionally followed by NBT; empty by default.
- `sound`: default sound event ID when `record` is empty; empty by default.
- `clearItem`: reusable clearing item, default `minecraft:paper`.
- `volume`: crank volume from 0 to 100, default 100.
- `loop`: repeat songs, default true.
- `graceTicks`: bridge gaps between turns, default 4 ticks.
- `fadeTicks`: fade in/out duration, default 6 ticks.
- `fadeBackgroundMusic`: locally fade background music, default true.

Server playback settings determine the shared song and timing. A crank explicitly
cleared with paper stays silent even when a default song is configured.

## Multiplayer

**Required on both client and server.**

Music plays positionally within 64 blocks. Nearby listeners receive the server's
playback time. Every listening client needs the matching custom audio, supplied
through an enabled resource pack or `kubejs/assets`. Server recipe scripts do not
distribute those files.

## Separate custom music resource pack

Custom songs need audio files and a sound event definition on each listening client.
For a setup without KubeJS, create an ordinary resource pack with this layout:

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

Use Ogg Vorbis audio, preferably mono for positional playback. Ogg Opus is unsupported.

`pack.mcmeta` for Minecraft 1.20.1:

```json
{
  "pack": {
    "pack_format": 15,
    "description": "Spin Me Round Discs"
  }
}
```

`assets/mymusic/sounds.json`:

```json
{
  "thiscouldbeanywhere_song": {
    "sounds": [{ "name": "mymusic:thiscouldbeanywhere", "stream": true }]
  },
  "spinmeround_song": {
    "sounds": [{ "name": "mymusic:spinmeround", "stream": true }]
  }
}
```

The event IDs are `mymusic:thiscouldbeanywhere_song` and `mymusic:spinmeround_song`.
Each `name` points to its `.ogg` filename without the extension. Keep one audio file
per event so the selected song is predictable.

Put the folder or ZIP in each client's `resourcepacks` folder, with `pack.mcmeta` and
`assets/` at the root, and enable it under Options > Resource Packs. This works with
either recipe example. CraftTweaker-only setups use this audio layout. The server
does not need the pack. Missing audio is silent and logged. Press **F3+T** on each
client after changing its audio assets.

KubeJS can also load the same audio files directly from `kubejs/assets`, as shown in
the KubeJS section below.

## Compact Disc item

Find Compact Disc in Tools & Utilities. Its `Sound` tag refers to a sound event in a
resource pack. Optional `BPM` (greater than 0 and at most 1000) controls note particles.
Omit `BPM` to use the default note timing. It does not change playback speed.
There is no built-in crafting recipe; KubeJS or CraftTweaker can add tagged disc outputs.
Both examples below use the same `spinmeround:compact_disc` item with different song
tags and display names. The scripting mods are optional.

For a quick test without a recipe:

```mcfunction
/give @s spinmeround:compact_disc{Sound:"mymusic:thiscouldbeanywhere_song",BPM:124.0}
/give @s spinmeround:compact_disc{Sound:"mymusic:spinmeround_song",BPM:256.0}
```

The audio setup above is required for these custom event IDs. Right-click a supported
crank with the disc, then turn it with an empty hand. The disc is not consumed and
cannot be played in a jukebox.

## CraftTweaker

For CraftTweaker on Forge 1.20.1, save this as `scripts/spinmeround_discs.zs` in the
world host/server instance:

```zenscript
val disc = <item:spinmeround:compact_disc>.withTag({
    Sound: "mymusic:spinmeround_song",
    BPM: 256.0, // Optional: remove this line to use default note timing.
    display: {Name: "{\"text\":\"You Spin Me Round\",\"italic\":false}"}
});

craftingTable.addShapeless("spinmeround_song_disc", disc, [
    <item:minecraft:iron_ingot>,
    <item:minecraft:redstone>,
    <item:minecraft:paper>
]);
```

Craft **iron ingot + redstone + paper**, in any arrangement, to get the named disc.
Restart the world after changing the script. The `Sound` and optional `BPM` tags work
the same way as in the KubeJS example below. Provide the audio through a resource
pack, or through `kubejs/assets` when KubeJS is also installed.

To add another song, add its Ogg file and sound event, then copy a recipe with a unique
recipe ID, `Sound` value, display name, and ingredient combination. Existing discs keep
their saved tags; craft a new disc after changing a recipe's song or BPM.

## KubeJS

For KubeJS 6 on Forge 1.20.1, save this as
`kubejs/server_scripts/spinmeround_discs.js` in the world host/server instance:

```javascript
ServerEvents.recipes(event => {
  const disc = Item.of('spinmeround:compact_disc', {
    Sound: 'mymusic:thiscouldbeanywhere_song',
    BPM: 124.0, // Optional: remove this line to use default note timing.
    display: { Name: '{"text":"This Could Be Anywhere","italic":false}' }
  })

  event.shapeless(disc, [
    'minecraft:iron_ingot',
    'minecraft:lapis_block',
    'minecraft:paper'
  ]).id('kubejs:spinmeround/this_could_be_anywhere')
})
```

Craft **iron ingot + lapis block + paper**, in any arrangement, to get the named disc.
Restart the world after adding or editing the recipe. Installing KubeJS or changing
startup scripts requires a full game/server restart.

### Audio files with KubeJS

KubeJS automatically loads its `assets` folder as client resources. You can use loose
files here without creating or enabling a separate resource-pack ZIP:

```text
kubejs/
  assets/
    mymusic/
      sounds.json
      sounds/
        spinmeround.ogg
        thiscouldbeanywhere.ogg
```

Use the same `sounds.json` and Ogg Vorbis files from the resource-pack example above.
Both recipe scripts can use these assets, including the CraftTweaker recipe when
KubeJS is also installed. Every listening client needs the same files. Press **F3+T**
on each client after changing its audio assets.
