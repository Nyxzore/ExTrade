<?php
require_once 'DbTestCase.php';

class CoreBackendTest extends DbTestCase {

    public function testGetVersions() {
        pg_query($this->db, "DELETE FROM versions");
        pg_query($this->db, "INSERT INTO versions (object, version) VALUES ('taxa', '0.1'), ('common_names', '0.2')");

        $response = $this->request('core/get_versions.php');
        $this->assertEquals('success', $response['status']);
        $this->assertEquals('0.1', $response['versions']['taxa']);
        $this->assertEquals('0.2', $response['versions']['common_names']);
    }
}
?>
