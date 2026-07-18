# Breeding System

A specialized module for finding breeding partners for animals.

## Components
- `BreedingFeed`: Similar to `BrowseListings`, but filters for breeding types.
- `BreedingListingDetails`: Shows breeding-specific info like "Willing to Loan".
- `CreateBreedingListing`: Form tailored for breeding parameters (e.g., loan terms).

## Matching Engine
The system includes a matching feature (Legacy PHP: `breeding/find_breeding_matches.php` - **Deprecated**) that automatically finds animals of:
1. The same species (via `SpeciesRepository`).
2. The opposite sex.
3. Belonging to different owners.

## Listing Types
- **Seeking Partner**: Looking for someone to bring an animal to them.
- **Willing to Loan**: Offering an animal for a breeding project elsewhere.

## Discovery Logic
The breeding feed shares the same **Hotness Algorithm** as sale listings, ensuring tier-based promotion and impression-based variety. It uses a dedicated `breeding_impressions` table to track view history via `db.LogImpressions`.

## Test Cases
### Matching Engine
- **TC-BREEDING-01**: Verify `GetBreedingMatches` correctly identifies animals of the same species but opposite sex and different `seller_id`.
- **TC-BREEDING-02**: Ensure `loan_fee` is displayed as "Contact" when set to null in the database.

### Feed Logic
- **TC-BREEDING-03**: Verify that impressions logged in `breeding_impressions` correctly influence the decay score in the breeding feed independently of sale listings.
