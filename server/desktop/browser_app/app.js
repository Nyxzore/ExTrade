const API_BASE = '/';
const storage = window.localStorage;

const state = {
    user: JSON.parse(storage.getItem('exotrade_user')) || null,
    listings: [],
    breeding: [],
    conversations: [],
    currentChat: null,
    crypto: null
};

// --- E2EE Crypto Manager ---
const CryptoManager = {
    async init() {
        await sodium.ready;
        state.crypto = sodium;
        console.log("Libsodium ready");
    },

    async generateIdentityKeys() {
        const keyPair = sodium.crypto_box_keypair();
        return {
            publicKey: sodium.to_base64(keyPair.publicKey),
            privateKey: keyPair.privateKey // UInt8Array
        };
    },

    deriveBackupKey(password, saltBase64) {
        const salt = sodium.from_base64(saltBase64);
        return sodium.crypto_pwhash(
            sodium.crypto_secretbox_KEYBYTES,
            password,
            salt,
            sodium.crypto_pwhash_OPSLIMIT_INTERACTIVE,
            sodium.crypto_pwhash_MEMLIMIT_INTERACTIVE,
            sodium.crypto_pwhash_ALG_ARGON2ID13
        );
    },

    encryptPrivateKey(privateKey, backupKey) {
        const nonce = sodium.randombytes_buf(sodium.crypto_secretbox_NONCEBYTES);
        const ciphertext = sodium.crypto_secretbox_easy(privateKey, nonce, backupKey);
        return {
            ciphertext: sodium.to_base64(ciphertext),
            nonce: sodium.to_base64(nonce)
        };
    },

    decryptPrivateKey(encryptedKeyBase64, nonceBase64, backupKey) {
        try {
            const ciphertext = sodium.from_base64(encryptedKeyBase64);
            const nonce = sodium.from_base64(nonceBase64);
            return sodium.crypto_secretbox_open_easy(ciphertext, nonce, backupKey);
        } catch (e) {
            console.error("Failed to decrypt private key", e);
            return null;
        }
    },

    encryptMessage(message, recipientPublicKeyBase64, myPrivateKey) {
        const recipientPublicKey = sodium.from_base64(recipientPublicKeyBase64);
        const nonce = sodium.randombytes_buf(sodium.crypto_box_NONCEBYTES);
        const ciphertext = sodium.crypto_box_easy(message, nonce, recipientPublicKey, myPrivateKey);
        return {
            ciphertext: sodium.to_base64(ciphertext),
            nonce: sodium.to_base64(nonce)
        };
    },

    decryptMessage(ciphertextBase64, nonceBase64, senderPublicKeyBase64, myPrivateKey) {
        try {
            const ciphertext = sodium.from_base64(ciphertextBase64);
            const nonce = sodium.from_base64(nonceBase64);
            const senderPublicKey = sodium.from_base64(senderPublicKeyBase64);
            return sodium.to_string(sodium.crypto_box_open_easy(ciphertext, nonce, senderPublicKey, myPrivateKey));
        } catch (e) {
            console.warn("Decryption failed - possibly not for this key or corrupt", e);
            return "[Decryption Error]";
        }
    }
};

// --- Utilities ---
function formatCurrency(val) {
    if (!val) return 'Contact';
    if (typeof val === 'string' && val.includes('R')) return val;
    return new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(val);
}

