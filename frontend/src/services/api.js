import axios from 'axios';

axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      // Очистить ключ и перенаправить на ввод мастер-ключа
      window.dispatchEvent(new CustomEvent('session-expired'));
    }
    return Promise.reject(error);
  }
);

const API_BASE = '/api';

export const unlockVault = async (masterKey) => {
  const response = await axios.post(`${API_BASE}/unlock`, { masterKey });
  return response;
};

export const getPasswords = async (masterKey) => {
  const response = await axios.get(`${API_BASE}/passwords`, {
    headers: { 'X-Master-Key': masterKey }
  });
  return response.data;
};

// Только дешифровка пароля
export const getPasswordDecrypted = async (service, masterKey) => {
  const response = await axios.get(`${API_BASE}/passwords/${service}/decrypt`, {
    headers: { 'X-Master-Key': masterKey }
  });
  return response.data; // { service, login, password }
};

export const addPassword = async (entry, masterKey) => {
  await axios.post(`${API_BASE}/passwords`, entry, {
    headers: { 'X-Master-Key': masterKey }
  });
};

export const updatePassword = async (service, entry, masterKey) => {
  await axios.put(`${API_BASE}/passwords/${service}`, entry, {
    headers: { 'X-Master-Key': masterKey }
  });
};

export const deletePassword = async (service, masterKey) => {
  await axios.delete(`${API_BASE}/passwords/${service}`, {
    headers: { 'X-Master-Key': masterKey }
  });
};

export const getStatus = async () => {
  const response = await axios.get(`${API_BASE}/status`);
  return response.data;
};
