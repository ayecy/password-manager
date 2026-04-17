<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  initialData: { type: Object, default: () => null }
});
const emit = defineEmits(['submit']);

const formData = ref({
  service: props.initialData?.service || '',
  login: props.initialData?.login || '',
  password: props.initialData?.password || ''
});
const showPassword = ref(false);

watch(() => props.initialData, (newVal) => {
  if (newVal) {
    formData.value = { ...newVal };
  }
}, { deep: true });

const handleSubmit = () => {
  const { service, login, password } = formData.value;
  if (!service || !login || !password) return;
  emit('submit', formData.value);
};

const handleCancel = () => {
  emit('submit', null);
};
</script>

<template>
  <div class="form-container">
    <h2>{{ initialData ? 'Редактировать запись' : 'Добавить новую запись' }}</h2>
    <form @submit.prevent="handleSubmit">
      <div class="form-group">
        <label>Сервис</label>
        <input v-model="formData.service" type="text" required placeholder="gmail.com, github.com и т.д." />
      </div>
      <div class="form-group">
        <label>Логин / Email</label>
        <input v-model="formData.login" type="text" required />
      </div>
      <div class="form-group">
        <label>
          Пароль
          <button type="button" class="toggle-password" @click="showPassword = !showPassword">
            {{ showPassword ? 'Скрыть' : 'Показать' }}
          </button>
        </label>
        <input :type="showPassword ? 'text' : 'password'" v-model="formData.password" required />
      </div>
      <div class="form-actions">
        <button type="submit" class="save-btn">
          {{ initialData ? 'Сохранить изменения' : 'Добавить запись' }}
        </button>
        <button v-if="initialData" type="button" class="cancel-btn" @click="handleCancel">
          ❌ Отмена
        </button>
      </div>
    </form>
  </div>
</template>