function formatDate(iso) {
    if (!iso) return '';
    const date = new Date(iso);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function showModal(contentHtml) {
    const container = document.getElementById('modal-container');
    const content = document.getElementById('modal-content');
    content.innerHTML = `<button class="modal-close" onclick="closeModal()">×</button>` + contentHtml;
    container.classList.remove('hidden');
}

function closeModal() {
    document.getElementById('modal-container').classList.add('hidden');
}

// --- Auth Handling ---
function saveSession(data) {
    state.user = {
        uuid: data.uuid,
        token: data.auth_token,
        username: data.username,
        isAdmin: data.is_admin,
        privateKey: data.private_key // Uint8Array or null if just logged in and needs password
    };
    storage.setItem('exotrade_user', JSON.stringify({ ...state.user, privateKey: null })); // Don't store raw privkey in localStorage
    window.location.hash = 'discovery';
}

function logout() {
    state.user = null;
    storage.removeItem('exotrade_user');
    window.location.hash = 'login';
}

async function apiPost(endpoint, body) {
    const payload = new URLSearchParams();

    // Auth parameters
    if (state.user && state.user.uuid && state.user.token) {
        payload.append('uuid', state.user.uuid);
        payload.append('auth_token', state.user.token);
    }

    // Body parameters
    for (const key in body) {
        if (body[key] !== null && body[key] !== undefined) {
            // Special handling for image data to still use form-data if it's too large,
            // but for now let's try URLSearchParams for everything to ensure Gin parses it.
            payload.append(key, body[key]);
        }
    }

    // If we have image data, URLSearchParams might be too large for some servers,
    // but GIN's DefaultPostForm should handle it.
    // If it fails, we might need to switch back to FormData for large payloads.
    const isLarge = Object.values(body).some(v => typeof v === 'string' && v.length > 10000);

    let res;
    if (isLarge) {
        const formData = new FormData();
        if (state.user && state.user.uuid && state.user.token) {
            formData.append('uuid', state.user.uuid);
            formData.append('auth_token', state.user.token);
        }
        for (const key in body) {
            if (body[key] !== null && body[key] !== undefined) formData.append(key, body[key]);
        }
        res = await fetch(API_BASE + endpoint, { method: 'POST', body: formData });
    } else {
        res = await fetch(API_BASE + endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: payload
        });
    }

    const json = await res.json();
    if (json.status === 'error' && (json.message.includes('Authentication') || json.message.includes('token') || res.status === 401)) {
        console.warn("Auth error detected, logging out", json.message);
        logout();
    }
    return json;
}

// --- Routing ---
const routes = {
    login: renderLogin,
    register: renderRegister,
    discovery: renderDiscovery,
    breeding: renderBreeding,
    inbox: renderInbox,
    chat: renderChat,
    'my-listings': renderMyListings,
    friends: renderFriends,
    profile: renderProfile
};

function router() {
    const fullHash = window.location.hash.slice(1) || 'discovery';
    const parts = fullHash.split('/');
    const hash = parts[0];
    const param = parts[1];

    const isAuthPath = hash === 'login' || hash === 'register';

    if (!state.user && !isAuthPath) {
        window.location.hash = 'login';
        return;
    }

    if (state.user && isAuthPath) {
        window.location.hash = 'discovery';
        return;
    }

    document.getElementById('main-header').classList.toggle('hidden', isAuthPath);

    // Update Nav Active State
    document.querySelectorAll('nav a').forEach(a => {
        a.classList.toggle('active', a.getAttribute('href') === '#' + hash);
    });

    const renderer = routes[hash] || renderDiscovery;
    renderer(param);
}

// --- View Renderers ---
function renderLogin() {
    const content = document.getElementById('content');
    content.innerHTML = `
        <div class="auth-container">
            <h1>ExoTrade Login</h1>
            <form id="form-login">
                <div class="form-group">
                    <label>Username</label>
                    <input type="text" id="login-user" required>
                </div>
                <div class="form-group">
                    <label>Password</label>
                    <input type="password" id="login-pass" required>
                </div>
                <button type="submit">Login</button>
            </form>
            <p class="auth-switch">Don't have an account? <span onclick="window.location.hash='register'">Register</span></p>
        </div>
    `;

    document.getElementById('form-login').onsubmit = async (e) => {
        e.preventDefault();
        const username = document.getElementById('login-user').value;
        const pass = document.getElementById('login-pass').value;

        const res = await apiPost('auth/auth', {
            username: username,
            password: pass,
            mode: 'login'
        });

        if (res.status === 'success') {
            // Restore Private Key
            const backup = await apiPost('messaging/get_backup', { uuid: res.data.uuid, auth_token: res.data.auth_token });
            if (backup.status === 'success') {
                const backupKey = CryptoManager.deriveBackupKey(pass, backup.data.kdf_salt);
                const privKey = CryptoManager.decryptPrivateKey(backup.data.encrypted_private_key, backup.data.private_key_nonce, backupKey);
                if (privKey) {
                    saveSession({ ...res.data, username: username, private_key: privKey });
                } else {
                    alert("Failed to decrypt chat keys. Password might be incorrect or keys corrupted.");
                }
            } else {
                saveSession({ ...res.data, username: username });
            }
        } else alert(res.message);
    };
}

