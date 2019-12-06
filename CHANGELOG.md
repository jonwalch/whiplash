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

## 0.1.41 - 2019-XX-XX - Main content layout

### Added
- [ ] Style Vote
- [ ] Style Bet (#34)
- [ ] Style Twitch video
  - [ ] Add toggle button to hide/show Twitch chat window (#32)
    - [ ] When hidden, expand video to fill the space (#32)
- [ ] Style Twitch chat
- [ ] Style Leaderboard
- [ ] Style Login/Signup form success/error states
- [x] Add favicon (#34)
- [ ] Add About page/route (#34)

### Changed
- [ ] Bring username and cash out of Login component (#34)
- [x] Change Log In button to Log Out when user is logged in (#34)
- [ ] Embed video and chat separately (#32)
- [ ] Conditionally render Twitch chat only when cookies are available (#32)
- [ ] Move inlined CSS to linked cacheable asset file(s)
- [ ] Further modularize markup components/layouts

### Removed
- [x] Remove button focus style after click (without breaking accessibility/keyboard/screenreaders) (#34)

### Fixed
- [ ] Fix Twitch embed height on mobile
- [ ] Fix Sign up form layout on large screens (#34)

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
