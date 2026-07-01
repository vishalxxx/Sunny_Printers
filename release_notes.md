# Sunny Printers ERP Release Notes

All notable changes to this project will be documented in this file.

## [1.0.1] - 2026-07-01
### Features
- Added automatic WiX toolset bootstrapper to package pipeline.
- Integrated PostgREST-based auto-update registry updater.
- Designed Enterprise Release Manager with command-line and graphical UI wrappers.

### Bug Fixes
- Resolved schema circular dependencies during SQLite database initialization.
- Fixed foreign key constraints for draft job creations.

## [1.0.0] - 2026-06-30
### Features
- Initial stable release of Sunny Printers ERP application.
- Configured Supabase synchronizer and local offline-first SQLite synchronization.