function renderRegister() {
    const content = document.getElementById('content');
    content.innerHTML = `
        <div class="auth-container">
            <h1>Create Account</h1>
            <form id="form-register">
                <div class="form-group"><label>Username</label><input type="text" id="reg-user" required></div>
                <div class="form-group"><label>Email</label><input type="email" id="reg-email" required></div>
                <div class="form-group"><label>Password</label><input type="password" id="reg-pass" required></div>
                <button type="submit">Register</button>
            </form>
            <p class="auth-switch">Already have an account? <span onclick="window.location.hash='login'">Login</span></p>
        </div>
    `;

    document.getElementById('form-register').onsubmit = async (e) => {
        e.preventDefault();
        const pass = document.getElementById('reg-pass').value;

        // Generate E2EE Keys
        const keys = await CryptoManager.generateIdentityKeys();
        const salt = sodium.randombytes_buf(sodium.crypto_pwhash_SALTBYTES);
        const backupKey = CryptoManager.deriveBackupKey(pass, sodium.to_base64(salt));
        const encrypted = CryptoManager.encryptPrivateKey(keys.privateKey, backupKey);

        const res = await apiPost('auth/auth', {
            username: document.getElementById('reg-user').value,
            email: document.getElementById('reg-email').value,
            password: pass,
            mode: 'register',
            public_key: keys.publicKey,
            encrypted_private_key: encrypted.ciphertext,
            private_key_nonce: encrypted.nonce,
            kdf_salt: sodium.to_base64(salt)
        });

        if (res.status === 'success') saveSession({ ...res.data, username: document.getElementById('reg-user').value, private_key: keys.privateKey });
        else alert(res.message);
    };
}

async function renderInbox() {
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Loading your conversations...</div>';

    const res = await apiPost('messaging/get_conversations', {});
    if (res.status === 'success') {
        state.conversations = res.conversations || [];
        content.innerHTML = `
            <h1 style="margin-bottom:2rem">Inbox</h1>
            <div class="inbox-list">
                ${state.conversations.map(c => `
                    <div class="conversation-item ${c.unread_count > 0 ? 'unread' : ''}" onclick="window.location.hash='chat/${c.conversation_id}'">
                        <img src="${c.other_profile_pic || '/logo.png'}" class="conv-avatar">
                        <div class="conv-info">
                            <div class="conv-meta">
                                <strong>${c.other_username}</strong>
                                <span style="font-size:0.8rem; color:var(--text-muted)">${formatDate(c.last_message_time)}</span>
                            </div>
                            <div class="conv-last-msg">
                                ${c.is_last_message_encrypted ? '🔒 [Encrypted Message]' : (c.last_message || 'No messages yet')}
                            </div>
                        </div>
                    </div>
                `).join('')}
                ${state.conversations.length === 0 ? '<p style="text-align:center; color:var(--text-muted)">No conversations yet.</p>' : ''}
            </div>
        `;
    }
}

