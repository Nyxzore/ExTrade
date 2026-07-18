# Backend Architecture

> [!CAUTION]
> **PHP DEPRECATION NOTICE**: The original PHP backend implementation in `server/php_variant/` is now **deprecated**. All new features and security-critical updates are being built in the Go backend (`server/main.go`). The Android client is being progressively updated to point to the Go implementation.

The backend is built with Go (Golang) and PostgreSQL, serving as a high-performance RESTful API.

## Core Structure
- **Framework**: [Gin Gonic](https://gin-gonic.com/) for routing and middleware.
- **Database Driver**: [pgx](https://github.com/jackc/pgx) for high-performance PostgreSQL interaction with connection pooling.
- **Authentication**: UUID + Auth Token pairs (session rows in `user_sessions`), verified via middleware. There is no JWT anywhere in this system.

## Implemented Endpoints (Go)
The Go server (`server/main.go`) is the **authoritative API**. It handles high-traffic and security-critical routes, registering handlers at legacy `.php` paths to maintain compatibility with the Android client while providing superior performance and safety.

**Public:**
- `GET /get_graph_data.php` — `handlers.GetGraphData` (Obsidian visualization).
- `GET /get_note_content.php` — `handlers.GetNoteContent` (Obsidian note retrieval).
- `POST /auth/auth.php` — `handlers.AuthHandler` (Login & Registration).

**Protected** (require `user_id` + `auth_token`, pass `AppVersionCheck`):
- `POST /listings/get_all_listings.php` — `handlers.GetAllListings` (Hotness-sorted feed).
- `POST /breeding/get_breeding_listings.php` — `handlers.GetBreedingListings` (Specialized breeding feed).
- `GET /friends/get_friends.php` — `handlers.GetFriends` (Retrieve active friend list).
- `POST /messaging/send_message.php` — `handlers.SendMessage` (Send E2EE message).

## Legacy Endpoints (PHP Variant)
The `server/php_variant/` directory contains the original PHP implementation. This code is **deprecated** and is being progressively replaced by the Go backend. 

Currently, the PHP variant is only used for endpoints NOT yet ported to Go. These include:
- **Listings**: `create_listing.php`, `update_listing.php`, `delete_listing.php`, `get_listing_details.php`, `get_all_species.php`.
- **Breeding**: `create_breeding_listing.php`, `delete_breeding_listing.php`, `find_breeding_matches.php`, `get_breeding_listing_details.php`, `get_my_breeding_status.php`.
- **Messaging**: `get_conversations.php`, `get_messages.php`, `mark_read.php`, `start_or_get_conversation.php`, `get_backup.php`.
- **Friends**: `search_users.php`, `send_friend_request.php`, `accept_friend_request.php`, `decline_friend_request.php`, `remove_friend.php`.
- **Profile**: `get_profile.php`, `update_profile.php`.
- **Admin**: `ban_user.php`, `get_flagged_items.php`, `get_notifications.php`, `resolve_report.php`, `take_down_listing.php`.
- **Core**: `get_versions.php`, `report_item.php`.

The Android client still calls these `.php` endpoints. The Go server handles some of these requests by registering identical `.php` routes (see `server/main.go`).

## Key Packages
- `server/internal/db/db.go`: Database connection pooling (`InitDB`).
- `server/internal/db/impressions.go`: Batch logging for [[Listings System|Impression Tracking]].
- `server/internal/api/handlers/`: Contains the logic for all API endpoints (e.g., `listings.go`, `breeding.go`, `messaging.go`).

## Databases
### Key Tables
- `users`: User credentials, public keys, and social handles.
- `listings`: Sale items linked to `taxa`.
- `breeding_listings`: Breeding-specific items.
- `taxa`: Taxonomy data (Genus, Species, Common Names).
- `conversations` & `messages`: Messaging storage.
- `listing_impressions`: Tracks views for the hotness algorithm.

## Features
### Hotness Algorithm
Listings are sorted by a combination of:
1. **Subscription Tier**: Higher tiers get priority.
2. **Freshness**: Newer listings score higher.
3. **Impression Decay**: Listings shown too many times to a specific user are penalized to keep the feed fresh.
4. **Randomized Sampling**: Seeded randomization ensures every refresh feels fresh while maintaining pagination consistency.

## Security
- Password Hashing: Argon2id (client-side) and BCRYPT (server-side).
- E2EE Storage: The server only ever sees encrypted message blobs (`body` + `nonce`).

## Deep Linking
The system handles shared listing and breeding links (`exotrade.co.za/listing/{id}`, `exotrade.co.za/breeding/{id}`) via Android App Links (intent-filters + `assetlinks.json`). When the app is installed, Android intercepts the URL directly and opens `ListingDetails`/`BreedingListingDetails`, which parse `getIntent().getData()` as a fallback if not launched from in-app navigation.

**Not yet built**: a server-side landing page for users who don't have the app installed (currently the URL just 404s or shows nothing useful in that case). This is still open work.

## Test Cases
See [[Testing]] for run commands and implementation status. Key backend cases:

### API Integration
- **TC-BACKEND-01**: Verify `verify_auth` middleware correctly validates UUID + Token and rejects expired/invalid sessions.
- **TC-BACKEND-02**: Ensure `LogImpressions` correctly handles batch inserts and prevents primary key collisions on rapid concurrent views.
- **TC-BACKEND-03**: Verify `GetListingDetails` returns 404 for non-existent IDs and 403 for listings marked as `inactive` by moderation.

### Database Performance
- **TC-BACKEND-04**: Benchmark the hotness algorithm query with 10k+ listings and 100+ concurrent users to ensure sub-100ms response time.
- **TC-BACKEND-05**: Verify GIN Trigram index usage in search queries using `EXPLAIN ANALYZE`.

## Data Synchronization
The `sync_spiders()` script is used to synchronize spider taxonomy data from the World Spider Catalog (WSC) into the local PostgreSQL database. It specifically filters for valid species within the *Theraphosidae* (tarantula) family to populate the `taxa` table used by the listings system.

```bash
sync_spiders() {
    local DB="ExoTrade"
    local USER="sgroup2689"
    local TMP="/tmp/species.csv"

    echo "Downloading latest spider data..."

    URL=$(curl -fsSL https://wsc.nmbe.ch/dataresources \
        | grep -oP '/resources/species_export_[0-9]+\.csv' \
        | head -n1)

    if [ -z "$URL" ]; then
        echo "Could not locate latest WSC CSV."
        return 1
    fi

    if ! curl -fsSL "https://wsc.nmbe.ch$URL" -o "$TMP"; then
        echo "Download failed."
        return 1
    fi

    echo "Importing data..."

    if ! psql -v ON_ERROR_STOP=1 -U "$USER" -d "$DB" <<EOF
BEGIN;

TRUNCATE TABLE spiders_import;

\copy spiders_import FROM '$TMP' CSV HEADER;

TRUNCATE TABLE spiders RESTART IDENTITY;

INSERT INTO spiders (
    "speciesId",
    "species_legacy_id",
    "species_lsid",
    "family",
    "genus",
    "species",
    "subspecies",
    "author",
    "year",
    "parentheses",
    "distribution",
    "validSpeciesId",
    "taxonStatus"
)
SELECT DISTINCT ON ("species_lsid")
    "speciesId",
    "species_legacy_id",
    "species_lsid",
    "family",
    "genus",
    "species",
    "subspecies",
    "author",
    "year",
    "parentheses",
    "distribution",
    "validSpeciesId",
    "taxonStatus"
FROM spiders_import
WHERE "species_lsid" IS NOT NULL
ORDER BY "species_lsid", "speciesId";

TRUNCATE TABLE taxa;

INSERT INTO taxa (
    species_lsid,
    genus,
    species,
    subspecies,
    author,
    distribution,
    year,
    common_name
)
SELECT
    s."species_lsid",
    s."genus",
    s."species",
    s."subspecies",
    s."author",
    s."distribution",
    s."year",
    c.common_name
FROM spiders s
LEFT JOIN common_names c
ON s."species_lsid" = c.species_lsid
WHERE "family" = 'Theraphosidae'
AND "taxonStatus" = 'VALID';

COMMIT;
EOF
    then
        rm -f "$TMP"
        echo "Database update failed."
        return 1
    fi

    rm -f "$TMP"

    COUNT=$(psql -U "$USER" -d "$DB" -tAc 'SELECT COUNT(*) FROM spiders;')
    echo "✓ Imported $COUNT spider records."
    COUNT=$(psql -U "$USER" -d "$DB" -tAc 'SELECT COUNT(*) FROM taxa;')
    echo "✓ Imported $COUNT tarantula records."
}
```

