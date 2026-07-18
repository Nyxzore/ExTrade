<?php
use PHPUnit\Framework\TestCase;

class BreedingBackendTest extends TestCase {
    private $db;
    private $user1;
    private $user2;
    private $token1;
    private $token2;
    private $species1 = 'lsid_1';

    protected function setUp(): void {
        $this->db = get_db_connection();
        pg_query($this->db, "DELETE FROM messages");
        pg_query($this->db, "DELETE FROM conversations");
        pg_query($this->db, "DELETE FROM breeding_listings");
        pg_query($this->db, "DELETE FROM listings");
        pg_query($this->db, "DELETE FROM user_sessions");
        pg_query($this->db, "DELETE FROM users");
        pg_query($this->db, "DELETE FROM taxa");

        // Setup users
        $res = pg_query($this->db, "INSERT INTO users (username, email, password_hash) VALUES ('u1', 'e1', 'h1'), ('u2', 'e2', 'h2') RETURNING id");
        $this->user1 = pg_fetch_result($res, 0, 0);
        $this->user2 = pg_fetch_result($res, 1, 0);

        $this->token1 = bin2hex(random_bytes(16));
        $this->token2 = bin2hex(random_bytes(16));
        pg_query_params($this->db, "INSERT INTO user_sessions (token, user_id) VALUES ($1, $2), ($3, $4)", [$this->token1, $this->user1, $this->token2, $this->user2]);

        // Setup taxa
        pg_query_params($this->db, "INSERT INTO taxa (species_lsid, genus, species, common_name) VALUES ($1, 'Genus1', 'Species1', 'Common1')", [$this->species1]);
    }

    private function post($url, $data) {
        $_POST = $data;
        $_GET = [];
        ob_start();
        try {
            require dirname(__DIR__) . '/' . $url;
        } catch (Exception $e) {
            if (strpos($e->getMessage(), "SEND_RESPONSE: ") === 0) {
                 // Already printed by echo in send_response
            } else {
                 error_log("CAUGHT EXCEPTION: " . $e->getMessage());
            }
        }
        $out = ob_get_clean();
        $json = json_decode($out, true);
        if ($json === null) {
             error_log("DEBUG OUTPUT: " . $out);
        }
        return $json;
    }

    public function testCreateBreedingListing() {
        // Success loan
        $res = $this->post('breeding/create_breeding_listing.php', [
            'user_id' => $this->user1,
            'auth_token' => $this->token1,
            'species_lsid' => $this->species1,
            'sex' => 'Male',
            'breeding_type' => 'loan',
            'loan_fee' => '150.50'
        ]);
        $this->assertEquals('success', $res['status']);
        $this->assertArrayHasKey('id', $res);

        // Success seeking (fee should be ignored/null)
        $res = $this->post('breeding/create_breeding_listing.php', [
            'user_id' => $this->user1,
            'auth_token' => $this->token1,
            'species_lsid' => $this->species1,
            'sex' => 'Female',
            'breeding_type' => 'seeking',
            'loan_fee' => '500' // Should be ignored
        ]);
        $id = $res['id'];
        $check = pg_query_params($this->db, "SELECT loan_fee FROM breeding_listings WHERE id = $1", [$id]);
        $this->assertNull(pg_fetch_result($check, 0, 0));

        // Failure: invalid sex
        $res = $this->post('breeding/create_breeding_listing.php', [
            'user_id' => $this->user1,
            'auth_token' => $this->token1,
            'species_lsid' => $this->species1,
            'sex' => 'Unsexed',
            'breeding_type' => 'loan'
        ]);
        $this->assertEquals('error', $res['status']);
    }

