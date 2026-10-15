<template>
  <div class="min-h-screen bg-ink text-slate-100 font-sans flex flex-col items-center px-4 py-10 touch-manipulation">
    <main class="w-full max-w-xl">
      <!-- Header -->
      <header class="text-center mb-6">
        <p class="font-mono text-xs tracking-widest text-accent mb-2">J2ME // MENU BUILDER</p>
        <h1 class="text-2xl font-bold">Ghép phiên bản game java</h1>
        <p class="text-mutetext text-sm mt-1">Upload 1 file game .jar → nhận lại bản ghép siêu nhanh</p>
      </header>

      <!-- Card -->
      <section class="bg-card border border-line rounded-2xl p-6 shadow-2xl">
        <!-- Dropzone -->
        <div role="button" tabindex="0" aria-label="Chọn file game jar" @click="browse" @keydown.enter="browse"
          @keydown.space.prevent="browse" @dragenter.prevent="dragging = true" @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false" @drop.prevent="onDrop" :class="[
            'border-2 border-dashed rounded-xl px-6 py-8 text-center cursor-pointer transition-colors duration-200',
            dragging ? 'border-accent bg-accent/10' : 'border-line hover:border-mutetext'
          ]">
          <svg class="mx-auto mb-3 h-10 w-10 text-mutetext" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            stroke-width="1.5" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5" />
          </svg>
          <p class="font-medium">Kéo-thả file .jar vào đây</p>
          <p class="text-sm text-mutetext mt-1">hoặc <span class="text-accent underline">bấm để chọn file</span></p>
          <input ref="fileInput" type="file" accept=".jar" class="hidden" @change="onPick" aria-hidden="true"
            tabindex="-1">
        </div>

        <!-- File chip -->
        <div v-if="file" class="mt-4 flex items-center gap-3 bg-mutedbox border border-line rounded-xl px-4 py-3">
          <svg class="h-6 w-6 shrink-0 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            stroke-width="1.5" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M20.25 7.5l-.625 12.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5m6 4.125l2.25 2.25m0 0l2.25 2.25M12 13.875l2.25-2.25M12 13.875l-2.25 2.25M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
          </svg>
          <div class="min-w-0 flex-1">
            <p class="font-mono text-sm truncate">{{ file.name }}</p>
            <p class="text-xs text-mutetext">{{ formatSize(file.size) }} · ra {{ outName }}</p>
          </div>
          <button type="button" @click="clear" aria-label="Bỏ file đã chọn"
            class="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-mutetext hover:text-danger transition-colors duration-200">
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Copies -->
        <div class="mt-4 flex items-center justify-between gap-4">
          <label for="copies" class="text-sm font-medium">Số bản cần ghép</label>
          <div class="flex items-center gap-2">
            <button type="button" @click="step(-1)" :disabled="copies <= 1" aria-label="Giảm copies"
              class="min-h-[44px] min-w-[44px] rounded-lg border border-line text-lg leading-none disabled:opacity-30 hover:border-accent transition-colors duration-200 touch-manipulation select-none">−</button>
            <input id="copies" v-model="copiesText" type="text" inputmode="numeric" autocomplete="off"
              spellcheck="false" @input="onCopiesInput" @blur="clampCopies" @keydown.enter="clampCopies"
              class="w-16 min-h-[44px] text-center text-base bg-mutedbox border border-line rounded-lg font-mono focus:border-accent">
            <button type="button" @click="step(1)" :disabled="copies >= 25" aria-label="Tăng copies"
              class="min-h-[44px] min-w-[44px] rounded-lg border border-line text-lg leading-none disabled:opacity-30 hover:border-accent transition-colors duration-200 touch-manipulation select-none">+</button>
          </div>
        </div>

        <!-- Merge button -->
        <button type="button" @click="merge" :disabled="!file || busy" class="mt-5 w-full min-h-[48px] rounded-xl bg-accent text-onaccent font-bold text-base
                 disabled:opacity-40 hover:brightness-110 active:brightness-95 transition-all duration-200
                 flex items-center justify-center gap-2 touch-manipulation">
          <svg v-if="busy" class="h-5 w-5 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
          {{ busy ? 'Đang ghép...' : 'Bắt đầu ghép' }}
        </button>

        <!-- Status -->
        <div aria-live="polite">
          <p v-if="hint" class="mt-3 text-sm text-mutetext font-mono break-words">{{ hint }}</p>
          <div v-if="error"
            class="mt-3 flex gap-2 bg-danger/10 border border-danger/50 text-red-200 rounded-xl px-4 py-3 text-sm">
            <svg class="h-5 w-5 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="1.5" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round"
                d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
            <span>{{ error }}</span>
          </div>
          <div v-if="done" class="mt-3 bg-accent/10 border border-accent/50 rounded-xl px-4 py-3 text-sm">
            <p class="font-medium text-accent font-mono break-all">Xong: {{ done }}</p>
            <button type="button" @click="downloadAgain"
              class="mt-2 min-h-[44px] px-4 rounded-lg border border-accent text-accent hover:bg-accent/10 transition-colors duration-200">
              Tải lại file
            </button>
          </div>
        </div>
      </section>

      <!-- Warning -->
      <aside class="mt-4 bg-warn/10 border border-warn/40 rounded-xl px-4 py-3 text-sm text-amber-200">
        <span class="font-medium">Lưu ý:</span> không phải file jar nào cũng ghép được —
        file obfuscate quá nặng, thiếu preverify, dị giáo hoặc bytecode sửa ẩu
        có thể lỗi trong quá trình chuyển phiên bản.
      </aside>

      <!-- Stats -->
      <section aria-label="Thống kê sử dụng" class="mt-4 grid grid-cols-3 gap-2">
        <div class="bg-card border border-line rounded-xl px-2 py-3 text-center">
          <span class="relative mx-auto mb-2 flex h-2 w-2">
            <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-accent opacity-60"></span>
            <span class="relative inline-flex rounded-full h-2 w-2 bg-accent"></span>
          </span>
          <p class="font-mono text-lg font-semibold leading-none">{{ stats.online }}</p>
          <p class="mt-1 text-[11px] text-mutetext">đang online</p>
        </div>
        <div class="bg-card border border-line rounded-xl px-2 py-3 text-center">
          <svg class="mx-auto mb-1.5 h-4 w-4 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            stroke-width="1.5" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
          <p class="font-mono text-lg font-semibold leading-none">{{ stats.visits }}</p>
          <p class="mt-1 text-[11px] text-mutetext">lượt truy cập</p>
        </div>
        <div class="bg-card border border-line rounded-xl px-2 py-3 text-center">
          <svg class="mx-auto mb-1.5 h-4 w-4 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            stroke-width="1.5" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 01-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9.06 9.06 0 011.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-8.876a9.06 9.06 0 00-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 01-1.125-1.125v-9.25" />
          </svg>
          <p class="font-mono text-lg font-semibold leading-none">{{ stats.merges }}</p>
          <p class="mt-1 text-[11px] text-mutetext">file đã ghép</p>
        </div>
      </section>

      <!-- Links -->
      <footer class="mt-2 bg-card border border-line rounded-xl px-4 py-3 text-center text-xs text-mutetext font-mono">
        <p>merge-only · upload ≤ 10MB · copies ≤ 25</p>
        <p class="mt-2 flex items-center justify-center gap-5">
          <a class="inline-flex items-center gap-1.5 hover:text-slate-300" href="/api/health">
            <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
            </svg>health</a>
          <a class="inline-flex items-center gap-1.5 hover:text-slate-300"
            href="https://github.com/0xFenix/tools.ezway.mobi" target="_blank" rel="noopener">
            <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <polyline points="16 18 22 12 16 6" />
              <polyline points="8 6 2 12 8 18" />
            </svg>mã nguồn</a>
        </p>
      </footer>

      <!-- Disclaimer -->
      <p class="mt-2 flex gap-2 text-xs text-mutetext">
        <svg class="h-4 w-4 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
          <polyline points="9 12 11 14 15 10" />
        </svg>
        <span>File upload chỉ xử lý trong RAM và xóa ngay sau khi ghép xong —
          chúng tôi không lưu trữ bất kỳ file nào của bạn. Bạn tự chịu trách nhiệm về bản quyền và nội dung file
          sử dụng.</span>
      </p>
    </main>
  </div>
