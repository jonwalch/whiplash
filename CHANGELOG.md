# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](changelog),
and this project adheres to [Semantic Versioning](semver).

<!--
## X.X.X - XXXX-XX-XX - XXXXXX

### Added
### Changed
### Deprecated
### Removed
### Fixed
### Security
-->

## 0.1.XX - 2019-XX-XX - ROADMAP

### Added
- [ ] Style Login/Signup form success/error states
- [ ] Add About page/route (#34)

### Changed
- [ ] Embed video and chat separately (#32)
  - [ ] Add toggle button to hide/show Twitch chat window (#32)
  - [ ] When chat is hidden, expand video to fill the space (#32)
- [ ] Conditionally render Twitch chat only when cookies are available (#32)
- [ ] Move inlined CSS to linked cacheable asset file(s)
- [ ] Further modularize markup components/layouts
- [ ] Refactor login, logout, and signup buttons as a single component with props

### Fixed
- [ ] Fix Sign up form layout on large screens (#34)

## 0.1.45 - 2019-12-11 - Update npm scripts and Readme

### Added
- [x] Add test script to run backend tests
- [x] Add serve script to build CSS and JS, then serve Whiplash to localhost:3000
- [x] Add flush:cache script to flush the CDN cache
- [x] Add some details to Readme and package.json

### Changed
- [x] Update develop script to also build JS

### Removed
- [x] Remove package.json license and version, since the package is not published on npm

## 0.1.42 - 2019-12-06 - Main content layout

### Added
- [x] Style Vote
- [x] Add favicon (#34)

### Changed
- [x] Bring username and cash out of Login component (#34)
- [x] Change Log In button to Log Out when user is logged in (#34)
- [x] Let site height fill window
- [x] Update Bets markup
- [x] Style Twitch text
- [x] Update Twitch inactive and loading placeholder and text
- [x] Style leaderboard
- [x] Update Readme
- [x] Style Bet (#34)
- [x] Never show login and signup forms at the same time
- [x] Never show signup button while logged in
- [x] Refactor login, logout, and signup buttons as functions

### Removed
- [x] Remove button focus style after click (without breaking accessibility/keyboard/screenreaders) (#34)
- [x] Remove inline styling and extra markup

### Fixed
- [x] Fix Twitch embed height on mobile
- [x] Fix container grandchild descendent bug
- [x] Fix leaderboard markup

## 0.1.39 - 2019-11-29 - Header, footer, navigation

### Added
- [x] Add CSS reset
- [x] Add a few CSS defaults
- [x] Add better CSS defaults
- Design layout and style components
  - [x] Site header
  - [x] Site footer
  - [x] Navigation
  - [x] Responsive Twitch embed
  - [x] Signup
  - [x] Remove container `max-width`
  - [x] Add brand fonts
  - [x] Signup form
  - [x] Form state(s)
  - [x] Login form
  - [x] Move Login into header/footer
- Add CSS pre-processing and post-processing
  - [x] Sass syntax
  - [x] PostCSS plugins (`autoprefixer`, dynamic imports, `precss`, beautify, etc.)
  - [x] Concatenate/bundle CSS components
  - [x] Minification with sourcemaps

### Changed
- [x] Update component markup to be a bit more semantic

### Removed
- [x] Remove extra `div` elements

[changelog]: https://keepachangelog.com/en/1.0.0/
[semver]: https://semver.org/spec/v2.0.0.html
