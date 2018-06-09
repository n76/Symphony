<img src="fastlane/metadata/android/en/images/featureGraphic.png"/>

Symphony - A music player for Android
=====================================
Yet another music player for Android, what makes this one different is that it supports playing random albums within a genre. It is tailored toward playing classical music and other genres where you wish to listen to a random albums (symphonies) with the songs (movements) played in the correct order.

Features
========
- “Gap-less” playback.
- Very simple interface.
- Within a genre, songs may be played in sequence, randomly or by random album.

Why the name “Symphony”?
========================
Partly because [AndreasK](https://f-droid.org/packages/de.kromke.andreas.unpopmusicplayerfree/) used the much better name “Unpopular Music Player”. And partly because it is tailored toward listening to symphonic music.

Operation
=========
Symphony gets the list of genres for music stored locally on the phone from the Android system.

When a genre is selected, a playlist is created from all the songs in the genre sorted by album and within album by track number.

To make it easy to access a specific album a selector is presented with the names of all albums in the list. When an album is selected the playlist is scrolled to the first song on the album.

Playback can be sequential (all tracks for all albums in alphabetical order of album name), by random track/song or by random album.

The result is that if you select a classical genre then you can sequentially or randomly play all the albums (performances/compositions) in the genre, with the movements (songs) in the correct order.

Copyright
=========
This program is Free Software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

Other
=====
The media control icons for this app are from the [GNOME Desktop icons](https://commons.wikimedia.org/wiki/GNOME_Desktop_icons) set.

Permissions
===========
|Permission|Use|
|:----------|:---|
READ_EXTERNAL_STORAGE|Needed to access music stored on your microSD card.
WAKE_LOCK|So the player can go to the background without stopping

Update History
==============
[History is now a separate file](CHANGELOG.md)

[![Get it on F-Droid](get_it_on_f-droid.png?raw=true)](https://f-droid.org/packages/org.fitchfamily.android.symphony/)
