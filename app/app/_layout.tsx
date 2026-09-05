import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { AuthProvider } from '../context/AuthContext';
import { DeviceProvider } from '../context/DeviceContext';
import { UpdateProvider } from '../context/UpdateContext';

export default function RootLayout() {
  return (
    <UpdateProvider>
      <AuthProvider>
        <DeviceProvider>
          <StatusBar style="dark" />
          <Stack screenOptions={{ headerShown: false, animation: 'fade' }}>
            <Stack.Screen name="index" />
            <Stack.Screen name="login" />
            <Stack.Screen name="(tabs)" />
            <Stack.Screen name="add-device" options={{ presentation: 'modal', animation: 'slide_from_bottom' }} />
            <Stack.Screen name="device/[id]" options={{ animation: 'slide_from_right' }} />
            <Stack.Screen name="profile/edit" options={{ animation: 'slide_from_right' }} />
            <Stack.Screen name="security" options={{ animation: 'slide_from_right' }} />
            <Stack.Screen name="notifications" options={{ animation: 'slide_from_right' }} />
          </Stack>
        </DeviceProvider>
      </AuthProvider>
    </UpdateProvider>
  );
}
