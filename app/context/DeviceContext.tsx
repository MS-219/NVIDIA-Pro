import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../lib/api';
import type { DashboardPayload, DevicePayload, EarningsPayload } from '../lib/api';
import { useAuth } from './AuthContext';

export type AppDevice = {
  id: number;
  name: string;
  code: string;
  status: 'pending' | 'online' | 'offline';
  hashrate: number;
  temperature: number | null;
  dailyEarnings: number;
  totalEarnings: number;
  lastReportedAt: string | null;
  boundAt: string | null;
};

export type AppEarning = EarningsPayload['items'][number];

type DeviceContextValue = {
  devices: AppDevice[];
  summary: DashboardPayload | null;
  earnings: EarningsPayload;
  loading: boolean;
  mutating: boolean;
  error: string;
  refresh: () => Promise<void>;
  addDevice: (code: string, name?: string) => Promise<AppDevice>;
  removeDevice: (id: number) => Promise<void>;
};

const EMPTY_EARNINGS: EarningsPayload = { items: [], todayEarnings: 0, totalEarnings: 0 };
const DeviceContext = createContext<DeviceContextValue | undefined>(undefined);

function mapDevice(payload: DevicePayload): AppDevice {
  const normalizedStatus = payload.status?.toLowerCase();
  return {
    id: payload.id,
    name: payload.name || 'Orin 节点',
    code: payload.code,
    status: normalizedStatus === 'online' || normalizedStatus === 'offline' ? normalizedStatus : 'pending',
    hashrate: Number(payload.hashrate) || 0,
    temperature: payload.temperature === null ? null : Number(payload.temperature),
    dailyEarnings: Number(payload.dailyEarnings) || 0,
    totalEarnings: Number(payload.totalEarnings) || 0,
    lastReportedAt: payload.lastReportedAt,
    boundAt: payload.boundAt,
  };
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '数据同步失败，请稍后重试';
}

export function DeviceProvider({ children }: React.PropsWithChildren) {
  const { session } = useAuth();
  const token = session?.token;
  const [devices, setDevices] = useState<AppDevice[]>([]);
  const [summary, setSummary] = useState<DashboardPayload | null>(null);
  const [earnings, setEarnings] = useState<EarningsPayload>(EMPTY_EARNINGS);
  const [loading, setLoading] = useState(false);
  const [mutating, setMutating] = useState(false);
  const [error, setError] = useState('');
  const requestVersion = useRef(0);

  const refresh = useCallback(async () => {
    const version = ++requestVersion.current;
    if (!token) {
      setDevices([]);
      setSummary(null);
      setEarnings(EMPTY_EARNINGS);
      setError('');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    try {
      const [devicePayload, summaryPayload, earningsPayload] = await Promise.all([
        api.listDevices(token),
        api.getDashboard(token),
        api.getEarnings(token),
      ]);
      if (version !== requestVersion.current) return;
      setDevices(devicePayload.map(mapDevice));
      setSummary(summaryPayload);
      setEarnings(earningsPayload);
    } catch (requestError) {
      if (version === requestVersion.current) setError(errorMessage(requestError));
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const refreshAggregates = useCallback(async (activeToken: string) => {
    try {
      const [summaryPayload, earningsPayload] = await Promise.all([
        api.getDashboard(activeToken),
        api.getEarnings(activeToken),
      ]);
      setSummary(summaryPayload);
      setEarnings(earningsPayload);
    } catch {
      // The mutation already succeeded. A later pull-to-refresh can recover
      // aggregate data if the dashboard endpoints are temporarily unavailable.
    }
  }, []);

  const addDevice = useCallback(async (code: string, name?: string) => {
    if (!token) throw new Error('请先登录');
    setMutating(true);
    setError('');
    try {
      const device = mapDevice(await api.bindDevice(token, code, name));
      setDevices((current) => [...current.filter((item) => item.id !== device.id), device]);
      await refreshAggregates(token);
      return device;
    } catch (requestError) {
      const message = errorMessage(requestError);
      setError(message);
      throw new Error(message);
    } finally {
      setMutating(false);
    }
  }, [refreshAggregates, token]);

  const removeDevice = useCallback(async (id: number) => {
    if (!token) throw new Error('请先登录');
    setMutating(true);
    setError('');
    try {
      await api.removeDevice(token, id);
      setDevices((current) => current.filter((item) => item.id !== id));
      await refreshAggregates(token);
    } catch (requestError) {
      const message = errorMessage(requestError);
      setError(message);
      throw new Error(message);
    } finally {
      setMutating(false);
    }
  }, [refreshAggregates, token]);

  const value = useMemo(() => ({
    devices,
    summary,
    earnings,
    loading,
    mutating,
    error,
    refresh,
    addDevice,
    removeDevice,
  }), [addDevice, devices, earnings, error, loading, mutating, refresh, removeDevice, summary]);

  return <DeviceContext.Provider value={value}>{children}</DeviceContext.Provider>;
}

export function useDevices(): DeviceContextValue {
  const value = useContext(DeviceContext);
  if (!value) throw new Error('useDevices must be used inside DeviceProvider');
  return value;
}
