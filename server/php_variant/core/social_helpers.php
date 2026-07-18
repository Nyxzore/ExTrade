<?php

function normalize_whatsapp($phone) {
    if ($phone === null) {
        return '';
    }
    $phone = trim($phone);
    if ($phone === '') {
        return '';
    }

    $digits = preg_replace('/[^0-9]/', '', $phone);
    if ($digits === '') {
        return null;
    }

    // South African local format: 0821234567 -> 27821234567
    if (strlen($digits) === 10 && $digits[0] === '0') {
        $digits = '27' . substr($digits, 1);
    }

    if (strlen($digits) < 10 || strlen($digits) > 15) {
        return null;
    }

    return $digits;
}

function normalize_facebook($value) {
    if ($value === null) {
        return '';
    }
    $value = trim($value);
    if ($value === '') {
        return '';
    }

    $value = ltrim($value, '@');

    if (preg_match('~^https?://~i', $value) || stripos($value, 'facebook.com') !== false || stripos($value, 'fb.com') !== false) {
        if (!preg_match('~^https?://~i', $value)) {
            $value = 'https://' . ltrim($value, '/');
        }
        $parts = parse_url($value);
        $path = trim($parts['path'] ?? '', '/');
        if ($path === '') {
            return null;
        }

        if (str_starts_with($path, 'profile.php')) {
            parse_str($parts['query'] ?? '', $query);
            $id = $query['id'] ?? null;
            return $id ? 'profile.php?id=' . $id : null;
        }

        $segments = explode('/', $path);
        if ($segments[0] === 'people' && count($segments) >= 2) {
            return $segments[1];
        }
        if ($segments[0] === 'pages' && count($segments) >= 2) {
            return $segments[1];
        }

        return $segments[0] !== '' ? $segments[0] : null;
    }

    if (!preg_match('/^[A-Za-z0-9.]{1,100}$/', $value)) {
        return null;
    }

    return $value;
}

function normalize_instagram($value) {
    if ($value === null) {
        return '';
    }
    $value = trim($value);
    if ($value === '') {
        return '';
    }

    $value = ltrim($value, '@');

    if (preg_match('~^https?://~i', $value) || stripos($value, 'instagram.com') !== false) {
        if (!preg_match('~^https?://~i', $value)) {
            $value = 'https://' . ltrim($value, '/');
        }
        $parts = parse_url($value);
        $path = trim($parts['path'] ?? '', '/');
        if ($path === '') {
            return null;
        }

        $segments = explode('/', $path);
        $handle = $segments[0];
        if (in_array($handle, ['p', 'reel', 'stories', 'explore'], true)) {
            return null;
        }

        return $handle !== '' ? $handle : null;
    }

    $value = preg_replace('/[?#].*$/', '', $value);
    $value = explode('/', $value)[0];

    if (!preg_match('/^[A-Za-z0-9._]{1,30}$/', $value)) {
        return null;
    }

    return $value;
}

function sanitize_social_fields($whatsapp, $facebook, $instagram) {
    $normalized_whatsapp = normalize_whatsapp($whatsapp);
    if ($normalized_whatsapp === null) {
        send_error("Invalid WhatsApp number");
    }

    $normalized_facebook = normalize_facebook($facebook);
    if ($normalized_facebook === null) {
        send_error("Invalid Facebook profile");
    }

    $normalized_instagram = normalize_instagram($instagram);
    if ($normalized_instagram === null) {
        send_error("Invalid Instagram handle");
    }

    return [
        'whatsapp' => $normalized_whatsapp,
        'facebook' => $normalized_facebook,
        'instagram' => $normalized_instagram,
    ];
}

?>
