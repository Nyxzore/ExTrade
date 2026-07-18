# Clean up PHP references and Fix Profile 404

The goal is to remove all remaining `.php` strings from the project (except for external social links) and investigate the 404 error on the profile page.

## User Review Required

> [!IMPORTANT]
> I will be updating all server-side test files and documentation to remove `.php` suffixes from API routes. This ensures consistency with the actual Go implementation.

> [!WARNING]
> I will investigate the 404 on the profile page. If it's a "User not found" error, it might be due to a UUID mismatch between the app and server.

## Proposed Changes

### [Server Layer]

#### [MODIFY] [auth_test.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/auth_test.go)
- Remove `.php` from all test routes and request paths.

#### [MODIFY] [breeding_test.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/breeding_test.go)
- Remove `.php` from all test routes and request paths.

#### [MODIFY] [friends_test.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/friends_test.go)
- Remove `.php` from all test routes and request paths.

#### [MODIFY] [listings_test.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/listings_test.go)
- Remove `.php` from all test routes and request paths.

#### [MODIFY] [messaging_test.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/messaging_test.go)
- Remove `.php` from all test routes and request paths.

#### [MODIFY] [exotrade-api-docs.html](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/exotrade-api-docs.html)
- Update fetch calls to remove `.php`.

#### [MODIFY] [Obsidian Documentation](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/Obsidian/ExTrade/)
- Update all `.md` files to remove `.php` references in route descriptions.

### [Verification Plan]

#### Automated Tests
- Run `go test ./...` in the `server` directory to ensure all tests pass with the new routes.

#### Manual Verification
- Verify the profile page in the app. If the 404 persists, I will add logging to `GetProfile` to see the exact `targetUserID` being requested.
