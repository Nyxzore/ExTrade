<?php
define('PHPUNIT_RUNNING', true);

set_include_path(get_include_path() . PATH_SEPARATOR . dirname(__DIR__));

require_once 'core/db_connect.php';

function init_test_db() {
    $host = getenv('DB_HOST') ?: "localhost";
    $port = getenv('DB_PORT') ?: "5432";
    $dbname = getenv('DB_NAME') ?: "exotrade_test";
    $user = getenv('DB_USER') ?: "sgroup2689";

    $conn = pg_connect("host=$host port=$port dbname=postgres user=$user");
    if (!$conn) return;

    $result = pg_query_params($conn, "SELECT 1 FROM pg_database WHERE datname = $1", array($dbname));
    if (pg_num_rows($result) == 0) {
        pg_query($conn, "CREATE DATABASE $dbname");
    }
    pg_close($conn);

    $db = pg_connect("host=$host port=$port dbname=$dbname user=$user");
    if (!$db) return;

    pg_query($db, "CREATE TABLE IF NOT EXISTS users (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        username TEXT UNIQUE NOT NULL,
        email TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        profile_picture TEXT,
        public_key TEXT,
        encrypted_private_key TEXT,
        private_key_nonce TEXT,
        kdf_salt TEXT,
        subscription_tier INTEGER DEFAULT 0,
        is_admin BOOLEAN DEFAULT FALSE,
        is_banned BOOLEAN DEFAULT FALSE,
        whatsapp TEXT DEFAULT '',
        facebook TEXT DEFAULT '',
        instagram TEXT DEFAULT '',
        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    )");

    pg_query($db, "ALTER TABLE users ADD COLUMN IF NOT EXISTS whatsapp TEXT DEFAULT ''");
    pg_query($db, "ALTER TABLE users ADD COLUMN IF NOT EXISTS facebook TEXT DEFAULT ''");
    pg_query($db, "ALTER TABLE users ADD COLUMN IF NOT EXISTS instagram TEXT DEFAULT ''");
    pg_query($db, "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT FALSE");
    pg_query($db, "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT FALSE");

    pg_query($db, "CREATE TABLE IF NOT EXISTS admin_notifications (
        id SERIAL PRIMARY KEY,
        user_id UUID REFERENCES users(id) ON DELETE CASCADE,
        message TEXT NOT NULL,
        is_read BOOLEAN DEFAULT FALSE,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    )");

    pg_query($db, "CREATE TABLE IF NOT EXISTS user_sessions (
        token TEXT PRIMARY KEY,
        user_id UUID REFERENCES users(id) ON DELETE CASCADE,
        expires_at TIMESTAMP WITH TIME ZONE DEFAULT (CURRENT_TIMESTAMP + INTERVAL '1 day')
    )");

    pg_query($db, "CREATE TABLE IF NOT EXISTS taxa (
        species_lsid TEXT PRIMARY KEY,
        genus TEXT,
        species TEXT,
        subspecies TEXT,
        common_name TEXT
    )");

    pg_query($db, "CREATE TABLE IF NOT EXISTS listings (
        id SERIAL PRIMARY KEY,
        seller_id UUID REFERENCES users(id) ON DELETE CASCADE,
        species_lsid TEXT,
        price NUMERIC NOT NULL,
        description TEXT,
        sex TEXT,
        size_in_cm NUMERIC,
        age TEXT,
        image_url TEXT,
        status TEXT DEFAULT 'active',
        listed_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    )");

    pg_query($db, "CREATE TABLE IF NOT EXISTS breeding_listings (
        id SERIAL PRIMARY KEY,
        seller_id UUID REFERENCES users(id) ON DELETE CASCADE,
        species_lsid TEXT REFERENCES taxa(species_lsid),
        sex TEXT NOT NULL CHECK (sex IN ('Male', 'Female')),
        size_in_cm NUMERIC,
        age TEXT,
        description TEXT,
        image_url TEXT,
        breeding_type TEXT NOT NULL CHECK (breeding_type IN ('loan', 'seeking')),
        loan_fee NUMERIC,
        status TEXT DEFAULT 'active',
        listed_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT loan_fee_only_for_loans CHECK (breeding_type = 'loan' OR loan_fee IS NULL)
    )");

    pg_query($db, "CREATE TABLE IF NOT EXISTS conversations (
        id SERIAL PRIMARY KEY,
        user_a_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        user_b_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT unique_user_pair UNIQUE(user_a_id, user_b_id),
        CONSTRAINT ordered_pair CHECK(user_a_id < user_b_id)
    )");

    pg_query($db, "CREATE TABLE IF NOT EXISTS messages (
        id SERIAL PRIMARY KEY,
        conversation_id INTEGER REFERENCES conversations(id) ON DELETE CASCADE,
        sender_id UUID REFERENCES users(id) ON DELETE CASCADE,
        body TEXT NOT NULL,
        nonce TEXT,
        is_encrypted BOOLEAN DEFAULT TRUE,
        sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
        read_at TIMESTAMP WITH TIME ZONE DEFAULT NULL
    )");

    pg_query($db, "CREATE TABLE IF NOT EXISTS listing_impressions (
        id SERIAL PRIMARY KEY,
        listing_id INTEGER REFERENCES listings(id) ON DELETE CASCADE,
        user_id UUID REFERENCES users(id) ON DELETE CASCADE,
        shown_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    )");

    pg_query($db, "CREATE TABLE IF NOT EXISTS versions (
        object TEXT PRIMARY KEY,
        version TEXT NOT NULL
    )");

    pg_close($db);
}

init_test_db();