    public function testGetBreedingListings() {
        // Create 2 listings
        $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user1, 'auth_token' => $this->token1, 'species_lsid' => $this->species1, 'sex' => 'Male', 'breeding_type' => 'loan', 'loan_fee' => '100']);
        $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user2, 'auth_token' => $this->token2, 'species_lsid' => $this->species1, 'sex' => 'Female', 'breeding_type' => 'seeking']);

        // Fetch all
        $res = $this->post('breeding/get_breeding_listings.php', ['user_id' => $this->user1, 'auth_token' => $this->token1]);
        $this->assertCount(2, $res['listings']);

        // Search
        $res = $this->post('breeding/get_breeding_listings.php', ['user_id' => $this->user1, 'auth_token' => $this->token1, 'search' => 'Common1']);
        $this->assertCount(2, $res['listings']);

        // Filter type
        $res = $this->post('breeding/get_breeding_listings.php', ['user_id' => $this->user1, 'auth_token' => $this->token1, 'breeding_type' => 'loan']);
        $this->assertCount(1, $res['listings']);
        $this->assertEquals('loan', $res['listings'][0]['breeding_type']);
    }

    public function testFindMatches() {
        // User 1 Male
        $res = $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user1, 'auth_token' => $this->token1, 'species_lsid' => $this->species1, 'sex' => 'Male', 'breeding_type' => 'loan']);
        $listing_id = $res['id'];

        // User 2 Female (Match)
        $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user2, 'auth_token' => $this->token2, 'species_lsid' => $this->species1, 'sex' => 'Female', 'breeding_type' => 'seeking']);
        // User 2 Male (No match - same sex)
        $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user2, 'auth_token' => $this->token2, 'species_lsid' => $this->species1, 'sex' => 'Male', 'breeding_type' => 'loan']);
        // User 1 Female (No match - own listing)
        $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user1, 'auth_token' => $this->token1, 'species_lsid' => $this->species1, 'sex' => 'Female', 'breeding_type' => 'seeking']);

        $res = $this->post('breeding/find_breeding_matches.php', ['user_id' => $this->user1, 'auth_token' => $this->token1, 'listing_id' => $listing_id]);
        $this->assertCount(1, $res['listings']);
        $this->assertEquals($this->user2, $res['listings'][0]['seller_id']);
    }

    public function testMyBreedingStatus() {
        $res = $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user1, 'auth_token' => $this->token1, 'species_lsid' => $this->species1, 'sex' => 'Male', 'breeding_type' => 'loan']);

        // Match
        $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user2, 'auth_token' => $this->token2, 'species_lsid' => $this->species1, 'sex' => 'Female', 'breeding_type' => 'seeking']);

        $res = $this->post('breeding/get_my_breeding_status.php', ['user_id' => $this->user1, 'auth_token' => $this->token1]);
        $this->assertEquals(1, $res['listings'][0]['match_count']);
    }

    public function testBreedingConversation() {
        $res = $this->post('breeding/create_breeding_listing.php', ['user_id' => $this->user2, 'auth_token' => $this->token2, 'species_lsid' => $this->species1, 'sex' => 'Female', 'breeding_type' => 'seeking']);
        $listing_id = $res['id'];

        // Start conversation
        $res = $this->post('messaging/start_or_get_conversation.php', [
            'user_id' => $this->user1,
            'auth_token' => $this->token1,
            'listing_id' => $listing_id,
            'seller_id' => $this->user2,
            'listing_kind' => 'breeding'
        ]);
        $this->assertEquals('success', $res['status']);
        $conv_id = $res['conversation_id'];

        // Get conversations
        $res = $this->post('messaging/get_conversations.php', ['user_id' => $this->user1, 'auth_token' => $this->token1]);
        $this->assertCount(1, $res['conversations']);
        $this->assertEquals($conv_id, $res['conversations'][0]['conversation_id']);
    }

    public function testBreedingImpressions() {
        // Create listing
        $res = $this->post('breeding/create_breeding_listing.php', [
            'user_id' => $this->user2,
            'auth_token' => $this->token2,
            'species_lsid' => $this->species1,
            'sex' => 'Female',
            'breeding_type' => 'seeking'
        ]);
        $listingId = $res['id'];

        // Initial fetch
        $res = $this->post('breeding/get_breeding_listings.php', [
            'user_id' => $this->user1,
            'auth_token' => $this->token1
        ]);
        $this->assertEquals('success', $res['status']);
        $this->assertNotEmpty($res['listings']);

        // Verify impression was logged
        $res_db = pg_query_params($this->db, "SELECT 1 FROM breeding_impressions WHERE listing_id = $1 AND user_id = $2", [$listingId, $this->user1]);
        $this->assertEquals(1, pg_num_rows($res_db), "Breeding impression should be recorded");
    }
}
?>
