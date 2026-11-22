import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.join(__dirname, '..')
const dataDir = path.join(root, 'data')
const sessionDir = path.join(dataDir, 'session')
const configPath = path.join(dataDir, 'bot-config.json')

if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true })
if (!fs.existsSync(sessionDir)) fs.mkdirSync(sessionDir, { recursive: true })

const defaults = {
  botName: process.env.BOT_NAME || 'MediaHub Bot',
  ownerName: process.env.OWNER_NAME || 'Owner',
  ownerJid: process.env.OWNER_JID || '',
  enabled: true,
  enableGroups: process.env.ENABLE_GROUPS !== 'false',
  enablePrivate: process.env.ENABLE_PRIVATE !== 'false',
  messageDelayMs: Number(process.env.MESSAGE_DELAY_MS || 800),
  welcomeMessage: process.env.WELCOME_MESSAGE || 'Hello! I am MediaHub Bot. Type /menu for commands.',
  awayMessage: process.env.AWAY_MESSAGE || '',
  ownerOnly: false,
  commands: {
    menu: true, help: true, ping: true, time: true, date: true, info: true,
    owner: true, alive: true, sticker: true, quote: true, joke: true,
    weather: true, news: true, translate: true, calculate: true
  },
  customCommands: {},
  autoReplies: [
    { keyword: 'hi', reply: 'Hello! Type /menu to see what I can do.' },
    { keyword: 'hello', reply: 'Hi there! Type /menu for commands.' }
  ]
}

function loadConfig() {
  try {
    if (fs.existsSync(configPath)) {
      return { ...defaults, ...JSON.parse(fs.readFileSync(configPath, 'utf8')) }
    }
  } catch {}
  return { ...defaults }
}

function saveConfig(cfg) {
  fs.writeFileSync(configPath, JSON.stringify(cfg, null, 2))
}

export const runtime = {
  port: Number(process.env.PORT || 8787),
  apiToken: process.env.API_TOKEN || 'change-me-to-a-long-random-secret',
  sessionDir,
  dataDir,
  configPath,
  loadConfig,
  saveConfig,
  defaults
}
