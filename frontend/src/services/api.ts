import axios from 'axios';

export interface PasswordEntry {
  service: string;
  login: string;
  password: string;
}

export interface LoginResponse {
  token: string;
}

export interface RegisterResponse {
  token: string;
}


const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
});


api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new CustomEvent('session-expired'));
    }
    return Promise.reject(error);
  }
);


export const register = (username: string, password: string, masterKey: string) =>
  api.post<RegisterResponse>('/auth/register', { username, password, masterKey });


export const login = (username: string, password: string) =>
  api.post<LoginResponse>('/auth/login', { username, password });


export const getPasswords = (token: string) =>
  api.get<PasswordEntry[]>('/passwords', {
    headers: { 'Authorization': `Bearer ${token}` }
  });


export const addPassword = (token: string, entry: Omit<PasswordEntry, 'id'>) =>
  api.post('/passwords', entry, {
    headers: { 'Authorization': `Bearer ${token}` }
  });


export const updatePassword = (token: string, service: string, entry: Omit<PasswordEntry, 'id'>) =>
  api.put(`/passwords/${service}`, entry, {
    headers: { 'Authorization': `Bearer ${token}` }
  });


export const deletePassword = (token: string, service: string) =>
  api.delete(`/passwords/${service}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });