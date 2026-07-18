# Task: Finalize Go Migration and Remove PHP

## Go Server
- [x] Remove `.php` from routes in `server/main.go`
- [x] Update `r.StaticFile("/get-app.php", ...)` to `r.StaticFile("/get-app", ...)`
- [x] Verify Go server builds

## Android Client
- [x] Search and replace `.php` in `app/src/main/java/com/example/exotrade/`
- [x] Exception: preserve `profile.php` for external social links (Facebook)
- [x] Verify Android project builds

## Cleanup
- [x] Delete `server/php_variant/` directory
- [x] Update `server/Obsidian/ExTrade/Backend.md` documentation
- [x] Final verification and walkthrough
