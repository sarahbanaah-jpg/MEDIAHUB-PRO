import makeWASocket, {
  DisconnectReason,
  downloadMediaMessage,
  fetchLatestBaileysVersion,
  useMultiFileAuthState
} from '@whiskeysockets/baileys'
import qrcode from 'qrcode'
import pino from 'pino'
import { Boom } from '@hapi/boom'
import fs from 'fs'
import path from 'path'
import { runtime } from './config.js'
import { handleCommand, matchAutoReply } from './commands.js'

const logger = pino({ level: 'silent' })

export function createBotController(io) {
  let sock = null
  let status = 'disconnected'
  let qrDataUrl = null
  let account = null
  let starting = false
  const stats = { received: 0, sent: 0, conversations: new Set() }
  const recent = []
  const processed = new Set()
  let cfg = runtime.loadConfig()

  function pushLog(line) {
    const entry = { t: Date.now(), line: String(line) }
    recent.unshift(entry)
    if (recent.length > 80) recent.pop()
    io.emit('bot:log', entry)
  }

  function broadcast() {
    io.emit('bot:status', getPublicState())
  }

  function getPublicState() {
    return {
      status,
      account,
      botEnabled: !!cfg.enabled,
      qr: status === 'connecting' || status === 'qr' ? qrDataUrl : null,
      stats: {
        received: stats.received,
        sent: stats.sent,
        activeConversations: stats.conversations.size
      },
      config: {
        botName: cfg.botName,
        enableGroups: cfg.enableGroups,
        enablePrivate: cfg.enablePrivate,
        messageDelayMs: cfg.messageDelayMs,
        ownerOnly: cfg.ownerOnly,
        commands: cfg.commands,
        customCommands: cfg.customCommands,
        autoReplies: cfg.autoReplies,
        welcomeMessage: cfg.welcomeMessage,
        awayMessage: cfg.awayMessage,
        ownerName: cfg.ownerName
      },
      logs: recent.slice(0, 30)
    }
  }

  async function sendText(jid, text) {
    if (!sock || !cfg.enabled) return
    const delay = Math.max(0, Number(cfg.messageDelayMs || 0))
    if (delay) await new Promise(r => setTimeout(r, delay))
    await sock.sendMessage(jid, { text: String(text) })
    stats.sent += 1
    broadcast()
  }

  async function start() {
    if (starting) return
    starting = true
    cfg = runtime.loadConfig()
    try {
      const { state, saveCreds } = await useMultiFileAuthState(runtime.sessionDir)
      const { version } = await fetchLatestBaileysVersion()
      status = 'connecting'
      qrDataUrl = null
      broadcast()
      pushLog('Starting WhatsApp connection…')

      sock = makeWASocket({
        version,
        auth: state,
        logger,
        printQRInTerminal: false,
        syncFullHistory: false,
        markOnlineOnConnect: false
      })

      sock.ev.on('creds.update', saveCreds)

      sock.ev.on('connection.update', async (update) => {
        const { connection, lastDisconnect, qr } = update
        if (qr) {
          status = 'qr'
          qrDataUrl = await qrcode.toDataURL(qr, { margin: 1, width: 320 })
          pushLog('QR code ready — scan with WhatsApp → Linked Devices')
          broadcast()
        }
        if (connection === 'open') {
          status = 'connected'
          qrDataUrl = null
          const id = sock.user?.id || null
          account = {
            id,
            jid: id,
            name: sock.user?.name || cfg.botName
          }
          pushLog(`Connected as ${account.name || account.id}`)
          broadcast()
        }
        if (connection === 'close') {
          const code = new Boom(lastDisconnect?.error)?.output?.statusCode
          const loggedOut = code === DisconnectReason.loggedOut
          status = loggedOut ? 'disconnected' : 'reconnecting'
          account = loggedOut ? null : account
          qrDataUrl = null
          pushLog(loggedOut ? 'Logged out' : `Connection closed (${code}). Reconnecting…`)
          broadcast()
          sock = null
          starting = false
          if (!loggedOut) {
            setTimeout(() => { start().catch(() => {}) }, 2500)
          }
        }
      })

      sock.ev.on('messages.upsert', async ({ messages, type }) => {
        if (type !== 'notify' || !cfg.enabled) return
        for (const msg of messages) {
          try {
            await onMessage(msg)
          } catch (e) {
            pushLog(`Message error: ${e.message || e}`)
          }
        }
      })
    } catch (e) {
      status = 'disconnected'
      pushLog(`Start failed: ${e.message || e}`)
      broadcast()
    } finally {
      starting = false
    }
  }

  async function onMessage(msg) {
    if (!msg.message || msg.key.fromMe) return
    const id = msg.key.id
    if (id && processed.has(id)) return
    if (id) {
      processed.add(id)
      if (processed.size > 2000) processed.clear()
    }

    const remoteJid = msg.key.remoteJid
    if (!remoteJid || remoteJid === 'status@broadcast') return
    const isGroup = remoteJid.endsWith('@g.us')
    if (isGroup && !cfg.enableGroups) return
    if (!isGroup && !cfg.enablePrivate) return

    if (cfg.ownerOnly && cfg.ownerJid) {
      const sender = msg.key.participant || remoteJid
      if (!String(sender).includes(String(cfg.ownerJid).split('@')[0])) return
    }

    stats.received += 1
    stats.conversations.add(remoteJid)

    const text =
      msg.message.conversation ||
      msg.message.extendedTextMessage?.text ||
      msg.message.imageMessage?.caption ||
      msg.message.videoMessage?.caption ||
      ''

    pushLog(`MSG ${isGroup ? 'group' : 'dm'}: ${(text || '[media]').slice(0, 80)}`)
    broadcast()

    // Sticker from replied image
    if (String(text).trim().toLowerCase() === '/sticker' && cfg.commands?.sticker !== false) {
      const ctx = msg.message.extendedTextMessage?.contextInfo
      const quoted = ctx?.quotedMessage
      if (quoted?.imageMessage) {
        try {
          const buffer = await downloadMediaMessage(
            { message: quoted },
            'buffer',
            {},
            { logger, reuploadRequest: sock.updateMediaMessage }
          )
          await sock.sendMessage(remoteJid, {
            sticker: buffer
          })
          stats.sent += 1
          broadcast()
          return
        } catch (e) {
          await sendText(remoteJid, 'Could not create sticker from that image.')
          return
        }
      }
    }

    if (text && text.trim().startsWith('/')) {
      const reply = await handleCommand(text, cfg, sock, remoteJid)
      if (reply) await sendText(remoteJid, reply)
      return
    }

    const auto = matchAutoReply(text, cfg)
    if (auto) {
      await sendText(remoteJid, auto)
      return
    }
  }

  async function logout() {
    try {
      if (sock) await sock.logout()
    } catch {}
    try {
      fs.rmSync(runtime.sessionDir, { recursive: true, force: true })
      fs.mkdirSync(runtime.sessionDir, { recursive: true })
    } catch {}
    sock = null
    status = 'disconnected'
    account = null
    qrDataUrl = null
    pushLog('Session deleted')
    broadcast()
  }

  function updateConfig(patch) {
    cfg = { ...cfg, ...patch }
    runtime.saveConfig(cfg)
    broadcast()
    return cfg
  }

  return {
    start,
    logout,
    reconnect: async () => {
      try { if (sock) sock.end(undefined) } catch {}
      sock = null
      starting = false
      await start()
    },
    getPublicState,
    updateConfig,
    setEnabled(on) {
      cfg.enabled = !!on
      runtime.saveConfig(cfg)
      pushLog(cfg.enabled ? 'Bot enabled' : 'Bot disabled')
      broadcast()
    }
  }
}
