# Social Links

ExoTrade integrates with external social platforms to facilitate faster communication.

## Supported Platforms
- **WhatsApp**: Direct chat links using `wa.me`.
- **Facebook**: Links to profiles or pages.
- **Instagram**: Links to handles.

## Core Logic
- **SocialLinkUtils**: The primary utility class for:
	- Normalizing inputs (e.g., converting "082..." to "2782..." for WhatsApp).
	- Validating handles using Regex.
	- Binding icons to UI views.
	- Launching external intents.

## Integration Points
- **Feed Menu**: Long-pressing a listing in `BrowseListings` or `BreedingFeed` allows direct WhatsApp contact.
- **Listing Details**: Social icons appear in the "Social Links" section.
- `EditAccount`: Users can link/unlink their social accounts with validation.
- `ShareUtils`: Generated share images include the seller's social links.

## Backend Support
- The Go backend ensures social handles are correctly stored and retrieved, following the same normalization rules as the client to maintain data integrity.
