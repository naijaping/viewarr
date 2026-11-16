# The UK Sport on TV plugin (optional)

**UK Sport on TV** is a Dispatcharr plugin that adds UK football TV listings to Dispatcharr. Viewarr doesn't need it, but uses it when it's there. This page explains what it does, and what Viewarr does without it.

## What the plugin does

It runs on your Dispatcharr server, after each guide (EPG) refresh, at most every 6 hours. It does three things.

### 1. Fetches the UK football TV listings

It reads the UK's televised football listings (live-footballontv.com) and keeps every match for the coming weeks: kick-off time, the two teams, the competition, and the channels showing it (Sky Sports, TNT Sports, Premier Sports, BBC, ITV, Amazon Prime, club TV…). The list is stored in the plugin's own settings, where players such as Viewarr can read it.

### 2. Adds matches to your channel guides ("Add matches to channel guides")

Most providers' guides only cover 3–7 days. The plugin puts each listed match **into your real TV guide**, on the channel showing it and at its time, so a match three weeks away already shows in the guide, for example *Man City v Fulham* on TNT Sports 1.

- It only fills gaps: where the guide already has a programme at that time, nothing is added.
- It never changes or removes the provider's own programmes.
- It re-adds the matches after every guide refresh. Once the provider's real listing arrives, the real one is used.

In other words, the guide holds the match early, until the real listing confirms it.

### 3. Fills in club channels ("Club channels")

If your lineup has the clubs' own channels (`SPFL - CELTIC TV`, `EPL - LFC TV`, MUTV…), each league game that **isn't** on UK TV (such as the Saturday 3pm games) is added to the **home club's** channel guide, since the home club streams it. Fixtures come from ESPN's public data. This is why the plugin creates a guide source called *UK Sport on TV* in Dispatcharr: it holds those club-channel listings.

Whether a club actually streams a particular game depends on the club and on where you are.

## What Viewarr does without it

From Viewarr 0.28.9, the Sports page finds where each game is on by itself, with no plugin:

1. **Your own TV guide**: a programme naming both teams around kick-off.
2. **Sky's public TV guide**: Sky Sports, TNT Sports, Premier Sports, BBC, ITV, Channel 4 and 5, S4C and BBC Alba, about a week ahead. Viewarr only sends channel numbers and dates.
3. **An event channel** named after the match.
4. **The club's own channel** for an English, Scottish or Welsh game that's on nothing else.

Saturday 3pm English and Scottish league games are marked *not on UK TV*. The game page says where each channel came from.

## Why you might still want it

- **Weeks ahead.** Viewarr's own lookups reach about a week; the plugin's listings go weeks further.
- **Amazon Prime and club TV games** are in its listings; Sky's guide doesn't carry them.
- **The guide itself.** Because the plugin writes into Dispatcharr's guide, matches show early in the Live TV guide (so you can set recordings ahead), and in **every** app that uses your Dispatcharr guide (TiviMate, Plex and others), not just Viewarr.

If none of that matters to you, you don't need it.

## Settings

| Setting | What it does |
|---|---|
| Days ahead | How many days of listings to keep. The source lists a few months; fewer keeps the data small. |
| Add matches to channel guides | Puts each match on its channels' guides where the guide has nothing at that time. |
| Club channels | Adds games that aren't on UK TV to the home club's channel, if you have it. |

Actions: **Refresh listings now**, **Show status**, **Remove added matches** (takes everything the plugin added out of the guide).
