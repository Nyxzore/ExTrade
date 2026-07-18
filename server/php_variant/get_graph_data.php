<?php
header('Content-Type: application/json');

$obsidianDir = __DIR__ . '/Obsidian/ExTrade';
$files = glob($obsidianDir . '/*.md');

$nodes = [];
$links = [];
$fileToId = [];

foreach ($files as $file) {
    $filename = basename($file, '.md');
    $nodes[] = [
        'id' => $filename,
        'label' => $filename,
        'group' => 'note'
    ];
    $fileToId[$filename] = $filename;
}

foreach ($files as $file) {
    $source = basename($file, '.md');
    $content = file_get_contents($file);

    // Match [[Link]] or [[Link|Alias]]
    preg_match_all('/\[\[(.*?)\]\]/', $content, $matches);

    foreach ($matches[1] as $match) {
        // Handle aliases
        $parts = explode('|', $match);
        $target = trim($parts[0]);

        if (isset($fileToId[$target])) {
            $links[] = [
                'source' => $source,
                'target' => $target
            ];
        }
    }
}

echo json_encode([
    'nodes' => $nodes,
    'links' => $links
]);
