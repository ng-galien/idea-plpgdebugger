<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# idea-plpgdebugger Changelog

## [Unreleased]

## [222.0.4]
- Remove indirect debug option
- Fix console output

## [222.0.3]
- Update plugin version
- Update plugin gradle version
- Update Github Actions

## [222.0.0]
- Branch 222
- Upgrade plugin version
- Upgrade kotlin version
- Upgrade platform version

## [221.1.4]
- Upgrade plugin version
- Upgrade kotlin version
- Upgrade platform version
- Remove deprecated implementation

## [221.1.3]
### Fixed
- Fix shared_preload_libraries detection
- Fix search_path from database tree
- Custom command to debugger session only
- Fix run non SELECT query
- Remove depreciated API warnings

### Added
- Build against Intellij 221.1.1

## [221.1.2]
- Kotlin upgrade
- Plugin versions upgrade
- Images for Dockerized debugger

## [221.1.1]
- Intellij 2022.1 support

## [213.1.1]
### Added
- Change version scheme

### Fixed
- NPE on database tree

## [1.2.1]
### Added
- Debug a routine from the database tree

## [1.2.0]
### Added
- Prevent running debugger twice
- Manage quoted identifier and schema
- Experimental support of indirect debugging

## [1.2.0-beta2]
### Fix
- Sessions issues
- NULL handling with array and composite types

## [1.2.0-beta]
- Use background task:
  - User can control debug task
  - Gracefully terminate session
- Inline variable display
  - User can see variable in code
  - Variable with long value are truncated

## [1.1.1]
### Added
- Plugin configuration:
  - Custom query on both running and debugging session
  - Add SQL specific verbosity

### Changed
- Fix call identification
- Fix breakpoint support for PROCEDURE
- Improved breakpoint management

## [1.0.3]
### Added
- Plugin configuration:
  - Timeout control
  - Output verbosity
  - Internal queries(to fix manually)
  - Failure tests(for developer)

### Changed
- Fix procedure detection on PG 14
- Better error handling
- Close owner connection when something has failed

## [1.0.2]
### Added
- Plugin configuration: timeouts and output verbosity

### Changed
- Fix procedure detection
- Many internal changes

## [1.0.1]
### Changed
- Ignore case when for function detection
- Avoid JSON transformation error

## [1.0.0-rc1]
### Changed
- Commands are within the PlExecutor
- Better verbosity
- Better use of coroutines
- Solve Prevent when extension is not avaible #12 (SQL issue)
- Progress on A routine with an enum in arg fails #13
- Fix compatibility with last DatabaseTool implementation

## [1.0.0-beta]
### Added
- Support for breakpoint
- Implements a Virtual File System to store debug files

### Changed
- Refactor many parts for future test support

## [1.0.0-alpha1]
### Added
- Fix procedure identification on specific schema
- Gracefully handle debug connection

## [1.0.0-alpha]
### Added
- Allow direct debug of a PL/pg function
- View variables
- Explore arrays and composite type