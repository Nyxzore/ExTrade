<?php
require_once 'DbTestCase.php';

class MessagingBackendTest extends DbTestCase {

    public function testStartConversationIdempotency() {
        $buyerId = $this->createUser('buyer');
        $sellerId = $this->createUser('seller');
        $token = $this->createSession($buyerId);

        $listingId = $this->createListing($sellerId);

        $params = [
            'user_id' => $buyerId,
            'auth_token' => $token,
            'listing_id' => $listingId,
            'seller_id' => $sellerId
        ];

        $response1 = $this->request('messaging/start_or_get_conversation.php', $params);
        $this->assertEquals('success', $response1['status']);
        $id1 = $response1['conversation_id'];

        $response2 = $this->request('messaging/start_or_get_conversation.php', $params);
        $this->assertEquals('success', $response2['status']);
        $id2 = $response2['conversation_id'];

        $this->assertEquals($id1, $id2, "Conversation creation should be idempotent");
    }

    public function testStartConversationWithSelf() {
        $userId = $this->createUser();
        $token = $this->createSession($userId);

        $listingId = $this->createListing($userId);

        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'listing_id' => $listingId,
            'seller_id' => $userId
        ];

        $response = $this->request('messaging/start_or_get_conversation.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertStringContainsString('yourself', $response['message']);
    }

    public function testConversationAccessControl() {
        $buyerId = $this->createUser('buyer2');
        $sellerId = $this->createUser('seller2');
        $intruderId = $this->createUser('intruder');

        $convId = $this->createConversation($buyerId, $sellerId);

        $authParams = [
            'user_id' => $intruderId,
            'auth_token' => $this->createSession($intruderId)
        ];
        $queryParams = [
            'conversation_id' => $convId
        ];

        $response = $this->request('messaging/get_messages.php', $authParams, $queryParams);
        $this->assertEquals('error', $response['status']);
        $this->assertStringContainsString('Access denied', $response['message'] ?: $response['status']);
    }

    public function testMarkReadOnlyOthers() {
        $u1 = $this->createUser('u1');
        $u2 = $this->createUser('u2');
        $convId = $this->createConversation($u1, $u2);

        // u1 sends a message
        pg_query_params($this->db, "INSERT INTO messages (conversation_id, sender_id, body) VALUES ($1, $2, $3)", [$convId, $u1, 'msg1']);

        // u1 tries to mark read. Regression: mark_read should NOT mark u1's own message as read by u1?
        // Actually mark_read is usually called by the recipient.
        // mark_read.php should mark messages where sender_id != current_user_id.

        $params = [
            'user_id' => $u1,
            'auth_token' => $this->createSession($u1),
            'conversation_id' => $convId
        ];
        $this->request('messaging/mark_read.php', $params);

        $res = pg_query($this->db, "SELECT read_at FROM messages WHERE conversation_id = $convId");
        $this->assertNull(pg_fetch_assoc($res)['read_at'], "User should not mark their own message as read");
    }

    public function testMessagePaginationAndOrder() {
        $u1 = $this->createUser('u1_pag');
        $u2 = $this->createUser('u2_pag');
        $convId = $this->createConversation($u1, $u2);

        // Insert 5 messages with different timestamps
        for ($i = 1; $i <= 5; $i++) {
            pg_query_params($this->db,
                "INSERT INTO messages (conversation_id, sender_id, body, sent_at) VALUES ($1, $2, $3, NOW() + INTERVAL '$i seconds')",
                [$convId, $u1, "msg$i"]
            );
        }

        $authParams = [
            'user_id' => $u1,
            'auth_token' => $this->createSession($u1)
        ];
        $queryParams = [
            'conversation_id' => $convId,
            'limit' => 2
        ];

        // First page (latest 2)
        $response1 = $this->request('messaging/get_messages.php', $authParams, $queryParams); // GET
        $this->assertCount(2, $response1['messages']);
        // Re-reverse happens in get_messages.php, so msg4, msg5
        $this->assertEquals('msg4', $response1['messages'][0]['body']);
        $this->assertEquals('msg5', $response1['messages'][1]['body']);

        $lastMsg = $response1['messages'][0]; // Keysets for pagination should be from the EARLIEST in the reversed list

        // Second page
        $queryParams['last_id'] = $lastMsg['id'];
        $queryParams['last_sent_at'] = $lastMsg['sent_at'];
        $response2 = $this->request('messaging/get_messages.php', $authParams, $queryParams);

        $this->assertCount(2, $response2['messages']);
        $this->assertEquals('msg2', $response2['messages'][0]['body']);
        $this->assertEquals('msg3', $response2['messages'][1]['body']);
    }