async function renderChat(convId) {
    if (!convId) { window.location.hash = 'inbox'; return; }
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Opening chat...</div>';

    const res = await apiPost('messaging/get_messages', { conversation_id: convId });
    if (res.status === 'success') {
        const messages = res.messages || [];
        const conv = state.conversations.find(c => c.conversation_id === convId) || { other_username: 'Chat' };

        content.innerHTML = `
            <div class="chat-container">
                <div class="chat-header">
                    <button onclick="window.location.hash='inbox'" style="width:auto; padding:0.5rem 1rem; background:var(--surface-light); color:white; border:none; cursor:pointer">←</button>
                    <h2 style="margin-left:1rem">${conv.other_username}</h2>
                </div>
                <div id="chat-messages">
                    ${messages.map(m => {
                        let body = m.body;
                        if (m.is_encrypted && state.user.privateKey) {
                            body = CryptoManager.decryptMessage(m.body, m.nonce, m.sender_public_key, state.user.privateKey);
                        }
                        return `
                            <div class="message ${m.sender_id === state.user.uuid ? 'sent' : 'received'}">
                                <div class="message-body">${body}</div>
                                <span class="message-time">${formatDate(m.sent_at)}</span>
                            </div>
                        `;
                    }).join('')}
                </div>
                <form id="chat-form" class="chat-input-area">
                    <input type="text" id="chat-input" placeholder="Type a message..." required autocomplete="off">
                    <button type="submit">Send</button>
                </form>
            </div>
        `;

        const msgContainer = document.getElementById('chat-messages');
        msgContainer.scrollTop = msgContainer.scrollHeight;

        document.getElementById('chat-form').onsubmit = async (e) => {
            e.preventDefault();
            const input = document.getElementById('chat-input');
            const text = input.value;
            input.value = '';

            let body = text;
            let nonce = 'AAA=';
            let isEncrypted = false;

            if (conv.other_public_key && state.user.privateKey) {
                const enc = CryptoManager.encryptMessage(text, conv.other_public_key, state.user.privateKey);
                body = enc.ciphertext;
                nonce = enc.nonce;
                isEncrypted = true;
            }

            const sendRes = await apiPost('messaging/send_message', {
                conversation_id: convId,
                body: body,
                nonce: nonce,
                is_encrypted: isEncrypted
            });

            if (sendRes.status === 'success') {
                renderChat(convId);
            }
        };
    }
}

async function renderMyListings() {
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Fetching your listings...</div>';

    // Mocking an endpoint or using get_all_listings and filtering if no specific 'my' endpoint exists
    // Looking at main.go, there isn't a specific 'get_my_listings', so we filter get_all_listings
    const res = await apiPost('listings/get_all_listings', {});
    if (res.status === 'success') {
        const myListings = (res.listings || []).filter(l => l.seller_id === state.user.uuid);
        content.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:2rem">
                <h1>My Listings</h1>
                <button onclick="renderCreateListingModal()" style="width:auto; padding:0.8rem 1.5rem">+ Create New</button>
            </div>
            <div class="feed-grid">
                ${myListings.map(l => `
                    <div class="listing-card">
                        <img src="${l.image_url || '/logo.png'}" onerror="this.src='/logo.png'">
                        <div class="listing-info">
                            <h3>${l.common_name}</h3>
                            <span class="scientific-name">${l.scientific_name}</span>
                            <div class="price">${l.price}</div>
                        </div>
                    </div>
                `).join('')}
                ${myListings.length === 0 ? '<p style="text-align:center; grid-column:1/-1; color:var(--text-muted)">You haven\'t posted any listings yet.</p>' : ''}
            </div>
        `;
    }
}

function renderCreateListingModal() {
    showModal(`
        <h2>Create New Listing</h2>
        <div style="display:flex; gap:1rem; margin:2rem 0">
            <button onclick="renderListingForm('sale')" style="background:var(--surface-light)">For Sale</button>
            <button onclick="renderListingForm('breeding')" style="background:var(--surface-light)">Breeding</button>
        </div>
    `);
}

async function renderListingForm(type) {
    const speciesRes = await apiPost('listings/get_all_species', {});
    const speciesList = speciesRes.status === 'success' ? (speciesRes.species || []) : [];

    showModal(`
        <h2>Create ${type === 'sale' ? 'Sale' : 'Breeding'} Listing</h2>
        <form id="form-create-listing" style="margin-top:1.5rem">
            <div class="form-group">
                <label>Species</label>
                <select id="listing-species" required>
                    ${speciesList.map(s => `<option value="${s.lsid}">${s.common_name} (${s.scientific_name})</option>`).join('')}
                </select>
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label>${type === 'sale' ? 'Price (ZAR)' : 'Stud Fee (ZAR)'}</label>
                    <input type="number" id="listing-price" placeholder="Leave empty for Contact">
                </div>
                <div class="form-group">
                    <label>Sex</label>
                    <select id="listing-sex">
                        <option value="Unknown">Unknown</option>
                        <option value="Male">Male</option>
                        <option value="Female">Female</option>
                    </select>
                </div>
            </div>
            <div class="form-group">
                <label>Description</label>
                <textarea id="listing-desc" placeholder="Details about age, size, temperament..."></textarea>
            </div>
            <div class="image-upload-preview" onclick="document.getElementById('listing-image-input').click()">
                <img id="image-preview-img" src="" style="display:none">
                <span id="image-upload-placeholder">Click to upload image</span>
                <input type="file" id="listing-image-input" hidden accept="image/*">
            </div>
            <button type="submit">Publish Listing</button>
        </form>
    `);

    const imageInput = document.getElementById('listing-image-input');
    imageInput.onchange = (e) => {
        const file = e.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = (ev) => {
                document.getElementById('image-preview-img').src = ev.target.result;
                document.getElementById('image-preview-img').style.display = 'block';
                document.getElementById('image-upload-placeholder').style.display = 'none';
            };
            reader.readAsDataURL(file);
        }
    };

    document.getElementById('form-create-listing').onsubmit = async (e) => {
        e.preventDefault();
        const imageData = document.getElementById('image-preview-img').src;

        const endpoint = type === 'sale' ? 'listings/create_listing' : 'breeding/create_breeding_listing';
        const body = {
            species_lsid: document.getElementById('listing-species').value,
            price: document.getElementById('listing-price').value || null,
            loan_fee: document.getElementById('listing-price').value || null,
            sex: document.getElementById('listing-sex').value,
            description: document.getElementById('listing-desc').value,
            image_data: imageData.startsWith('data:') ? imageData : null
        };

        const res = await apiPost(endpoint, body);
        if (res.status === 'success') {
            closeModal();
            renderMyListings();
        } else alert(res.message);
    };
}

async function renderFriends() {
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Loading your network...</div>';

    const res = await apiPost('friends/get_friends', {});
    if (res.status === 'success') {
        const friends = res.friends || [];
        content.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:2rem">
                <h1>Friends</h1>
                <div style="display:flex; gap:1rem">
                    <input type="text" id="search-users" placeholder="Search users..." style="width:200px">
                    <button onclick="searchUsers()" style="width:auto">Search</button>
                </div>
            </div>
            <div id="friends-list" class="inbox-list">
                ${friends.map(f => `
                    <div class="conversation-item">
                        <img src="${f.profile_picture || '/logo.png'}" class="conv-avatar">
                        <div class="conv-info">
                            <strong>${f.username}</strong>
                            <p style="color:var(--text-muted); font-size:0.8rem">Friend</p>
                        </div>
                        <button onclick="window.location.hash='chat/${f.conversation_id}'" style="width:auto; padding:0.5rem 1rem">Message</button>
                    </div>
                `).join('')}
                ${friends.length === 0 ? '<p style="text-align:center; color:var(--text-muted)">No friends yet. Try searching for users!</p>' : ''}
            </div>
            <div id="search-results" style="margin-top:2rem" class="hidden">
                <h3>Search Results</h3>
                <div id="search-results-list" class="inbox-list" style="margin-top:1rem"></div>
            </div>
        `;
    }
}

