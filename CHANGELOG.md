<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# idea-plpgdebugger Changelog

## [Unreleased]

### Maintenance

- Bump library versions

## [233.0.1] - 2023-12-12

### Maintenance

- Compatibility with Intellij 2023.* [#154](https://github.com/ng-galien/idea-plpgdebugger/issues/154)

### Enhancements

- Diagnostic for missing extension [#153](https://github.com/ng-galien/idea-plpgdebugger/issues/153)

## [232.0.3] - 2023-08-12

### Maintenance

- Project template
- Update dependencies
- Code cleanup

### Bugfixes

- Fix does not load the latest function [#107](https://github.com/ng-galien/idea-plpgdebugger/issues/107)

## [231.0.1] - 2023-05-09

### Bugfixes

- Fix NoSuchElementException when running pldbg_create_listener [#102](https://github.com/ng-galien/idea-plpgdebugger/issues/102)

## [222.3.3] - 2023-03-31

### Maintenance

- Platform 2023.1
- Remove deprecated code

## [222.3.2] - 2023-02-02

### Bugfixes

- CHAR type is now managed as TEXT [#93](https://github.com/ng-galien/idea-plpgdebugger/issues/93) [#94](https://github.com/ng-galien/idea-plpgdebugger/issues/94) - Thanks to  [ghtropic](https://github.com/ghtropic)

### Dependencies

- Kotlin 1.7
- Intellij plugin 1.12

## [222.3.0] - 2022-12-07

### Dependencies

- Intellij 222.3
- Graddle plugin 1.10

## [222.2.1]

### Bugfixes

- Incorrect line position [#86](https://github.com/ng-galien/idea-plpgdebugger/issues/86)

## [222.2.0]

### Features

- Tree support for JSON variables
- RECORD type is managed as JSON
- Update Dockerfile to use latest version of plpgdebugger

### Breaking changes

- Update the postgres plugin for proper RECORD handling

## [222.1.0]

- Outputs RECORD type for PG 14 & 13 (Thanks to @cvas71)
- Update Docker images.

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

- - User can control debug task
  - Gracefully terminate session
- - User can see variable in code
  - Variable with long value are truncated

## [1.1.1]

### Added

- - Custom query on both running and debugging session
  - Add SQL specific verbosity

### Changed

- Fix call identification
- Fix breakpoint support for PROCEDURE
- Improved breakpoint management

## [1.0.3]

### Added

- - Timeout control
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

[Unreleased]: https://github.com/ng-galien/idea-plpgdebugger/compare/v233.0.1...HEAD
[233.0.1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v232.0.3...v233.0.1
[232.0.3]: https://github.com/ng-galien/idea-plpgdebugger/compare/v231.0.1...v232.0.3
[231.0.1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.3.3...v231.0.1
[222.3.3]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.3.2...v222.3.3
[222.3.2]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.3.0...v222.3.2
[222.3.0]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.2.1...v222.3.0
[222.2.1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.2.0...v222.2.1
[222.2.0]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.1.0...v222.2.0
[222.1.0]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.0.4...v222.1.0
[222.0.4]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.0.3...v222.0.4
[222.0.3]: https://github.com/ng-galien/idea-plpgdebugger/compare/v222.0.0...v222.0.3
[222.0.0]: https://github.com/ng-galien/idea-plpgdebugger/compare/v221.1.4...v222.0.0
[221.1.4]: https://github.com/ng-galien/idea-plpgdebugger/compare/v221.1.3...v221.1.4
[221.1.3]: https://github.com/ng-galien/idea-plpgdebugger/compare/v221.1.2...v221.1.3
[221.1.2]: https://github.com/ng-galien/idea-plpgdebugger/compare/v221.1.1...v221.1.2
[221.1.1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v213.1.1...v221.1.1
[213.1.1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.2.1...v213.1.1
[1.2.1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.2.0-beta2...v1.2.0
[1.2.0-beta2]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.2.0-beta...v1.2.0-beta2
[1.2.0-beta]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.1.1...v1.2.0-beta
[1.1.1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.0.3...v1.1.1
[1.0.3]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.0.0-rc1...v1.0.1
[1.0.0-alpha]: https://github.com/ng-galien/idea-plpgdebugger/commits/v1.0.0-alpha
[1.0.0-alpha1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.0.0-alpha...v1.0.0-alpha1
[1.0.0-beta]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.0.0-alpha1...v1.0.0-beta
[1.0.0-rc1]: https://github.com/ng-galien/idea-plpgdebugger/compare/v1.0.0-beta...v1.0.0-rc1
