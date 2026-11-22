# MediaHub Pro — WhatsApp Bot Backend

Real WhatsApp Web bot using **Baileys**. Runs on a always-on Node.js host (not on your phone).

## Requirements
- Node.js 18+
- Always-on server (Railway, Render, Fly.io, VPS, etc.)
- Your phone only scans the QR once (WhatsApp → Linked Devices)

## Setup
```bash
cd whatsapp-bot
cp .env.example .env
# edit API_TOKEN to a long random secret
npm install
npm start
```

## Environment
- `PORT` — default 8787
- `API_TOKEN` — required; same token goes into the Android app
- `BOT_NAME`, `OWNER_NAME`, `OWNER_JID`
- `NEWS_API_KEY` — optional for `/news`

## Android app
1. Open **Command Centre → WhatsApp Bot**
2. Enter backend URL (e.g. `https://your-bot.example.com`) and API token
3. Tap **Start / Connect**
4. Scan the QR with WhatsApp → Linked Devices
5. Toggle Bot ON

The bot keeps running when MediaHub Pro is closed, as long as this server is online.

## Security
- Never commit `.env` or `data/session`
- Use HTTPS in production
- Rotate `API_TOKEN` if leaked
- Logout from the app to revoke the linked device