async function searchUsers() {
    const query = document.getElementById('search-users').value;
    const res = await apiPost('friends/search_users', { query: query });
    if (res.status === 'success') {
        const results = res.users || [];
        const list = document.getElementById('search-results-list');
        document.getElementById('search-results').classList.remove('hidden');
        list.innerHTML = results.map(u => `
            <div class="conversation-item">
                <img src="${u.profile_picture || '/logo.png'}" class="conv-avatar">
                <div class="conv-info">
                    <strong>${u.username}</strong>
                </div>
                <button onclick="sendFriendRequest('${u.id}')" style="width:auto; padding:0.5rem 1rem">Add Friend</button>
            </div>
        `).join('');
    }
}

async function sendFriendRequest(userId) {
    const res = await apiPost('friends/send_friend_request', { target_user_id: userId });
    if (res.status === 'success') alert("Friend request sent!");
    else alert(res.message);
}

async function renderDiscovery() {
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Fetching the latest listings...</div>';

    const res = await apiPost('listings/get_all_listings', {});
    if (res.status === 'success') {
        state.listings = res.listings || [];
        content.innerHTML = `
            <h1 style="margin-bottom:2rem">Discovery Feed</h1>
            <div class="feed-grid">
                ${state.listings.map(l => `
                    <div class="listing-card" onclick="renderListingDetail('${l.id}', 'sale')">
                        ${(l.is_unverified_scientific || l.is_unverified_common) ? '<div class="unverified-badge" title="Unverified name">!</div>' : ''}
                        <img src="${l.image_url || '/logo.png'}" onerror="this.src='/logo.png'">
                        <div class="listing-info">
                            <h3>${l.common_name}</h3>
                            <span class="scientific-name">${l.scientific_name}</span>
                            <div class="price">${l.price}</div>
                        </div>
                    </div>
                `).join('')}
            </div>
            <button class="fab" onclick="renderCreateListingModal()">+</button>
        `;
    }
}

