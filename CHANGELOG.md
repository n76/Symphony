# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Display artwork from track on lock screen if track has artwork.
- Revise notification icon

### Changed
- Minimum API changed to 21 (LOLLIPOP). Not sure how it was being compiled before as media manager calls require API 21.

### Removed
- Not applicable

## [1.1.3] 2018-05-25
### Changed
- Revised icons. Thanks to @mansya
- Gradle version changed

## [1.1.2] 2018-01-31
### Changed
- Change "random" to "shuffle" on spinner to more accurately describe what it does.
- Change min SDK version to 10 (allow to run on Android Gingerbread 2.3.3 and later).

## [1.1.1] 2017-08-18
### Added
- Show album, track and artist in notification.

### Changed
- Swap artist and album information published to other apps on phone if in random album mode. See issue 5.
- Avoid crash on start on some conditions where the local music on the phone has been changed.

## [1.1.0] 2017-08-10
### Added
- Support for media session (allows other apps to see/control what is playing). Specifically, if plugged into a car entertainment system the current track information should be shown on the display and the previous and next track buttons, etc. on the car should control the app.

### Changed
- Fixed bug where notification bar show next track info instead of current track info.

## [1.0.3] 2017-08-08
### Added
- Resolve Issue 3: Remember last track and position in last track when starting app.

### Changed
- Fix issue 4: Switching between portrait and landscape while playing causes ⏸ to become ▶

## [1.0.2] 2017-08-06
### Added
- Support for landscape mode on display.

### Changed
- Fix issue 2: Track timer and length display 00:00 when paused

## [1.0.1] 2017-07-28
### Changed
- Improve random play to better avoid replaying recent items.
- New app icon to better indicate app’s focus on classical music.

## [1.0.0] - 2017-07-27
### Added
- Japanese Translation. Thanks to @naofum

### Changed
- Avoid crash on empty genre
- Better conformance with semantic versioning

### Removed
- No Applicable

## [0.9.2] - 2017-07-24

### Changed
- Improve sorting of album titles.
- Remove write external storage permission.
- Better resume from returning to app while music paused.
- Revise build to not use beta version of support library

## [0.9.1] - 2017-07-19

### Added
- Show album name in notification.

### Changed
- Fix issue with current track not resuming after long loss of audio focus.

## [0.9] - 2017-07-19

### Added
- Initial release.
