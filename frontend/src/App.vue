<script setup>
import { ref, onMounted } from 'vue';
import MasterKeyModal from './components/MasterKeyModal.vue';
import PasswordTable from './components/PasswordTable.vue';
import PasswordForm from './components/PasswordForm.vue';
import {
  unlockVault,
  getPasswords,
  addPassword,
  updatePassword,
  deletePassword,
  getStatus
} from './services/api.js';

const masterKey = ref(null);
const passwords = ref([]);
const loading = ref(true);
const error = ref('');
const editingEntry = ref(null);

onMounted(async () => {
    window.addEventListener('session-expired', () => {
    masterKey.value = null;
    passwords.value = [];
    error.value = 'Сессия истекла, введите мастер-ключ заново';
  });
  try {
    await getStatus();
    loading.value = false;
  } catch (err) {
    error.value = 'Ошибка подключения к серверу';
    loading.value = false;
  }
});

const handleUnlock = (key) => {
  masterKey.value = key;
  loadPasswords();
};

const loadPasswords = async () => {
  if (!masterKey.value) return;
  try {
    const data = await getPasswords(masterKey.value);
    passwords.value = data;
    error.value = '';
  } catch (err) {
    const msg = err.response?.data?.message || 'Не удалось загрузить пароли';
    if (msg.includes('Неверный мастер-ключ')) {
      masterKey.value = null;
    }
    error.value = msg;
  }
};

const handleAddPassword = async (entry) => {
  if (!entry) {
    editingEntry.value = null;
    return;
  }
  try {
    if (editingEntry.value?.service) {
      await updatePassword(editingEntry.value.service, entry, masterKey.value);
    } else {
      await addPassword(entry, masterKey.value);
    }
    await loadPasswords();
    editingEntry.value = null;
    error.value = '';
  } catch (err) {
    error.value = err.response?.data?.message || 'Ошибка операции';
  }
};

const handleDelete = async (service) => {
  if (!confirm(`Удалить запись для ${service}? Это действие нельзя отменить!`)) return;
  try {
    await deletePassword(service, masterKey.value);
    await loadPasswords();
    error.value = '';
  } catch (err) {
    error.value = err.response?.data?.message || 'Ошибка удаления';
  }
};

const handleEdit = (entry) => {
  editingEntry.value = { ...entry };
};
</script>

<template>
  <div class="app-container">
    <header>
      <h1>🔒 Менеджер паролей</h1>
      <p>Все ваши пароли надежно зашифрованы</p>
      <div v-if="error" class="error-banner">{{ error }}</div>
    </header>

    <main>
      <PasswordForm
        v-if="editingEntry"
        :initial-data="editingEntry"
        @submit="handleAddPassword"
      />
      <button
        v-else
        class="add-btn"
        @click="editingEntry = { service: '', login: '', password: '' }"
      >
        ➕ Добавить запись
      </button>

      <PasswordTable
        v-if="passwords.length > 0"
        :passwords="passwords"
        :master-key="masterKey"
        @delete="handleDelete"
        @edit="handleEdit"
      />

      <div v-else-if="!loading" class="empty-state">
        <h2>Нет сохраненных паролей</h2>
        <p>Нажмите "Добавить запись", чтобы создать первую запись</p>
      </div>
    </main>

    <footer>
      <button @click="masterKey = null">Сменить мастер-ключ</button>
    </footer>

    <MasterKeyModal
      v-if="!masterKey && !loading"
      @unlock="handleUnlock"
    />

    <div v-if="loading" class="loading">Загрузка...</div>
  </div>
</template>
