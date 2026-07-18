<?php
require_once 'DbTestCase.php';

class ProfileBackendTest extends DbTestCase {

    public function testViewOtherProfile() {
        $viewerId = $this->createUser('viewer', 'p', 'v@test.com');
        $targetId = $this->createUser('target', 'p', 't@test.com');
        $token = $this->createSession($viewerId);

        // Regression: Assert we can view another profile by providing target_user_id
        // while authenticating as the viewer.
        $params = [
            'user_id' => $viewerId,
            'auth_token' => $token,
            'target_user_id' => $targetId
        ];

        $response = $this->request('profile/get_profile.php', $params);
        $this->assertEquals('success', $response['status']);
        $this->assertEquals('target', $response['username']);
        $this->assertArrayHasKey('public_key', $response);
    }

    public function testEditProfileUniqueness() {
        $user1Id = $this->createUser('user1', 'p', 'u1@test.com');
        $user2Id = $this->createUser('user2', 'p', 'u2@test.com');
        $token1 = $this->createSession($user1Id);

        // Try updating user1 to have user2's username
        $params = [
            'user_id' => $user1Id,
            'auth_token' => $token1,
            'username' => 'user2',
            'email' => 'u1_new@test.com'
        ];
        $response = $this->request('profile/update_profile.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertStringContainsString('taken', $response['message']);

        // Try updating user1 with their own current username (should succeed)
        $params['username'] = 'user1';
        $response = $this->request('profile/update_profile.php', $params);
        $this->assertEquals('success', $response['status']);
    }

    public function testSocialLinksNormalization() {
        $userId = $this->createUser('socialuser', 'p', 'social@test.com');
        $token = $this->createSession($userId);

        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'username' => 'socialuser',
            'email' => 'social@test.com',
            'whatsapp' => '082 123 4567',
            'facebook' => 'https://facebook.com/my.page',
            'instagram' => '@my_handle',
        ];

        $response = $this->request('profile/update_profile.php', $params);
        $this->assertEquals('success', $response['status']);

        $profile = $this->request('profile/get_profile.php', [
            'user_id' => $userId,
            'auth_token' => $token,
        ]);

        $this->assertEquals('27821234567', $profile['whatsapp']);
        $this->assertEquals('my.page', $profile['facebook']);
        $this->assertEquals('my_handle', $profile['instagram']);
    }

    public function testSocialLinksCanBeCleared() {
        $userId = $this->createUser('clearsocial', 'p', 'clear@test.com');
        $token = $this->createSession($userId);

        $params = [
            'user_id' => $userId,
            'auth_token' => $token,
            'username' => 'clearsocial',
            'email' => 'clear@test.com',
            'whatsapp' => '0821234567',
            'facebook' => 'seller',
            'instagram' => 'seller',
        ];
        $this->request('profile/update_profile.php', $params);

        $params['whatsapp'] = '';
        $params['facebook'] = '';
        $params['instagram'] = '';
        $response = $this->request('profile/update_profile.php', $params);
        $this->assertEquals('success', $response['status']);

        $profile = $this->request('profile/get_profile.php', [
            'user_id' => $userId,
            'auth_token' => $token,
        ]);

        $this->assertSame('', $profile['whatsapp']);
        $this->assertSame('', $profile['facebook']);
        $this->assertSame('', $profile['instagram']);
    }
}
