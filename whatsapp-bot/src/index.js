import express from 'express'
import cors from 'cors'
import rateLimit from 'express-rate-limit'
import { createServer } from 'http'
import { Server } from 'socket.io'
import { runtime } from './config.js'
import { createBotController } from './bot.js'

const app = express()
const server = createServer(app)
const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] }
})

const bot = createBotController(io)

app.use(cors())
app.use(express.json({ limit: '1mb' }))
app.use(rateLimit({ windowMs: 60_000, max: 120 }))

function auth(req, res, next) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : (req.query.token || '')
  if (!runtime.apiToken || token !== runtime.apiToken) {
    return res.status(401).json({ error: 'Unauthorized' })
  }
  next()
}

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'mediahub-whatsapp-bot', time: Date.now() })
})

app.get('/api/status', auth, (_req, res) => {
  res.json(bot.getPublicState())
})

app.post('/api/start', auth, async (_req, res) => {
  await bot.start()
  res.json(bot.getPublicState())
})

app.post('/api/reconnect', auth, async (_req, res) => {
  await bot.reconnect()
  res.json(bot.getPublicState())
})

app.post('/api/logout', auth, async (_req, res) => {
  await bot.logout()
  res.json(bot.getPublicState())
})

app.post('/api/enabled', auth, (req, res) => {
  bot.setEnabled(!!req.body?.enabled)
  res.json(bot.getPublicState())
})

app.post('/api/config', auth, (req, res) => {
  const patch = req.body || {}
  // never accept secrets from client blindly beyond known fields
  const allowed = [
    'botName', 'ownerName', 'ownerJid', 'enableGroups', 'enablePrivate',
    'messageDelayMs', 'ownerOnly', 'commands', 'customCommands', 'autoReplies',
    'welcomeMessage', 'awayMessage', 'enabled'
  ]
  const clean = {}
  for (const k of allowed) if (k in patch) clean[k] = patch[k]
  bot.updateConfig(clean)
  res.json(bot.getPublicState())
})

io.use((socket, next) => {
  const token = socket.handshake.auth?.token || socket.handshake.query?.token
  if (token !== runtime.apiToken) return next(new Error('Unauthorized'))
  next()
})

io.on('connection', (socket) => {
  socket.emit('bot:status', bot.getPublicState())
})

server.listen(runtime.port, () => {
  console.log(`MediaHub WhatsApp bot API on :${runtime.port}`)
  bot.start().catch((e) => console.error('bot start', e))
})
