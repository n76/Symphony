# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Not applicable

### Changed
- Fix crash when next pressed and there are no tracks in genre.

### Removed
- Not applicable

## [1.1.12 - 29-Apr-2019]

### Changed
- Fix crash prev or next pressed when denied permission to access song storage.

## [1.1.11 - 04-Apr-2019]
### Changed
- Added privacy policy.
- Update Gradle version.
- Revise logic on previous track button: Previous track button now uses the shuffle list rather than tracking play history. This allows the previous button to be operational even if there was no previous track played (initial startup) and eliminates need for special handling to avoid putting previous track into play history.
- Fix crash when starting notification on newer versions of Android (tested on 8.1)

## [1.1.10]
### Changed
- Update build environment.

## [1.1.9]
### Changed
- Improve landscape display on some phones.

## [1.1.8]
### Changed
- Avoid cutting off player controls on some displays.

### Removed
- Remove work around for Grom Audio adaptor metadata information display.

## [1.1.7] 2018-06-12
### Changed
- Improve appearance of user interface.
- Updated screenshots

## [1.1.6] 2018-06-11
### Changed
- Clean up logic for getting track artwork. If track has no artwork use application's icon.
- Add album artwork (from first song/track in album) to album selection.

## [1.1.5] 2018-06-08
### Changed
- Customize playback controls
- Revise color scheme
- Revise display logic

## [1.1.4] 2018-06-03
### Added
- Display artwork from track on lock screen if track has artwork.
- Revise notification icon
- Place media controller under song list.

### Changed
- Minimum API changed to 21 (LOLLIPOP). Not sure how it was being compiled before as media manager calls require API 21.

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
