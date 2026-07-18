# Recover Deleted PHP Backend Files

The legacy PHP backend files in `server/php_variant/` were deleted in a dangling branch history that is not reachable from the current `main` branch. The files were progressively removed starting around commit `da9ac82` and completely gone in `c69e451`. The most complete state of the `server/php_variant/` directory exists in commit `1f2fdfe3055232407a2f5ab62099fa98638e404f`.

## User Review Required

> [!IMPORTANT]
> The recovery will be performed by extracting files from a dangling commit (`1f2fdfe3055232407a2f5ab62099fa98638e404f`) discovered via `git fsck`. This commit is from July 16, 2026, with the message "Phase 0: untrack ignored files and update security settings".

## Proposed Changes

### PHP Backend Recovery

#### [MODIFY] [server/php_variant/](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/php_variant/)
- Restore all `.php`, `.sql`, `.json`, and configuration files that existed in `1f2fdfe`.
- Preserve the current `core/secrets.php` if it contains the correct `AUTH_PEPPER` as found in the current `.env`.

## Verification Plan

### Automated Tests
- Compare the list of files in `server/php_variant/` after recovery against the file list in commit `1f2fdfe`.
- Verify that `core/secrets.php` contains the same `AUTH_PEPPER` as `server/.env`.

### Manual Verification
- Check `git status` to ensure no files outside `server/php_variant/` were modified.
- List all recovered files for the user.
