<template>
  <div class="min-h-screen bg-ink text-slate-100 font-sans flex flex-col items-center px-4 py-10">
    <main class="w-full max-w-xl">
      <!-- Header -->
      <header class="text-center mb-6">
        <p class="font-mono text-xs tracking-widest text-accent mb-2">J2ME // MENU BUILDER</p>
        <h1 class="text-2xl font-bold">Gộp game thành menu</h1>
        <p class="text-mutetext text-sm mt-1">Upload 1 file game .jar → nhận lại bản menu nhiều nick</p>
      </header>

      <!-- Card -->
      <section class="bg-card border border-line rounded-2xl p-6 shadow-2xl">
        <!-- Dropzone -->
        <div
          role="button" tabindex="0" aria-label="Chọn file game jar"
          @click="browse" @keydown.enter="browse" @keydown.space.prevent="browse"
          @dragenter.prevent="dragging = true" @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false" @drop.prevent="onDrop"
          :class="[
            'border-2 border-dashed rounded-xl px-6 py-8 text-center cursor-pointer transition-colors duration-200',
            dragging ? 'border-accent bg-accent/10' : 'border-line hover:border-mutetext'
          ]"
        >
          <svg class="mx-auto mb-3 h-10 w-10 text-mutetext" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5" />
          </svg>
          <p class="font-medium">Kéo-thả file .jar vào đây</p>
          <p class="text-sm text-mutetext mt-1">hoặc <span class="text-accent underline">bấm để chọn file</span></p>
          <input ref="fileInput" type="file" accept=".jar" class="hidden" @change="onPick" aria-hidden="true" tabindex="-1">
        </div>

        <!-- File chip -->
        <div v-if="file" class="mt-4 flex items-center gap-3 bg-mutedbox border border-line rounded-xl px-4 py-3">
          <svg class="h-6 w-6 shrink-0 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M20.25 7.5l-.625 12.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5m6 4.125l2.25 2.25m0 0l2.25 2.25M12 13.875l2.25-2.25M12 13.875l-2.25 2.25M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
          </svg>
          <div class="min-w-0 flex-1">
            <p class="font-mono text-sm truncate">{{ file.name }}</p>
            <p class="text-xs text-mutetext">{{ formatSize(file.size) }} · ra {{ outName }}</p>
          </div>
          <button type="button" @click="clear" aria-label="Bỏ file đã chọn"
            class="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-mutetext hover:text-danger transition-colors duration-200">
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Copies -->
        <div class="mt-4 flex items-center justify-between gap-4">
          <label for="copies" class="text-sm font-medium">Số bản (copies)</label>
          <div class="flex items-center gap-2">
            <button type="button" @click="step(-1)" :disabled="copies <= 1" aria-label="Giảm copies"
              class="min-h-[44px] min-w-[44px] rounded-lg border border-line text-lg leading-none disabled:opacity-30 hover:border-accent transition-colors duration-200">−</button>
            <input id="copies" v-model.number="copies" type="number" min="1" max="10"
              class="w-16 min-h-[44px] text-center bg-mutedbox border border-line rounded-lg font-mono focus:border-accent">
            <button type="button" @click="step(1)" :disabled="copies >= 10" aria-label="Tăng copies"
              class="min-h-[44px] min-w-[44px] rounded-lg border border-line text-lg leading-none disabled:opacity-30 hover:border-accent transition-colors duration-200">+</button>
          </div>
        </div>

        <!-- Merge button -->
        <button type="button" @click="merge" :disabled="!file || busy"
          class="mt-5 w-full min-h-[48px] rounded-xl bg-accent text-onaccent font-bold text-base
                 disabled:opacity-40 hover:brightness-110 active:brightness-95 transition-all duration-200
                 flex items-center justify-center gap-2">
          <svg v-if="busy" class="h-5 w-5 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
          {{ busy ? 'Đang ghép...' : 'Bắt đầu ghép' }}
        </button>

        <!-- Status -->
        <div aria-live="polite">
          <p v-if="hint" class="mt-3 text-sm text-mutetext font-mono break-words">{{ hint }}</p>
          <div v-if="error" class="mt-3 flex gap-2 bg-danger/10 border border-danger/50 text-red-200 rounded-xl px-4 py-3 text-sm">
            <svg class="h-5 w-5 shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
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
        file obfuscate quá nặng, thiếu preverify, mã hóa dị hoặc bytecode sửa ẩu
        có thể merge lỗi.
      </aside>

      <footer class="mt-4 text-center text-xs text-mutetext font-mono">
        merge-only · upload ≤ 30MB · copies ≤ 10 · <a class="underline hover:text-slate-300" href="/api/health">health</a>
      </footer>
    </main>
  </div>
</template>

<script>
const MAX_COPIES = 10

export default {
  name: 'App',
  data() {
    return {
      file: null,
      copies: 6,
      busy: false,
      hint: '',
      error: '',
      done: '',
      blobUrl: '',
      dragging: false
    }
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
    step(d) { this.copies = Math.max(1, Math.min(MAX_COPIES, (parseInt(this.copies, 10) || 6) + d)) },
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
      this.copies = copies
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
