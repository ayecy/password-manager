<script setup>
import { ref } from 'vue';

const props = defineProps({
  passwords: { type: Array, required: true, default: () => [] },
  masterKey: { type: String, required: true } // пока просто пробрасывается, вдруг пригодится
});
const emit = defineEmits(['delete', 'edit']);

const copied = ref(null);


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

const copyToClipboard = async (text) => {
  if (!text && text !== '') return false;

  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(String(text));
      return true;
    }
  } catch (e) {
  }
  return copyToClipboardFallback(String(text));
};


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
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18">
                  <path stroke-linecap="round" stroke-linejoin="round" d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L10.582 16.07a4.5 4.5 0 0 1-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 0 1 1.13-1.897l8.932-8.931Zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0 1 15.75 21H5.25A2.25 2.25 0 0 1 3 18.75V8.25A2.25 2.25 0 0 1 5.25 6H10" />
                </svg>
              </button>
              
              <button class="delete-btn" @click="$emit('delete', entry.service)" title="Удалить">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18">
                  <path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
                </svg>
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
