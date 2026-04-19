<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { unlockVault, getPasswords, addPassword, updatePassword, deletePassword, type PasswordEntry } from './services/api'

// State
const isAuthenticated = ref(false)
const masterPassword = ref('')
const showMasterPassword = ref(false)
const isLoading = ref(false)
const error = ref('')
const searchQuery = ref('')
const entries = ref<PasswordEntry[]>([])
const visiblePasswords = ref<Set<string>>(new Set()) // используем service вместо id
const copiedId = ref<string | null>(null)

// Modal state
const showModal = ref(false)
const modalMode = ref<'add' | 'edit'>('add')
const editingService = ref<string | null>(null)
const formData = ref({ service: '', login: '', password: '' })
const showFormPassword = ref(false)

// Particles (оставляем анимацию, она не мешает)
const particles = ref<any[]>([])
const mousePos = ref({ x: 0, y: 0 })
let animationFrame: number

// Filtered entries
const filteredEntries = computed(() => {
  if (!searchQuery.value) return entries.value
  const q = searchQuery.value.toLowerCase()
  return entries.value.filter(e => 
    e.service.toLowerCase().includes(q) || e.login.toLowerCase().includes(q)
  )
})

const stats = computed(() => ({
  total: entries.value.length,
  services: new Set(entries.value.map(e => e.service)).size
}))

// Auth
const handleLogin = async () => {
  if (!masterPassword.value) return
  isLoading.value = true
  error.value = ''
  try {
    await unlockVault(masterPassword.value)
    isAuthenticated.value = true
    await loadPasswords()
  } catch (err: any) {
    error.value = err.response?.status === 401 ? 'Неверный мастер-ключ' : 'Ошибка подключения к серверу'
  } finally {
    isLoading.value = false
  }
}

const loadPasswords = async () => {
  if (!masterPassword.value) return
  try {
    const { data } = await getPasswords(masterPassword.value)
    // Добавляем локальный ID для Vue key, так как бэкенд его не возвращает
    entries.value = data.map((item, i) => ({ ...item, id: `entry-${i}` }))
  } catch {
    error.value = 'Не удалось загрузить пароли'
  }
}

const handleLogout = () => {
  isAuthenticated.value = false
  masterPassword.value = ''
  entries.value = []
  visiblePasswords.value.clear()
  error.value = ''
}

// Clipboard & Visibility
const togglePasswordVisibility = (service: string) => {
  const s = new Set(visiblePasswords.value)
  s.has(service) ? s.delete(service) : s.add(service)
  visiblePasswords.value = s
}

const copyToClipboard = async (text: string, id: string) => {
  await navigator.clipboard.writeText(text)
  copiedId.value = id
  setTimeout(() => { copiedId.value = null }, 2000)
}

// Modal & CRUD
const openAddModal = () => {
  modalMode.value = 'add'
  editingService.value = null
  formData.value = { service: '', login: '', password: '' }
  showModal.value = true
}

const openEditModal = (entry: PasswordEntry) => {
  modalMode.value = 'edit'
  editingService.value = entry.service
  formData.value = { service: entry.service, login: entry.login, password: entry.password }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  showFormPassword.value = false
}

const saveEntry = async () => {
  if (!formData.value.service || !formData.value.login || !formData.value.password) return
  isLoading.value = true
  try {
    if (modalMode.value === 'add') {
      await addPassword(masterPassword.value, formData.value)
    } else if (editingService.value) {
      await updatePassword(masterPassword.value, editingService.value, formData.value)
    }
    await loadPasswords()
    closeModal()
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Ошибка сохранения'
  } finally {
    isLoading.value = false
  }
}

const deleteEntry = async (service: string) => {
  if (!confirm(`Удалить запись для ${service}?`)) return
  try {
    await deletePassword(masterPassword.value, service)
    await loadPasswords()
  } catch {
    error.value = 'Ошибка удаления'
  }
}

