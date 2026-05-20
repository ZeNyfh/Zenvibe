# Zenvibe

Discord bot, written in java using JDA and lavaplayer for audio functionality. Has various site support, custom (user-defined)
dj users or roles and some other cool features for you to play with once you [invite](http://zenvibe.ddns.net/) or self-host the bot.

## Language support:
* English 100% - ZeNyfh
* Polish 100% - ZeNyfh
* Dutch 98% - etherealelysia
* Danish 100% - SharkRainstorm
* Spanish 98% - Nois
* Bulgarian 100% - sami0505

## Requirements

* [JDK 25](https://adoptium.net/temurin/releases/?version=25&package=jdk)

## Usage

Make sure to define a token and change any parameters you wish to change (such as the bot colour) within the .env file.
If there is no .env file, try running the bot once. It will proceed to create any missing files.

Run the bot using the latest included .jar file in releases or compile the jar yourself from source.

## Spotify Setup

Spotify support uses [Spotify Tokener](https://github.com/topi314/spotify-tokener) for Spotify's anonymous/account web tokens.

Start the tokener service:

```bash
docker compose up -d spotify-tokener
```

Then set these values in `.env`:

```dotenv
SPOTIFYCLIENTID=your_spotify_client_id
SPOTIFYCLIENTSECRET=your_spotify_client_secret
SPOTIFYTOKENERENDPOINT=http://localhost:8080/api/token
SPOTIFYPREFERPARTNERAPI=true
SPOTIFYUSECLIENTCREDENTIALS=false
SPOTIFYCOUNTRYCODE=gb
```

Keep `SPOTIFYUSECLIENTCREDENTIALS=false` unless the owner of the Spotify app has Premium. For account-backed Spotify features, also set `SPOTIFYSPDC` to the value of your Spotify `sp_dc` cookie from `https://open.spotify.com`.

## Java Installation

JDK 25 can be acquired from [Temurin by Adoptium](https://adoptium.net/temurin/releases/?version=25&package=jdk) or from other trusted sources.

When launching manually with `java -jar`, add `--enable-native-access=ALL-UNNAMED` if your runtime does not honor the manifest `Enable-Native-Access` entry.

## Note / Help

If you have any problems with self-hosting or using the bot, contact me on discord `@ZeNyfh`
