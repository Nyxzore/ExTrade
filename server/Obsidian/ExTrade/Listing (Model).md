# Listing Model

The `Listing` class is the central data object for items in the app, defined as a Kotlin `data class` in the `:shared` module. It is used for both marketplace sales and breeding listings.

## Fields
- `id`: Unique identifier (String).
- `commonName`: English/Common name of the species (nullable).
- `scientificName`: Genus + species + subspecies (nullable).
- `price`: Display price (formatted on the Go backend or legacy PHP).
- `description`: User-provided text (nullable).
- `imageUrl`: Absolute URL to the primary photo (nullable). `@SerialName("image_url")`
- `sellerId`: UUID of the owner (nullable). `@SerialName("seller_id")`
- `listingType`: Type of listing: `"sale"`, `"seeking_partner"`, or `"willing_to_loan"`. `@SerialName("listing_type")`
- `sex`: `"Male"`, `"Female"`, or `"Unsexed"`.
- `status`: `"active"`, `"sold"`, or `"expired"`.
- `subscriptionTier`: Seller's tier (0, 1, or 2). `@SerialName("subscription_tier")`
- **Social Links**: `whatsapp`, `facebook`, `instagram` (all nullable).
- `listedTime`: Timestamp of when the item was posted (ISO String, nullable). `@SerialName("listed_time")`
- `probability`: Discovery score used for backend randomization.

## Usage
- Defined in `commonMain` to ensure consistency between API responses and UI rendering.
- Annotated with `@Serializable` for Ktor/JSON integration.
- Used by `ListingAdapter` in the Android app to bind data to Material 3 cards.
