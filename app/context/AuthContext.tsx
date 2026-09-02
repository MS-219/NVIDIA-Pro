import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from '../lib/api';
import { clearSession, readSession, Session, writeSession } from '../lib/session';

type AuthContextValue = {
  session: Session | null;
  loading: boolean;
  signIn: (phone: string, code: string) => Promise<void>;
  updateNickname: (nickname: string) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: React.PropsWithChildren) {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    readSession()
      .then(setSession)
      .finally(() => setLoading(false));
  }, []);

  const signIn = useCallback(async (phone: string, code: string) => {
    const data = await api.login(phone, code);
    const next: Session = {
      token: data.token,
      user: { userId: data.userId, phone: data.phone, nickname: data.nickname },
    };
    await writeSession(next);
    setSession(next);
  }, []);

  const signOut = useCallback(async () => {
    await clearSession();
    setSession(null);
  }, []);

  const updateNickname = useCallback(async (nickname: string) => {
    if (!session) throw new Error('请先登录');
    const data = await api.updateProfile(session.token, nickname);
    const next: Session = {
      ...session,
      user: { ...session.user, nickname: data.nickname },
    };
    await writeSession(next);
    setSession(next);
  }, [session]);

  const value = useMemo(() => ({ session, loading, signIn, updateNickname, signOut }), [session, loading, signIn, updateNickname, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
