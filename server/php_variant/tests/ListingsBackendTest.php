<?php
require_once 'DbTestCase.php';

class ListingsBackendTest extends DbTestCase {

    public function testCreateListingValidation() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        // Test missing fields
        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'species_lsid' => '123'
            // price missing
        ];
        $response = $this->request('listings/create_listing.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertEquals('Required fields missing', $response['message']);

        // Test non-numeric price
        $params['price'] = 'abc';
        $response = $this->request('listings/create_listing.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertEquals('Price must be numeric', $response['message']);
    }

    public function testUpdateListingValidationParity() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        // Create a listing first
        $listingId = $this->createListing($userId);

        // Test non-numeric price in update (Regression: previously skipped validation)
        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'listing_id' => $listingId,
            'price' => 'invalid'
        ];
        $response = $this->request('listings/update_listing.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertEquals('Price must be numeric', $response['message']);
    }

    public function testCreateListing_usesReturningId_notLastval() {
        $userId1 = $this->createUser('user1', 'p1', 'e1@test.com');
        $userId2 = $this->createUser('user2', 'p2', 'e2@test.com');
        $token1 = $this->createSession($userId1);
        $token2 = $this->createSession($userId2);

        // We can't easily do "concurrent" in a single PHP process, but we can verify
        // that if we do two inserts, each gets its own ID.
        // The real danger of LASTVAL() is that it returns the last inserted ID for the connection.
        // If we use the same connection (like in this test), and the code uses LASTVAL(),
        // then if we had concurrent requests on different connections, they might get mixed up
        // IF the backend shared connections (unlikely in PHP-FPM but possible in some setups)
        // OR if the code is just buggy.

        // More importantly, RETURNING id is per-query.

        $params1 = [
            'user_id' => $userId1,
            'auth_token' => $token1,
            'species_lsid' => 's1',
            'price' => '100'
        ];
        $response1 = $this->request('listings/create_listing.php', $params1);
        $id1 = $response1['listing_id'];

        $params2 = [
            'user_id' => $userId2,
            'auth_token' => $token2,
            'species_lsid' => 's2',
            'price' => '200'
        ];
        $response2 = $this->request('listings/create_listing.php', $params2);
        $id2 = $response2['listing_id'];

        $this->assertNotEquals($id1, $id2);

        // Verify they are actually in the DB
        $res = pg_query_params($this->db, "SELECT seller_id FROM listings WHERE id = $1", [$id1]);
        $this->assertEquals($userId1, pg_fetch_assoc($res)['seller_id']);

        $res = pg_query_params($this->db, "SELECT seller_id FROM listings WHERE id = $1", [$id2]);
        $this->assertEquals($userId2, pg_fetch_assoc($res)['seller_id']);
    }

    public function testEditListingOwnership() {
        $ownerId = $this->createUser('owner');
        $otherId = $this->createUser('other');
        $ownerToken = $this->createSession($ownerId);
        $otherToken = $this->createSession($otherId);

        $listingId = $this->createListing($ownerId);

        // Try editing as other user
        $params = [
            'user_id' => $otherId,
            'auth_token' => $otherToken,
            'listing_id' => $listingId,
            'price' => '500'
        ];
        $response = $this->request('listings/update_listing.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertStringContainsString('Unauthorized', $response['message']);
    }

    public function testDeleteListingOwnership() {
        $ownerId = $this->createUser('owner2');
        $otherId = $this->createUser('other2');
        $ownerToken = $this->createSession($ownerId);
        $otherToken = $this->createSession($otherId);

        $listingId = $this->createListing($ownerId);

        // Try deleting as other user
        $params = [
            'user_id' => $otherId,
            'auth_token' => $otherToken,
            'listing_id' => $listingId
        ];
        $response = $this->request('listings/delete_listing.php', $params);
        $this->assertEquals('error', $response['status']);

        // Verify listing still exists
        $res = pg_query_params($this->db, "SELECT 1 FROM listings WHERE id = $1", [$listingId]);
        $this->assertEquals(1, pg_num_rows($res));
    }

    public function testDeleteListingPreservesConversations() {
        $sellerId = $this->createUser('seller');
        $buyerId = $this->createUser('buyer');
        $listingId = $this->createListing($sellerId);

        // Start conversation
        $convId = $this->createConversation($buyerId, $sellerId);

        // Delete listing
        $params = [
            'user_id' => $sellerId,
            'auth_token' => $this->createSession($sellerId),
            'listing_id' => $listingId
        ];
        $this->request('listings/delete_listing.php', $params);

        // Verify conversation still exists (listing columns are gone, so no more SET NULL check needed)
        $res = pg_query_params($this->db, "SELECT 1 FROM conversations WHERE id = $1", [$convId]);
        $this->assertEquals(1, pg_num_rows($res));
    }

    public function testSexFieldRoundTrip() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        pg_query($this->db, "INSERT INTO taxa (species_lsid, genus, species) VALUES ('s_trip', 'Genus', 'Species') ON CONFLICT DO NOTHING");

        foreach (['Male', 'Female', 'Unsexed'] as $sex) {
            $params = [
                'user_id' => $userId,
                'auth_token' => $token,
                'species_lsid' => 's_trip',
                'price' => '100',
                'sex' => $sex
            ];
            $response = $this->request('listings/create_listing.php', $params);
            $listingId = $response['listing_id'];

            $getParams = [
                'user_id' => $userId,
                'auth_token' => $token,
                'id' => $listingId
            ];
            $details = $this->request('listings/get_listing_details.php', [], $getParams);
            $this->assertEquals($sex, $details['sex'], "Sex mismatch for $sex");
        }
    }

    public function testBrowseListingsImpressions() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        $listingId = $this->createListing($userId, 's1');

        // Browse
        $params = [
            'user_id' => $userId,
            'auth_token' => $token
        ];
        $response = $this->request('listings/get_all_listings.php', $params);
        $this->assertEquals('success', $response['status']);
        $this->assertNotEmpty($response['listings']);

        // Regression: Verify impressions table actually gets rows (was broken by SQL typo)
        $res = pg_query_params($this->db, "SELECT 1 FROM listing_impressions WHERE listing_id = $1 AND user_id = $2", [$listingId, $userId]);
        $this->assertEquals(1, pg_num_rows($res), "Impression should be recorded");
    }


    public function testMarkAsSold() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);
        $listingId = $this->createListing($userId);

        // Mark as sold
        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'listing_id' => $listingId,
            'status' => 'sold'
        ];
        $response = $this->request('listings/update_listing.php', $params);
        $this->assertEquals('success', $response['status']);

        // Verify in DB
        $res = pg_query_params($this->db, "SELECT status FROM listings WHERE id = $1", [$listingId]);
        $this->assertEquals('sold', pg_fetch_assoc($res)['status']);

        // Verify it is hidden from feed
        $response = $this->request('listings/get_all_listings.php', ['user_id' => $userId, 'auth_token' => $token]);
        foreach ($response['listings'] as $l) {
            $this->assertNotEquals($listingId, $l['id'], "Sold listing should not appear in public feed");
        }
    }

    public function testSearchListings() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        pg_query($this->db, "INSERT INTO taxa (species_lsid, genus, species, common_name) VALUES ('lsid_search', 'SearchGenus', 'SearchSpecies', 'SearchCommon') ON CONFLICT DO NOTHING");

        $res = pg_query_params($this->db,
            "INSERT INTO listings (seller_id, species_lsid, price, sex) VALUES ($1, $2, $3, $4) RETURNING id",
            [$userId, 'lsid_search', 100, 'Unsexed']
        );
        $listingId = pg_fetch_assoc($res)['id'];

        // Search for it
        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'search' => 'SearchCommon'
        ];
        $response = $this->request('listings/get_all_listings.php', $params);
        $this->assertCount(1, $response['listings']);
        $this->assertEquals($listingId, $response['listings'][0]['id']);

        // Search for something else
        $params['search'] = 'NonExistent';
        $response = $this->request('listings/get_all_listings.php', $params);
        $this->assertEmpty($response['listings']);
    }

    public function testSearchSimilarityDominance() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        // Create two taxa: one very similar to "Ball Python", one slightly similar but "hotter"
        pg_query($this->db, "INSERT INTO taxa (species_lsid, genus, species, common_name) VALUES
            ('lsid_v_sim', 'Python', 'regius', 'Ball Python'),
            ('lsid_s_sim', 'Python', 'molurus', 'Burmese Python')
        ON CONFLICT DO NOTHING");

        // The Burmese Python seller is Tier 2 (high hotness)
        $tier2UserId = $this->createUser('tier2_seller');
        pg_query($this->db, "UPDATE users SET subscription_tier = 2 WHERE id = '$tier2UserId'");

        $id1 = $this->createListing($userId, 'lsid_v_sim'); // Ball Python (Tier 0)
        $id2 = $this->createListing($tier2UserId, 'lsid_s_sim'); // Burmese Python (Tier 2 - Very Hot)

        // Search for "Ball"
        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'search' => 'Ball'
        ];
        $response = $this->request('listings/get_all_listings.php', $params);

        // Even though Burmese is Tier 2, Ball Python should be first because of cubing similarity
        $this->assertGreaterThan(0, count($response['listings']));
        $this->assertEquals($id1, $response['listings'][0]['id'], "Ball Python should dominate Burmese even if Burmese is Tier 2");
    }

    public function testImpressionPenalty() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        $id1 = $this->createListing($userId, 's1');
        $id2 = $this->createListing($userId, 's2');

        // Initial fetch: both should be available
        $params = ['user_id' => $userId, 'auth_token' => $token];
        $response = $this->request('listings/get_all_listings.php', $params);
        $this->assertCount(2, $response['listings']);

        // Log many impressions for $id1
        for ($i = 0; $i < 10; $i++) {
            pg_query($this->db, "INSERT INTO listing_impressions (listing_id, user_id, shown_at) VALUES ($id1, '$userId', NOW())");
        }

        // Fetch again. Due to randomness it's not guaranteed, but with 10 impressions
        // the probability of $id1 being top should be significantly lower than $id2.
        // We'll just verify the exposure score is lower.

        $response = $this->request('listings/get_all_listings.php', $params);
        $scores = [];
        foreach ($response['listings'] as $l) {
            $scores[$l['id']] = $l['exposure_score'];
        }

        $this->assertLessThan($scores[$id2], $scores[$id1], "Listing with many impressions should have lower exposure score");
    }

    public function testPaginationOffset() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        // Create 15 listings
        for ($i = 0; $i < 15; $i++) {
            $this->createListing($userId, "s$i");
        }

        // Fetch first page with a fixed seed
        $params = ['user_id' => $userId, 'auth_token' => $token, 'offset' => 0, 'seed' => 'test_seed'];
        $response1 = $this->request('listings/get_all_listings.php', $params);
        $this->assertCount(10, $response1['listings']);

        // Fetch second page with the same seed
        // We clear impressions to ensure the scores don't shift between pages
        pg_query($this->db, "DELETE FROM listing_impressions");

        $params['offset'] = 10;
        $response2 = $this->request('listings/get_all_listings.php', $params);
        $this->assertCount(5, $response2['listings']);

        // Verify no overlap
        $page1Ids = array_column($response1['listings'], 'id');
        foreach ($response2['listings'] as $l) {
            $this->assertNotContains($l['id'], $page1Ids, "Overlap detected in stable seeded pagination");
        }
    }
}
