<script setup>
import { ref } from 'vue';
import { unlockVault } from '../services/api.js';

const emit = defineEmits(['unlock']);
const masterKey = ref('');
const error = ref('');

const handleSubmit = async () => {
  try {
    await unlockVault(masterKey.value);
    emit('unlock', masterKey.value);
    error.value = '';
  } catch (err) {
    error.value = err.response?.data?.message || 'Ошибка разблокировки хранилища';
  }
};
</script>

<template>
  <div class="modal">
    <div class="modal-content">
      <div class="modal-header">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M19 11H5C3.89543 11 3 11.8954 3 13V21C3 22.1046 3.89543 23 5 23H19C20.1046 23 21 22.1046 21 21V13C21 11.8954 20.1046 11 19 11Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M7 11V7C7 4.23858 9.23858 2 12 2C14.7614 2 17 4.23858 17 7V11" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <h2>Введите мастер-ключ</h2>
      </div>
      
      <div v-if="error" class="error-alert">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
          <path d="M12 8V12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <path d="M12 16H12.01" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>{{ error }}</span>
      </div>
      
      <form @submit.prevent="handleSubmit">
        <input
          type="password"
          v-model="masterKey"
          placeholder="Мастер-ключ"
          autofocus
          required
        />
        <button type="submit">Разблокировать</button>
      </form>
    </div>
  </div>
</template>