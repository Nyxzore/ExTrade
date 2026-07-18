<?php
require_once 'DbTestCase.php';

class FriendsBackendTest extends DbTestCase {

    public function testFriendRequestFlow() {
        $u1 = $this->createUser('alice');
        $u2 = $this->createUser('bob');
        $t1 = $this->createSession($u1);
        $t2 = $this->createSession($u2);

        // 1. Search for bob
        $res = $this->request('friends/search_users.php', ['user_id' => $u1, 'auth_token' => $t1, 'query' => 'bob']);
        $this->assertEquals('success', $res['status']);
        $this->assertCount(1, $res['users']);
        $this->assertEquals('bob', $res['users'][0]['username']);

        // 2. Send friend request
        $res = $this->request('friends/send_friend_request.php', ['user_id' => $u1, 'auth_token' => $t1, 'target_user_id' => $u2]);
        $this->assertEquals('success', $res['status']);

        // 3. Bob checks requests
        $res = $this->request('friends/get_friend_requests.php', ['user_id' => $u2, 'auth_token' => $t2]);
        $this->assertCount(1, $res['requests']);
        $this->assertEquals('alice', $res['requests'][0]['username']);

        // 4. Bob accepts
        $res = $this->request('friends/accept_friend_request.php', ['user_id' => $u2, 'auth_token' => $t2, 'requester_id' => $u1]);
        $this->assertEquals('success', $res['status']);

        // 5. Both check friends list
        $res = $this->request('friends/get_friends.php', ['user_id' => $u1, 'auth_token' => $t1]);
        $this->assertCount(1, $res['friends']);
        $this->assertEquals('bob', $res['friends'][0]['username']);

        $res = $this->request('friends/get_friends.php', ['user_id' => $u2, 'auth_token' => $t2]);
        $this->assertCount(1, $res['friends']);
        $this->assertEquals('alice', $res['friends'][0]['username']);

        // 6. Alice removes Bob
        $res = $this->request('friends/remove_friend.php', ['user_id' => $u1, 'auth_token' => $t1, 'friend_id' => $u2]);
        $this->assertEquals('success', $res['status']);

        // 7. Verify no friends
        $res = $this->request('friends/get_friends.php', ['user_id' => $u1, 'auth_token' => $t1]);
        $this->assertEmpty($res['friends']);
    }

    public function testDeclineRequest() {
        $u1 = $this->createUser('requester');
        $u2 = $this->createUser('target');
        $t1 = $this->createSession($u1);
        $t2 = $this->createSession($u2);

        $this->request('friends/send_friend_request.php', ['user_id' => $u1, 'auth_token' => $t1, 'target_user_id' => $u2]);

        // Decline
        $res = $this->request('friends/decline_friend_request.php', ['user_id' => $u2, 'auth_token' => $t2, 'requester_id' => $u1]);
        $this->assertEquals('success', $res['status']);

        // Verify no friends and no requests
        $res = $this->request('friends/get_friend_requests.php', ['user_id' => $u2, 'auth_token' => $t2]);
        $this->assertEmpty($res['requests']);

        $res = $this->request('friends/get_friends.php', ['user_id' => $u1, 'auth_token' => $t1]);
        $this->assertEmpty($res['friends']);
    }
}
