<?php
$uri = urldecode(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH));

if (preg_match('#^/listing/\d+/?$#', $uri) || preg_match('#^/breeding/\d+/?$#', $uri)) {
    require __DIR__ . '/get-app.php';
    return true;
}

// Let real files (like .well-known/assetlinks.json) serve normally
return false;