</template>

<script>
const MAX_COPIES = 25

export default {
  name: 'App',
  data() {
    return {
      file: null,
      copies: 6,
      copiesText: '6',
      busy: false,
      hint: '',
      error: '',
      done: '',
      blobUrl: '',
      dragging: false,
      sid: '',
      stats: { visits: 0, online: 0, merges: 0 },
      statsTimer: 0
    }
  },
  mounted() {
    this.sid = this.getSid()
    this.ping()
    this.statsTimer = setInterval(this.ping, 30000)
  },
  beforeUnmount() {
    if (this.statsTimer) { clearInterval(this.statsTimer); this.statsTimer = 0 }
  },
  computed: {
    outName() {
      if (!this.file) return ''
      const base = this.file.name.replace(/\.jar$/i, '')
      return base + '-menu-x' + this.clampedCopies + '.jar'
    },
    clampedCopies() {
      const n = parseInt(this.copies, 10)
      if (isNaN(n)) return 6
      return Math.max(1, Math.min(MAX_COPIES, n))
    }
  },
  methods: {
    getSid() {
      try {
        let sid = localStorage.getItem('j2me-sid')
        if (!sid) {
          sid = (window.crypto && crypto.randomUUID)
            ? crypto.randomUUID()
            : (Date.now() + '-' + Math.random().toString(36).slice(2))
          localStorage.setItem('j2me-sid', sid)
        }
        return sid
      } catch (e) { return 'mem-' + Date.now() }
    },
    async ping() {
      try {
        const res = await fetch('/api/ping', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sid: this.sid })
        })
        if (!res.ok) return
        const s = await res.json()
        this.stats = { visits: s.visits || 0, online: s.online || 0, merges: s.merges || 0 }
      } catch (e) { /* stats best-effort, keep silent */ }
    },
    browse() { this.$refs.fileInput.click() },
    onPick(e) { this.setFile(e.target.files[0]); e.target.value = '' },
    onDrop(e) {
      this.dragging = false
      this.setFile(e.dataTransfer.files && e.dataTransfer.files[0])
    },
    setFile(f) {
      this.error = ''; this.done = ''
      if (!f) return
      if (!/\.jar$/i.test(f.name)) { this.error = 'Chỉ nhận file .jar'; return }
      this.revokeBlob()
      this.file = f
    },
    clear() {
      this.file = null; this.error = ''; this.done = ''; this.hint = ''
      this.revokeBlob()
    },
    step(d) { this.setCopies((parseInt(this.copiesText, 10) || 6) + d) },
    onCopiesInput() {
      this.copiesText = this.copiesText.replace(/[^0-9]/g, '').slice(0, 2)
      if (this.copiesText !== '') this.setCopies(parseInt(this.copiesText, 10))
    },
    clampCopies() { this.setCopies(parseInt(this.copiesText, 10)) },
    setCopies(n) {
      if (isNaN(n)) n = 6
      n = Math.max(1, Math.min(MAX_COPIES, n))
      this.copies = n
      this.copiesText = String(n)
    },
    formatSize(n) {
      if (n < 1024) return n + ' B'
      if (n < 1048576) return (n / 1024).toFixed(1) + ' KB'
      return (n / 1048576).toFixed(1) + ' MB'
    },
    revokeBlob() {
      if (this.blobUrl) { URL.revokeObjectURL(this.blobUrl); this.blobUrl = '' }
    },
    downloadAgain() {
      if (!this.blobUrl || !this.done) return
      const a = document.createElement('a')
      a.href = this.blobUrl
      a.download = this.done
      a.click()
    },
    async merge() {
      if (!this.file || this.busy) return
      this.busy = true; this.error = ''; this.done = ''
      const copies = this.clampedCopies
      this.setCopies(copies)
      this.hint = 'Đang ghép ' + this.file.name + ' x' + copies + ' ...'
      try {
        const fd = new FormData()
        fd.append('game', this.file, this.file.name)
        fd.append('copies', String(copies))
        const res = await fetch('/api/merge', { method: 'POST', body: fd })
        if (!res.ok) {
          let msg = 'HTTP ' + res.status
          try { msg = (await res.json()).error || msg } catch (e) { /* keep */ }
          throw new Error(msg)
        }
        const cd = res.headers.get('Content-Disposition') || ''
        const m = /filename="([^"]+)"/.exec(cd)
        const name = m ? m[1] : this.outName
        const blob = await res.blob()
        this.revokeBlob()
        this.blobUrl = URL.createObjectURL(blob)
        this.done = name
        this.hint = ''
        this.downloadAgain()
        this.ping()
      } catch (e) {
        this.error = 'Lỗi: ' + (e && e.message ? e.message : e)
        this.hint = ''
      } finally {
        this.busy = false
      }
    }
  }
}
</script>