async function renderBreeding() {
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Finding breeding matches...</div>';

    const res = await apiPost('breeding/get_breeding_listings', {});
    if (res.status === 'success') {
        state.breeding = res.listings || [];
        content.innerHTML = `
            <h1 style="margin-bottom:2rem">Breeding Feed</h1>
            <div class="feed-grid">
                ${state.breeding.map(l => `
                    <div class="listing-card" onclick="renderListingDetail('${l.id}', 'breeding')">
                        <img src="${l.image_url || '/logo.png'}" onerror="this.src='/logo.png'">
                        <div class="listing-info">
                            <h3>${l.common_name}</h3>
                            <span class="scientific-name">${l.scientific_name}</span>
                            <div class="price">${l.price}</div>
                            <div style="font-size:0.8rem; color:#888; margin-top:0.5rem">${l.sex} • ${l.breeding_type}</div>
                        </div>
                    </div>
                `).join('')}
            </div>
            <button class="fab" onclick="renderCreateListingModal()">+</button>
        `;
    }
}

async function renderListingDetail(id, kind) {
    const endpoint = kind === 'sale' ? 'listings/get_listing_details' : 'breeding/get_breeding_listing_details';
    const res = await apiPost(endpoint, { listing_id: id });
    if (res.status === 'success') {
        const l = res.listing;
        showModal(`
            <div class="detail-view">
                <img src="${l.image_url || '/logo.png'}" style="width:100%; height:300px; object-fit:contain; background:#000; border-radius:16px; margin-bottom:1.5rem">
                <h1>${l.common_name}</h1>
                <p class="scientific-name" style="font-size:1.1rem">${l.scientific_name}</p>
                <div class="price" style="font-size:1.5rem; margin:1rem 0">${kind === 'sale' ? l.price : (l.loan_fee ? 'R ' + l.loan_fee : 'Contact')}</div>
                <div style="display:flex; gap:1rem; margin-bottom:1.5rem">
                    <span style="background:var(--surface-light); padding:0.5rem 1rem; border-radius:20px">${l.sex}</span>
                    ${l.breeding_type ? `<span style="background:var(--surface-light); padding:0.5rem 1rem; border-radius:20px">${l.breeding_type}</span>` : ''}
                </div>
                <p style="margin-bottom:2rem">${l.description || 'No description provided.'}</p>

                <div style="border-top:1px solid var(--surface-light); padding-top:1.5rem; display:flex; align-items:center; gap:1rem">
                    <img src="${l.seller_profile_pic || '/logo.png'}" style="width:50px; height:50px; border-radius:50%">
                    <div style="flex:1">
                        <strong>${l.seller_username}</strong>
                        <p style="font-size:0.8rem; color:var(--text-muted)">Seller</p>
                    </div>
                    ${l.seller_id !== state.user.uuid ? `
                        <button onclick="contactSeller('${l.id}', '${l.seller_id}', '${kind}')" style="width:auto; padding:0.8rem 1.5rem">Message Seller</button>
                    ` : ''}
                </div>
            </div>
        `);
    }
}

async function contactSeller(listingId, sellerId, kind) {
    const res = await apiPost('messaging/start_or_get_conversation', {
        listing_id: listingId,
        seller_id: sellerId,
        listing_kind: kind
    });
    if (res.status === 'success') {
        closeModal();
        window.location.hash = `chat/${res.conversation_id}`;
    } else alert(res.message);
}

