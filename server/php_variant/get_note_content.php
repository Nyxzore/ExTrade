<?php
header('Content-Type: text/plain');

if (!isset($_GET['note'])) {
    die("No note specified");
}

$note = basename($_GET['note']); // Security: prevent directory traversal
$filePath = __DIR__ . '/Obsidian/ExTrade/' . $note . '.md';

if (file_exists($filePath)) {
    echo file_get_contents($filePath);
} else {
    http_response_code(404);
    echo "Note not found: " . $note;
}
