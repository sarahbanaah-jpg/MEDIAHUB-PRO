const QUOTES = [
  'The only way to do great work is to love what you do. — Steve Jobs',
  'It always seems impossible until it is done. — Nelson Mandela',
  'Believe you can and you are halfway there. — Theodore Roosevelt',
  'Dream big and dare to fail. — Norman Vaughan',
  'Stay hungry. Stay foolish. — Steve Jobs'
]

const JOKES = [
  'Why do programmers prefer dark mode? Because light attracts bugs.',
  'There are 10 kinds of people: those who understand binary and those who do not.',
  'A SQL query walks into a bar, walks up to two tables and asks: Can I join you?',
  'Why did the developer go broke? Because he used up all his cache.'
]

function safeEvalMath(expr) {
  const cleaned = String(expr || '').replace(/[^0-9+\-*/().%\s]/g, '')
  if (!cleaned.trim()) throw new Error('empty')
  // eslint-disable-next-line no-new-func
  const result = Function(`"use strict"; return (${cleaned})`)()
  if (typeof result !== 'number' || !Number.isFinite(result)) throw new Error('invalid')
  return result
}

export async function handleCommand(text, cfg, sock, remoteJid) {
  const body = String(text || '').trim()
  if (!body.startsWith('/')) return null

  const [rawCmd, ...rest] = body.slice(1).split(/\s+/)
  const cmd = rawCmd.toLowerCase()
  const arg = rest.join(' ').trim()

  if (cfg.customCommands && cfg.customCommands[cmd]) {
    return String(cfg.customCommands[cmd])
  }

  if (cfg.commands && cfg.commands[cmd] === false) {
    return `Command /${cmd} is disabled by the bot owner.`
  }

  switch (cmd) {
    case 'menu':
    case 'help':
      return [
        `*${cfg.botName || 'MediaHub Bot'}*`,
        '',
        '/menu — this list',
        '/ping — latency check',
        '/time — current time',
        '/date — current date',
        '/info — bot info',
        '/owner — owner info',
        '/alive — status',
        '/quote — motivational quote',
        '/joke — random joke',
        '/weather <city> — weather',
        '/news — headlines (needs NEWS_API_KEY)',
        '/translate <lang> <text>',
        '/calculate <expression>',
        '/sticker — reply to an image'
      ].join('\n')
    case 'ping':
      return 'pong ✅'
    case 'time':
      return `Time: ${new Date().toLocaleTimeString()}`
    case 'date':
      return `Date: ${new Date().toLocaleDateString()}`
    case 'info':
      return `${cfg.botName || 'MediaHub Bot'}\nPowered by MediaHub Pro + Baileys\nOwner: ${cfg.ownerName || 'Owner'}`
    case 'owner':
      return `Owner: ${cfg.ownerName || 'Owner'}${cfg.ownerJid ? `\nJID: ${cfg.ownerJid}` : ''}`
    case 'alive':
      return `${cfg.botName || 'Bot'} is alive ✅`
    case 'quote':
      return QUOTES[Math.floor(Math.random() * QUOTES.length)]
    case 'joke':
      return JOKES[Math.floor(Math.random() * JOKES.length)]
    case 'calculate':
    case 'calc':
      try {
        return `Result: ${safeEvalMath(arg)}`
      } catch {
        return 'Usage: /calculate 12*(3+4)'
      }
    case 'weather': {
      const city = arg || 'Nairobi'
      try {
        const geo = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city)}&count=1`).then(r => r.json())
        const place = geo?.results?.[0]
        if (!place) return `Could not find city: ${city}`
        const w = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m`).then(r => r.json())
        const c = w.current
        return `Weather · ${place.name}${place.country ? ', ' + place.country : ''}\nTemp: ${c.temperature_2m}°C\nHumidity: ${c.relative_humidity_2m}%\nWind: ${c.wind_speed_10m} km/h`
      } catch {
        return 'Weather service unavailable right now.'
      }
    }
    case 'news': {
      const key = process.env.NEWS_API_KEY
      if (!key) return 'News requires NEWS_API_KEY on the server (.env).'
      try {
        const data = await fetch(`https://newsapi.org/v2/top-headlines?country=us&pageSize=5&apiKey=${key}`).then(r => r.json())
        const articles = data.articles || []
        if (!articles.length) return 'No headlines available.'
        return articles.map((a, i) => `${i + 1}. ${a.title}`).join('\n')
      } catch {
        return 'News service unavailable.'
      }
    }
    case 'translate': {
      // libretranslate-style public instances are unreliable; keep honest fallback
      if (!arg) return 'Usage: /translate es Hello world'
      const [lang, ...words] = arg.split(/\s+/)
      const phrase = words.join(' ')
      if (!lang || !phrase) return 'Usage: /translate es Hello world'
      try {
        const res = await fetch('https://libretranslate.com/translate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ q: phrase, source: 'auto', target: lang, format: 'text' })
        }).then(r => r.json())
        if (res.translatedText) return `${phrase} → (${lang}) ${res.translatedText}`
        return 'Translation unavailable. Try again later.'
      } catch {
        return 'Translation service unavailable.'
      }
    }
    case 'sticker':
      return 'Reply to an image with /sticker to convert it (image must be in the same chat).'
    default:
      return `Unknown command: /${cmd}\nType /menu for available commands.`
  }
}

export function matchAutoReply(text, cfg) {
  const lower = String(text || '').toLowerCase().trim()
  if (!lower || lower.startsWith('/')) return null
  const rules = cfg.autoReplies || []
  for (const rule of rules) {
    const kw = String(rule.keyword || '').toLowerCase().trim()
    if (kw && lower.includes(kw)) return rule.reply
  }
  return null
}
