<?php
require_once 'DbTestCase.php';

class SecurityBackendTest extends DbTestCase {

    public function testAuthRejection() {
        $endpoints = [
            'listings/create_listing.php',
            'listings/update_listing.php',
            'listings/delete_listing.php',
            'profile/get_profile.php',
            'profile/update_profile.php',
            'messaging/get_conversations.php',
            'messaging/get_messages.php',
            'messaging/send_message.php',
            'messaging/mark_read.php',
            'messaging/get_backup.php'
        ];

        foreach ($endpoints as $endpoint) {
            // No auth params
            $response = $this->request($endpoint, []);
            $this->assertEquals('error', $response['status'], "Endpoint $endpoint should reject unauthenticated request");
            $this->assertStringContainsString('Authentication', $response['message']);

            // Invalid auth params
            $response = $this->request($endpoint, ['user_id' => '00000000-0000-0000-0000-000000000000', 'auth_token' => 'invalid']);
            $this->assertEquals('error', $response['status'], "Endpoint $endpoint should reject invalid token");
            $this->assertStringContainsString('session', $response['message']);
        }
    }

    public function testSqlInjectionHandling() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        // Try to inject in description
        $injectionPayload = "'); DROP TABLE users; --";
        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'species_lsid' => 's1',
            'price' => '100',
            'description' => $injectionPayload
        ];

        $response = $this->request('listings/create_listing.php', $params);
        $this->assertEquals('success', $response['status']);
        $listingId = $response['listing_id'];

        // Verify it was stored literally
        $res = pg_query_params($this->db, "SELECT description FROM listings WHERE id = $1", [$listingId]);
        $stored = pg_fetch_assoc($res)['description'];
        $this->assertEquals($injectionPayload, $stored);

        // Verify table still exists
        $res = pg_query($this->db, "SELECT 1 FROM users LIMIT 1");
        $this->assertNotFalse($res);
    }
}
