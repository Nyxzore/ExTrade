<?php
require_once 'DbTestCase.php';

class ModerationBackendTest extends DbTestCase {

    public function testReportSubmission() {
        $u1 = $this->createUser('reporter');
        $u2 = $this->createUser('badactor');
        $t1 = $this->createSession($u1);

        $params = [
            'user_id' => $u1,
            'auth_token' => $t1,
            'target_type' => 'user',
            'target_id' => $u2,
            'reason' => 'Scam',
            'details' => 'He took my money'
        ];

        $res = $this->request('core/report_item.php', $params);
        $this->assertEquals('success', $res['status']);

        // Verify in DB
        $check = pg_query($this->db, "SELECT * FROM reports");
        $this->assertEquals(1, pg_num_rows($check));
        $row = pg_fetch_assoc($check);
        $this->assertEquals('user', $row['target_type']);
        $this->assertEquals($u2, $row['target_id']);
    }

    public function testAdminAccessOnly() {
        $u1 = $this->createUser('regular');
        $t1 = $this->createSession($u1);

        $res = $this->request('admin/get_flagged_items.php', ['user_id' => $u1, 'auth_token' => $t1]);
        $this->assertEquals('error', $res['status']);
        $this->assertStringContainsString('Admin access required', $res['message']);
    }

    public function testBanAction() {
        $admin = $this->createUser('admin');
        pg_query($this->db, "UPDATE users SET is_admin = TRUE WHERE id = '$admin'");
        $adminToken = $this->createSession($admin);

        $badActor = $this->createUser('badactor');
        $badToken = $this->createSession($badActor);

        // Regular request works
        $res = $this->request('profile/get_profile.php', ['user_id' => $badActor, 'auth_token' => $badToken]);
        $this->assertEquals('success', $res['status']);

        // Admin bans him (simulating a report resolution)
        pg_query($this->db, "INSERT INTO reports (reporter_id, target_type, target_id, reason) VALUES ('$admin', 'user', '$badActor', 'Test')");
        $res = pg_query($this->db, "SELECT id FROM reports LIMIT 1");
        $reportId = pg_fetch_result($res, 0, 0);

        $res = $this->request('admin/resolve_report.php', [
            'user_id' => $admin,
            'auth_token' => $adminToken,
            'report_id' => $reportId,
            'action' => 'ban'
        ]);
        $this->assertEquals('success', $res['status']);

        // Now bad actor is blocked
        $res = $this->request('profile/get_profile.php', ['user_id' => $badActor, 'auth_token' => $badToken]);
        $this->assertEquals('error', $res['status']);
        $this->assertStringContainsString('banned', $res['message']);
        $this->assertTrue($res['force_logout']);
    }

    public function testDeleteListingAction() {
        $admin = $this->createUser('admin2');
        pg_query($this->db, "UPDATE users SET is_admin = TRUE WHERE id = '$admin'");
        $adminToken = $this->createSession($admin);

        $seller = $this->createUser('seller');
        $listingId = $this->createListing($seller);

        pg_query($this->db, "INSERT INTO reports (reporter_id, target_type, target_id, reason) VALUES ('$admin', 'listing', '$listingId', 'Fake')");
        $res = pg_query($this->db, "SELECT id FROM reports LIMIT 1");
        $reportId = pg_fetch_result($res, 0, 0);

        $res = $this->request('admin/resolve_report.php', [
            'user_id' => $admin,
            'auth_token' => $adminToken,
            'report_id' => $reportId,
            'action' => 'delete'
        ]);
        $this->assertEquals('success', $res['status']);

        // Verify listing is gone
        $check = pg_query($this->db, "SELECT 1 FROM listings WHERE id = $listingId");
        $this->assertEquals(0, pg_num_rows($check));
    }
}
