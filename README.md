Symphony - A music player for Android
=====================================

Yet another music player for Android, what makes this one different is that it supports playing random albums within a genre. It was written because the author was unable to find a free music player that was tailored toward playing classical music and other genres where you might wish to listen to a random symphony (album) with the movements (songs) within that symphony (album) played in order.

Why the name “Symphony”? Partly because [AndreasK](https://f-droid.org/packages/de.kromke.andreas.unpopmusicplayerfree/) beat me to the much better name “Unpopular Music Player” and partly because I tailored it to how I listen to symphonic music.

There are whole discussions on how classical music might be tagged and which music player supports which tags. [The Well-Tempered Computer](http://www.thewelltemperedcomputer.com/TG/2_Classical.html) has a pretty good presentation of the issue. In the case of Symphony, only tags supported by Android’s media store are used.

Operation
=========
Symphony gets the list of known music genres from Android’s media provider. For each genre, it gets all “songs” with their album information. This is sorted by album and within album by track. To make it easy to access a specific performance (album) a selector is presented with the names of all albums in the list. When an album is selected the genre playlist (songs) is scrolled to the first track on the album.

Playback can be sequential (all tracks for all albums in alphabetical order of album name), by random track/song or by random album.

The result is that if you select a classical genre then you can sequentially or randomly play all the albums (performances/compositions) in the genre, with the movements (tracks) in the correct order.

Copyright
=========
This program is Free Software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

Other
=====
The icon for this app was created from an image stated to be royalty free. If that representation is incorrect, the icon will be changed immediately upon notice.

Permissions
===========
|Permission|Use|
|:----------|:---|
READ_EXTERNAL_STORAGE|Needed to access music stored on your microSD card.
WAKE_LOCK|So the player can go to the background without stopping
MEDIA_CONTENT_CONTROL|So we can actually control and play you music.

Update History
==============
|Version|Date|Comment|
|:-------|:----:|:-------|
0.9|19July2017|Initial release. Not 1.0.0 as testing on multiple devices with multiple versions of Android has not been performed.
0.9.1|21July2017|<ul><li>Show album name in notification.</li><li>Fix issue with current track not resuming after long loss of audio focus.</li></ul>
0.9.2| ???? |<ul><li>Improve sorting of album titles.</li><li>Remove write external storage permission.</li><li>Better resume from returning to app while music paused.</li><li>Revise build to not use beta version of support library</li></ul>

