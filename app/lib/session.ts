import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const TOKEN_KEY = 'orin_app_access_token';
const USER_KEY = 'orin_app_user';

function webStorage(): Storage | null {
  if (Platform.OS !== 'web' || typeof globalThis.localStorage === 'undefined') return null;
  return globalThis.localStorage;
}

export type SessionUser = {
  userId: number;
  phone: string;
  nickname: string;
};

export type Session = {
  token: string;
  user: SessionUser;
};

export async function readSession(): Promise<Session | null> {
  const storage = webStorage();
  if (storage) {
    const token = storage.getItem(TOKEN_KEY);
    const rawUser = storage.getItem(USER_KEY);
    if (!token || !rawUser) return null;
    try {
      return { token, user: JSON.parse(rawUser) as SessionUser };
    } catch {
      await clearSession();
      return null;
    }
  }
  const [token, rawUser] = await Promise.all([
    SecureStore.getItemAsync(TOKEN_KEY),
    SecureStore.getItemAsync(USER_KEY),
  ]);
  if (!token || !rawUser) return null;
  try {
    return { token, user: JSON.parse(rawUser) as SessionUser };
  } catch {
    await clearSession();
    return null;
  }
}

export async function writeSession(session: Session): Promise<void> {
  const storage = webStorage();
  if (storage) {
    storage.setItem(TOKEN_KEY, session.token);
    storage.setItem(USER_KEY, JSON.stringify(session.user));
    return;
  }
  await Promise.all([
    SecureStore.setItemAsync(TOKEN_KEY, session.token),
    SecureStore.setItemAsync(USER_KEY, JSON.stringify(session.user)),
  ]);
}

export async function clearSession(): Promise<void> {
  const storage = webStorage();
  if (storage) {
    storage.removeItem(TOKEN_KEY);
    storage.removeItem(USER_KEY);
    return;
  }
  await Promise.all([
    SecureStore.deleteItemAsync(TOKEN_KEY),
    SecureStore.deleteItemAsync(USER_KEY),
  ]);
}
