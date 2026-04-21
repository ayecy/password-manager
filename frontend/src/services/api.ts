import axios from 'axios';

export interface PasswordEntry {
  service: string;
  login: string;
  password: string;
}

export interface AuthResponse {
  token: string;
}

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      window.dispatchEvent(new CustomEvent('session-expired'));
    }
    return Promise.reject(err);
  }
);

// === АВТОРИЗАЦИЯ ===
export const register = (username: string, password: string, masterKey: string) =>
  api.post<AuthResponse>('/auth/register', { username, password, masterKey });

export const login = (username: string, password: string) =>
  api.post<AuthResponse>('/auth/login', { username, password });

// === ХРАНИЛИЩЕ (теперь требуют masterKey для деривации) ===
export const getPasswords = (token: string, masterKey: string) =>
  api.get<PasswordEntry[]>('/passwords', {
    headers: { 'Authorization': `Bearer ${token}`, 'X-Master-Key': masterKey }
  });

export const addPassword = (token: string, masterKey: string, entry: Omit<PasswordEntry, 'id'>) =>
  api.post('/passwords', entry, {
    headers: { 'Authorization': `Bearer ${token}`, 'X-Master-Key': masterKey }
  });

export const updatePassword = (token: string, masterKey: string, service: string, entry: Omit<PasswordEntry, 'id'>) =>
  api.put(`/passwords/${service}`, entry, {
    headers: { 'Authorization': `Bearer ${token}`, 'X-Master-Key': masterKey }
  });

export const deletePassword = (token: string, masterKey: string, service: string) =>
  api.delete(`/passwords/${service}`, {
    headers: { 'Authorization': `Bearer ${token}`, 'X-Master-Key': masterKey }
  });