// Password generator
const generatePassword = () => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*'
  formData.value.password = Array.from({ length: 20 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

const getServiceColor = (service: string) => { /* твой код цветов оставь без изменений */
  const colors: Record<string, string> = {
    'Google': 'from-red-500/20 to-yellow-500/20 border-red-500/30',
    'GitHub': 'from-gray-500/20 to-gray-700/20 border-gray-500/30',
    'Netflix': 'from-red-600/20 to-red-800/20 border-red-600/30',
    'Spotify': 'from-green-500/20 to-green-700/20 border-green-500/30',
    'Amazon': 'from-orange-500/20 to-orange-700/20 border-orange-500/30',
    'Discord': 'from-indigo-500/20 to-indigo-700/20 border-indigo-500/30',
  }
  return colors[service] || 'from-primary/20 to-primary/5 border-primary/30'
}

const getPasswordStrength = (password: string) => { /* твой код оставь без изменений */
  let strength = 0
  if (password.length >= 8) strength++
  if (password.length >= 12) strength++
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) strength++
  if (/\d/.test(password)) strength++
  if (/[!@#$%^&*]/.test(password)) strength++
  return strength
}

// Particles init (оставь как было)
const initParticles = () => {
  const count = 50
  particles.value = Array.from({ length: count }, (_, i) => ({
    id: i, x: Math.random() * window.innerWidth, y: Math.random() * window.innerHeight,
    size: Math.random() * 3 + 1, speedX: (Math.random() - 0.5) * 0.5,
    speedY: (Math.random() - 0.5) * 0.5, opacity: Math.random() * 0.5 + 0.1
  }))
}
const animateParticles = () => {
  particles.value.forEach(p => {
    p.x += p.speedX; p.y += p.speedY
    if (p.x < 0) p.x = window.innerWidth; if (p.x > window.innerWidth) p.x = 0
    if (p.y < 0) p.y = window.innerHeight; if (p.y > window.innerHeight) p.y = 0
  })
  animationFrame = requestAnimationFrame(animateParticles)
}
const handleMouseMove = (e: MouseEvent) => { mousePos.value = { x: e.clientX, y: e.clientY } }

// Lifecycle
onMounted(() => {
  initParticles()
  animateParticles()
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('session-expired', handleLogout)
})
onUnmounted(() => {
  cancelAnimationFrame(animationFrame)
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('session-expired', handleLogout)
})
</script>

<template>
  <div class="min-h-screen relative overflow-hidden">
    <!-- Background Image with Overlay -->
    <div class="fixed inset-0 z-0">
      <img 
        src="/cyber-bg.jpg" 
        alt="" 
        class="w-full h-full object-cover"
      />
      <div class="absolute inset-0 bg-gradient-to-br from-background/95 via-background/90 to-background/80"></div>
      <div class="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/10 via-transparent to-transparent"></div>
    </div>

    <!-- Animated Particles -->
    <div class="fixed inset-0 z-0 pointer-events-none overflow-hidden">
      <div
        v-for="particle in particles"
        :key="particle.id"
        class="absolute rounded-full bg-primary/50"
        :style="{
          left: `${particle.x}px`,
          top: `${particle.y}px`,
          width: `${particle.size}px`,
          height: `${particle.size}px`,
          opacity: particle.opacity,
        }"
      ></div>
    </div>

    <!-- Mouse Glow Effect -->
    <div 
      class="fixed w-96 h-96 rounded-full pointer-events-none z-0 transition-all duration-300"
      :style="{
        left: `${mousePos.x - 192}px`,
        top: `${mousePos.y - 192}px`,
        background: 'radial-gradient(circle, rgba(16,185,129,0.08) 0%, transparent 70%)',
      }"
    ></div>

    <!-- Grid Pattern Overlay -->
    <div class="fixed inset-0 z-0 opacity-[0.02]" style="background-image: url('data:image/svg+xml,%3Csvg width=&quot;60&quot; height=&quot;60&quot; viewBox=&quot;0 0 60 60&quot; xmlns=&quot;http://www.w3.org/2000/svg&quot;%3E%3Cg fill=&quot;none&quot; fill-rule=&quot;evenodd&quot;%3E%3Cg fill=&quot;%2310b981&quot; fill-opacity=&quot;0.4&quot;%3E%3Cpath d=&quot;M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z&quot;/%3E%3C/g%3E%3C/g%3E%3C/svg%3E');"></div>

    <!-- Login Screen -->
    <div v-if="!isAuthenticated" class="relative z-10 min-h-screen flex items-center justify-center p-4">
      <div class="w-full max-w-lg animate-fade-in">
        <!-- Decorative Elements -->
        <div class="absolute -top-20 -left-20 w-72 h-72 bg-primary/20 rounded-full blur-3xl"></div>
        <div class="absolute -bottom-20 -right-20 w-72 h-72 bg-cyan-500/20 rounded-full blur-3xl"></div>

        <!-- Logo & Title -->
        <div class="text-center mb-10 relative">
          <div class="inline-flex items-center justify-center w-24 h-24 rounded-3xl bg-gradient-to-br from-primary/30 to-cyan-500/20 border border-primary/30 mb-8 glow animate-float">
            <svg class="w-12 h-12 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </div>
          <h1 class="text-4xl md:text-5xl font-bold mb-4 bg-gradient-to-r from-white via-white to-primary/80 bg-clip-text text-transparent">
            Менеджер паролей
          </h1>
          <p class="text-lg text-muted-foreground">Безопасное хранение учётных данных</p>
        </div>

        <!-- Login Card -->
        <div class="relative glass-card rounded-3xl p-8 shadow-2xl border border-white/10">
          <!-- Card Glow -->
          <div class="absolute -inset-1 bg-gradient-to-r from-primary/20 via-cyan-500/20 to-primary/20 rounded-3xl blur-xl opacity-50"></div>
          
          <div class="relative">
            <form @submit.prevent="handleLogin" class="space-y-6">
              <div>
                <label class="block text-sm font-medium text-foreground mb-3">Мастер-пароль</label>
                <div class="relative group">
                  <div class="absolute -inset-0.5 bg-gradient-to-r from-primary/50 to-cyan-500/50 rounded-2xl blur opacity-0 group-focus-within:opacity-100 transition duration-500"></div>
                  <div class="relative">
                    <input
                      v-model="masterPassword"
                      :type="showMasterPassword ? 'text' : 'password'"
                      placeholder="Введите мастер-пароль"
                      class="w-full px-5 py-4 bg-white/5 border border-white/10 rounded-2xl text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 transition-all text-lg"
                    />
                    <button
                      type="button"
                      @click="showMasterPassword = !showMasterPassword"
                      class="absolute right-4 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors p-1"
                    >
                      <svg v-if="showMasterPassword" class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                      </svg>
                      <svg v-else class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>

              <button
                type="submit"
                :disabled="!masterPassword || isLoading"
                class="w-full py-4 px-6 bg-gradient-to-r from-primary to-emerald-600 hover:from-primary/90 hover:to-emerald-600/90 disabled:from-primary/50 disabled:to-emerald-600/50 disabled:cursor-not-allowed text-white font-semibold rounded-2xl transition-all duration-300 flex items-center justify-center gap-3 text-lg shadow-lg shadow-primary/25 hover:shadow-xl hover:shadow-primary/30 hover:-translate-y-0.5"
              >
                <svg v-if="isLoading" class="w-6 h-6 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <svg v-else class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 11V7a4 4 0 118 0m-4 8v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2z" />
                </svg>
                <span>{{ isLoading ? 'Расшифровка...' : 'Разблокировать' }}</span>
              </button>
            </form>

            <!-- Security Info -->
            <div class="mt-8 pt-6 border-t border-white/10">
              <div class="grid grid-cols-3 gap-4">
                <div class="text-center">
                  <div class="w-10 h-10 mx-auto mb-2 rounded-xl bg-primary/10 flex items-center justify-center">
                    <svg class="w-5 h-5 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                    </svg>
                  </div>
                  <p class="text-xs text-muted-foreground">AES-256</p>
                </div>
                <div class="text-center">
                  <div class="w-10 h-10 mx-auto mb-2 rounded-xl bg-cyan-500/10 flex items-center justify-center">
                    <svg class="w-5 h-5 text-cyan-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                    </svg>
                  </div>
                  <p class="text-xs text-muted-foreground">Zero-knowledge</p>
                </div>
                <div class="text-center">
                  <div class="w-10 h-10 mx-auto mb-2 rounded-xl bg-violet-500/10 flex items-center justify-center">
                    <svg class="w-5 h-5 text-violet-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01" />
                    </svg>
                  </div>
                  <p class="text-xs text-muted-foreground">Локально</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Dashboard -->
    <div v-else class="relative z-10 min-h-screen">
      <!-- Header -->
      <header class="sticky top-0 z-40 glass-card border-b border-white/10">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div class="flex items-center justify-between h-20">
            <div class="flex items-center gap-4">
              <div class="w-12 h-12 rounded-2xl bg-gradient-to-br from-primary/30 to-cyan-500/20 border border-primary/30 flex items-center justify-center glow-sm">
                <svg class="w-6 h-6 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
              </div>
              <div>
                <span class="text-xl font-bold text-foreground">SecureVault</span>
                <p class="text-xs text-muted-foreground">Менеджер паролей</p>
              </div>
            </div>
            <button
              @click="handleLogout"
              class="flex items-center gap-2 px-5 py-2.5 text-sm text-muted-foreground hover:text-foreground glass-card hover:border-red-500/30 rounded-xl transition-all group"
            >
              <svg class="w-5 h-5 group-hover:text-red-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
              <span>Заблокировать</span>
            </button>
          </div>
        </div>
      </header>

      <!-- Main Content -->
      <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
        <!-- Stats Cards -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-10">
          <div class="col-span-1 md:col-span-2 glass-card rounded-3xl p-6 border border-white/10 animate-slide-up group hover:border-primary/30 transition-all">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-muted-foreground mb-1">Всего записей</p>
                <p class="text-5xl font-bold bg-gradient-to-r from-primary to-cyan-500 bg-clip-text text-transparent">{{ stats.total }}</p>
              </div>
              <div class="w-20 h-20 rounded-2xl bg-gradient-to-br from-primary/20 to-cyan-500/20 flex items-center justify-center group-hover:scale-110 transition-transform">
                <svg class="w-10 h-10 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
                </svg>
              </div>
            </div>
          </div>
          
          <div class="glass-card rounded-3xl p-6 border border-white/10 animate-slide-up group hover:border-cyan-500/30 transition-all" style="animation-delay: 0.1s;">
            <div class="flex flex-col h-full justify-between">
              <div class="w-12 h-12 rounded-xl bg-cyan-500/10 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <svg class="w-6 h-6 text-cyan-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9" />
                </svg>
              </div>
              <div>
                <p class="text-3xl font-bold text-foreground">{{ stats.services }}</p>
                <p class="text-sm text-muted-foreground">Сервисов</p>
              </div>
            </div>
          </div>
          
          <div class="glass-card rounded-3xl p-6 border border-white/10 animate-slide-up group hover:border-amber-500/30 transition-all" style="animation-delay: 0.2s;">
            <div class="flex flex-col h-full justify-between">
              <div class="w-12 h-12 rounded-xl bg-amber-500/10 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <svg class="w-6 h-6 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
              </div>
              <div>
                <p class="text-xl font-bold text-foreground">AES-256</p>
                <p class="text-sm text-muted-foreground">Шифрование</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Search & Add -->
        <div class="flex flex-col sm:flex-row gap-4 mb-8 animate-slide-up" style="animation-delay: 0.3s;">
          <div class="relative flex-1 group">
            <div class="absolute -inset-0.5 bg-gradient-to-r from-primary/30 to-cyan-500/30 rounded-2xl blur opacity-0 group-focus-within:opacity-100 transition duration-500"></div>
            <div class="relative flex items-center">
              <svg class="absolute left-5 w-5 h-5 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <input
                v-model="searchQuery"
                type="text"
                placeholder="Поиск по сервису или логину..."
                class="w-full pl-14 pr-5 py-4 glass-card border border-white/10 rounded-2xl text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 transition-all"
              />
            </div>
          </div>
          <button
            @click="openAddModal"
            class="flex items-center justify-center gap-3 px-8 py-4 bg-gradient-to-r from-primary to-emerald-600 hover:from-primary/90 hover:to-emerald-600/90 text-white font-semibold rounded-2xl transition-all duration-300 whitespace-nowrap shadow-lg shadow-primary/25 hover:shadow-xl hover:shadow-primary/30 hover:-translate-y-0.5"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            <span>Добавить запись</span>
          </button>
        </div>

        <!-- Password Cards Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-slide-up" style="animation-delay: 0.4s;">
          <!-- Empty State -->
          <div v-if="filteredEntries.length === 0" class="col-span-full glass-card rounded-3xl p-16 text-center border border-white/10">
            <div class="w-20 h-20 mx-auto mb-6 rounded-2xl bg-muted/50 flex items-center justify-center">
              <svg class="w-10 h-10 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
              </svg>
            </div>
            <p class="text-xl text-muted-foreground mb-2">Записи не найдены</p>
            <p class="text-sm text-muted-foreground/60">Попробуйте изменить поисковый запрос</p>
          </div>

          <!-- Password Cards -->
          <div
            v-for="(entry, index) in filteredEntries"
            :key="entry.id"
            class="glass-card rounded-3xl p-6 border border-white/10 hover:border-primary/30 transition-all group"
            :style="{ animationDelay: `${0.4 + index * 0.05}s` }"
          >
            <!-- Card Header -->
            <div class="flex items-start justify-between mb-5">
              <div class="flex items-center gap-4">
                <div :class="['w-14 h-14 rounded-2xl bg-gradient-to-br flex items-center justify-center border', getServiceColor(entry.service)]">
                  <span class="text-lg font-bold text-foreground">{{ entry.service.substring(0, 2).toUpperCase() }}</span>
                </div>
                <div>
                  <h3 class="font-semibold text-lg text-foreground">{{ entry.service }}</h3>
                  <p class="text-sm text-muted-foreground truncate max-w-[150px]">{{ entry.login }}</p>
                </div>
              </div>
              <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button
                  @click="openEditModal(entry)"
                  class="p-2 text-muted-foreground hover:text-primary hover:bg-primary/10 rounded-xl transition-all"
                  title="Редактировать"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button
                  @click="deleteEntry(entry.service)"
                  class="p-2 text-muted-foreground hover:text-red-500 hover:bg-red-500/10 rounded-xl transition-all"
                  title="Удалить"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- Login Field -->
            <div class="mb-4">
              <label class="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-2 block">Логин</label>
              <div class="flex items-center justify-between bg-white/5 rounded-xl px-4 py-3 border border-white/5">
                <span class="text-sm text-foreground truncate mr-2">{{ entry.login }}</span>
                <button
                  @click="copyToClipboard(entry.login, `login-${entry.id}`)"
                  class="shrink-0 p-1.5 text-muted-foreground hover:text-primary transition-colors"
                  title="Копировать логин"
                >
                  <svg v-if="copiedId === `login-${entry.id}`" class="w-4 h-4 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                  </svg>
                  <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- Password Field -->
            <div>
              <label class="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-2 block">Пароль</label>
              <div class="flex items-center justify-between bg-white/5 rounded-xl px-4 py-3 border border-white/5">
                <code class="font-mono text-sm text-foreground truncate mr-2">
                  {{ visiblePasswords.has(entry.service) ? entry.password : '••••••••••••' }}
                </code>
                <div class="flex items-center gap-1 shrink-0">
                  <button
                    @click="togglePasswordVisibility(entry.service)"
                    class="p-1.5 text-muted-foreground hover:text-primary transition-colors"
                    :title="visiblePasswords.has(entry.service) ? 'Скрыть' : 'Показать'"
                  >
                    <svg v-if="visiblePasswords.has(entry.service)" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                    </svg>
                    <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                    </svg>
                  </button>
                  <button
                    @click="copyToClipboard(entry.password, `pass-${entry.id}`)"
                    class="p-1.5 text-muted-foreground hover:text-primary transition-colors"
                    title="Копировать пароль"
                  >
                    <svg v-if="copiedId === `pass-${entry.id}`" class="w-4 h-4 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                    </svg>
                    <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                    </svg>
                  </button>
                </div>
              </div>
              <!-- Password Strength -->
              <div class="flex gap-1 mt-3">
                <div 
                  v-for="i in 5" 
                  :key="i"
                  :class="[
                    'h-1 flex-1 rounded-full transition-colors',
                    i <= getPasswordStrength(entry.password) 
                      ? (getPasswordStrength(entry.password) >= 4 ? 'bg-primary' : getPasswordStrength(entry.password) >= 2 ? 'bg-amber-500' : 'bg-red-500')
                      : 'bg-white/10'
                  ]"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- Modal -->
    <Teleport to="body">
      <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-black/70 backdrop-blur-md" @click="closeModal"></div>

        <!-- Modal Content -->
        <div class="relative w-full max-w-lg glass-card border border-white/10 rounded-3xl shadow-2xl animate-scale-in overflow-hidden">
          <!-- Glow Effect -->
          <div class="absolute -top-20 -right-20 w-60 h-60 bg-primary/30 rounded-full blur-3xl"></div>
          <div class="absolute -bottom-20 -left-20 w-60 h-60 bg-cyan-500/30 rounded-full blur-3xl"></div>
          
          <div class="relative p-8">
            <div class="flex items-center justify-between mb-8">
              <h2 class="text-2xl font-bold text-foreground">
                {{ modalMode === 'add' ? 'Новая запись' : 'Редактировать' }}
              </h2>
              <button
                @click="closeModal"
                class="p-2 text-muted-foreground hover:text-foreground hover:bg-white/10 rounded-xl transition-all"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form @submit.prevent="saveEntry" class="space-y-5">
              <!-- Service -->
              <div>
                <label class="block text-sm font-medium text-foreground mb-2">Сервис</label>
                <input
                  v-model="formData.service"
                  type="text"
                  placeholder="Google, GitHub, Netflix..."
                  class="w-full px-5 py-4 bg-white/5 border border-white/10 rounded-2xl text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 transition-all"
                />
              </div>

              <!-- Login -->
              <div>
                <label class="block text-sm font-medium text-foreground mb-2">Логин / Email</label>
                <input
                  v-model="formData.login"
                  type="text"
                  placeholder="user@example.com"
                  class="w-full px-5 py-4 bg-white/5 border border-white/10 rounded-2xl text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 transition-all"
                />
              </div>

              <!-- Password -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <label class="block text-sm font-medium text-foreground">Пароль</label>
                  <button
                    type="button"
                    @click="generatePassword"
                    class="flex items-center gap-1.5 text-sm text-primary hover:text-primary/80 transition-colors"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    Сгенерировать
                  </button>
                </div>
                <div class="relative">
                  <input
                    v-model="formData.password"
                    :type="showFormPassword ? 'text' : 'password'"
                    placeholder="Надёжный пароль"
                    class="w-full px-5 py-4 bg-white/5 border border-white/10 rounded-2xl text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 transition-all pr-14 font-mono"
                  />
                  <button
                    type="button"
                    @click="showFormPassword = !showFormPassword"
                    class="absolute right-4 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors p-1"
                  >
                    <svg v-if="showFormPassword" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                    </svg>
                    <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                    </svg>
                  </button>
                </div>
                <!-- Password Strength Indicator -->
                <div v-if="formData.password" class="mt-3">
                  <div class="flex gap-1 mb-2">
                    <div 
                      v-for="i in 5" 
                      :key="i"
                      :class="[
                        'h-1.5 flex-1 rounded-full transition-colors',
                        i <= getPasswordStrength(formData.password) 
                          ? (getPasswordStrength(formData.password) >= 4 ? 'bg-primary' : getPasswordStrength(formData.password) >= 2 ? 'bg-amber-500' : 'bg-red-500')
                          : 'bg-white/10'
                      ]"
                    ></div>
                  </div>
                  <p class="text-xs text-muted-foreground">
                    {{ getPasswordStrength(formData.password) >= 4 ? 'Надёжный пароль' : getPasswordStrength(formData.password) >= 2 ? 'Средний пароль' : 'Слабый пароль' }}
                  </p>
                </div>
              </div>

              <!-- Buttons -->
              <div class="flex gap-4 pt-4">
                <button
                  type="submit"
                  class="flex-1 py-4 px-6 bg-gradient-to-r from-primary to-emerald-600 hover:from-primary/90 hover:to-emerald-600/90 text-white font-semibold rounded-2xl transition-all duration-300 shadow-lg shadow-primary/25"
                >
                  {{ modalMode === 'add' ? 'Добавить' : 'Сохранить' }}
                </button>
                <button
                  type="button"
                  @click="closeModal"
                  class="flex-1 py-4 px-6 bg-white/5 hover:bg-white/10 text-foreground font-semibold rounded-2xl transition-all duration-300 border border-white/10"
                >
                  Отмена
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