async function renderProfile() {
    const content = document.getElementById('content');
    content.innerHTML = '<div class="loader">Loading profile...</div>';

    const res = await apiPost('profile/get_profile', { target_user_id: state.user.uuid });
    if (res.status === 'success') {
        state.currentUserProfile = res; // Store full profile info for editing
        const u = res;
        content.innerHTML = `
            <div class="profile-header">
                <img src="${u.profile_picture || '/logo.png'}" class="profile-pic">
                <div>
                    <h1>${u.username}</h1>
                    <p style="color:var(--brand-orange); font-weight:600">${u.subscription_tier === 2 ? 'PRO MEMBER' : 'MEMBER'}</p>
                    <p style="color:var(--text-muted); margin-top:0.5rem">${u.bio || 'ExoTrade Member'}</p>
                </div>
                <button onclick="renderEditProfileModal()" style="width:auto; margin-left:auto">Edit Profile</button>
            </div>

            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:2rem">
                <div class="auth-container" style="margin:0; max-width:none">
                    <h3>Account Info</h3>
                    <p style="margin-top:1rem"><strong>Email:</strong> ${u.email || 'Private'}</p>
                    <p><strong>WhatsApp:</strong> ${u.whatsapp || 'Not set'}</p>
                </div>
                <div class="auth-container" style="margin:0; max-width:none">
                    <h3>Social</h3>
                    <p style="margin-top:1rem"><strong>Facebook:</strong> ${u.facebook || 'Not set'}</p>
                    <p><strong>Instagram:</strong> ${u.instagram || 'Not set'}</p>
                </div>
            </div>
        `;
    }
}

function renderEditProfileModal() {
    const u = state.currentUserProfile;
    showModal(`
        <h2>Edit Profile</h2>
        <form id="form-edit-profile" style="margin-top:1.5rem">
            <div class="form-row">
                <div class="form-group">
                    <label>Username</label>
                    <input type="text" id="edit-username" value="${u.username}" required>
                </div>
                <div class="form-group">
                    <label>Email</label>
                    <input type="email" id="edit-email" value="${u.email}" required>
                </div>
            </div>
            <div class="form-group">
                <label>Bio (Saved locally for now)</label>
                <textarea id="edit-bio" placeholder="Tell us about yourself...">${u.bio || ''}</textarea>
            </div>
            <div class="form-row">
                <div class="form-group"><label>WhatsApp</label><input type="text" id="edit-whatsapp" value="${u.whatsapp || ''}"></div>
                <div class="form-group"><label>Facebook</label><input type="text" id="edit-facebook" value="${u.facebook || ''}"></div>
                <div class="form-group"><label>Instagram</label><input type="text" id="edit-instagram" value="${u.instagram || ''}"></div>
            </div>
            <div class="image-upload-preview" onclick="document.getElementById('profile-image-input').click()">
                <img id="profile-preview-img" src="${u.profile_picture || ''}" style="${u.profile_picture ? 'display:block' : 'display:none'}">
                <span id="profile-upload-placeholder" style="${u.profile_picture ? 'display:none' : 'display:block'}">Click to change profile picture</span>
                <input type="file" id="profile-image-input" hidden accept="image/*">
            </div>
            <button type="submit">Save Changes</button>
        </form>
    `);

    const imageInput = document.getElementById('profile-image-input');
    imageInput.onchange = (e) => {
        const file = e.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = (ev) => {
                document.getElementById('profile-preview-img').src = ev.target.result;
                document.getElementById('profile-preview-img').style.display = 'block';
                document.getElementById('profile-upload-placeholder').style.display = 'none';
            };
            reader.readAsDataURL(file);
        }
    };

    document.getElementById('form-edit-profile').onsubmit = async (e) => {
        e.preventDefault();
        const imageData = document.getElementById('profile-preview-img').src;

        const res = await apiPost('profile/update_profile', {
            username: document.getElementById('edit-username').value,
            email: document.getElementById('edit-email').value,
            whatsapp: document.getElementById('edit-whatsapp').value,
            facebook: document.getElementById('edit-facebook').value,
            instagram: document.getElementById('edit-instagram').value,
            profile_picture_data: imageData.startsWith('data:') ? imageData : null
        });

        if (res.status === 'success') {
            closeModal();
            renderProfile();
        } else alert(res.message);
    };
}


// --- Initialization ---
async function initApp() {
    await CryptoManager.init();
    window.onhashchange = router;
    document.getElementById('btn-logout').onclick = logout;
    router();
}

initApp();