    public function testTotalUnreadCount() {
        $u1 = $this->createUser('u1_unread');
        $u2 = $this->createUser('u2_unread');
        $token1 = $this->createSession($u1);

        $convId = $this->createConversation($u1, $u2);

        // u2 sends 3 messages to u1
        pg_query_params($this->db, "INSERT INTO messages (conversation_id, sender_id, body) VALUES ($1, $2, 'm1')", [$convId, $u2]);
        pg_query_params($this->db, "INSERT INTO messages (conversation_id, sender_id, body) VALUES ($1, $2, 'm2')", [$convId, $u2]);
        pg_query_params($this->db, "INSERT INTO messages (conversation_id, sender_id, body) VALUES ($1, $2, 'm3')", [$convId, $u2]);

        // u1 gets conversations
        $response = $this->request('messaging/get_conversations.php', ['user_id' => $u1, 'auth_token' => $token1]);
        $this->assertEquals(3, $response['total_unread']);

        // u1 sends a message back (should not increase unread for u1)
        pg_query_params($this->db, "INSERT INTO messages (conversation_id, sender_id, body) VALUES ($1, $2, 'reply')", [$convId, $u1]);
        $response = $this->request('messaging/get_conversations.php', ['user_id' => $u1, 'auth_token' => $token1]);
        $this->assertEquals(3, $response['total_unread']);

        // u1 marks read
        $this->request('messaging/mark_read.php', ['user_id' => $u1, 'auth_token' => $token1, 'conversation_id' => $convId]);
        $response = $this->request('messaging/get_conversations.php', ['user_id' => $u1, 'auth_token' => $token1]);
        $this->assertEquals(0, $response['total_unread']);
    }

    public function testConversationsArePairsNotListings() {
        $buyerId = $this->createUser('buyer_pair');
        $sellerId = $this->createUser('seller_pair');
        $token = $this->createSession($buyerId);

        $listing1 = $this->createListing($sellerId, 'species1');
        $listing2 = $this->createListing($sellerId, 'species2');

        // Start chat for listing 1
        $res1 = $this->request('messaging/start_or_get_conversation.php', [
            'user_id' => $buyerId,
            'auth_token' => $token,
            'listing_id' => $listing1,
            'seller_id' => $sellerId
        ]);
        $id1 = $res1['conversation_id'];

        // Start chat for listing 2
        $res2 = $this->request('messaging/start_or_get_conversation.php', [
            'user_id' => $buyerId,
            'auth_token' => $token,
            'listing_id' => $listing2,
            'seller_id' => $sellerId
        ]);
        $id2 = $res2['conversation_id'];

        $this->assertEquals($id1, $id2, "Different listings between same users should reuse the same conversation");
    }

    public function testConversationSymmetry() {
        $userA = $this->createUser('user_a_sym');
        $userB = $this->createUser('user_b_sym');

        $listingB = $this->createListing($userB);
        $listingA = $this->createListing($userA);

        // A initiates chat with B
        $res1 = $this->request('messaging/start_or_get_conversation.php', [
            'user_id' => $userA,
            'auth_token' => $this->createSession($userA),
            'listing_id' => $listingB,
            'seller_id' => $userB
        ]);
        $id1 = $res1['conversation_id'];

        // B initiates chat with A
        $res2 = $this->request('messaging/start_or_get_conversation.php', [
            'user_id' => $userB,
            'auth_token' => $this->createSession($userB),
            'listing_id' => $listingA,
            'seller_id' => $userA
        ]);
        $id2 = $res2['conversation_id'];

        $this->assertEquals($id1, $id2, "Conversation should be the same regardless of who initiates");
    }

    public function testUniqueConstraintEnforcement() {
        $u1 = $this->createUser('u1_unique');
        $u2 = $this->createUser('u2_unique');

        // Ensure u1 < u2 for the "correct" order test
        if ($u1 > $u2) { $tmp = $u1; $u1 = $u2; $u2 = $tmp; }

        // 1. Insert correct order
        pg_query_params($this->db, "INSERT INTO conversations (user_a_id, user_b_id) VALUES ($1, $2)", [$u1, $u2]);

        // 2. Try to insert same pair (should fail unique constraint)
        $res = @pg_query_params($this->db, "INSERT INTO conversations (user_a_id, user_b_id) VALUES ($1, $2)", [$u1, $u2]);
        $this->assertFalse($res, "Direct duplicate insert should fail unique constraint");

        // 3. Try to insert wrong order (should fail ordered_pair check constraint)
        $res = @pg_query_params($this->db, "INSERT INTO conversations (user_a_id, user_b_id) VALUES ($1, $2)", [$u2, $u1]);
        $this->assertFalse($res, "Wrong order insert should fail check constraint");
    }
}
