# Seeding a test server's Discover sliders

The discovery hubs show the rows a server's Discover settings list. The demo has a few. A real server has whatever its admin set up. To try every kind of row on a test server, run `tools/seed-discover-sliders.py` against it.

Run it by hand. It is not part of the build and CI never runs it.

## Run it

```
SEERR_URL=http://192.168.1.20:5055 SEERR_API_KEY=<an administrator's key> tools/seed-discover-sliders.py
```

It needs Python 3 and nothing else. Both variables are required, and the key must be an administrator's.

| Flag or variable | What it does |
| --- | --- |
| `--dry-run` | Reads from the server, prints each write it would make, and writes nothing. |
| `--clean` | Removes the sliders the script made. It leaves everything else alone, including the built-in sliders it switched on. |
| `SEED_REGION` | The region for the streaming sliders. The default is `GB`. |

## What it sets

It switches every built-in slider on and keeps the server's order. It then adds one custom slider of each kind the hubs map. Each is titled `Seed: ...`, so running the script again updates them and does not add copies.

| Kind | Holds | Shows on |
| --- | --- | --- |
| Movie keyword | the keyword `superhero` | Movies |
| TV keyword | the keyword `anime` | Series |
| Movie genre | Science Fiction | Movies |
| TV genre | Sci-Fi & Fantasy | Series |
| Studio | Pixar | Movies |
| Network | Netflix (TMDB id 213) | Series |
| Search | the text `heist` | Movies and Series |
| Movie streaming | Netflix, Amazon Prime Video and Disney Plus, where the region lists them | Movies |
| TV streaming | the same providers | Series |

The built-in sliders (recently added, recent requests, watchlist, trending, popular and upcoming, genres, studios and networks) need no data.

The ids come from the server itself. The script searches by name through the server's own lookups, so each slider holds an id TMDB really has. The network is the one exception, because TMDB has no network search. The script asks the server for that id's name and prints it.

## What it never does

- It never deletes a slider it did not make.
- It never resets the list.
- It never touches users, requests, or any setting outside Discover.
- It never writes a slider with empty data. If a lookup finds nothing, it names that slider as skipped and goes on.

The server's other custom sliders are kept in place, because every write sends the whole list back with each slider as it was.

## What it prints

It prints how many sliders it added, updated and skipped. It then lists every slider on the server: its id, kind, whether it is on, its title, which hub it shows on, and the data it holds with the name that data resolved to. A dry run prints the writes and not the list.
