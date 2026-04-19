import axios from 'axios';

export interface PasswordEntry {
  service: string;
  login: string;
  password: string;
  id?: string; 
}


const api = axios.create({ baseURL: '/api' });


api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      window.dispatchEvent(new CustomEvent('session-expired'));
    }
    return Promise.reject(err);
  }
);

export const unlockVault = (masterKey: string) => 
  api.post('/unlock', { masterKey });

export const getPasswords = (masterKey: string) => 
  api.get<PasswordEntry[]>('/passwords', { 
    headers: { 'X-Master-Key': masterKey } 
  });

export const addPassword = (masterKey: string, entry: Omit<PasswordEntry, 'id'>) => 
  api.post('/passwords', entry, { 
    headers: { 'X-Master-Key': masterKey } 
  });

export const updatePassword = (masterKey: string, service: string, entry: Omit<PasswordEntry, 'id'>) => 
  api.put(`/passwords/${service}`, entry, { 
    headers: { 'X-Master-Key': masterKey } 
  });

export const deletePassword = (masterKey: string, service: string) => 
  api.delete(`/passwords/${service}`, { 
    headers: { 'X-Master-Key': masterKey } 
  });