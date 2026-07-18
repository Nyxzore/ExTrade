<?php
require_once 'DbTestCase.php';

class AuthBackendTest extends DbTestCase {

    /**
     * @runInSeparateProcess
     * @preserveGlobalState disabled
     */
    public function testRegisterSuccess() {
        $params = [
            'mode' => 'register',
            'username' => 'newuser',
            'email' => 'new@example.com',
            'password' => 'password123',
            'public_key' => 'pubkey',
            'encrypted_private_key' => 'privkey'
        ];

        $response = $this->request('auth/auth.php', $params);
        $this->assertEquals('success', $response['status']);
        $this->assertArrayHasKey('uuid', $response);
        $this->assertArrayHasKey('auth_token', $response);
    }

    /**
     * @runInSeparateProcess
     * @preserveGlobalState disabled
     */
    public function testRegisterDuplicateUsername() {
        $this->createUser('existinguser');

        $params = [
            'mode' => 'register',
            'username' => 'existinguser',
            'email' => 'other@example.com',
            'password' => 'password123',
            'public_key' => 'pubkey',
            'encrypted_private_key' => 'privkey'
        ];

        $response = $this->request('auth/auth.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertEquals('Username taken', $response['message']);
    }

    /**
     * @runInSeparateProcess
     * @preserveGlobalState disabled
     */
    public function testRegisterInvalidEmail() {
        $params = [
            'mode' => 'register',
            'username' => 'user2',
            'email' => 'invalid-email',
            'password' => 'password123',
            'public_key' => 'pubkey',
            'encrypted_private_key' => 'privkey'
        ];

        $response = $this->request('auth/auth.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertEquals('Invalid email format', $response['message']);
    }

    /**
     * @runInSeparateProcess
     * @preserveGlobalState disabled
     */
    public function testLoginSuccess() {
        $password = 'mypassword';
        $this->createUser('loginuser', $password); // DbTestCase handles pepper

        $params = [
            'mode' => 'login',
            'username' => 'loginuser',
            'password' => $password
        ];

        $response = $this->request('auth/auth.php', $params);
        $this->assertEquals('success', $response['status']);
        $this->assertArrayHasKey('uuid', $response);
        $this->assertArrayHasKey('auth_token', $response);
    }

    /**
     * @runInSeparateProcess
     * @preserveGlobalState disabled
     */
    public function testLoginFailure() {
        $this->createUser('user3', 'password');

        $params = [
            'mode' => 'login',
            'username' => 'user3',
            'password' => 'wrongpassword'
        ];

        $response = $this->request('auth/auth.php', $params);
        $this->assertEquals('error', $response['status']);
        $this->assertEquals('Invalid username or password', $response['message']);
    }

    public function testVerifyAuthSessionExpiry() {
        $userId = $this->createUser('expiryuser');

        // Regression: Test that if expires_at is in the past, verify_auth fails
        $token = 'expired-token';
        pg_query_params($this->db,
            "INSERT INTO user_sessions (token, user_id, expires_at) VALUES ($1, $2, NOW() - INTERVAL '1 hour')",
            array($token, $userId)
        );

        $_POST['user_id'] = $userId;
        $_POST['auth_token'] = $token;

        ob_start();
        try {
            verify_auth($this->db);
            $this->fail("verify_auth should have failed for expired session");
        } catch (Exception $e) {
            // expected if send_error throws (but it doesn't currently, it just echoes and exits/returns)
        }
        $output = ob_get_clean();
        $response = json_decode($output, true);

        $this->assertEquals('error', $response['status']);
        $this->assertEquals('Invalid or expired session', $response['message']);
        $this->assertTrue($response['force_logout']);
    }

    public function testVerifyAuthSessionValid() {
        $userId = $this->createUser('validuser');
        $token = $this->createSession($userId);

        $_POST['user_id'] = $userId;
        $_POST['auth_token'] = $token;

        ob_start();
        $result = verify_auth($this->db);
        ob_end_clean();

        $this->assertEquals($userId, $result);
    }
}
