<?php
use PHPUnit\Framework\TestCase;

abstract class DbTestCase extends TestCase {
    protected $db;

    protected function setUp(): void {
        $this->db = get_db_connection();
        pg_query($this->db, "DELETE FROM users");
        pg_query($this->db, "DELETE FROM taxa");
        pg_query($this->db, "DELETE FROM versions");
    }

    protected function tearDown(): void {
        if ($this->db) {
            try {
                @pg_close($this->db);
            } catch (Throwable $t) {}
        }
    }

    protected function createUser($username = 'testuser', $password = 'password123', $email = null) {
        if ($email === null) {
            $email = $username . '_' . bin2hex(random_bytes(4)) . '@example.com';
        }
        $hashed = password_hash($password . AUTH_PEPPER, PASSWORD_BCRYPT);
        $result = pg_query_params($this->db,
            "INSERT INTO users (username, email, password_hash, public_key, encrypted_private_key) VALUES ($1, $2, $3, $4, $5) RETURNING id",
            array($username, $email, $hashed, 'test_pub_key', 'test_priv_key')
        );
        return pg_fetch_assoc($result)['id'];
    }

    protected function createListing($sellerId, $lsid = 'test-species') {
        // Ensure taxon exists
        pg_query_params($this->db, "INSERT INTO taxa (species_lsid, genus, species) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING", [$lsid, 'Genus', 'Species']);

        $res = pg_query_params($this->db,
            "INSERT INTO listings (seller_id, species_lsid, price, sex) VALUES ($1, $2, $3, $4) RETURNING id",
            [$sellerId, $lsid, 100, 'Unsexed']
        );
        return pg_fetch_assoc($res)['id'];
    }

    protected function createSession($userId) {
        $token = bin2hex(random_bytes(32));
        pg_query_params($this->db, "INSERT INTO user_sessions (token, user_id) VALUES ($1, $2)", array($token, $userId));
        return $token;
    }

    protected function createConversation($u1, $u2) {
        $user_a = ($u1 < $u2) ? $u1 : $u2;
        $user_b = ($u1 < $u2) ? $u2 : $u1;
        $res = pg_query_params($this->db,
            "INSERT INTO conversations (user_a_id, user_b_id) VALUES ($1, $2) RETURNING id",
            [$user_a, $user_b]
        );
        return pg_fetch_assoc($res)['id'];
    }

    protected function request($relativePath, $post = [], $get = []) {
        $_POST = $post;
        $_GET = $get;
        $_SERVER['REQUEST_METHOD'] = !empty($post) ? 'POST' : 'GET';

        ob_start();
        $path = dirname(__DIR__) . '/' . $relativePath;

        $oldCwd = getcwd();
        chdir(dirname($path));

        try {
            include basename($path);
        } catch (Exception $e) {
            if (strpos($e->getMessage(), "SEND_RESPONSE: ") !== 0) {
                chdir($oldCwd);
                ob_end_clean();
                throw $e;
            }
        }

        chdir($oldCwd);
        $output = ob_get_clean();
        return json_decode($output, true);
    }
}
