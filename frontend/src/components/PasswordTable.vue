<script setup>
import { ref } from 'vue';

const props = defineProps({
  passwords: { type: Array, required: true, default: () => [] },
  masterKey: { type: String, required: true } // пока просто пробрасывается, вдруг пригодится
});
const emit = defineEmits(['delete', 'edit']);

const copied = ref(null);

// Fallback для копирования (если современный Clipboard API недоступен)
const copyToClipboardFallback = (text) => {
  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    textarea.style.pointerEvents = 'none';

    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();

    const ok = document.execCommand && document.execCommand('copy');
    document.body.removeChild(textarea);
    return ok;
  } catch (e) {
    return false;
  }
};

// Универсальный метод копирования
const copyToClipboard = async (text) => {
  if (!text && text !== '') return false;

  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(String(text));
      return true;
    }
  } catch (e) {
    // игнорируем, пойдём в fallback
  }
  return copyToClipboardFallback(String(text));
};

// ЛОГИН: копируем напрямую из entry.login
const handleCopyLogin = async (entry) => {
  const login = entry?.login;
  if (!login) {
    alert('Логин не найден или не загружен');
    return;
  }

  const success = await copyToClipboard(login);
  if (success) {
    copied.value = `login-${entry.service}`;
    setTimeout(() => (copied.value = null), 2000);
  } else {
    alert('Не удалось скопировать логин');
  }
};

// ПАРОЛЬ: копируем напрямую из entry.password
const handleCopyPassword = async (entry) => {
  const password = entry?.password;
  if (!password) {
    alert('Пароль не найден или не загружен');
    return;
  }

  const success = await copyToClipboard(password);
  if (success) {
    copied.value = `password-${entry.service}`;
    setTimeout(() => (copied.value = null), 2000);
  } else {
    alert('Не удалось скопировать пароль');
  }
};
</script>

<template>
  <div class="table-container">
    <table>
      <thead>
        <tr>
          <th>Сервис</th>
          <th>Логин</th>
          <th>Пароль</th>
          <th>Действия</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(entry, index) in passwords" :key="index">
          <td data-label="Сервис">{{ entry.service }}</td>

          <td data-label="Логин">
            <div class="copy-cell">
              <span class="login-text">{{ entry.login }}</span>
              <button @click="handleCopyLogin(entry)" title="Копировать логин">
                <svg
                  v-if="copied === `login-${entry.service}`"
                  width="18"
                  height="18"
                  fill="green"
                  viewBox="0 0 24 24"
                >
                  <polyline
                    points="20 6 9 17 4 12"
                    stroke="green"
                    stroke-width="2"
                    fill="none"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <svg
                  v-else
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <rect
                    x="9"
                    y="9"
                    width="13"
                    height="13"
                    rx="2"
                    ry="2"
                    stroke="currentColor"
                    stroke-width="2"
                    fill="none"
                  />
                  <path
                    d="M5 15H4C3.44772 15 3 14.5523 3 14V4C3 3.44772 3.44772 3 4 3H14C14.5523 3 15 3.44772 15 4V5"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </div>
          </td>

          <td data-label="Пароль">
            <div class="copy-cell">
              <span class="password-mask">••••••••</span>
              <button @click="handleCopyPassword(entry)" title="Копировать пароль">
                <svg
                  v-if="copied === `password-${entry.service}`"
                  width="18"
                  height="18"
                  fill="green"
                  viewBox="0 0 24 24"
                >
                  <polyline
                    points="20 6 9 17 4 12"
                    stroke="green"
                    stroke-width="2"
                    fill="none"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <svg
                  v-else
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <rect
                    x="9"
                    y="9"
                    width="13"
                    height="13"
                    rx="2"
                    ry="2"
                    stroke="currentColor"
                    stroke-width="2"
                    fill="none"
                  />
                  <path
                    d="M5 15H4C3.44772 15 3 14.5523 3 14V4C3 3.44772 3.44772 3 4 3H14C14.5523 3 15 3.44772 15 4V5"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </div>
          </td>

          <td data-label="Действия">
            <div class="actions">
              <button class="edit-btn" @click="$emit('edit', entry)" title="Редактировать">
                <!-- иконка редактирования -->
              </button>
              <button class="delete-btn" @click="$emit('delete', entry.service)" title="Удалить">
                <!-- иконка удаления -->
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
