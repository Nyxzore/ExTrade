<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Download ExoTrade</title>
    <style>
        :root {
            --brand-orange: #FF6B00;
            --bg-dark: #201A19;
            --text-main: #EDE0DD;
            --text-muted: #D0C4C1;
            --surface: #2D2422;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            margin: 0;
            background-color: var(--bg-dark);
            color: var(--text-main);
            text-align: center;
        }
        .container {
            padding: 40px 20px;
            max-width: 400px;
            width: 100%;
        }
        .logo {
            font-size: 2.5rem;
            font-weight: 800;
            letter-spacing: -1.5px;
            margin-bottom: 24px;
        }
        .logo span { color: var(--brand-orange); }
        h1 {
            font-size: 1.25rem;
            font-weight: 600;
            margin-bottom: 12px;
            color: var(--text-main);
        }
        p {
            font-size: 1rem;
            color: var(--text-muted);
            margin-bottom: 32px;
            line-height: 1.5;
        }
        .download-btn {
            display: inline-block;
            background-color: var(--brand-orange);
            color: #fff;
            text-decoration: none;
            padding: 16px 32px;
            border-radius: 32px;
            font-weight: 700;
            font-size: 1.1rem;
            transition: transform 0.2s, background-color 0.2s;
            box-shadow: 0 4px 12px rgba(255, 107, 0, 0.3);
        }
        .download-btn:active {
            transform: scale(0.98);
        }
        .note {
            margin-top: 20px;
            font-size: 0.85rem;
            color: #857471;
            line-height: 1.4;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="logo">Exo<span>Trade</span></div>
        <h1>View this listing in the ExoTrade app</h1>
        <p>Experience the full marketplace for exotic animals with end-to-end encrypted messaging.</p>

        <a href="/downloads/extrade-v0.0.2.apk" class="download-btn">Download for Android</a>

        <div class="note">
            You may need to allow installs from this source in your Android settings.
        </div>
    </div>
</body>
</html>